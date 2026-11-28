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

import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.entity.LogicalTableRelation;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.mapper.LogicalTableRelationMapper;
import io.github.malonetalk.service.semantic.SemanticAvailabilityHelper;
import io.github.malonetalk.service.semantic.relation.LogicalTableRelationHelper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.springframework.stereotype.Service;

/** Validate approved JOIN paths before execution, preserving the base table's row grain. */
@Service
@RequiredArgsConstructor
public class SqlJoinValidationService {
    private final LogicalTableRelationMapper relations;
    private final ColumnSemanticInfoMapper columns;
    private final LogicalTableRelationHelper relationHelper;

    public void validate(Integer datasourceId, Select select) {
        if (select.getWithItemsList() != null && !select.getWithItemsList().isEmpty()) {
            throw blocked("CTEs are unsupported.", "Use a MySQL 5.7 compatible SELECT.");
        }
        var expressions =
                new SqlExpressionInspector(column -> {}, nested -> validate(datasourceId, nested));
        if (select instanceof PlainSelect query) {
            validateJoins(datasourceId, query);
            if (query.getFromItem() instanceof ParenthesedSelect nested)
                validate(datasourceId, nested);
            query.getSelectItems().forEach(item -> expressions.inspect(item.getExpression()));
            expressions.inspect(query.getWhere());
            expressions.inspect(query.getHaving());
            if (query.getGroupBy() != null) {
                expressions.inspect(query.getGroupBy().getGroupByExpressionList());
                if (query.getGroupBy().getGroupingSets() != null)
                    query.getGroupBy().getGroupingSets().forEach(expressions::inspect);
            }
        } else if (select instanceof ParenthesedSelect nested) {
            validate(datasourceId, nested.getSelect());
        } else if (select instanceof SetOperationList union) {
            union.getSelects().forEach(branch -> validate(datasourceId, branch));
        } else {
            throw blocked("Unsupported query structure.", "Use a plain SELECT.");
        }
        if (select.getOrderByElements() != null)
            select.getOrderByElements()
                    .forEach(order -> expressions.inspect(order.getExpression()));
        for (Limit limit : new Limit[] {select.getLimit(), select.getLimitBy()}) {
            if (limit == null) continue;
            expressions.inspect(limit.getOffset());
            expressions.inspect(limit.getRowCount());
            expressions.inspect(limit.getByExpressions());
        }
        if (select.getOffset() != null) expressions.inspect(select.getOffset().getOffset());
        if (select.getFetch() != null) expressions.inspect(select.getFetch().getExpression());
    }

    private void validateJoins(Integer datasourceId, PlainSelect query) {
        if (query.getJoins() == null || query.getJoins().isEmpty()) return;
        Table base = requireTable(query.getFromItem());
        Map<String, String> sources = new HashMap<>();
        sources.put(alias(base), normalizeIdentifier(base.getName()));
        var approved =
                relations.selectByDatasourceId(datasourceId).stream()
                        .filter(relation -> Boolean.TRUE.equals(relation.getIsEnabled()))
                        .toList();
        var metadata = columns.selectByDatasourceId(datasourceId);
        for (Join join : query.getJoins()) {
            requireSupportedJoin(join);
            Table target = requireTable(join.getRightItem());
            String name = normalizeIdentifier(target.getName());
            if (sources.containsKey(alias(target)) || sources.containsValue(name)) {
                throw blocked(
                        "Repeated table or alias in JOIN.",
                        "Split self-joins into separate queries.");
            }
            Set<Set<String>> keys = new HashSet<>();
            if (join.getOnExpressions() != null)
                join.getOnExpressions().forEach(on -> collectKeys(on, keys));
            boolean matched =
                    sources.entrySet().stream()
                            .anyMatch(
                                    source ->
                                            approved.stream()
                                                    .anyMatch(
                                                            relation ->
                                                                    matches(
                                                                            relation,
                                                                            source.getValue(),
                                                                            name,
                                                                            source.getKey(),
                                                                            alias(target),
                                                                            keys)));
            if (!matched) {
                throw blocked(
                        "JOIN to "
                                + target.getName()
                                + " has no enabled many-to-one/one-to-one relation matching all"
                                + " keys.",
                        "Use complete approved keys in the row-preserving direction; remove"
                                + " one-to-many joins or query the detail table separately.");
            }
            requirePrimaryKey(name, alias(target), keys, metadata);
            sources.put(alias(target), name);
        }
    }

    private void requireSupportedJoin(Join join) {
        if (join.isSimple()
                || join.isCross()
                || join.isRight()
                || join.isFull()
                || join.isNatural()
                || join.isApply()
                || join.isSemi()
                || join.isStraight()
                || join.isGlobal()
                || join.isWindowJoin()
                || join.isOuter() && !join.isLeft()
                || join.getUsingColumns() != null && !join.getUsingColumns().isEmpty()) {
            throw blocked(
                    "Unsupported JOIN type.",
                    "Use INNER/LEFT JOIN with explicit qualified equality keys.");
        }
    }

