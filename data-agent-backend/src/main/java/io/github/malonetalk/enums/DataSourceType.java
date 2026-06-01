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
package io.github.malonetalk.enums;

import java.util.Arrays;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DataSourceType {
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://"),
    POSTGRESQL("postgresql", "org.postgresql.Driver", "jdbc:postgresql://"),
    ORACLE("oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@");

    private final String code;
    private final String driverClassName;
    private final String urlPrefix;

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
