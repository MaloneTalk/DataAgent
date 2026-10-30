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

import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.ColumnUse;
import io.github.malonetalk.model.vo.AnswerProvenanceVo.TableUse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.sf.jsqlparser.expression.AnyComparisonExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.TrimFunction;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.springframework.stereotype.Component;

@Component
public class SqlLineageExtractor {

    public Lineage extract(String sql, List<TableInfo> tableInfo, List<ColumnInfo> columnInfo) {
        try {
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            Map<String, TableInfo> tablesByName = new LinkedHashMap<>();
            tableInfo.forEach(t -> tablesByName.put(key(t.getTableName()), t));
            Map<String, Map<String, ColumnInfo>> columnsByTable = new LinkedHashMap<>();
            columnInfo.forEach(
                    c ->
                            columnsByTable
                                    .computeIfAbsent(
                                            key(c.getTableName()), ignored -> new LinkedHashMap<>())
                                    .put(key(c.getColumnName()), c));
            Map<String, Set<String>> usedColumns = new LinkedHashMap<>();
            List<String> warnings = new ArrayList<>();
            visitSelect(select, usedColumns, columnsByTable, warnings, Map.of());
            List<TableUse> tables = new ArrayList<>();
            usedColumns.forEach(
                    (name, fields) -> {
                        TableInfo info = tablesByName.get(key(name));
                        Map<String, ColumnInfo> known =
                                columnsByTable.getOrDefault(key(name), Map.of());
                        List<ColumnUse> columns =
                                fields.stream()
                                        .map(
                                                field -> {
                                                    ColumnInfo column = known.get(key(field));
                                                    return new ColumnUse(
                                                            field,
                                                            column == null
                                                                    ? null
                                                                    : column
                                                                            .getColumnDescription());
                                                })
                                        .toList();
                        tables.add(
                                new TableUse(
                                        name,
                                        info == null ? null : info.getDomain(),
                                        info == null ? null : info.getTableDescription(),
                                        columns));
                    });
            return new Lineage(tables, warnings);
        } catch (Exception e) {
            return new Lineage(List.of(), List.of("SQL 来源解析失败：" + e.getMessage()));
        }
    }

    private void visitSelect(
            Select select,
            Map<String, Set<String>> used,
            Map<String, Map<String, ColumnInfo>> known,
            List<String> warnings,
            Map<String, String> inheritedAliases) {
        if (select instanceof ParenthesedSelect parenthesed) {
            visitSelect(parenthesed.getSelect(), used, known, warnings, inheritedAliases);
        } else if (select instanceof SetOperationList set) {
            set.getSelects()
                    .forEach(item -> visitSelect(item, used, known, warnings, inheritedAliases));
        } else if (select instanceof PlainSelect plain) {
            Map<String, String> aliases = new LinkedHashMap<>();
            addFromItem(plain.getFromItem(), aliases, inheritedAliases, used, known, warnings);
            if (plain.getJoins() != null) {
                for (Join join : plain.getJoins()) {
                    addFromItem(
                            join.getRightItem(), aliases, inheritedAliases, used, known, warnings);
                    if (join.getOnExpressions() != null) {
                        for (Expression on : join.getOnExpressions()) {
                            collect(on, aliases, inheritedAliases, used, known, warnings);
                        }
                    }
                    if (join.getUsingColumns() != null) {
                        for (Column column : join.getUsingColumns()) {
                            List<String> matches = matchingTables(column, aliases, known);
                            matches.forEach(
                                    table ->
                                            used.computeIfAbsent(
                                                            table, ignored -> new LinkedHashSet<>())
                                                    .add(column.getColumnName()));
                            if (matches.size() < 2) {
                                warnings.add("JOIN USING 字段未能映射到两侧：" + column);
                            }
                        }
                    }
                    if (join.isNatural()) warnings.add("NATURAL JOIN 字段无法精确识别");
                }
            }
            plain.getSelectItems()
                    .forEach(
                            item -> {
                                Expression selected = item.getExpression();
                                if (selected instanceof AllColumns) {
                                    aliases.values().stream()
                                            .distinct()
                                            .forEach(
                                                    table ->
                                                            expandStar(
                                                                    table, used, known, warnings));
                                } else if (selected instanceof AllTableColumns allTableColumns) {
                                    String table =
                                            aliases.get(key(allTableColumns.getTable().getName()));
                                    if (table == null) {
                                        table =
                                                inheritedAliases.get(
                                                        key(allTableColumns.getTable().getName()));
                                    }
                                    if (table == null) warnings.add("无法识别通配符来源：" + selected);
                                    else expandStar(table, used, known, warnings);
                                } else {
                                    collect(
                                            selected,
                                            aliases,
                                            inheritedAliases,
                                            used,
                                            known,
                                            warnings);
                                }
                            });
            collectClauses(plain, aliases, inheritedAliases, used, known, warnings);
        }
    }

