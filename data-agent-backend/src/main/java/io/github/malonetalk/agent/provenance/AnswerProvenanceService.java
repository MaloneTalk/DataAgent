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
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.model.vo.AnswerProvenanceVo;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.DatasourceUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.MetricUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.PythonUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.SqlUse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnswerProvenanceService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SqlLineageExtractor lineageExtractor;
    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnInfoMapper;
    private final LogicalTableRelationMapper relationMapper;

    public AnswerProvenanceVo find(String sessionId, String answerId) {
        List<String> rows =
                jdbcTemplate.queryForList(
                        "SELECT provenance_json FROM answer_provenance WHERE session_id = ? AND"
                                + " answer_id = ?",
                        String.class,
                        sessionId,
                        answerId);
        if (rows.isEmpty()) return null;
        try {
            return objectMapper.readValue(rows.get(0), AnswerProvenanceVo.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read answer provenance", e);
        }
    }

    public void deleteSession(String sessionId) {
        jdbcTemplate.update("DELETE FROM answer_provenance WHERE session_id = ?", sessionId);
    }

    public Map<String, AnswerProvenanceVo> listBySession(String sessionId) {
        Map<String, AnswerProvenanceVo> answers = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT answer_id, provenance_json FROM answer_provenance WHERE session_id = ?",
                rs -> {
                    try {
                        answers.put(
                                rs.getString(1),
                                objectMapper.readValue(rs.getString(2), AnswerProvenanceVo.class));
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("Cannot read answer provenance", e);
                    }
                },
                sessionId);
        return answers;
    }

    public void save(
            String sessionId,
            String answerId,
            String traceId,
            List<ChatStreamEvent> events,
            String status) {
        if (events.isEmpty()) return;
        AnswerProvenanceVo previous = find(sessionId, answerId);
        if (previous == null
                && events.stream()
                        .noneMatch(
                                event ->
                                        event.toolCall() != null
                                                && ("execute_sql".equals(event.toolCall().name())
                                                        || "export_table"
                                                                .equals(event.toolCall().name())
                                                        || "get_metric_caliber"
                                                                .equals(event.toolCall().name())
                                                        || "execute_python"
                                                                .equals(event.toolCall().name()))))
            return;
        AnswerProvenanceVo current = build(answerId, traceId, events, status);
        AnswerProvenanceVo merged =
                validateSources(previous == null ? current : merge(previous, current));
        try {
            jdbcTemplate.update(
                    "INSERT INTO answer_provenance (answer_id, session_id, provenance_json) VALUES"
                            + " (?, ?, ?) ON DUPLICATE KEY UPDATE provenance_json ="
                            + " VALUES(provenance_json)",
                    answerId,
                    sessionId,
                    objectMapper.writeValueAsString(merged));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot save answer provenance", e);
        }
    }

    private AnswerProvenanceVo build(
            String answerId, String traceId, List<ChatStreamEvent> events, String status) {
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
        List<SqlLineageExtractor.TableUse> tables = new ArrayList<>();
        List<SqlLineageExtractor.RelationUse> relations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<Integer, List<TableInfo>> tableCache = new LinkedHashMap<>();
        Map<Integer, List<ColumnInfo>> columnCache = new LinkedHashMap<>();
        Map<Integer, List<LogicalTableRelation>> relationCache = new LinkedHashMap<>();
        for (ToolCallInfo call : calls.values()) {
            ToolResultInfo result = results.get(call.id());
            if ("execute_sql".equals(call.name()) || "export_table".equals(call.name())) {
                String sql = string(call.input().get("sql"));
                boolean success = result != null && result.metadata().containsKey("executedSql");
                sqlExecutions.add(
                        new SqlUse(
                                call.id(),
                                sql,
                                success ? string(result.metadata().get("executedSql")) : null,
                                success ? "SUCCESS" : "UNCONFIRMED",
                                success ? integer(result.metadata().get("returnedRows")) : null,
                                success
                                        ? Boolean.TRUE.equals(result.metadata().get("truncated"))
                                        : null));
                if (success) {
                    Integer dsId = integer(result.metadata().get("datasourceId"));
                    datasources.add(
                            new DatasourceUse(
                                    dsId,
                                    string(result.metadata().get("datasourceName")),
                                    string(result.metadata().get("databaseName"))));
                    if (dsId != null) {
                        List<TableInfo> allTables =
                                tableCache.computeIfAbsent(
                                        dsId, tableInfoMapper::selectByDatasourceId);
                        List<ColumnInfo> allColumns =
                                columnCache.computeIfAbsent(
                                        dsId, columnInfoMapper::selectByDatasourceId);
                        List<LogicalTableRelation> allRelations =
                                relationCache.computeIfAbsent(
                                        dsId, relationMapper::selectByDatasourceId);
                        SqlLineageExtractor.Lineage lineage =
                                lineageExtractor.extract(sql, allTables, allColumns, allRelations);
                        tables.addAll(lineage.tables());
                        relations.addAll(lineage.relations());
                        warnings.addAll(lineage.warnings());
                    }
                }
            } else if ("get_metric_caliber".equals(call.name()) && result != null) {
                if (result.metadata().get("metrics") instanceof List<?> list) {
                    list.stream()
                            .filter(MetricUse.class::isInstance)
                            .map(MetricUse.class::cast)
                            .forEach(metrics::add);
                }
            } else if ("execute_python".equals(call.name())) {
                List<String> sources =
                        result == null
                                ? List.of()
                                : stringList(result.metadata().get("sourceSqlCallIds"));
                pythonExecutions.add(
                        new PythonUse(
                                call.id(),
                                string(call.input().get("code")),
                                result == null ? null : result.output(),
                                result == null
                                        ? "UNCONFIRMED"
                                        : Boolean.TRUE.equals(result.metadata().get("success"))
                                                ? "SUCCESS"
                                                : "FAILED",
                                sources));
            }
        }
        return new AnswerProvenanceVo(
                answerId,
                status,
                traceId == null || traceId.isBlank() ? List.of() : List.of(traceId),
                distinct(datasources),
                sqlExecutions,
                distinct(metrics),
                pythonExecutions,
                mergeTables(tables),
                distinct(relations),
                distinct(warnings));
    }

    private AnswerProvenanceVo validateSources(AnswerProvenanceVo answer) {
        Set<String> successfulSqlIds =
                answer.sqlExecutions().stream()
                        .filter(sql -> "SUCCESS".equals(sql.status()))
                        .map(SqlUse::callId)
                        .collect(java.util.stream.Collectors.toSet());
        List<String> warnings = new ArrayList<>(answer.warnings());
        if (!answer.pythonExecutions().isEmpty()) {
            warnings.add("Python 内联数据与 SQL 结果未做逐值校验");
        }
        answer.pythonExecutions().stream()
                .flatMap(python -> python.sourceSqlCallIds().stream())
                .filter(id -> !successfulSqlIds.contains(id))
                .forEach(id -> warnings.add("Python 输入引用了未确认的 SQL 调用：" + id));
        answer.pythonExecutions().stream()
                .filter(python -> python.sourceSqlCallIds().isEmpty())
                .forEach(python -> warnings.add("Python 输入未关联具体 SQL：" + python.callId()));
        return new AnswerProvenanceVo(
                answer.answerId(),
                answer.status(),
                answer.traceIds(),
                answer.datasources(),
                answer.sqlExecutions(),
                answer.metricReferences(),
                answer.pythonExecutions(),
                answer.tables(),
                answer.relations(),
                distinct(warnings));
    }

    private AnswerProvenanceVo merge(AnswerProvenanceVo old, AnswerProvenanceVo next) {
        return new AnswerProvenanceVo(
                old.answerId(),
                next.status(),
                distinct(Stream.concat(old.traceIds().stream(), next.traceIds().stream()).toList()),
                distinct(
                        Stream.concat(old.datasources().stream(), next.datasources().stream())
                                .toList()),
                distinct(
                        Stream.concat(old.sqlExecutions().stream(), next.sqlExecutions().stream())
                                .toList()),
                distinct(
                        Stream.concat(
                                        old.metricReferences().stream(),
                                        next.metricReferences().stream())
                                .toList()),
                distinct(
                        Stream.concat(
                                        old.pythonExecutions().stream(),
                                        next.pythonExecutions().stream())
                                .toList()),
                mergeTables(Stream.concat(old.tables().stream(), next.tables().stream()).toList()),
                distinct(
                        Stream.concat(old.relations().stream(), next.relations().stream())
                                .toList()),
                distinct(
                        Stream.concat(old.warnings().stream(), next.warnings().stream()).toList()));
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static List<String> stringList(Object value) {
        return value instanceof List<?> list
                ? list.stream().filter(Objects::nonNull).map(String::valueOf).toList()
                : List.of();
    }

    private static <T> List<T> distinct(List<T> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static List<SqlLineageExtractor.TableUse> mergeTables(
            List<SqlLineageExtractor.TableUse> tables) {
        Map<String, SqlLineageExtractor.TableUse> merged = new LinkedHashMap<>();
        for (SqlLineageExtractor.TableUse table : tables) {
            merged.merge(
                    table.name().toLowerCase(java.util.Locale.ROOT),
                    table,
                    (old, next) ->
                            new SqlLineageExtractor.TableUse(
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
