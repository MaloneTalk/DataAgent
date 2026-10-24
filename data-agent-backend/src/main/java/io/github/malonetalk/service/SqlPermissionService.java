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

import io.github.malonetalk.entity.Datasource;
import io.github.malonetalk.enums.PermissionEnum;
import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import io.github.malonetalk.mapper.ColumnSemanticInfoMapper;
import io.github.malonetalk.model.bo.UserContextBo;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.UserVariable;
import net.sf.jsqlparser.expression.VariableAssignment;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.piped.FromQuery;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.LateralSubSelect;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectVisitorAdapter;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.TableStatement;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.select.WithItem;
import org.springframework.stereotype.Service;

/** Restrict every physical source to a projection of authorized columns before execution. */
@Service
@RequiredArgsConstructor
public class SqlPermissionService {
    private final PermissionService permissions;
    private final DataPermissionService data;
    private final ColumnSemanticInfoMapper metadata;
    private static final Set<String> FUNCTIONS =
            Set.of(
                    ("count,sum,avg,min,max,round,abs,ceil,ceiling,floor,coalesce,ifnull,nullif,if,concat,concat_ws,substring,substr,left,right,length,char_length,lower,upper,trim,replace,date,date_format,date_add,date_sub"
                         + ",datediff,timestampdiff,year,month,day,dayofmonth,dayofweek,week,quarter,hour,now,curdate,current_date,current_timestamp,str_to_date,cast,convert,group_concat,greatest,least,mod,power,sqrt,stddev,stddev_pop,stddev_samp,variance,var_pop,var_samp,extract,unix_timestamp,from_unixtime,find_in_set,locate,instr,json_extract,json_unquote,json_length,json_contains,format,sign,log,log10,exp")
                            .split(","));

