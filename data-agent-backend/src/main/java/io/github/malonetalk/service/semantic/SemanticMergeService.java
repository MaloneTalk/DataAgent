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

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.github.malonetalk.agent.tools.response.ColumnPromptResponse;
import io.github.malonetalk.agent.tools.response.TablePromptResponse;
import io.github.malonetalk.agent.tools.response.TableRelationResponse;
import io.github.malonetalk.dto.PageResponse;
import io.github.malonetalk.dto.semantic.ColumnSemanticPageQuery;
import io.github.malonetalk.dto.semantic.TableSchemaSemanticPrompt;
import io.github.malonetalk.dto.semantic.TableSemanticPageQuery;
import io.github.malonetalk.entity.Column;
import io.github.malonetalk.entity.ColumnSemantic;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.entity.TableSemantic;
import io.github.malonetalk.infrastructure.SchemaReader;
import io.github.malonetalk.mapper.ColumnSemanticMapper;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.mapper.TableSemanticMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticMergeService {

    private final DatasourceService datasourceService;
    private final SchemaReader schemaReader;
    private final TableSemanticMapper tableSemanticMapper;
    private final ColumnSemanticMapper columnSemanticMapper;
    private final LogicalTableRelationMapper logicalTableRelationMapper;
    private final LogicalTableRelationHelper logicalTableRelationHelper;

    public PageResponse<TablePromptResponse> getVisibleTablePromptPage(int page, int pageSize) {
        Datasource datasource = datasourceService.requireActiveDatasource();
        PageHelper.startPage(page, pageSize);
        Page<TableSemantic> pageResult =
                (Page<TableSemantic>)
                        tableSemanticMapper.selectVisiblePageByDatasourceId(
                                new TableSemanticPageQuery(
                                        datasource.getId(), page, pageSize, null, "asc"),
                                false);

        Map<String, List<LogicalTableRelation>> logicalRelationsBySource =
                buildLogicalRelationsBySource(datasource.getId());

        List<TablePromptResponse> items =
                pageResult.stream()
                        .map(
                                t ->
                                        new TablePromptResponse(
                                                t.getTableName(),
                                                SemanticUtils.normalizeBlankToNull(t.getDomain()),
                                                SemanticUtils.normalizeBlankToNull(
                                                        t.getTableDescription()),
                                                resolveVisibleRelations(
                                                        t.getTableName(),
                                                        logicalRelationsBySource)))
                        .toList();

        return PageResponse.of(items, pageResult.getTotal(), page, pageSize);
    }

    public TableSchemaSemanticPrompt getTableSchema(String tableName, int page, int pageSize) {
        Datasource datasource = datasourceService.requireActiveDatasource();
        String normalizedTableName = SemanticUtils.requireName(tableName, "tableName");

        TableSemantic semanticTable =
                tableSemanticMapper.selectByDatasourceIdAndTableName(
                        datasource.getId(), normalizedTableName);
        if (semanticTable == null || !Boolean.TRUE.equals(semanticTable.getIsVisible())) {
            throw new IllegalArgumentException("表 " + normalizedTableName + " 不存在或不可见。");
        }

        Map<String, Column> physicalByKey = new HashMap<>();
        try {
            for (Column col : schemaReader.getTableSchema(datasource, normalizedTableName)) {
                physicalByKey.put(col.columnName().toLowerCase(Locale.ROOT), col);
            }
        } catch (SchemaReader.SchemaReadException e) {
            throw new IllegalArgumentException(
                    "无法读取表 " + normalizedTableName + " 的 Schema: " + e.getMessage(), e);
        }
        if (physicalByKey.isEmpty()) {
            throw new IllegalArgumentException("表 " + normalizedTableName + " 不存在或没有可读列。");
        }

        PageHelper.startPage(page, pageSize);
        Page<ColumnSemantic> pageResult =
                (Page<ColumnSemantic>)
                        columnSemanticMapper.selectVisiblePageByDatasourceIdAndTableName(
                                new ColumnSemanticPageQuery(
                                        datasource.getId(),
                                        normalizedTableName,
                                        page,
                                        pageSize,
                                        null,
                                        "asc"),
                                false);

        List<ColumnPromptResponse> items =
                pageResult.stream().map(c -> mapColumnPrompt(c, physicalByKey)).toList();

        String description =
                SemanticUtils.normalizeBlankToNull(semanticTable.getTableDescription());
        String domain = SemanticUtils.normalizeBlankToNull(semanticTable.getDomain());

        PageResponse<ColumnPromptResponse> columnPage =
                PageResponse.of(items, pageResult.getTotal(), page, pageSize);
        return new TableSchemaSemanticPrompt(normalizedTableName, domain, description, columnPage);
    }

    private List<TableRelationResponse> resolveVisibleRelations(
            String sourceTableName,
            Map<String, List<LogicalTableRelation>> logicalRelationsBySource) {
        List<LogicalTableRelation> logicalRelations =
                logicalRelationsBySource.getOrDefault(
                        sourceTableName.toLowerCase(Locale.ROOT), Collections.emptyList());
        if (logicalRelations.isEmpty()) {
            return Collections.emptyList();
        }
        List<TableRelationResponse> result = new ArrayList<>();
        for (LogicalTableRelation relation : logicalRelations) {
            if (!Boolean.TRUE.equals(relation.getIsEnabled())) {
                continue;
            }
            List<String> sourceColumns;
            List<String> targetColumns;
            try {
                sourceColumns =
                        logicalTableRelationHelper.fromJson(
                                relation.getSourceColumnNamesJson(), "sourceColumnNames");
                targetColumns =
                        logicalTableRelationHelper.fromJson(
                                relation.getTargetColumnNamesJson(), "targetColumnNames");
            } catch (IllegalArgumentException e) {
                log.warn("跳过无效的逻辑关系 id={}: {}", relation.getId(), e.getMessage());
                continue;
            }
            result.add(
                    new TableRelationResponse(
                            relation.getRelationType(),
                            LogicalTableRelationHelper.RELATION_SOURCE_LOGICAL,
                            relation.getSourceTableName(),
                            sourceColumns,
                            relation.getTargetTableName(),
                            targetColumns,
                            relation.getDescription()));
        }
        return result;
    }

    private ColumnPromptResponse mapColumnPrompt(
            ColumnSemantic semanticColumn, Map<String, Column> physicalByKey) {
        Column physicalColumn =
                physicalByKey.get(semanticColumn.getColumnName().toLowerCase(Locale.ROOT));
        String description =
                SemanticUtils.normalizeBlankToNull(semanticColumn.getColumnDescription());
        if (description == null && physicalColumn != null) {
            description = SemanticUtils.normalizeBlankToNull(physicalColumn.remarks());
        }
        String typeText = "";
        boolean primaryKey = false;
        boolean nullable = true;
        String defaultValue = null;
        if (physicalColumn != null) {
            StringBuilder typeBuilder = new StringBuilder(physicalColumn.typeName());
            if (physicalColumn.columnSize() > 0) {
                typeBuilder.append("(").append(physicalColumn.columnSize()).append(")");
            }
            typeText = typeBuilder.toString();
            primaryKey = physicalColumn.primaryKey();
            nullable = physicalColumn.nullable();
            defaultValue = SemanticUtils.normalizeBlankToNull(physicalColumn.defaultValue());
        }
        return new ColumnPromptResponse(
                semanticColumn.getColumnName(),
                typeText,
                primaryKey,
                nullable,
                defaultValue,
                description);
    }

    private Map<String, List<LogicalTableRelation>> buildLogicalRelationsBySource(
            Integer datasourceId) {
        List<LogicalTableRelation> allRelations =
                logicalTableRelationMapper.selectByDatasourceId(datasourceId);
        Map<String, List<LogicalTableRelation>> result = new HashMap<>();
        for (LogicalTableRelation relation : allRelations) {
            String key = relation.getSourceTableName().toLowerCase(Locale.ROOT);
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(relation);
        }
        return result;
    }
}
