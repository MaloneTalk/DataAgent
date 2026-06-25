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
import io.github.malonetalk.dto.prompt.ColumnPromptResponse;
import io.github.malonetalk.dto.prompt.TablePromptResponse;
import io.github.malonetalk.dto.prompt.TableRelationPromptResponse;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.experimental.UtilityClass;

/** 物理层/语义层 → Agent Prompt DTO 的统一转换器，集中管理所有面向 LLM 的 DTO 映射逻辑。 */
@UtilityClass
public class PromptConverter {

    /** 将物理列信息与语义列信息合并，转换为面向 Agent 的列响应 DTO */
    public static ColumnPromptResponse mapColumnPrompt(
            io.github.malonetalk.dto.datasource.ColumnInfo physicalColumn,
            Map<String, ColumnInfo> semanticByKey) {
        ColumnInfo semanticColumn =
                semanticByKey.get(physicalColumn.columnName().toLowerCase(Locale.ROOT));
        if (semanticColumn != null && !Boolean.TRUE.equals(semanticColumn.getIsVisible())) {
            return null;
        }

        String description =
                semanticColumn == null
                        ? null
                        : SemanticUtils.normalizeBlankToNull(semanticColumn.getColumnDescription());
        if (description == null) {
            description = SemanticUtils.normalizeBlankToNull(physicalColumn.remarks());
        }

        StringBuilder typeBuilder = new StringBuilder(physicalColumn.typeName());
        if (physicalColumn.columnSize() > 0) {
            typeBuilder.append("(").append(physicalColumn.columnSize()).append(")");
        }

        return new ColumnPromptResponse(
                physicalColumn.columnName(),
                typeBuilder.toString(),
                physicalColumn.primaryKey(),
                physicalColumn.nullable(),
                SemanticUtils.normalizeBlankToNull(physicalColumn.defaultValue()),
                description);
    }

    /** 将物理表信息与语义表信息合并，转换为面向 Agent 的表响应 DTO */
    public static TablePromptResponse mapTablePrompt(
            io.github.malonetalk.dto.datasource.TableInfo physicalTable,
            Map<String, TableInfo> semanticByKey,
            List<TableRelationPromptResponse> resolvedRelations) {
        TableInfo semanticTable =
                semanticByKey.get(physicalTable.tableName().toLowerCase(Locale.ROOT));
        if (semanticTable != null && !Boolean.TRUE.equals(semanticTable.getIsVisible())) {
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
                : normalizeDomain(semanticTable.getDomain());
    }

    private static String resolveDescription(
            io.github.malonetalk.dto.datasource.TableInfo physicalTable, TableInfo semanticTable) {
        String description =
                semanticTable == null
                        ? null
                        : SemanticUtils.normalizeBlankToNull(semanticTable.getTableDescription());
        if (description == null) {
            description = SemanticUtils.normalizeBlankToNull(physicalTable.remarks());
        }
        return description;
    }

    private static String normalizeDomain(String domain) {
        String normalized = SemanticUtils.normalizeBlankToNull(domain);
        return normalized == null ? SemanticConstants.DEFAULT_DOMAIN : normalized;
    }
}