    private void collectClauses(
            PlainSelect plain,
            Map<String, String> aliases,
            Map<String, String> inheritedAliases,
            Map<String, Set<String>> used,
            Map<String, Map<String, ColumnInfo>> known,
            List<String> warnings) {
        collect(plain.getWhere(), aliases, inheritedAliases, used, known, warnings);
        collect(plain.getHaving(), aliases, inheritedAliases, used, known, warnings);
        if (plain.getGroupBy() != null && plain.getGroupBy().getGroupByExpressionList() != null) {
            plain.getGroupBy()
                    .getGroupByExpressionList()
                    .forEach(
                            expression ->
                                    collect(
                                            (Expression) expression,
                                            aliases,
                                            inheritedAliases,
                                            used,
                                            known,
                                            warnings));
        }
        if (plain.getOrderByElements() != null) {
            plain.getOrderByElements()
                    .forEach(
                            order ->
                                    collect(
                                            order.getExpression(),
                                            aliases,
                                            inheritedAliases,
                                            used,
                                            known,
                                            warnings));
        }
    }

    private void expandStar(
            String table,
            Map<String, Set<String>> used,
            Map<String, Map<String, ColumnInfo>> known,
            List<String> warnings) {
        Map<String, ColumnInfo> columns = known.get(key(table));
        if (columns == null || columns.isEmpty()) {
            warnings.add("无法展开 " + table + ".*：缺少列元数据");
            return;
        }
        columns.values()
                .forEach(
                        column ->
                                used.computeIfAbsent(table, ignored -> new LinkedHashSet<>())
                                        .add(column.getColumnName()));
    }

    private void addFromItem(
            FromItem item,
            Map<String, String> aliases,
            Map<String, String> inheritedAliases,
            Map<String, Set<String>> used,
            Map<String, Map<String, ColumnInfo>> known,
            List<String> warnings) {
        if (item instanceof Table table) {
            String name = table.getName();
            used.computeIfAbsent(name, ignored -> new LinkedHashSet<>());
            aliases.put(key(name), name);
            if (table.getAlias() != null) {
                aliases.put(key(table.getAlias().getName()), name);
            }
        } else if (item instanceof ParenthesedSelect nested) {
            visitSelect(
                    nested.getSelect(),
                    used,
                    known,
                    warnings,
                    visibleAliases(inheritedAliases, aliases));
            warnings.add("派生表字段未自动映射到物理字段");
        }
    }

    private void collect(
            Expression expression,
            Map<String, String> aliases,
            Map<String, String> inheritedAliases,
            Map<String, Set<String>> used,
            Map<String, Map<String, ColumnInfo>> known,
            List<String> warnings) {
        if (expression == null) return;
        expression.accept(
                new ExpressionVisitorAdapter<Void>() {
                    @Override
                    public <S> Void visit(Select nested, S context) {
                        visitSelect(
                                nested,
                                used,
                                known,
                                warnings,
                                visibleAliases(inheritedAliases, aliases));
                        return null;
                    }

                    @Override
                    public <S> Void visit(AnyComparisonExpression comparison, S context) {
                        return visit(comparison.getSelect(), context);
                    }

                    @Override
                    public <S> Void visit(TrimFunction trim, S context) {
                        if (trim.getFromExpression() != null)
                            trim.getFromExpression().accept(this, context);
                        return super.visit(trim, context);
                    }

                    @Override
                    public <S> Void visit(Column column, S context) {
                        String field = column.getColumnName();
                        if ("*".equals(field)) {
                            warnings.add("SELECT * 的字段未展开");
                            return null;
                        }
                        String table = resolve(column, aliases, inheritedAliases, known);
                        if (table == null) {
                            warnings.add("字段无法唯一归属：" + column);
                        } else {
                            used.computeIfAbsent(table, ignored -> new LinkedHashSet<>())
                                    .add(field);
                        }
                        return null;
                    }
                });
    }

    private String resolve(
            Column column,
            Map<String, String> aliases,
            Map<String, String> inheritedAliases,
            Map<String, Map<String, ColumnInfo>> known) {
        if (column.getTable() != null && column.getTable().getName() != null) {
            String alias = key(column.getTable().getName());
            String table = aliases.get(alias);
            return table != null ? table : inheritedAliases.get(alias);
        }
        List<String> matches = matchingTables(column, aliases, known);
        if (matches.size() == 1) return matches.get(0);
        if (!matches.isEmpty()) return null;
        List<String> inheritedMatches = matchingTables(column, inheritedAliases, known);
        return inheritedMatches.size() == 1 ? inheritedMatches.get(0) : null;
    }

    private List<String> matchingTables(
            Column column,
            Map<String, String> aliases,
            Map<String, Map<String, ColumnInfo>> known) {
        return aliases.values().stream()
                .distinct()
                .filter(
                        table ->
                                known.getOrDefault(key(table), Map.of())
                                        .containsKey(key(column.getColumnName())))
                .toList();
    }

    private Map<String, String> visibleAliases(
            Map<String, String> inheritedAliases, Map<String, String> aliases) {
        Map<String, String> visible = new LinkedHashMap<>(inheritedAliases);
        visible.putAll(aliases);
        return visible;
    }

    private static String key(String name) {
        if (name == null) return "";
        String unquoted = name.replace("`", "");
        return unquoted.substring(unquoted.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    public record Lineage(List<TableUse> tables, List<String> warnings) {}
}
