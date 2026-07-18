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
package io.github.malonetalk.convertor;

import io.github.malonetalk.common.SemanticConstants;
import io.github.malonetalk.dto.datasource.PhysicalColumnInfo;
import io.github.malonetalk.dto.datasource.PhysicalTableInfo;
import io.github.malonetalk.dto.prompt.ColumnPromptResponse;
import io.github.malonetalk.dto.prompt.TablePromptResponse;
import io.github.malonetalk.dto.prompt.TableRelationPromptResponse;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.service.semantic.SemanticAvailabilityHelper;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.List;
import java.util.Map;

/** 物理层/语义层 → Agent Prompt DTO 的统一转换器，集中管理所有面向 LLM 的 DTO 映射逻辑。 */
public final class PromptConverter {

    private PromptConverter() {}

    /** 将物理列信息与语义列信息合并，转换为面向 Agent 的列响应 DTO */
    public static ColumnPromptResponse mapColumnPrompt(
            PhysicalColumnInfo physicalColumn, Map<String, ColumnInfo> semanticByName) {
        ColumnInfo semanticColumn =
                semanticByName.get(
                        SemanticUtils.objectKey(
                                physicalColumn.columnName(),
                                "Missing physical column name for prompt conversion."));
        if (!SemanticAvailabilityHelper.isColumnAvailable(
                semanticColumn, SemanticAvailabilityHelper.UsageLevel.AI_PROMPT)) {
            return null;
        }

        String description =
                SemanticUtils.firstNonBlank(
                        semanticColumn == null ? null : semanticColumn.getColumnDescription(),
                        physicalColumn.remarks());

        StringBuilder typeBuilder = new StringBuilder(physicalColumn.typeName());
        if (physicalColumn.columnSize() > 0) {
            typeBuilder.append("(").append(physicalColumn.columnSize()).append(")");
        }

        return ColumnPromptResponse.builder()
                .name(physicalColumn.columnName())
                .type(typeBuilder.toString())
                .primaryKey(physicalColumn.primaryKey())
                .nullable(physicalColumn.nullable())
                .defaultValue(SemanticUtils.trimToNull(physicalColumn.defaultValue()))
                .description(description)
                .build();
    }

    /** 将物理表信息与语义表信息合并，转换为面向 Agent 的表响应 DTO */
    public static TablePromptResponse mapTablePrompt(
            PhysicalTableInfo physicalTable,
            Map<String, TableInfo> semanticByName,
            List<TableRelationPromptResponse> resolvedRelations) {
        TableInfo semanticTable =
                semanticByName.get(
                        SemanticUtils.objectKey(
                                physicalTable.tableName(),
                                "Missing physical table name for prompt conversion."));
        if (!SemanticAvailabilityHelper.isTableAvailable(
                semanticTable, SemanticAvailabilityHelper.UsageLevel.AI_PROMPT)) {
            return null;
        }

        return TablePromptResponse.builder()
                .name(physicalTable.tableName())
                .domain(resolveDomain(semanticTable))
                .description(resolveDescription(physicalTable, semanticTable))
                .relations(resolvedRelations)
                .build();
    }

    private static String resolveDomain(TableInfo semanticTable) {
        return semanticTable == null
                ? SemanticConstants.DEFAULT_DOMAIN
                : SemanticUtils.normalizeDomain(semanticTable.getDomain());
    }

    private static String resolveDescription(
            PhysicalTableInfo physicalTable, TableInfo semanticTable) {
        return SemanticUtils.firstNonBlank(
                semanticTable == null ? null : semanticTable.getTableDescription(),
                physicalTable.remarks());
    }
}
