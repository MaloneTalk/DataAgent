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
package io.github.malonetalk.service.impl;

import io.github.malonetalk.agent.datasource.DynamicDataSourceManager;
import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.entity.ResolvedTable;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.enums.Status;
import io.github.malonetalk.mapper.DatasourceMapper;
import io.github.malonetalk.service.DatasourceService;
import io.github.malonetalk.service.semantic.SemanticContext;
import io.github.malonetalk.service.semantic.SemanticContextFactory;
import io.github.malonetalk.service.semantic.column.ColumnSemanticRepository;
import io.github.malonetalk.service.semantic.relation.RelationSemanticRepository;
import io.github.malonetalk.service.semantic.table.TableSemanticRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasourceServiceImpl implements DatasourceService {

    private final DatasourceMapper dataSourceMapper;
    private final TableSemanticRepository tableSemanticRepository;
    private final ColumnSemanticRepository columnSemanticRepository;
    private final RelationSemanticRepository relationSemanticRepository;
    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final SemanticContextFactory semanticContextFactory;
    private final io.github.malonetalk.agent.datasource.SchemaReader schemaReader;

    public DatasourceServiceImpl(
            DatasourceMapper dataSourceMapper,
            TableSemanticRepository tableSemanticRepository,
            ColumnSemanticRepository columnSemanticRepository,
            @Lazy RelationSemanticRepository relationSemanticRepository,
            DynamicDataSourceManager dynamicDataSourceManager,
            SemanticContextFactory semanticContextFactory,
            io.github.malonetalk.agent.datasource.SchemaReader schemaReader) {
        this.dataSourceMapper = dataSourceMapper;
        this.tableSemanticRepository = tableSemanticRepository;
        this.columnSemanticRepository = columnSemanticRepository;
        this.relationSemanticRepository = relationSemanticRepository;
        this.dynamicDataSourceManager = dynamicDataSourceManager;
        this.semanticContextFactory = semanticContextFactory;
        this.schemaReader = schemaReader;
    }

    @Override
    public List<Datasource> findAll() {
        return dataSourceMapper.selectAll();
    }

    @Override
    public Datasource findById(Integer id) {
        return dataSourceMapper.selectById(id);
    }

    @Override
    @Transactional
    public boolean save(Datasource dataSource) {
        boolean needsExclusiveActivation =
                Status.ACTIVE.getCode().equalsIgnoreCase(dataSource.getStatus());
        if (needsExclusiveActivation) {
            dataSource.setStatus(Status.INACTIVE.getCode());
        }
        dataSource.setCreateTime(LocalDateTime.now());
        dataSource.setUpdateTime(LocalDateTime.now());
        boolean saved = dataSourceMapper.insert(dataSource) > 0;
        if (!saved || !needsExclusiveActivation) {
            return saved;
        }
        activateExclusivelyOrThrow(dataSource.getId());
        invalidateDatasourceRuntimeState(dataSource.getId());
        return true;
    }

    @Override
    @Transactional
    public boolean update(Datasource dataSource) {
        Datasource existingDatasource = findExistingDatasource(dataSource.getId());
        if (existingDatasource == null) {
            return false;
        }
        String targetStatus =
                dataSource.getStatus() != null
                        ? dataSource.getStatus()
                        : existingDatasource.getStatus();
        boolean needsExclusiveActivation =
                Status.ACTIVE.getCode().equalsIgnoreCase(targetStatus)
                        && !Status.ACTIVE
                                .getCode()
                                .equalsIgnoreCase(existingDatasource.getStatus());
        if (needsExclusiveActivation) {
            dataSource.setStatus(null);
        }
        dataSource.setUpdateTime(LocalDateTime.now());
        boolean updated = dataSourceMapper.update(dataSource) > 0;
        if (!updated) {
            return false;
        }
        if (needsExclusiveActivation) {
            activateExclusivelyOrThrow(existingDatasource.getId());
        }
        invalidateDatasourceRuntimeState(existingDatasource.getId());
        return true;
    }

    @Override
    @Transactional
    public boolean deleteById(Integer id) {
        Datasource existingDatasource = findExistingDatasource(id);
        if (existingDatasource == null) {
            return false;
        }
        relationSemanticRepository.deleteByDatasourceId(id);
        columnSemanticRepository.deleteByDatasourceId(id);
        tableSemanticRepository.deleteByDatasourceId(id);
        boolean deleted = dataSourceMapper.deleteById(id) > 0;
        if (deleted) {
            invalidateDatasourceRuntimeState(id);
        }
        return deleted;
    }

    @Override
    public List<Datasource> findByStatus(String status) {
        return dataSourceMapper.selectByStatus(status);
    }

    @Override
    public List<Datasource> findByType(String type) {
        return dataSourceMapper.selectByType(type);
    }

    @Override
    @Transactional
    public boolean activate(Integer id, List<String> activeDomains) {
        Datasource existingDatasource = findExistingDatasource(id);
        if (existingDatasource == null) {
            return false;
        }
        activateExclusivelyOrThrow(id);
        applyDomainVisibility(existingDatasource, activeDomains);
        invalidateDatasourceRuntimeState(id);
        return true;
    }

    @Override
    @Transactional
    public boolean updateStatus(Integer id, String status) {
        Datasource existingDatasource = findExistingDatasource(id);
        if (existingDatasource == null) {
            return false;
        }
        boolean updated;
        if (Status.ACTIVE.getCode().equalsIgnoreCase(status)) {
            activateExclusivelyOrThrow(id);
            updated = true;
        } else {
            updated = dataSourceMapper.updateStatus(id, status) > 0;
        }
        if (updated) {
            invalidateDatasourceRuntimeState(id);
        }
        return updated;
    }

    private void activateExclusivelyOrThrow(Integer datasourceId) {
        if (dataSourceMapper.activateIfNoOtherActive(datasourceId, Status.ACTIVE.getCode()) == 0) {
            throw new IllegalStateException(
                    "Only one active datasource is allowed. Please deactivate the current active"
                            + " datasource first.");
        }
    }

    private Datasource findExistingDatasource(Integer datasourceId) {
        if (datasourceId == null) {
            return null;
        }
        return dataSourceMapper.selectById(datasourceId);
    }

    private void invalidateDatasourceRuntimeState(Integer datasourceId) {
        dynamicDataSourceManager.removeDataSource(datasourceId);
        schemaReader.invalidateCache(datasourceId);
    }

    private void applyDomainVisibility(Datasource datasource, List<String> activeDomains) {
        List<TableInfo> overlays = tableSemanticRepository.listByDatasourceId(datasource.getId());
        if (activeDomains == null) {
            overlays.stream()
                    .filter(tableInfo -> !Boolean.TRUE.equals(tableInfo.getIsVisible()))
                    .forEach(
                            tableInfo -> {
                                tableInfo.setIsVisible(true);
                                tableSemanticRepository.update(tableInfo);
                            });
            return;
        }

        Set<String> enabledDomains = normalizeActiveDomains(activeDomains);
        Map<String, TableInfo> overlaysByTableName = new LinkedHashMap<>();
        for (TableInfo overlay : overlays) {
            overlaysByTableName.put(normalizeTableKey(overlay.getTableName()), overlay);
        }

        SemanticContext context = semanticContextFactory.createContext(datasource);
        for (ResolvedTable table : context.listTables()) {
            String tableKey = normalizeTableKey(table.canonicalName());
            boolean visible = matchesDomain(table.domain(), enabledDomains);
            TableInfo existingOverlay = overlaysByTableName.remove(tableKey);
            if (existingOverlay != null) {
                if (!Objects.equals(existingOverlay.getIsVisible(), visible)) {
                    existingOverlay.setIsVisible(visible);
                    tableSemanticRepository.update(existingOverlay);
                }
                continue;
            }
            if (!visible) {
                TableInfo overlay = new TableInfo();
                overlay.setDatasourceId(datasource.getId());
                overlay.setTableName(table.canonicalName());
                overlay.setDomain(normalizeDomainValue(table.domain()));
                overlay.setTableDescription(null);
                overlay.setIsVisible(false);
                tableSemanticRepository.save(overlay);
            }
        }

        for (TableInfo overlay : overlaysByTableName.values()) {
            boolean visible = matchesDomain(overlay.getDomain(), enabledDomains);
            if (!Objects.equals(overlay.getIsVisible(), visible)) {
                overlay.setIsVisible(visible);
                tableSemanticRepository.update(overlay);
            }
        }
    }

    private Set<String> normalizeActiveDomains(List<String> activeDomains) {
        return activeDomains.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(domain -> domain.isEmpty() ? "" : domain)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private boolean matchesDomain(String domain, Set<String> enabledDomains) {
        return enabledDomains.contains(normalizeDomainKey(domain));
    }

    private String normalizeDomainKey(String domain) {
        if (domain == null) {
            return "";
        }
        String normalized = domain.trim();
        return normalized.isEmpty() ? "" : normalized;
    }

    private String normalizeDomainValue(String domain) {
        String normalized = normalizeDomainKey(domain);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeTableKey(String tableName) {
        return tableName == null ? "" : tableName.trim().toLowerCase(Locale.ROOT);
    }
}
