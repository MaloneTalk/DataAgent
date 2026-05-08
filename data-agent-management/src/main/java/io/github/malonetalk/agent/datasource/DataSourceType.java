package io.github.malonetalk.agent.datasource;

import java.util.Arrays;
import java.util.Optional;

public enum DataSourceType {

    MYSQL(
            "mysql",
            "com.mysql.cj.jdbc.Driver",
            "jdbc:mysql://"),
    POSTGRESQL(
            "postgresql",
            "org.postgresql.Driver",
            "jdbc:postgresql://"),
    ORACLE(
            "oracle",
            "oracle.jdbc.OracleDriver",
            "jdbc:oracle:thin:@");

    private final String code;
    private final String driverClassName;
    private final String urlPrefix;

    DataSourceType(String code, String driverClassName, String urlPrefix) {
        this.code = code;
        this.driverClassName = driverClassName;
        this.urlPrefix = urlPrefix;
    }

    public String getCode() {
        return code;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public static Optional<DataSourceType> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(t -> t.code.equalsIgnoreCase(code.trim()))
                .findFirst();
    }

    public String buildJdbcUrl(String host, int port, String databaseName) {
        return switch (this) {
            case MYSQL -> String.format("%s%s:%d/%s", urlPrefix, host, port, databaseName);
            case POSTGRESQL -> String.format("%s%s:%d/%s", urlPrefix, host, port, databaseName);
            case ORACLE -> String.format("%s%s:%d:%s", urlPrefix, host, port, databaseName);
        };
    }
}
