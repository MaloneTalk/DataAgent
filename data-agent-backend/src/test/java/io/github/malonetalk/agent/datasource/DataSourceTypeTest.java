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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DataSourceTypeTest {
    @Test
    void postgresInspectionReplacesConflictingTimeouts() {
        String url =
                DataSourceType.POSTGRESQL.inspectionJdbcUrl(
                        "jdbc:postgresql://localhost/db?connectTimeout=0&socketTimeout=600&socketTimeout=0&loginTimeout=0");
        // No merge function: duplicate timeout keys must fail this assertion too.
        Map<String, String> query =
                Arrays.stream(url.split("\\?", 2)[1].split("&"))
                        .map(parameter -> parameter.split("=", 2))
                        .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));
        assertEquals(
                Map.of("connectTimeout", "5", "socketTimeout", "5", "loginTimeout", "10"), query);
    }

    @Test
    void maxComputeUrlUsesEndpointAndProject() {
        String url =
                DataSourceType.MAXCOMPUTE.buildJdbcUrl(
                        "http://service.cn-hangzhou.maxcompute.aliyun.com/api", null, "my_project");
        assertEquals(
                "jdbc:odps:http://service.cn-hangzhou.maxcompute.aliyun.com/api?project=my_project",
                url);
    }

    @Test
    void maxComputePassesProjectAndCredentialsToDriver() {
        Map<String, String> properties =
                DataSourceType.MAXCOMPUTE.driverProperties(
                        "http://service.cn-hangzhou.maxcompute.aliyun.com/api",
                        null,
                        "my_project",
                        "ak_id",
                        "ak_secret");
        assertEquals("my_project", properties.get("project"));
        assertEquals("ak_id", properties.get("accessId"));
        assertEquals("ak_secret", properties.get("accessKey"));
    }

    @Test
    void maxComputeOmitsBlankDriverProperties() {
        Map<String, String> properties =
                DataSourceType.MAXCOMPUTE.driverProperties(
                        "http://service.cn-hangzhou.maxcompute.aliyun.com/api",
                        null,
                        "",
                        null,
                        null);
        assertFalse(properties.containsKey("project"));
        assertFalse(properties.containsKey("accessId"));
        assertFalse(properties.containsKey("accessKey"));
    }

    @Test
    void nullPortDoesNotBreakOtherTypes() {
        // 未填端口（仅填连接URL）时不得抛 NullPointerException。
        assertEquals(
                Map.of(),
                DataSourceType.MYSQL.driverProperties("localhost", null, "db", "root", "pw"));
        assertEquals(
                "jdbc:odps:http://service.cn-hangzhou.maxcompute.aliyun.com/api?project=my_project",
                DataSourceType.MAXCOMPUTE.buildJdbcUrl(
                        "http://service.cn-hangzhou.maxcompute.aliyun.com/api",
                        null,
                        "my_project"));
    }

    @Test
    void portIsRequiredForHostPortDatabases() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DataSourceType.MYSQL.buildJdbcUrl("localhost", null, "db"));
    }
}
