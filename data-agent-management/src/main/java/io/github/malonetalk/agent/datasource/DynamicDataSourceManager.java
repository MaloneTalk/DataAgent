package io.github.malonetalk.agent.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.malonetalk.entity.Datasource;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DynamicDataSourceManager {

    private static final Logger logger = LoggerFactory.getLogger(DynamicDataSourceManager.class);

    private static final int MAX_POOL_SIZE = 5;
    private static final int MIN_IDLE = 1;
    private static final long IDLE_TIMEOUT = 300000L;
    private static final long CONNECTION_TIMEOUT = 10000L;
    private static final long MAX_LIFETIME = 600000L;

    private final ConcurrentHashMap<Integer, HikariDataSource> dataSourcePool =
            new ConcurrentHashMap<>();

    public javax.sql.DataSource getOrCreateDataSource(Datasource datasource) {
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

        logger.info(
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
            logger.info("Closed datasource pool for datasourceId={}", datasourceId);
        }
    }

    @PreDestroy
    public void destroy() {
        logger.info("Shutting down all dynamic datasource pools, count={}", dataSourcePool.size());
        dataSourcePool.forEach(
                (id, ds) -> {
                    if (!ds.isClosed()) {
                        ds.close();
                    }
                });
        dataSourcePool.clear();
    }
}
