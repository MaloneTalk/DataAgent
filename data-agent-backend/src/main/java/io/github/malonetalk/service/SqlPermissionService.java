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

import io.github.malonetalk.agent.datasource.DataSourceType;
import io.github.malonetalk.entity.ColumnInfo;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.model.bo.AuthorizedSqlBo;
import io.github.malonetalk.model.bo.DataPermissionScopeBo;
import io.github.malonetalk.model.po.DatasourcePo;
import io.github.malonetalk.service.semantic.SemanticAvailabilityHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.springframework.stereotype.Service;

/** Restrict every physical source to a projection of authorized columns before execution. */
@Service
@RequiredArgsConstructor
public class SqlPermissionService {
    private final PermissionService permissions;
    private final DataPermissionService data;
    private final ColumnSemanticInfoMapper metadata;

    public AuthorizedSqlBo authorize(Integer userId, DatasourcePo datasource, Select select) {
        var user = permissions.require(userId, PermissionEnum.ANALYSIS_SQL_EXECUTE);
        var scope = data.loadScope(user, datasource.getId());
        if (scope.unrestricted()) {
            var columns =
                    metadata.selectByDatasourceId(datasource.getId()).stream()
                            .filter(SemanticAvailabilityHelper::hasPhysicalColumn)
                            .collect(Collectors.groupingBy(c -> c.getTableName()));
            return new AuthorizedSqlBo(select, columnSnapshot(columns));
        }
        if (scope.tables().isEmpty()) throw denied();
        try {
            Inspector inspector = new Inspector(scope, datasource);
            inspector.inspect(select, Optional.empty());
            return new AuthorizedSqlBo(select, columnSnapshot(inspector.visibleColumns));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "SQL cannot be authorized safely.", ex);
        }
    }

    private Map<String, Set<String>> columnSnapshot(Map<String, List<ColumnInfo>> columns) {
        return columns.entrySet().stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                entry -> normalizeIdentifier(entry.getKey()),
                                entry ->
                                        entry.getValue().stream()
                                                .map(c -> normalizeIdentifier(c.getColumnName()))
                                                .collect(Collectors.toUnmodifiableSet())));
    }

    private BusinessException denied() {
        return BusinessException.of(
                ErrorCode.FORBIDDEN, "SQL references inaccessible data or unsupported syntax.");
    }

    private class Inspector {
        private final DataPermissionScopeBo scope;
        private final Integer datasourceId;
        private final String identifierQuote;
        private final Map<String, List<ColumnInfo>> visibleColumns = new HashMap<>();

        Inspector(DataPermissionScopeBo scope, DatasourcePo datasource) {
            this.scope = scope;
            this.datasourceId = datasource.getId();
            var type =
                    DataSourceType.fromCode(datasource.getType())
                            .orElseThrow(
                                    () ->
                                            BusinessException.of(
                                                    ErrorCode.UNSUPPORTED_DATASOURCE_TYPE));
            this.identifierQuote =
                    switch (type) {
                        case MYSQL, OCEANBASE, CLICKHOUSE -> "`";
                        default -> "\"";
                    };
        }

        private SqlQueryScope inspect(Select select, Optional<SqlQueryScope> parent) {
            if (select.getWithItemsList() != null && !select.getWithItemsList().isEmpty())
                throw denied(); // MySQL 5.7 does not support CTEs.
            SqlQueryScope query;
            if (select.getClass() == PlainSelect.class) {
                query = inspectPlainSelect((PlainSelect) select, parent);
            } else if (select.getClass() == ParenthesedSelect.class) {
                query = inspect(((ParenthesedSelect) select).getSelect(), parent);
            } else if (select.getClass() == SetOperationList.class) {
                query = new SqlQueryScope(parent);
                var branches = ((SetOperationList) select).getSelects();
                for (int i = 0; i < branches.size(); i++) {
                    var branch = inspect(branches.get(i), parent);
                    if (i == 0) query.outputColumns.addAll(branch.outputColumns);
                }
            } else {
                throw denied();
            }
            var expressions = expressions(query, query::requireColumn);
            inspectLimit(select.getLimit(), expressions);
            inspectLimit(select.getLimitBy(), expressions);
            if (select.getOffset() != null) expressions.inspect(select.getOffset().getOffset());
            if (select.getFetch() != null) expressions.inspect(select.getFetch().getExpression());
            var ordering = expressions(query, query::requireOrderColumn);
            if (select.getOrderByElements() != null)
                select.getOrderByElements()
                        .forEach(order -> ordering.inspect(order.getExpression()));
            return query;
        }

        private SqlExpressionInspector expressions(SqlQueryScope query, Consumer<Column> columns) {
            return new SqlExpressionInspector(
                    columns, nested -> inspect(nested, Optional.of(query)));
        }

        private void inspectLimit(Limit limit, SqlExpressionInspector expressions) {
            if (limit == null) return;
            expressions.inspect(limit.getOffset());
            expressions.inspect(limit.getRowCount());
            expressions.inspect(limit.getByExpressions());
        }

        private SqlQueryScope inspectPlainSelect(
                PlainSelect select, Optional<SqlQueryScope> parent) {
            if (select.getIntoTables() != null || select.getIntoTempTable() != null) throw denied();
            if (select.getTop() != null
                    || select.getSkip() != null
                    || select.getFirst() != null
                    || select.getOracleHierarchical() != null
                    || select.getPreferringClause() != null) throw denied();
            if (select.getDistinct() != null && select.getDistinct().getOnSelectItems() != null)
                throw denied();
            if (select.getWindowDefinitions() != null && !select.getWindowDefinitions().isEmpty())
                throw denied();
            if (select.getQualify() != null) throw denied();
            var query = new SqlQueryScope(parent);
            var expressions = expressions(query, query::requireColumn);
            if (select.getFromItem() != null)
                select.setFromItem(project(select.getFromItem(), query));
            if (select.getJoins() != null) {
                select.getJoins()
                        .forEach(
                                join -> {
                                    join.setRightItem(project(join.getRightItem(), query));
                                    if (join.getOnExpressions() != null)
                                        join.getOnExpressions().forEach(expressions::inspect);
                                    if (join.getUsingColumns() != null)
                                        join.getUsingColumns().forEach(query::requireColumn);
                                });
            }
            select.getSelectItems()
                    .forEach(
                            item -> {
                                expressions.inspect(item.getExpression());
                                query.addOutput(item);
                            });
            expressions.inspect(select.getWhere());
            var grouping = expressions(query, query::requireGroupingColumn);
            grouping.inspect(select.getHaving());
            if (select.getGroupBy() != null
                    && select.getGroupBy().getGroupByExpressionList() != null)
                grouping.inspect(select.getGroupBy().getGroupByExpressionList());
            if (select.getGroupBy() != null && select.getGroupBy().getGroupingSets() != null)
                select.getGroupBy().getGroupingSets().forEach(grouping::inspect);
            return query;
        }

        private FromItem project(FromItem source, SqlQueryScope query) {
            if (source instanceof ParenthesedSelect nested) {
                // MySQL 5.7 derived tables cannot reference outer query sources.
                var derived = inspect(nested, Optional.empty());
                var alias =
                        Optional.ofNullable(nested.getAlias())
                                .orElseThrow(SqlPermissionService.this::denied);
                query.addSource(alias.getName(), derived.outputColumns, Set.of());
                return source;
            }
            if (!(source instanceof Table table)
                    || table.getSchemaName() != null
                    || table.getDatabase() != null && table.getDatabase().getDatabaseName() != null)
                throw denied();
            String name = scope.requireTable(table.getName());
            var columns =
                    visibleColumns.computeIfAbsent(
                            name,
                            tableName ->
                                    metadata
                                            .selectByDatasourceIdAndTableName(
                                                    datasourceId, tableName)
                                            .stream()
                                            .filter(SemanticAvailabilityHelper::hasPhysicalColumn)
                                            .filter(
                                                    c ->
                                                            scope.canColumn(
                                                                    tableName, c.getColumnName()))
                                            .toList());
            query.addSource(
                    table.getAlias() == null ? table.getName() : table.getAlias().getName(),
                    columns.stream().map(ColumnInfo::getColumnName).collect(Collectors.toSet()),
                    scope.hidden(name));
            String projection =
                    columns.stream()
                            .map(c -> quote(c.getColumnName()))
                            .collect(Collectors.joining(", "));
            if (projection.isEmpty()) throw denied();
            try {
                ParenthesedSelect restricted = new ParenthesedSelect();
                restricted.setSelect(
                        (Select)
                                CCJSqlParserUtil.parse(
                                        "SELECT " + projection + " FROM " + quote(name)));
                restricted.setAlias(
                        table.getAlias() == null
                                ? new Alias(table.getName(), false)
                                : table.getAlias());
                return restricted;
            } catch (Exception ex) {
                throw denied();
            }
        }

        private String quote(String identifier) {
            return identifierQuote
                    + identifier.replace(identifierQuote, identifierQuote + identifierQuote)
                    + identifierQuote;
        }
    }
}