    private void collectKeys(Expression expression, Set<Set<String>> keys) {
        if (expression instanceof AndExpression and) {
            collectKeys(and.getLeftExpression(), keys);
            collectKeys(and.getRightExpression(), keys);
        } else if (expression instanceof ParenthesedExpressionList<?> parentheses
                && parentheses.size() == 1) {
            collectKeys(parentheses.get(0), keys);
        } else if (expression instanceof EqualsTo equals
                && equals.getLeftExpression() instanceof Column left
                && equals.getRightExpression() instanceof Column right
                && left.getTable() != null
                && right.getTable() != null) {
            keys.add(
                    Set.copyOf(
                            List.of(
                                    normalizeIdentifier(left.toString()),
                                    normalizeIdentifier(right.toString()))));
        } else {
            throw blocked(
                    "JOIN condition cannot prove row preservation.",
                    "Use alias.column = alias.column keys connected by AND; OR and expression-based"
                            + " joins are unsupported.");
        }
    }

    private boolean matches(
            LogicalTableRelation relation,
            String source,
            String target,
            String sourceAlias,
            String targetAlias,
            Set<Set<String>> keys) {
        boolean forward =
                normalizeIdentifier(relation.getSourceTableName()).equals(source)
                        && normalizeIdentifier(relation.getTargetTableName()).equals(target);
        boolean reverse =
                normalizeIdentifier(relation.getTargetTableName()).equals(source)
                        && normalizeIdentifier(relation.getSourceTableName()).equals(target);
        String type = relation.getRelationType();
        if (!(forward && ("many_to_one".equals(type) || "one_to_one".equals(type))
                || reverse && ("one_to_many".equals(type) || "one_to_one".equals(type))))
            return false;
        var sourceKeys =
                relationHelper.fromJson(
                        forward
                                ? relation.getSourceColumnNamesJson()
                                : relation.getTargetColumnNamesJson(),
                        "sourceColumnNames");
        var targetKeys =
                relationHelper.fromJson(
                        forward
                                ? relation.getTargetColumnNamesJson()
                                : relation.getSourceColumnNamesJson(),
                        "targetColumnNames");
        if (sourceKeys.size() != targetKeys.size()) return false;
        for (int i = 0; i < sourceKeys.size(); i++) {
            if (!keys.contains(
                    Set.of(
                            sourceAlias + "." + normalizeIdentifier(sourceKeys.get(i)),
                            targetAlias + "." + normalizeIdentifier(targetKeys.get(i)))))
                return false;
        }
        return true;
    }

    private void requirePrimaryKey(
            String table, String targetAlias, Set<Set<String>> keys, List<ColumnInfo> metadata) {
        var primary =
                metadata.stream()
                        .filter(column -> normalizeIdentifier(column.getTableName()).equals(table))
                        .filter(column -> Boolean.TRUE.equals(column.getPrimaryKey()))
                        .toList();
        var joinedKeys =
                keys.stream()
                        .filter(pair -> pair.size() == 2)
                        .filter(
                                pair ->
                                        pair.stream()
                                                .anyMatch(
                                                        key -> !key.startsWith(targetAlias + ".")))
                        .flatMap(Set::stream)
                        .collect(Collectors.toSet());
        if (primary.isEmpty()
                || primary.stream()
                        .anyMatch(
                                column ->
                                        !SemanticAvailabilityHelper.hasPhysicalColumn(column)
                                                || !joinedKeys.contains(
                                                        targetAlias
                                                                + "."
                                                                + normalizeIdentifier(
                                                                        column.getColumnName())))) {
            throw blocked(
                    "JOIN target uniqueness is unproven for " + table + ".",
                    "This version requires all target primary-key columns in the JOIN. Synchronize"
                            + " schema and join the complete primary key.");
        }
    }

    private Table requireTable(FromItem item) {
        if (item instanceof Table table
                && table.getSchemaName() == null
                && (table.getDatabase() == null || table.getDatabase().getDatabaseName() == null))
            return table;
        throw blocked(
                "Unsupported JOIN source.",
                "Use direct table sources in the current schema; derived-table joins are"
                        + " unsupported.");
    }

    private String alias(Table table) {
        return normalizeIdentifier(
                table.getAlias() == null ? table.getName() : table.getAlias().getName());
    }

    private BusinessException blocked(String reason, String hint) {
        return BusinessException.of(
                ErrorCode.SQL_NOT_ALLOWED, "SQL validation blocked: " + reason + " Hint: " + hint);
    }
}
