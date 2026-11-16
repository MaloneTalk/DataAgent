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
}
