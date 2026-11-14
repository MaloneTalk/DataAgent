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
package io.github.malonetalk.service;

import static io.github.malonetalk.utils.SemanticUtils.normalizeIdentifier;

import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.SelectItem;

/** Column bindings for one SELECT; hidden columns remain bound so they cannot fall back outward. */
@RequiredArgsConstructor
final class SqlQueryScope {
    private final Optional<SqlQueryScope> parent;
    // Each source maps its column names to whether the column is accessible.
    private final Map<String, Map<String, Boolean>> sources = new HashMap<>();
    // Saved wildcard results must reject columns revoked from that source since execution.
    private final Set<String> narrowedSources = new HashSet<>();
    final Set<String> outputColumns = new LinkedHashSet<>();

    void addSource(String alias, Set<String> visible, Set<String> hidden) {
        addSource(alias, visible, hidden, Set.of());
    }

    void addSource(String alias, Set<String> visible, Set<String> hidden, Set<String> previous) {
        Map<String, Boolean> columns = new HashMap<>();
        visible.forEach(column -> columns.put(normalizeIdentifier(column), true));
        hidden.forEach(column -> columns.put(normalizeIdentifier(column), false));
        if (sources.putIfAbsent(normalizeIdentifier(alias), columns) != null) throw denied();
        if (previous.stream()
                .anyMatch(column -> !columns.getOrDefault(normalizeIdentifier(column), false))) {
            narrowedSources.add(normalizeIdentifier(alias));
        }
    }

    void requireColumn(Column column) {
        if (!resolve(column).orElse(false)) throw denied();
    }

    void requireOrderColumn(Column column) {
        if (!isOutput(column)) requireColumn(column);
    }

    void requireGroupingColumn(Column column) {
        var local = resolveLocal(column);
        if (local.isPresent()) {
            if (!local.get()) throw denied();
        } else if (!isOutput(column)) {
            requireColumn(column);
        }
    }

    private boolean isOutput(Column column) {
        return qualifier(column).isEmpty()
                && outputColumns.contains(normalizeIdentifier(column.getColumnName()));
    }

    private Optional<Boolean> resolve(Column column) {
        return resolveLocal(column).or(() -> parent.flatMap(scope -> scope.resolve(column)));
    }

    private Optional<Boolean> resolveLocal(Column column) {
        String name = normalizeIdentifier(column.getColumnName());
        var qualifier = qualifier(column);
        if (qualifier.isPresent()) {
            return Optional.ofNullable(sources.get(qualifier.get()))
                    .map(columns -> columns.getOrDefault(name, false));
        }
        var matches =
                sources.values().stream().filter(columns -> columns.containsKey(name)).toList();
        if (matches.isEmpty()) return Optional.empty();
        return Optional.of(matches.stream().allMatch(columns -> columns.get(name)));
    }

    private Optional<String> qualifier(Column column) {
        return Optional.ofNullable(column.getTable()).map(this::tableName);
    }

    private String tableName(Table table) {
        if (table.getSchemaName() != null
                || table.getDatabase() != null && table.getDatabase().getDatabaseName() != null)
            throw denied();
        return normalizeIdentifier(table.getName());
    }

    void addOutput(SelectItem<?> item) {
        Expression expression = item.getExpression();
        while (expression instanceof ParenthesedExpressionList<?> parentheses
                && parentheses.size() == 1) {
            expression = parentheses.get(0);
        }
        if (item.getAlias() != null) {
            String alias = item.getAlias().getName();
            if (alias.startsWith("'") && alias.endsWith("'")) {
                alias = new StringValue(alias).getNotExcapedValue();
            }
            outputColumns.add(normalizeIdentifier(alias));
        } else if (expression instanceof AllTableColumns all) {
            if (narrowedSources.contains(tableName(all.getTable()))) throw denied();
            var columns =
                    Optional.ofNullable(sources.get(tableName(all.getTable())))
                            .orElseThrow(this::denied);
            addVisibleOutputs(columns);
        } else if (expression instanceof AllColumns) {
            if (!narrowedSources.isEmpty()) throw denied();
            sources.values().forEach(this::addVisibleOutputs);
        } else if (expression instanceof Column column) {
            outputColumns.add(normalizeIdentifier(column.getColumnName()));
        } else {
            // 计算表达式保留原始括号，只有直接列投影的列名不受括号影响。
            outputColumns.add(normalizeIdentifier(item.getExpression().toString()));
        }
    }

    private void addVisibleOutputs(Map<String, Boolean> columns) {
        columns.forEach(
                (name, visible) -> {
                    if (visible) outputColumns.add(name);
                });
    }

    private BusinessException denied() {
        return BusinessException.of(
                ErrorCode.FORBIDDEN, "SQL column is inaccessible or unresolved.");
    }
}
