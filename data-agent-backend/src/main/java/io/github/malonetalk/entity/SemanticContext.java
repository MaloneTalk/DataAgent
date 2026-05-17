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
package io.github.malonetalk.entity;

import static io.github.malonetalk.common.SemanticConstants.RELATION_KEY_SEPARATOR;
import static io.github.malonetalk.utils.SemanticStringUtils.normalizeBlankToNull;
import static io.github.malonetalk.utils.SemanticStringUtils.normalizeName;

import io.github.malonetalk.agent.datasource.SchemaReader;
import io.github.malonetalk.agent.datasource.TableRelationInfo;
import io.github.malonetalk.dto.semantic.RelationValidationRequest;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.service.semantic.SemanticManager.TableMergeSnapshot;
import io.github.malonetalk.service.semantic.SemanticManager.VisibilityContext;
import io.github.malonetalk.service.semantic.SemanticResolver;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SemanticContext {

    private static final Logger logger = LoggerFactory.getLogger(SemanticContext.class);

    private final Datasource datasource;
    private final VisibilityContext visibilityContext;
    private final SemanticResolver semanticResolver;
    private final LogicalTableRelationMapper logicalTableRelationMapper;
    private final LogicalTableRelationHelper logicalTableRelationHelper;
    private final SchemaReader schemaReader;
    private final SemanticCache cache = new SemanticCache();

    public SemanticContext(
            Datasource datasource,
            VisibilityContext visibilityContext,
            SemanticResolver semanticResolver,
            LogicalTableRelationMapper logicalTableRelationMapper,
            LogicalTableRelationHelper logicalTableRelationHelper,
            SchemaReader schemaReader) {
        this.datasource = datasource;
        this.visibilityContext = visibilityContext;
        this.semanticResolver = semanticResolver;
        this.logicalTableRelationMapper = logicalTableRelationMapper;
        this.logicalTableRelationHelper = logicalTableRelationHelper;
        this.schemaReader = schemaReader;
    }

    public List<ResolvedTable> listTables() {
        return cache.getOrComputeTables(
                () ->
                        visibilityContext.mergeTables().stream()
                                .map(this::resolveCachedTable)
                                .toList());
    }

    public ResolvedTable findTable(String tableName) {
        TableMergeSnapshot snapshot = visibilityContext.findMergedTable(tableName);
        if (snapshot == null) {
            return null;
        }
        return resolveCachedTable(snapshot);
    }

    public List<ResolvedColumn> listColumns(String tableName) {
        String tableKey = normalizeName(tableName);
        if (tableKey.isBlank()) {
            return Collections.emptyList();
        }
        List<ResolvedColumn> cachedColumns = cache.findColumns(tableKey);
        if (cachedColumns != null) {
            return cachedColumns;
        }
        ResolvedTable table = findTable(tableName);
        if (table == null || !table.hasPhysicalTable()) {
            return Collections.emptyList();
        }
        String canonicalTableName = table.canonicalName();
        List<ResolvedColumn> resolvedColumns =
                visibilityContext.mergeColumns(canonicalTableName).stream()
                        .map(
                                snapshot ->
                                        semanticResolver.resolveColumn(
                                                canonicalTableName, snapshot))
                        .toList();
        cache.putColumns(tableKey, canonicalTableName, resolvedColumns);
        return resolvedColumns;
    }

    public ResolvedColumn findColumn(String tableName, String columnName) {
        String tableKey = normalizeName(tableName);
        String columnKey = normalizeName(columnName);
        if (tableKey.isBlank() || columnKey.isBlank()) {
            return null;
        }
        listColumns(tableName);
        return cache.findColumn(tableKey, columnKey);
    }

    public String validateVisibleTable(String tableName, String roleLabel) {
        ResolvedTable table = findTable(tableName);
        if (table == null || !table.hasPhysicalTable()) {
            return roleLabel + " table does not exist: " + tableName;
        }
        if (!table.visible()) {
            return roleLabel + " table is not visible: " + tableName;
        }
        return null;
    }

    public String validateVisibleColumns(
            String tableName, List<String> columnNames, String roleLabel) {
        for (String columnName : columnNames) {
            ResolvedColumn column = findColumn(tableName, columnName);
            if (column == null || !column.hasPhysicalColumn()) {
                return roleLabel + " column does not exist: " + tableName + "." + columnName;
            }
            if (!column.visible()) {
                return roleLabel + " column is not visible: " + tableName + "." + columnName;
            }
        }
        return null;
    }

    public RelationState evaluateRelationState(RelationValidationRequest request) {
        if (!request.enabled()) {
            return new RelationState(false, "Relation is disabled.");
        }
        if (request.sourceColumnErrorMessage() != null) {
            return new RelationState(false, request.sourceColumnErrorMessage());
        }
        if (request.targetColumnErrorMessage() != null) {
            return new RelationState(false, request.targetColumnErrorMessage());
        }
        String sourceTableError = validateVisibleTable(request.sourceTableName(), "Source");
        if (sourceTableError != null) {
            return new RelationState(false, sourceTableError);
        }
        String targetTableError = validateVisibleTable(request.targetTableName(), "Target");
        if (targetTableError != null) {
            return new RelationState(false, targetTableError);
        }
        String sourceColumnError =
                validateVisibleColumns(
                        request.sourceTableName(), request.sourceColumnNames(), "Source");
        if (sourceColumnError != null) {
            return new RelationState(false, sourceColumnError);
        }
        String targetColumnError =
                validateVisibleColumns(
                        request.targetTableName(), request.targetColumnNames(), "Target");
        if (targetColumnError != null) {
            return new RelationState(false, targetColumnError);
        }
        return new RelationState(true, null);
    }

    public boolean isRelationEndpointVisible(
            String sourceTableName,
            List<String> sourceColumnNames,
            String targetTableName,
            List<String> targetColumnNames) {
        ResolvedTable sourceTable = findTable(sourceTableName);
        if (sourceTable == null || !sourceTable.hasPhysicalTable() || !sourceTable.visible()) {
            return false;
        }
        ResolvedTable targetTable = findTable(targetTableName);
        if (targetTable == null || !targetTable.hasPhysicalTable() || !targetTable.visible()) {
            return false;
        }
        for (String columnName : sourceColumnNames) {
            ResolvedColumn column = findColumn(sourceTableName, columnName);
            if (column == null || !column.hasPhysicalColumn() || !column.visible()) {
                return false;
            }
        }
        for (String columnName : targetColumnNames) {
            ResolvedColumn column = findColumn(targetTableName, columnName);
            if (column == null || !column.hasPhysicalColumn() || !column.visible()) {
                return false;
            }
        }
        return true;
    }

    public List<ResolvedRelation> listVisibleRelations(String tableName) {
        String tableKey = normalizeName(tableName);
        if (tableKey.isBlank()) {
            return Collections.emptyList();
        }
        List<ResolvedRelation> cached = cache.findRelations(tableKey);
        if (cached != null) {
            return cached;
        }
        ResolvedTable table = findTable(tableName);
        if (table == null || !table.hasPhysicalTable()) {
            return Collections.emptyList();
        }
        String canonical = table.canonicalName();
        LinkedHashMap<String, ResolvedRelation> merged = new LinkedHashMap<>();

        for (TableRelationInfo physical :
                schemaReader.getImportedRelations(datasource, canonical)) {
            ResolvedRelation resolved = resolvePhysicalRelation(physical);
            if (resolved != null) {
                merged.put(relationKey(resolved), resolved);
            }
        }

        List<LogicalTableRelation> logicalRelations =
                logicalTableRelationMapper.selectEnabledByDatasourceIdAndSourceTable(
                        datasource.getId(), canonical);
        for (LogicalTableRelation entity : logicalRelations) {
            ResolvedRelation resolved = resolveLogicalRelation(entity);
            if (resolved != null) {
                merged.putIfAbsent(relationKey(resolved), resolved);
            }
        }

        List<ResolvedRelation> result = List.copyOf(merged.values());
        cache.putRelations(tableKey, canonical, result);
        return result;
    }

    private ResolvedRelation resolvePhysicalRelation(TableRelationInfo relation) {
        List<String> sourceColumnNames = relation.sourceColumnNames();
        List<String> targetColumnNames = relation.targetColumnNames();
        if (!isRelationEndpointVisible(
                relation.sourceTableName(), sourceColumnNames,
                relation.targetTableName(), targetColumnNames)) {
            return null;
        }
        return new ResolvedRelation(
                null,
                LogicalTableRelationHelper.RELATION_TYPE_FOREIGN_KEY,
                LogicalTableRelationHelper.RELATION_SOURCE_PHYSICAL,
                relation.sourceTableName(),
                sourceColumnNames,
                relation.targetTableName(),
                targetColumnNames,
                normalizeBlankToNull(relation.description()),
                true);
    }

    private ResolvedRelation resolveLogicalRelation(LogicalTableRelation entity) {
        if (!Boolean.TRUE.equals(entity.getIsEnabled())) {
            return null;
        }
        List<String> sourceColumnNames;
        List<String> targetColumnNames;
        try {
            sourceColumnNames =
                    logicalTableRelationHelper.fromJson(
                            entity.getSourceColumnNamesJson(), "sourceColumnNames");
        } catch (IllegalArgumentException e) {
            logger.warn("Skipping relation id={}: invalid source columns JSON.", entity.getId(), e);
            return null;
        }
        try {
            targetColumnNames =
                    logicalTableRelationHelper.fromJson(
                            entity.getTargetColumnNamesJson(), "targetColumnNames");
        } catch (IllegalArgumentException e) {
            logger.warn("Skipping relation id={}: invalid target columns JSON.", entity.getId(), e);
            return null;
        }
        if (!isRelationEndpointVisible(
                entity.getSourceTableName(), sourceColumnNames,
                entity.getTargetTableName(), targetColumnNames)) {
            return null;
        }
        return new ResolvedRelation(
                entity.getId(),
                entity.getRelationType(),
                LogicalTableRelationHelper.RELATION_SOURCE_LOGICAL,
                entity.getSourceTableName(),
                sourceColumnNames,
                entity.getTargetTableName(),
                targetColumnNames,
                normalizeBlankToNull(entity.getDescription()),
                true);
    }

    private String relationKey(ResolvedRelation relation) {
        String sourceColumns =
                relation.sourceColumnNames().stream()
                        .map(c -> c.trim().toLowerCase(Locale.ROOT))
                        .reduce((a, b) -> a + RELATION_KEY_SEPARATOR + b)
                        .orElse("");
        String targetColumns =
                relation.targetColumnNames().stream()
                        .map(c -> c.trim().toLowerCase(Locale.ROOT))
                        .reduce((a, b) -> a + RELATION_KEY_SEPARATOR + b)
                        .orElse("");
        return normalizeName(relation.sourceTableName())
                + RELATION_KEY_SEPARATOR
                + sourceColumns
                + RELATION_KEY_SEPARATOR
                + normalizeName(relation.targetTableName())
                + RELATION_KEY_SEPARATOR
                + targetColumns;
    }

    private ResolvedTable resolveCachedTable(TableMergeSnapshot snapshot) {
        String tableKey = normalizeName(semanticResolver.resolveCanonicalTableName(snapshot));
        return cache.getOrComputeTable(tableKey, key -> semanticResolver.resolveTable(snapshot));
    }
}
