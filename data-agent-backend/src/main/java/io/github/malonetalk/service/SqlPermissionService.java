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
import io.github.malonetalk.model.bo.UserContextBo;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;

/** Restrict every physical source to a projection of authorized columns before execution. */
@Service
@RequiredArgsConstructor
public class SqlPermissionService {
    private final PermissionService permissions;
    private final DataPermissionService data;
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
            inspector.getTables((Statement) select);
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

    private class Inspector extends TablesNamesFinder<Void> {
        private final UserContextBo user;
        private final Integer datasourceId;
        private final Set<String> hidden = new HashSet<>();
        private final Set<String> referenced = new HashSet<>();

        Inspector(UserContextBo user, Integer datasourceId) {
            this.user = user;
            this.datasourceId = datasourceId;
        }

        @Override
        public <S> Void visit(WithItem<?> item, S context) {
            throw denied(); // MySQL 5.7 does not support CTEs.
        }

        @Override
        public <S> Void visit(Column column, S context) {
            referenced.add(normalizeIdentifier(column.getColumnName()));
            if (column.getTable() != null && column.getTable().getSchemaName() != null)
                throw denied();
            return super.visit(column, context);
        }

        @Override
        public <S> Void visit(Function function, S context) {
            if (!FUNCTIONS.contains(function.getName().toLowerCase(Locale.ROOT))) throw denied();
            return super.visit(function, context);
        }

        @Override
        public <S> Void visit(PlainSelect select, S context) {
            if (select.getIntoTables() != null || select.getIntoTempTable() != null) throw denied();
            // Visit original nested queries first. Generated projections must not be rewritten
            // again.
            super.visit(select, context);
            if (select.getOrderByElements() != null)
                select.getOrderByElements()
                        .forEach(order -> order.getExpression().accept(this, context));
            if (select.getGroupBy() != null
                    && select.getGroupBy().getGroupByExpressionList() != null)
                select.getGroupBy().getGroupByExpressionList().accept(this, context);
            if (select.getHaving() != null) select.getHaving().accept(this, context);
            if (select.getQualify() != null) throw denied();
            if (select.getFromItem() != null) select.setFromItem(project(select.getFromItem()));
            if (select.getJoins() != null) {
                select.getJoins()
                        .forEach(
                                join -> {
                                    if (join.getUsingColumns() != null)
                                        join.getUsingColumns()
                                                .forEach(
                                                        c ->
                                                                referenced.add(
                                                                        normalizeIdentifier(
                                                                                c
                                                                                        .getColumnName())));
                                    join.setRightItem(project(join.getRightItem()));
                                });
            }
            return null;
        }

        @Override
        public <S> Void visit(SetOperationList select, S context) {
            super.visit(select, context);
            if (select.getOrderByElements() != null)
                select.getOrderByElements()
                        .forEach(order -> order.getExpression().accept(this, context));
            return null;
        }

        private FromItem project(FromItem source) {
            if (source instanceof ParenthesedSelect) return source;
            if (!(source instanceof Table table)
                    || table.getSchemaName() != null
                    || table.getDatabase() != null && table.getDatabase().getDatabaseName() != null)
                throw denied();
            String name = normalizeIdentifier(table.getName());
            hidden.addAll(data.hidden(user, datasourceId, name));
            var columns = data.visibleColumns(user, datasourceId, name);
            if (columns.isEmpty()) throw denied();
            String projection =
                    columns.stream()
                            .map(c -> quote(c.getColumnName()))
                            .collect(Collectors.joining(", "));
            try {
                ParenthesedSelect restricted = new ParenthesedSelect();
                restricted.setSelect(
                        (Select)
                                CCJSqlParserUtil.parse(
                                        "SELECT "
                                                + projection
                                                + " FROM "
                                                + quote(table.getName().replace("`", ""))));
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
