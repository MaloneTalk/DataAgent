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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.ChatStreamEvent.ToolCallInfo;
import io.github.malonetalk.dto.ChatStreamEvent.ToolResultInfo;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.mapper.AnswerProvenanceMapper;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.model.po.AnswerProvenancePo;
import io.github.malonetalk.model.vo.AnswerProvenanceVo;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.DatasourceUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.MetricUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.PythonUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.SqlUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.TableUse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

    @Override
    public Optional<AnswerProvenanceVo> find(String sessionId, String answerId) {
        return read(provenanceMapper.selectBySessionIdAndAnswerId(sessionId, answerId));
    }

    @Override
    public void deleteSession(String sessionId) {
        provenanceMapper.deleteBySessionId(sessionId);
    }

    @Override
    public Map<String, AnswerProvenanceVo> listBySession(String sessionId) {
        Map<String, AnswerProvenanceVo> answers = new LinkedHashMap<>();
        for (AnswerProvenancePo provenance : provenanceMapper.selectBySessionId(sessionId)) {
            read(provenance).ifPresent(answer -> answers.put(provenance.getAnswerId(), answer));
        }
        return answers;
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
        AnswerProvenanceVo merged =
                validateSources(previous.map(old -> merge(old, current)).orElse(current));
        persist(sessionId, answerId, write(merged), stored != null);
    }

    private boolean isProvenanceEvent(ChatStreamEvent event) {
        if (event.toolCall() == null) return false;
        return switch (event.toolCall().name()) {
            case "execute_sql", "export_table", "get_metric_caliber", "execute_python" -> true;
            default -> false;
        };
    }

    private void persist(String sessionId, String answerId, String json, boolean exists) {
        if (exists) {
            provenanceMapper.updateJsonBySessionIdAndAnswerId(sessionId, answerId, json);
            return;
        }
        AnswerProvenancePo provenance = new AnswerProvenancePo();
        provenance.setAnswerId(answerId);
        provenance.setSessionId(sessionId);
        provenance.setProvenanceJson(json);
        provenanceMapper.insert(provenance);
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
        return new AnswerProvenanceVo(
                status,
                distinct(datasources),
                sqlExecutions,
                distinct(metrics),
                pythonExecutions,
                mergeTables(tables),
                distinct(warnings));
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
                new SqlUse(
                        call.id(),
                        sql,
                        success ? string(result.metadata().get("executedSql")) : null,
                        success ? "SUCCESS" : "UNCONFIRMED",
                        success ? integer(result.metadata().get("returnedRows")) : null,
                        success ? Boolean.TRUE.equals(result.metadata().get("truncated")) : null));
        if (!success) return;

        Integer datasourceId = integer(result.metadata().get("datasourceId"));
        datasources.add(
                new DatasourceUse(
                        datasourceId,
                        string(result.metadata().get("datasourceName")),
                        string(result.metadata().get("databaseName"))));
        if (datasourceId == null) return;

        List<TableInfo> allTables =
                tableCache.computeIfAbsent(datasourceId, tableInfoMapper::selectByDatasourceId);
        List<ColumnInfo> allColumns =
                columnCache.computeIfAbsent(datasourceId, columnInfoMapper::selectByDatasourceId);
        SqlLineageExtractor.Lineage lineage = lineageExtractor.extract(sql, allTables, allColumns);
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

    private AnswerProvenanceVo validateSources(AnswerProvenanceVo answer) {
        List<String> warnings = new ArrayList<>(answer.warnings());
        if (!answer.pythonExecutions().isEmpty()) {
            warnings.add("Python 内联数据与 SQL 结果未做逐值校验");
        }
        return new AnswerProvenanceVo(
                answer.status(),
                answer.datasources(),
                answer.sqlExecutions(),
                answer.metricReferences(),
                answer.pythonExecutions(),
                answer.tables(),
                distinct(warnings));
    }

    private AnswerProvenanceVo merge(AnswerProvenanceVo old, AnswerProvenanceVo next) {
        return new AnswerProvenanceVo(
                next.status(),
                mergeDistinct(old.datasources(), next.datasources()),
                mergeDistinct(old.sqlExecutions(), next.sqlExecutions()),
                mergeDistinct(old.metricReferences(), next.metricReferences()),
                mergeDistinct(old.pythonExecutions(), next.pythonExecutions()),
                mergeTables(Stream.concat(old.tables().stream(), next.tables().stream()).toList()),
                mergeDistinct(old.warnings(), next.warnings()));
    }

    private Optional<AnswerProvenanceVo> read(AnswerProvenancePo provenance) {
        if (provenance == null) return Optional.empty();
        try {
            return Optional.of(
                    objectMapper.readValue(
                            provenance.getProvenanceJson(), AnswerProvenanceVo.class));
        } catch (JsonProcessingException e) {
            log.warn(
                    "Skipping invalid answer provenance: answerId={}", provenance.getAnswerId(), e);
            return Optional.empty();
        }
    }

    private String write(AnswerProvenanceVo provenance) {
        try {
            return objectMapper.writeValueAsString(provenance);
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
