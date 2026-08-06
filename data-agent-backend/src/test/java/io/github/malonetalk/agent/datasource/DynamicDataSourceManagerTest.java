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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.malonetalk.common.ErrorCode;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.exception.BusinessException;
import org.junit.jupiter.api.Test;

/** 池初始化失败的转译逻辑：缺驱动必须给出可操作的 pom.xml 指引，而不是裸 INTERNAL_ERROR。 */
class DynamicDataSourceManagerTest {

    private final DynamicDataSourceManager manager = new DynamicDataSourceManager();

    @Test
    void missingDriverYieldsActionableBusinessException() {
        Datasource ds = new Datasource();
        ds.setId(1);
        ds.setName("pg-without-driver");
        ds.setType("postgresql");
        ds.setHost("localhost");
        ds.setPort(5432);
        ds.setDatabaseName("db");

        BusinessException ex =
                assertThrows(BusinessException.class, () -> manager.getOrCreateDataSource(ds));

        assertEquals(ErrorCode.JDBC_DRIVER_NOT_FOUND, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("org.postgresql:postgresql"));
        assertTrue(ex.getMessage().contains("pom.xml"));
    }
}
