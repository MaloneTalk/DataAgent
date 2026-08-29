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

import io.github.malonetalk.dto.prompt.ColumnPromptResponse;
import io.github.malonetalk.dto.prompt.TablePromptResponse;
import io.github.malonetalk.dto.prompt.TableRelationPromptResponse;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.service.semantic.SemanticAvailabilityHelper;
import io.github.malonetalk.service.semantic.enums.UsageLevelEnum;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.List;

/** Converts synced semantic-layer snapshots into Agent-facing prompt DTOs. */
public final class PromptConverter {

    private PromptConverter() {}

    public static ColumnPromptResponse mapColumnPrompt(ColumnInfo column) {
        if (!SemanticAvailabilityHelper.isColumnAvailable(column, UsageLevelEnum.AI_PROMPT)) {
            return null;
        }
        return ColumnPromptResponse.builder()
                .name(column.getColumnName())
                .type(SemanticUtils.trimToNull(column.getTypeName()))
                .primaryKey(column.getPrimaryKey())
                .description(SemanticUtils.trimToNull(column.getColumnDescription()))
                .indexInfo(SemanticUtils.trimToNull(column.getIndexInfo()))
                .build();
    }

    public static TablePromptResponse mapTablePrompt(
            TableInfo table, List<TableRelationPromptResponse> resolvedRelations) {
        if (!SemanticAvailabilityHelper.isTableAvailable(table, UsageLevelEnum.AI_PROMPT)) {
            return null;
        }
        return TablePromptResponse.builder()
                .name(table.getTableName())
                .domain(SemanticUtils.normalizeDomain(table.getDomain()))
                .description(SemanticUtils.trimToNull(table.getTableDescription()))
                .relations(resolvedRelations)
                .build();
    }
}
