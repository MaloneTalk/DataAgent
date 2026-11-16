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

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DataSourceType {
    MYSQL(
            "mysql",
            "com.mysql.cj.jdbc.Driver",
            "jdbc:mysql://",
            "com.mysql:mysql-connector-j",
            Map.of("connectTimeout", "5000", "socketTimeout", "5000")),
    POSTGRESQL(
            "postgresql",
            "org.postgresql.Driver",
            "jdbc:postgresql://",
            "org.postgresql:postgresql",
            Map.of("connectTimeout", "5", "socketTimeout", "5", "loginTimeout", "10")),
    ORACLE(
            "oracle",
            "oracle.jdbc.OracleDriver",
            "jdbc:oracle:thin:@",
            "com.oracle.database.jdbc:ojdbc11",
            Map.of("oracle.net.CONNECT_TIMEOUT", "5000", "oracle.jdbc.ReadTimeout", "5000")),
    CLICKHOUSE(
            "clickhouse",
            "com.clickhouse.jdbc.ClickHouseDriver",
            "jdbc:clickhouse://",
            "com.clickhouse:clickhouse-jdbc",
            Map.of("connection_timeout", "5000", "socket_timeout", "5000")),
    SQLSERVER(
            "sqlserver",
            "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "jdbc:sqlserver://",
            "com.microsoft.sqlserver:mssql-jdbc",
            Map.of("loginTimeout", "5", "socketTimeout", "5000")),
    DAMENG(
            "dameng",
            "dm.jdbc.driver.DmDriver",
            "jdbc:dm://",
            "com.dameng:DmJdbcDriver18",
            Map.of("connectTimeout", "5000", "socketTimeout", "5000")),
    OCEANBASE(
            "oceanbase",
            "com.oceanbase.jdbc.Driver",
            "jdbc:oceanbase://",
            "com.oceanbase:oceanbase-client",
            Map.of("connectTimeout", "5000", "socketTimeout", "5000")),
    SQLITE(
            "sqlite",
            "org.sqlite.JDBC",
            "jdbc:sqlite:",
            "org.xerial:sqlite-jdbc",
            Map.of("busy_timeout", "5000"));

    private final String code;
    private final String driverClassName;
    private final String urlPrefix;
    private final String mavenCoordinates;

    /** Driver-specific units: PostgreSQL/login timeouts use seconds, others milliseconds. */
    private final Map<String, String> inspectionTimeouts;

    public static Optional<DataSourceType> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(t -> t.code.equalsIgnoreCase(code.trim()))
                .findFirst();
    }

    String inspectionJdbcUrl(String jdbcUrl) {
        if (this != POSTGRESQL) return jdbcUrl;
        // PostgreSQL 的 URL 参数优先于 Properties；只改临时测试 URL，保留其他参数原文。
        String[] parts = jdbcUrl.split("\\?", 2);
        Map<String, String> timeouts = getInspectionTimeouts();
        StringJoiner query = new StringJoiner("&");
        if (parts.length == 2) {
            for (String parameter : parts[1].split("&")) {
                String key = parameter.split("=", 2)[0];
                if (!parameter.isEmpty() && !timeouts.containsKey(key)) query.add(parameter);
            }
        }
        timeouts.forEach((key, value) -> query.add(key + "=" + value));
        return parts[0] + "?" + query;
    }

    public String buildJdbcUrl(String host, int port, String databaseName) {
        return switch (this) {
            case MYSQL, POSTGRESQL, CLICKHOUSE, DAMENG, OCEANBASE ->
                    String.format("%s%s:%d/%s", urlPrefix, host, port, databaseName);
            case ORACLE -> String.format("%s%s:%d:%s", urlPrefix, host, port, databaseName);
            case SQLSERVER ->
                    String.format("%s%s:%d;databaseName=%s", urlPrefix, host, port, databaseName);
            case SQLITE ->
                    throw new IllegalArgumentException(
                            "SQLite datasource does not support host/port-based URLs; please"
                                    + " provide a connectionUrl like jdbc:sqlite:/path/to/db");
        };
    }
}
