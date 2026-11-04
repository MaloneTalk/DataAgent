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
package io.github.malonetalk.agent.provenance;

import static io.github.malonetalk.utils.SemanticUtils.normalizeIdentifier;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.ChatStreamEvent.ToolCallInfo;
import io.github.malonetalk.dto.ChatStreamEvent.ToolResultInfo;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.mapper.AnswerProvenanceMapper;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.mapper.UserSessionMapper;
import io.github.malonetalk.model.dto.DataEvidencePageQuery;
import io.github.malonetalk.model.po.AnswerProvenancePo;
import io.github.malonetalk.model.vo.AnswerProvenanceVo;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.DataEvidenceVo;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.DatasourceUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.MetricUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.PythonUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.SqlUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.TableUse;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerProvenanceServiceImpl implements AnswerProvenanceService {

    private final AnswerProvenanceMapper provenanceMapper;
    private final ObjectMapper objectMapper;
    private final SqlLineageExtractor lineageExtractor;
    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnInfoMapper;
    private final UserSessionMapper userSessionMapper;

    @Override
    public void deleteSession(String sessionId) {
        provenanceMapper.deleteBySessionId(sessionId);
    }

    @Override
    public PageResponse<DataEvidenceVo> getPage(DataEvidencePageQuery query, Integer userId) {
        int pageNumber = PageResponse.resolvePage(query.page());
        int pageSize = PageResponse.resolvePageSize(query.pageSize());
        String sessionId = SemanticUtils.trimToNull(query.sessionId());
        List<String> sessionIds =
                userId == null ? null : userSessionMapper.selectSessionIdsByUserId(userId);
        if (sessionIds != null
                && (sessionIds.isEmpty() || sessionId != null && !sessionIds.contains(sessionId))) {
            return PageResponse.empty(pageNumber, pageSize);
        }

        IPage<AnswerProvenancePo> page =
                provenanceMapper.selectPage(
                        new Page<>(pageNumber, pageSize),
                        sessionId,
                        SemanticUtils.trimToNull(query.answerId()),
                        sessionId == null ? sessionIds : null);
        List<DataEvidenceVo> evidence =
                page.getRecords().stream()
                        .flatMap(
                                stored ->
                                        read(stored)
                                                .map(
                                                        value ->
                                                                new DataEvidenceVo(
                                                                        stored.getAnswerId(),
                                                                        stored.getSessionId(),
                                                                        value))
                                                .stream())
                        .toList();
        return PageResponse.of(evidence, page.getTotal(), pageNumber, pageSize);
    }

    @Override
    @Transactional
    public void save(
            String sessionId, String answerId, List<ChatStreamEvent> events, String status) {
        AnswerProvenancePo stored =
                provenanceMapper.selectForUpdateBySessionIdAndAnswerId(sessionId, answerId);
        Optional<AnswerProvenanceVo> previous = read(stored);
        if (previous.isEmpty() && events.stream().noneMatch(this::isProvenanceEvent)) return;

        AnswerProvenanceVo current = build(events, status);
        AnswerProvenanceVo merged = previous.map(old -> merge(old, current)).orElse(current);
        persist(sessionId, answerId, merged, stored != null);
    }

    private boolean isProvenanceEvent(ChatStreamEvent event) {
        if (event.toolCall() == null) return false;
        return switch (event.toolCall().name()) {
            case "execute_sql", "export_table", "get_metric_caliber", "execute_python" -> true;
            default -> false;
        };
    }

    private void persist(
            String sessionId, String answerId, AnswerProvenanceVo evidence, boolean exists) {
        AnswerProvenancePo provenance = new AnswerProvenancePo();
        provenance.setAnswerId(answerId);
        provenance.setSessionId(sessionId);
        provenance.setStatus(evidence.status());
        provenance.setDatasourcesJson(write(evidence.datasources()));
        provenance.setSqlExecutionsJson(write(evidence.sqlExecutions()));
        provenance.setMetricReferencesJson(write(evidence.metricReferences()));
        provenance.setPythonExecutionsJson(write(evidence.pythonExecutions()));
        provenance.setTablesJson(write(evidence.tables()));
        provenance.setWarningsJson(write(evidence.warnings()));
        if (exists) {
            provenanceMapper.updateBySessionIdAndAnswerId(provenance);
        } else {
            provenanceMapper.insert(provenance);
        }
    }

    private AnswerProvenanceVo build(List<ChatStreamEvent> events, String status) {
        Map<String, ToolCallInfo> calls = new LinkedHashMap<>();
        Map<String, ToolResultInfo> results = new LinkedHashMap<>();
        for (ChatStreamEvent event : events) {
            if (event.toolCall() != null) calls.put(event.toolCall().id(), event.toolCall());
            if (event.toolResult() != null)
                results.put(event.toolResult().id(), event.toolResult());
        }
        List<DatasourceUse> datasources = new ArrayList<>();
        List<SqlUse> sqlExecutions = new ArrayList<>();
        List<MetricUse> metrics = new ArrayList<>();
        List<PythonUse> pythonExecutions = new ArrayList<>();
        List<TableUse> tables = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<Integer, List<TableInfo>> tableCache = new LinkedHashMap<>();
        Map<Integer, List<ColumnInfo>> columnCache = new LinkedHashMap<>();
        for (ToolCallInfo call : calls.values()) {
            ToolResultInfo result = results.get(call.id());
            if ("execute_sql".equals(call.name()) || "export_table".equals(call.name())) {
                addSql(
                        call,
                        result,
                        datasources,
                        sqlExecutions,
                        tables,
                        warnings,
                        tableCache,
                        columnCache);
            } else if ("get_metric_caliber".equals(call.name()) && result != null) {
                addMetrics(result, metrics);
            } else if ("execute_python".equals(call.name())) {
                pythonExecutions.add(pythonExecution(call, result));
            }
        }
        if (!pythonExecutions.isEmpty()) {
            warnings.add("Python 内联数据与 SQL 结果未做逐值校验");
        }
        return AnswerProvenanceVo.builder()
                .status(status)
                .datasources(distinct(datasources))
                .sqlExecutions(sqlExecutions)
                .metricReferences(distinct(metrics))
                .pythonExecutions(pythonExecutions)
                .tables(mergeTables(tables))
                .warnings(distinct(warnings))
                .build();
    }

    private void addSql(
            ToolCallInfo call,
            ToolResultInfo result,
            List<DatasourceUse> datasources,
            List<SqlUse> sqlExecutions,
            List<TableUse> tables,
            List<String> warnings,
            Map<Integer, List<TableInfo>> tableCache,
            Map<Integer, List<ColumnInfo>> columnCache) {
        String sql = string(call.input().get("sql"));
        boolean success = result != null && result.metadata().containsKey("executedSql");
        sqlExecutions.add(
                SqlUse.builder()
                        .callId(call.id())
                        .sql(sql)
                        .executedSql(success ? string(result.metadata().get("executedSql")) : null)
                        .status(success ? "SUCCESS" : "UNCONFIRMED")
                        .returnedRows(
                                success ? integer(result.metadata().get("returnedRows")) : null)
                        .truncated(
                                success
                                        ? Boolean.TRUE.equals(result.metadata().get("truncated"))
                                        : null)
                        .build());
        if (!success) return;

        Integer datasourceId = integer(result.metadata().get("datasourceId"));
        datasources.add(
                new DatasourceUse(
                        datasourceId,
                        string(result.metadata().get("datasourceName")),
                        string(result.metadata().get("databaseName"))));
        if (datasourceId == null) return;

        // Tool results carry the execution-time snapshot, including after JSON persistence.
        Map<String, Set<String>> authorizedColumns =
                objectMapper.convertValue(
                        result.metadata().get("authorizedColumns"), new TypeReference<>() {});
        if (authorizedColumns == null) {
            warnings.add("SQL 缺少执行时的列授权快照，未生成表列证据");
            return;
        }
        List<TableInfo> allTables =
                tableCache.computeIfAbsent(datasourceId, tableInfoMapper::selectByDatasourceId);
        List<ColumnInfo> allColumns =
                columnCache.computeIfAbsent(datasourceId, columnInfoMapper::selectByDatasourceId);
        var visibleTables =
                allTables.stream()
                        .filter(
                                t ->
                                        authorizedColumns.containsKey(
                                                normalizeIdentifier(t.getTableName())))
                        .toList();
        var visibleColumns =
                allColumns.stream()
                        .filter(
                                c ->
                                        authorizedColumns
                                                .getOrDefault(
                                                        normalizeIdentifier(c.getTableName()),
                                                        Set.of())
                                                .contains(normalizeIdentifier(c.getColumnName())))
                        .toList();
        SqlLineageExtractor.Lineage lineage =
                lineageExtractor.extract(sql, visibleTables, visibleColumns);
        tables.addAll(lineage.tables());
        warnings.addAll(lineage.warnings());
    }

    private void addMetrics(ToolResultInfo result, List<MetricUse> metrics) {
        if (result.metadata().get("metrics") instanceof List<?> list) {
            list.stream()
                    .filter(MetricUse.class::isInstance)
                    .map(MetricUse.class::cast)
                    .forEach(metrics::add);
        }
    }

    private PythonUse pythonExecution(ToolCallInfo call, ToolResultInfo result) {
        return new PythonUse(
                call.id(),
                string(call.input().get("code")),
                result == null
                        ? "UNCONFIRMED"
                        : Boolean.TRUE.equals(result.metadata().get("success"))
                                ? "SUCCESS"
                                : "FAILED");
    }

    private AnswerProvenanceVo merge(AnswerProvenanceVo old, AnswerProvenanceVo next) {
        return AnswerProvenanceVo.builder()
                .status(next.status())
                .datasources(mergeDistinct(old.datasources(), next.datasources()))
                .sqlExecutions(mergeDistinct(old.sqlExecutions(), next.sqlExecutions()))
                .metricReferences(mergeDistinct(old.metricReferences(), next.metricReferences()))
                .pythonExecutions(mergeDistinct(old.pythonExecutions(), next.pythonExecutions()))
                .tables(
                        mergeTables(
                                Stream.concat(old.tables().stream(), next.tables().stream())
                                        .toList()))
                .warnings(mergeDistinct(old.warnings(), next.warnings()))
                .build();
    }

    private Optional<AnswerProvenanceVo> read(AnswerProvenancePo provenance) {
        if (provenance == null) return Optional.empty();
        try {
            return Optional.of(
                    AnswerProvenanceVo.builder()
                            .status(provenance.getStatus())
                            .datasources(
                                    readList(provenance.getDatasourcesJson(), DatasourceUse.class))
                            .sqlExecutions(
                                    readList(provenance.getSqlExecutionsJson(), SqlUse.class))
                            .metricReferences(
                                    readList(provenance.getMetricReferencesJson(), MetricUse.class))
                            .pythonExecutions(
                                    readList(provenance.getPythonExecutionsJson(), PythonUse.class))
                            .tables(readList(provenance.getTablesJson(), TableUse.class))
                            .warnings(readList(provenance.getWarningsJson(), String.class))
                            .build());
        } catch (JsonProcessingException e) {
            log.warn(
                    "Skipping invalid answer provenance: answerId={}", provenance.getAnswerId(), e);
            return Optional.empty();
        }
    }

    private <T> List<T> readList(String json, Class<T> elementType) throws JsonProcessingException {
        return objectMapper.readValue(
                json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot save answer provenance", e);
        }
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static <T> List<T> distinct(List<T> values) {
        return values.stream().distinct().toList();
    }

    private static <T> List<T> mergeDistinct(List<T> oldValues, List<T> newValues) {
        return distinct(Stream.concat(oldValues.stream(), newValues.stream()).toList());
    }

    private static List<TableUse> mergeTables(List<TableUse> tables) {
        Map<String, TableUse> merged = new LinkedHashMap<>();
        for (TableUse table : tables) {
            merged.merge(
                    table.name().toLowerCase(Locale.ROOT),
                    table,
                    (old, next) ->
                            new TableUse(
                                    old.name(),
                                    old.domain(),
                                    old.description(),
                                    distinct(
                                            Stream.concat(
                                                            old.columns().stream(),
                                                            next.columns().stream())
                                                    .toList())));
        }
        return new ArrayList<>(merged.values());
    }
}
