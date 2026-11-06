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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.malonetalk.dto.ChatStreamEvent;
import io.github.malonetalk.dto.ChatStreamEvent.ToolCallInfo;
import io.github.malonetalk.dto.ChatStreamEvent.ToolResultInfo;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.TableInfoMapper;
import io.github.malonetalk.model.bo.MetricToolMetadataBo;
import io.github.malonetalk.model.bo.PythonToolMetadataBo;
import io.github.malonetalk.model.bo.SqlToolMetadataBo;
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
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds and merges evidence without owning its JSON persistence or session access. */
@Component
@RequiredArgsConstructor
public class AnswerProvenanceBuilder {
    private static final Set<String> TOOLS =
            Set.of("execute_sql", "export_table", "get_metric_caliber", "execute_python");
    private final ObjectMapper objectMapper;
    private final SqlLineageExtractor lineageExtractor;
    private final TableInfoMapper tableInfoMapper;
    private final ColumnSemanticInfoMapper columnInfoMapper;

    public Optional<AnswerProvenanceVo> build(
            List<ChatStreamEvent> events, String status, Optional<AnswerProvenanceVo> previous) {
        Map<String, ToolCallInfo> calls = new LinkedHashMap<>();
        Map<String, ToolResultInfo> results = new LinkedHashMap<>();
        for (ChatStreamEvent event : events) {
            Optional.ofNullable(event.toolCall()).ifPresent(call -> calls.put(call.id(), call));
            Optional.ofNullable(event.toolResult())
                    .ifPresent(result -> results.put(result.id(), result));
        }
        if (previous.isEmpty()
                && calls.values().stream().noneMatch(call -> TOOLS.contains(call.name()))) {
            return Optional.empty();
        }
        var evidence = new Evidence();
        for (ToolCallInfo call : calls.values()) {
            var result = Optional.ofNullable(results.get(call.id()));
            switch (call.name()) {
                case "execute_sql", "export_table" ->
                        evidence.addSql(call, metadata(result, SqlToolMetadataBo.class));
                case "get_metric_caliber" ->
                        metadata(result, MetricToolMetadataBo.class)
                                .map(MetricToolMetadataBo::metrics)
                                .ifPresent(evidence.metrics::addAll);
                case "execute_python" ->
                        evidence.pythonExecutions.add(pythonExecution(call, result));
                default -> {}
            }
        }
        var current = evidence.toResponse(status);
        return Optional.of(previous.map(old -> merge(old, current)).orElse(current));
    }

    private <T> Optional<T> metadata(Optional<ToolResultInfo> result, Class<T> type) {
        // AgentScope persists metadata as maps; decode it once before evidence construction.
        return result.map(ToolResultInfo::metadata)
                .map(value -> objectMapper.convertValue(value, type));
    }

    private Optional<String> input(ToolCallInfo call, String name) {
        return Optional.ofNullable(call.input().get(name)).map(Object::toString);
    }

    private PythonUse pythonExecution(ToolCallInfo call, Optional<ToolResultInfo> result) {
        String status = "UNCONFIRMED";
        if (result.isPresent()) {
            boolean success =
                    metadata(result, PythonToolMetadataBo.class)
                            .map(PythonToolMetadataBo::success)
                            .orElse(false);
            status = success ? "SUCCESS" : "FAILED";
        }
        return new PythonUse(call.id(), input(call, "code").orElse(null), status);
    }

    private class Evidence {
        private final List<DatasourceUse> datasources = new ArrayList<>();
        private final List<SqlUse> sqlExecutions = new ArrayList<>();
        private final List<MetricUse> metrics = new ArrayList<>();
        private final List<PythonUse> pythonExecutions = new ArrayList<>();
        private final List<TableUse> tables = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final Map<Integer, List<TableInfo>> tableCache = new LinkedHashMap<>();
        private final Map<Integer, List<ColumnInfo>> columnCache = new LinkedHashMap<>();

        private void addSql(ToolCallInfo call, Optional<SqlToolMetadataBo> metadata) {
            var sql = input(call, "sql");
            var execution = metadata.filter(value -> value.executedSql() != null);
            sqlExecutions.add(
                    SqlUse.builder()
                            .callId(call.id())
                            .sql(sql.orElse(null))
                            .executedSql(execution.map(SqlToolMetadataBo::executedSql).orElse(null))
                            .status(execution.isPresent() ? "SUCCESS" : "UNCONFIRMED")
                            .returnedRows(
                                    execution.map(SqlToolMetadataBo::returnedRows).orElse(null))
                            .truncated(
                                    execution
                                            .map(value -> Boolean.TRUE.equals(value.truncated()))
                                            .orElse(null))
                            .build());
            execution.ifPresent(value -> addLineage(sql.orElse(""), value));
        }

        private void addLineage(String sql, SqlToolMetadataBo metadata) {
            datasources.add(
                    new DatasourceUse(
                            metadata.datasourceId(),
                            metadata.datasourceName(),
                            metadata.databaseName()));
            var datasourceId = Optional.ofNullable(metadata.datasourceId());
            if (datasourceId.isEmpty()) return;
            var snapshot = Optional.ofNullable(metadata.authorizedColumns());
            if (snapshot.isEmpty()) {
                warnings.add("SQL 缺少执行时的列授权快照，未生成表列证据");
                return;
            }
            var authorizedColumns = snapshot.get();
            var allTables =
                    tableCache.computeIfAbsent(
                            datasourceId.get(), tableInfoMapper::selectByDatasourceId);
            var allColumns =
                    columnCache.computeIfAbsent(
                            datasourceId.get(), columnInfoMapper::selectByDatasourceId);
            var visibleTables =
                    allTables.stream()
                            .filter(
                                    table ->
                                            authorizedColumns.containsKey(
                                                    normalizeIdentifier(table.getTableName())))
                            .toList();
            var visibleColumns =
                    allColumns.stream()
                            .filter(
                                    column ->
                                            authorizedColumns
                                                    .getOrDefault(
                                                            normalizeIdentifier(
                                                                    column.getTableName()),
                                                            Set.of())
                                                    .contains(
                                                            normalizeIdentifier(
                                                                    column.getColumnName())))
                            .toList();
            var lineage = lineageExtractor.extract(sql, visibleTables, visibleColumns);
            tables.addAll(lineage.tables());
            warnings.addAll(lineage.warnings());
        }

        private AnswerProvenanceVo toResponse(String status) {
            if (!pythonExecutions.isEmpty()) warnings.add("Python 内联数据与 SQL 结果未做逐值校验");
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
                                    mergeDistinct(old.columns(), next.columns())));
        }
        return new ArrayList<>(merged.values());
    }
}
