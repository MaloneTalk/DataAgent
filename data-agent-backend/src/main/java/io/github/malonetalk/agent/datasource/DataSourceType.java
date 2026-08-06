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
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DataSourceType {
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://", "com.mysql:mysql-connector-j"),
    POSTGRESQL(
            "postgresql",
            "org.postgresql.Driver",
            "jdbc:postgresql://",
            "org.postgresql:postgresql"),
    ORACLE(
            "oracle",
            "oracle.jdbc.OracleDriver",
            "jdbc:oracle:thin:@",
            "com.oracle.database.jdbc:ojdbc11"),
    CLICKHOUSE(
            "clickhouse",
            "com.clickhouse.jdbc.ClickHouseDriver",
            "jdbc:clickhouse://",
            "com.clickhouse:clickhouse-jdbc"),
    SQLSERVER(
            "sqlserver",
            "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "jdbc:sqlserver://",
            "com.microsoft.sqlserver:mssql-jdbc"),
    DAMENG("dameng", "dm.jdbc.driver.DmDriver", "jdbc:dm://", "com.dameng:DmJdbcDriver18"),
    OCEANBASE(
            "oceanbase",
            "com.oceanbase.jdbc.Driver",
            "jdbc:oceanbase://",
            "com.oceanbase:oceanbase-client"),
    SQLITE("sqlite", "org.sqlite.JDBC", "jdbc:sqlite:", "org.xerial:sqlite-jdbc");

    private final String code;
    private final String driverClassName;
    private final String urlPrefix;
    private final String mavenCoordinates;

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
