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
package io.github.malonetalk.service.semantic;

import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.service.semantic.enums.ColumnInvalidReasonEnum;
import io.github.malonetalk.service.semantic.enums.TableInvalidReasonEnum;
import java.util.Objects;

public final class SemanticAvailabilityHelper {

    private SemanticAvailabilityHelper() {}

    public static boolean isTableAvailable(TableInfo tableInfo) {
        Objects.requireNonNull(tableInfo, "tableInfo should not be null");
        return Boolean.TRUE.equals(tableInfo.getIsVisible()) && hasPhysicalTable(tableInfo);
    }

    public static boolean isColumnAvailable(ColumnInfo columnInfo) {
        Objects.requireNonNull(columnInfo, "columnInfo should not be null");
        return Boolean.TRUE.equals(columnInfo.getIsVisible()) && hasPhysicalColumn(columnInfo);
    }

    public static boolean hasPhysicalTable(TableInfo tableInfo) {
        Objects.requireNonNull(tableInfo, "tableInfo should not be null");
        return !Boolean.FALSE.equals(tableInfo.getPhysicalStatus());
    }

    public static boolean hasPhysicalColumn(ColumnInfo columnInfo) {
        Objects.requireNonNull(columnInfo, "columnInfo should not be null");
        return !Boolean.FALSE.equals(columnInfo.getPhysicalStatus());
    }

    public static String tableInvalidReason(TableInfo tableInfo) {
        if (isTableAvailable(tableInfo)) {
            return null;
        }
        if (!hasPhysicalTable(tableInfo)) {
            return TableInvalidReasonEnum.PHYSICAL_TABLE_NOT_FOUND.getReason();
        }
        return TableInvalidReasonEnum.TABLE_HIDDEN.getReason();
    }

    public static String columnInvalidReason(ColumnInfo columnInfo) {
        if (isColumnAvailable(columnInfo)) {
            return null;
        }
        if (!hasPhysicalColumn(columnInfo)) {
            return ColumnInvalidReasonEnum.PHYSICAL_COLUMN_NOT_FOUND.getReason();
        }
        return ColumnInvalidReasonEnum.COLUMN_HIDDEN.getReason();
    }

    public static String unavailableMessage(String fieldName, String objectName, String reason) {
        String fallbackReason = reason == null || reason.isBlank() ? "unavailable" : reason;
        return fieldName + " " + objectName + " is unavailable: " + fallbackReason;
    }
}
