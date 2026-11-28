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
import java.util.LinkedHashMap;
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
            Map.of("busy_timeout", "5000")),
    MAXCOMPUTE(
            "maxcompute",
            "com.aliyun.odps.jdbc.OdpsDriver",
            "jdbc:odps:",
            "com.aliyun.odps:odps-jdbc",
            Map.of("connect_timeout", "10000", "read_timeout", "60000")) {
        @Override
        public Map<String, String> driverProperties(
                String host, Integer port, String databaseName, String username, String password) {
            // ODPS 驱动要求 Project 名；账号密码用 accessId / accessKey 传递。
            // accessKey 形如 "xxx:yyy" 时会被驱动 URLDecode，冒号无需额外转义。
            Map<String, String> properties = new LinkedHashMap<>();
            if (databaseName != null && !databaseName.isBlank()) {
                properties.put("project", databaseName);
            }
            if (username != null && !username.isBlank()) {
                properties.put("accessId", username);
            }
            if (password != null && !password.isBlank()) {
                properties.put("accessKey", password);
            }
            return properties;
        }
    };

    private final String code;
    private final String driverClassName;
    private final String urlPrefix;
    private final String mavenCoordinates;

    /** Driver-specific units: PostgreSQL/login timeouts use seconds, others milliseconds. */
    private final Map<String, String> inspectionTimeouts;

    /**
     * 驱动在 Properties 中额外要求的连接参数（键为驱动属性名）。默认无需额外参数。
     *
     * <p>MaxCompute 的 ODPS 驱动要求显式给出 Project 名，账号密码则通过 {@code accessId} /
     * {@code accessKey} 传递（驱动同时兼容 {@code user} / {@code password} 别名）。
     */
    public Map<String, String> driverProperties(
            String host, Integer port, String databaseName, String username, String password) {
        return Map.of();
    }

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

    public String buildJdbcUrl(String host, Integer port, String databaseName) {
        return switch (this) {
            case MYSQL, POSTGRESQL, CLICKHOUSE, DAMENG, OCEANBASE ->
                    String.format("%s%s:%d/%s", urlPrefix, host, requirePort(port), databaseName);
            case ORACLE ->
                    String.format("%s%s:%d:%s", urlPrefix, host, requirePort(port), databaseName);
            case SQLSERVER ->
                    String.format(
                            "%s%s:%d;databaseName=%s",
                            urlPrefix, host, requirePort(port), databaseName);
            case MAXCOMPUTE ->
                    // MaxCompute 的「主机」是服务 Endpoint（如
                    // http://service.cn-hangzhou.maxcompute.aliyun.com/api），
                    // 「数据库名」对应 ODPS Project 名。端口由 Endpoint 自身决定，故不使用 port。
                    String.format("%s%s?project=%s", urlPrefix, host, databaseName);
            case SQLITE ->
                    throw new IllegalArgumentException(
                            "SQLite datasource does not support host/port-based URLs; please"
                                    + " provide a connectionUrl like jdbc:sqlite:/path/to/db");
        };
    }

    /** 需要端口的数据库在端口缺失时给出明确错误，而不是触发自动拆箱的 NullPointerException。 */
    private static int requirePort(Integer port) {
        if (port == null) {
            throw new IllegalArgumentException(
                    "Datasource port is required for this database type.");
        }
        return port;
    }
}