    public String authorize(Integer userId, Datasource datasource, String sql) {
        UserContextBo user = permissions.require(userId, PermissionEnum.ANALYSIS_SQL_EXECUTE);
        if (Boolean.TRUE.equals(user.superAdmin())) return sql;
        data.requireDatasource(user, datasource.getId());
        try {
            if (!(CCJSqlParserUtil.parse(sql) instanceof Select select)) throw denied();
            Inspector inspector = new Inspector(user, datasource.getId());
            inspector.inspect(select, null);
            if (inspector.referenced.stream().anyMatch(inspector.hidden::contains)) throw denied();
            return select.toString();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "SQL cannot be authorized safely.", ex);
        }
    }

    private BusinessException denied() {
        return BusinessException.of(
                ErrorCode.FORBIDDEN, "SQL references inaccessible data or unsupported syntax.");
    }

    private class Inspector extends SelectVisitorAdapter<Void> {
        private final UserContextBo user;
        private final Integer datasourceId;
        private final Set<String> hidden = new HashSet<>();
        private final Set<String> referenced = new HashSet<>();
        private final ExpressionVisitorAdapter<Void> expressions =
                new ExpressionVisitorAdapter<>() {
                    @Override
                    public <S> Void visit(Column column, S context) {
                        referenced.add(normalizeIdentifier(column.getColumnName()));
                        if (column.getTable() != null && column.getTable().getSchemaName() != null)
                            throw denied();
                        return null;
                    }

                    @Override
                    public <S> Void visit(Function function, S context) {
                        if (!FUNCTIONS.contains(function.getName().toLowerCase(Locale.ROOT)))
                            throw denied();
                        return super.visit(function, context);
                    }

                    @Override
                    public <S> Void visit(UserVariable variable, S context) {
                        throw denied();
                    }

                    @Override
                    public <S> Void visit(VariableAssignment assignment, S context) {
                        throw denied();
                    }

                    @Override
                    public <S> Void visit(Select select, S context) {
                        inspect(select, context);
                        return null;
                    }
                };

        Inspector(UserContextBo user, Integer datasourceId) {
            this.user = user;
            this.datasourceId = datasourceId;
            expressions.setSelectVisitor(this);
        }

        private <S> void inspect(Select select, S context) {
            select.accept(this, context);
        }

        private void rejectWith(Select select) {
            if (select.getWithItemsList() != null && !select.getWithItemsList().isEmpty())
                throw denied(); // MySQL 5.7 does not support CTEs.
        }

        @Override
        public <S> Void visit(WithItem<?> item, S context) {
            throw denied();
        }

        @Override
        public <S> Void visit(Values select, S context) {
            throw denied();
        }

        @Override
        public <S> Void visit(TableStatement select, S context) {
            throw denied();
        }

        @Override
        public <S> Void visit(LateralSubSelect select, S context) {
            throw denied();
        }

        @Override
        public <S> Void visit(FromQuery select, S context) {
            throw denied();
        }

        @Override
        public <S> Void visit(ParenthesedSelect select, S context) {
            rejectWith(select);
            inspect(select.getSelect(), context);
            inspectOrderBy(select, context);
            return null;
        }

        @Override
        public <S> Void visit(PlainSelect select, S context) {
            rejectWith(select);
            if (select.getIntoTables() != null || select.getIntoTempTable() != null) throw denied();
            // ExpressionVisitorAdapter visits nested expressions, including GROUP_CONCAT.
            select.getSelectItems().forEach(item -> item.accept(expressions, context));
            inspectExpression(select.getWhere(), context);
            inspectExpression(select.getHaving(), context);
            inspectOrderBy(select, context);
            if (select.getGroupBy() != null
                    && select.getGroupBy().getGroupByExpressionList() != null)
                select.getGroupBy().getGroupByExpressionList().accept(expressions, context);
            if (select.getQualify() != null) throw denied();
            if (select.getFromItem() != null)
                select.setFromItem(project(select.getFromItem(), context));
            if (select.getJoins() != null) {
                select.getJoins()
                        .forEach(
                                join -> {
                                    if (join.getOnExpressions() != null)
                                        join.getOnExpressions()
                                                .forEach(on -> inspectExpression(on, context));
                                    if (join.getUsingColumns() != null)
                                        join.getUsingColumns()
                                                .forEach(
                                                        c ->
                                                                referenced.add(
                                                                        normalizeIdentifier(
                                                                                c
                                                                                        .getColumnName())));
                                    join.setRightItem(project(join.getRightItem(), context));
                                });
            }
            return null;
        }

        @Override
        public <S> Void visit(SetOperationList select, S context) {
            rejectWith(select);
            select.getSelects().forEach(child -> inspect(child, context));
            inspectOrderBy(select, context);
            return null;
        }

        private <S> void inspectExpression(Expression expression, S context) {
            if (expression != null) expression.accept(expressions, context);
        }

        private <S> void inspectOrderBy(Select select, S context) {
            if (select.getOrderByElements() != null)
                select.getOrderByElements()
                        .forEach(order -> inspectExpression(order.getExpression(), context));
        }

        private <S> FromItem project(FromItem source, S context) {
            if (source instanceof ParenthesedSelect nested) {
                inspect(nested, context);
                return source;
            }
            if (!(source instanceof Table table)
                    || table.getSchemaName() != null
                    || table.getDatabase() != null && table.getDatabase().getDatabaseName() != null)
                throw denied();
            String name = data.requireTable(user, datasourceId, table.getName());
            var hiddenColumns = data.hidden(user, datasourceId, name);
            hidden.addAll(hiddenColumns);
            String projection =
                    metadata.selectByDatasourceIdAndTableName(datasourceId, name).stream()
                            .filter(
                                    c ->
                                            !hiddenColumns.contains(
                                                    normalizeIdentifier(c.getColumnName())))
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
                                ? new Alias(quote(table.getName().replace("`", "")))
                                : table.getAlias());
                return restricted;
            } catch (Exception ex) {
                throw denied();
            }
        }

        private String quote(String identifier) {
            return "`" + identifier.replace("`", "``") + "`";
        }
    }
}
