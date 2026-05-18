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
import io.github.malonetalk.entity.Datasource;
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
                                        new IllegalArgumentException(
                                                "Unsupported datasource type: "
                                                        + datasource.getType()));

        String jdbcUrl = resolveJdbcUrl(datasource, type);

        HikariConfig config = getHikariConfig(datasource, jdbcUrl, type);

        log.info(
                "Creating datasource pool for [{}] type={} url={}",
                datasource.getName(),
                type.getCode(),
                jdbcUrl);

        return new HikariDataSource(config);
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
