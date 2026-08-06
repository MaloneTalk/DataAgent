/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * limitations under the License.
 */
package io.github.malonetalk.agent.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DynamicDataSourceManager {

    private static final int MAX_POOL_SIZE = 5;
    private static final int MIN_IDLE = 1;
    private static final long IDLE_TIMEOUT = 300000L;
    private static final long CONNECTION_TIMEOUT = 10000L;
    private static final long MAX_LIFETIME = 600000L;

    private final ConcurrentHashMap<Integer, HikariDataSource> dataSourcePool =
            new ConcurrentHashMap<>();

    public DataSource getOrCreateDataSource(Datasource datasource) {
        return dataSourcePool.computeIfAbsent(
                datasource.getId(), id -> createDataSource(datasource));
    }

    private HikariDataSource createDataSource(Datasource datasource) {
        DataSourceType type =
                DataSourceType.fromCode(datasource.getType())
                        .orElseThrow(
                                () ->
                                        BusinessException.of(
                                                ErrorCode.UNSUPPORTED_DATASOURCE_TYPE,
                                                "Unsupported datasource type: "
                                                        + datasource.getType()));

        String jdbcUrl = resolveJdbcUrl(datasource, type);

        // 注意：HikariConfig.setDriverClassName() 会同步加载驱动类，缺驱动时在此即抛异常，
        // 因此 config 构建与池创建必须同在一个 try 内，才能被转译为可操作的缺驱动提示。
        try {
            HikariConfig config = getHikariConfig(datasource, jdbcUrl, type);

            log.info(
                    "Creating datasource pool for [{}] type={} url={}",
                    datasource.getName(),
                    type.getCode(),
                    jdbcUrl);

            return new HikariDataSource(config);
        } catch (RuntimeException e) {
            throw translateInitializationFailure(type, e);
        } catch (Error e) {
            // 驱动依赖缺失时 JVM 抛的是 NoClassDefFoundError（Error 而非 Exception），
            // 且 Error 会绕过 ToolExceptionMapper 的 catch(Exception)，必须在此一并转译。
            throw translateInitializationFailure(type, e);
        }
    }

    /**
     * 区分池初始化失败的两类原因：驱动未打包（给出 pom.xml 修复指引）与连接失败（原样抛出，
     * 交由全局异常映射处理）。
     */
    private RuntimeException translateInitializationFailure(
            DataSourceType type, Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ClassNotFoundException
                    || current instanceof NoClassDefFoundError) {
                return BusinessException.of(
                        ErrorCode.JDBC_DRIVER_NOT_FOUND,
                        String.format(
                                "未找到数据库驱动 %s（%s 类型）。后端默认仅内置 MySQL 驱动，"
                                        + "请在 data-agent-backend/pom.xml 中添加依赖 %s 后重新构建并启动后端。",
                                type.getDriverClassName(),
                                type.getCode(),
                                type.getMavenCoordinates()),
                        exception);
            }
        }
        if (exception instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException("Unexpected datasource initialization failure", exception);
    }

    private static HikariConfig getHikariConfig(
            Datasource datasource, String jdbcUrl, DataSourceType type) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(datasource.getUsername());
        config.setPassword(datasource.getPassword());
        config.setDriverClassName(type.getDriverClassName());

        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(MIN_IDLE);
        config.setIdleTimeout(IDLE_TIMEOUT);
        config.setConnectionTimeout(CONNECTION_TIMEOUT);
        config.setMaxLifetime(MAX_LIFETIME);

        config.setPoolName("ds-" + datasource.getId() + "-" + type.getCode());
        return config;
    }

    private String resolveJdbcUrl(Datasource datasource, DataSourceType type) {
        if (datasource.getConnectionUrl() != null && !datasource.getConnectionUrl().isBlank()) {
            return datasource.getConnectionUrl();
        }
        return type.buildJdbcUrl(
                datasource.getHost(), datasource.getPort(), datasource.getDatabaseName());
    }

    public void removeDataSource(Integer datasourceId) {
        HikariDataSource ds = dataSourcePool.remove(datasourceId);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("Closed datasource pool for datasourceId={}", datasourceId);
        }
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down all dynamic datasource pools, count={}", dataSourcePool.size());
        dataSourcePool.forEach(
                (id, ds) -> {
                    if (!ds.isClosed()) {
                        ds.close();
                    }
                });
        dataSourcePool.clear();
    }
}
