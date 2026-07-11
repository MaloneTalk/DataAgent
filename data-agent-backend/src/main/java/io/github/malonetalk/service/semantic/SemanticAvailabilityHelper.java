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

public final class SemanticAvailabilityHelper {

    private SemanticAvailabilityHelper() {}

    public enum UsageLevel {
        AI_PROMPT,
        FRONTEND_DISPLAY,
        USER_OPERATION
    }

    public static boolean isTableAvailable(TableInfo tableInfo, UsageLevel usageLevel) {
        if (tableInfo == null) {
            return true;
        }
        if (usageLevel == UsageLevel.FRONTEND_DISPLAY) {
            return true;
        }
        return Boolean.TRUE.equals(tableInfo.getIsVisible()) && hasPhysicalTable(tableInfo);
    }

    public static boolean isColumnAvailable(ColumnInfo columnInfo, UsageLevel usageLevel) {
        if (columnInfo == null) {
            return true;
        }
        if (usageLevel == UsageLevel.FRONTEND_DISPLAY) {
            return true;
        }
        return Boolean.TRUE.equals(columnInfo.getIsVisible()) && hasPhysicalColumn(columnInfo);
    }

    public static boolean hasPhysicalTable(TableInfo tableInfo) {
        return tableInfo == null || !Boolean.FALSE.equals(tableInfo.getPhysicalStatus());
    }

    public static boolean hasPhysicalColumn(ColumnInfo columnInfo) {
        return columnInfo == null || !Boolean.FALSE.equals(columnInfo.getPhysicalStatus());
    }

    public static boolean isUnavailable(TableInfo tableInfo, UsageLevel usageLevel) {
        return !isTableAvailable(tableInfo, usageLevel);
    }

    public static boolean isUnavailable(ColumnInfo columnInfo, UsageLevel usageLevel) {
        return !isColumnAvailable(columnInfo, usageLevel);
    }

    public static String tableInvalidReason(TableInfo tableInfo, UsageLevel usageLevel) {
        if (isTableAvailable(tableInfo, usageLevel)) {
            return null;
        }
        if (!hasPhysicalTable(tableInfo)) {
            return "物理表不存在";
        }
        if (!Boolean.TRUE.equals(tableInfo.getIsVisible())) {
            return "表已隐藏";
        }
        return "表不可用";
    }

    public static String columnInvalidReason(ColumnInfo columnInfo, UsageLevel usageLevel) {
        if (isColumnAvailable(columnInfo, usageLevel)) {
            return null;
        }
        if (!hasPhysicalColumn(columnInfo)) {
            return "物理列不存在";
        }
        if (!Boolean.TRUE.equals(columnInfo.getIsVisible())) {
            return "列已隐藏";
        }
        return "列不可用";
    }

    public static String unavailableMessage(String fieldName, String objectName, String reason) {
        String fallbackReason = reason == null || reason.isBlank() ? "不可用" : reason;
        return fieldName + " " + objectName + " is unavailable: " + fallbackReason;
    }
}
