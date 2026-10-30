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

import io.github.malonetalk.exception.BusinessException;
import io.github.malonetalk.exception.ErrorCode;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.AnyComparisonExpression;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.BooleanValue;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.CollateExpression;
import net.sf.jsqlparser.expression.DateTimeLiteralExpression;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExtractExpression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.HexValue;
import net.sf.jsqlparser.expression.IntervalExpression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.MySQLGroupConcat;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeKeyExpression;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.TranscodingFunction;
import net.sf.jsqlparser.expression.TrimFunction;
import net.sf.jsqlparser.expression.WhenClause;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseAnd;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseLeftShift;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseOr;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseRightShift;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseXor;
import net.sf.jsqlparser.expression.operators.arithmetic.Concat;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.IntegerDivision;
import net.sf.jsqlparser.expression.operators.arithmetic.Modulo;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.conditional.XorExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsBooleanExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.expression.operators.relational.RegExpMatchOperator;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Select;

/** Only explicitly supported expression nodes are traversed; every other node fails closed. */
@RequiredArgsConstructor
final class SqlExpressionInspector {
    private static final Set<Class<?>> LITERALS =
            Set.of(
                    NullValue.class,
                    DoubleValue.class,
                    LongValue.class,
                    DateValue.class,
                    TimeValue.class,
                    TimestampValue.class,
                    StringValue.class,
                    BooleanValue.class,
                    HexValue.class,
                    DateTimeLiteralExpression.class);
    private static final Set<Class<?>> BINARY =
            Set.of(
                    Addition.class,
                    Subtraction.class,
                    Multiplication.class,
                    Division.class,
                    IntegerDivision.class,
                    Modulo.class,
                    BitwiseAnd.class,
                    BitwiseOr.class,
                    BitwiseXor.class,
                    BitwiseLeftShift.class,
                    BitwiseRightShift.class,
                    Concat.class,
                    AndExpression.class,
                    OrExpression.class,
                    XorExpression.class,
                    EqualsTo.class,
                    NotEqualsTo.class,
                    GreaterThan.class,
                    GreaterThanEquals.class,
                    MinorThan.class,
                    MinorThanEquals.class,
                    LikeExpression.class,
                    RegExpMatchOperator.class);
    private static final Set<String> FUNCTIONS =
            Set.of(
                    ("count,sum,avg,min,max,round,abs,ceil,ceiling,floor,coalesce,ifnull,nullif,if,concat,concat_ws,substring,substr,left,right,length,char_length,lower,upper,trim,replace,date,date_format,date_add,date_sub"
                         + ",datediff,timestampdiff,year,month,day,dayofmonth,dayofweek,week,quarter,hour,now,curdate,current_date,current_timestamp,str_to_date,cast,convert,group_concat,greatest,least,mod,power,sqrt,stddev,stddev_pop,stddev_samp,variance,var_pop,var_samp,extract,unix_timestamp,from_unixtime,find_in_set,locate,instr,json_extract,json_unquote,json_length,json_contains,format,sign,log,log10,exp")
                            .split(","));
    private final Consumer<Column> columns;
    private final Consumer<Select> selects;

    void inspect(Expression expression) {
        if (expression == null || LITERALS.contains(expression.getClass())) return;
        if (expression instanceof Column column) {
            columns.accept(column);
        } else if (expression instanceof Select select) {
            selects.accept(select);
        } else if (expression instanceof AnyComparisonExpression comparison) {
            selects.accept(comparison.getSelect());
        } else if (expression instanceof ExpressionList<?> list) {
            list.forEach(this::inspect);
        } else if (expression instanceof BinaryExpression binary
                && BINARY.contains(expression.getClass())) {
            inspect(binary.getLeftExpression());
            inspect(binary.getRightExpression());
            if (binary instanceof LikeExpression like) inspect(like.getEscape());
        } else if (expression instanceof Function function) {
            inspectFunction(function);
        } else if (expression instanceof TrimFunction trim) {
            inspect(trim.getExpression());
            inspect(trim.getFromExpression());
        } else if (expression instanceof MySQLGroupConcat concat) {
            inspect(concat.getExpressionList());
            if (concat.getOrderByElements() != null)
                concat.getOrderByElements().forEach(order -> inspect(order.getExpression()));
        } else if (expression instanceof AllColumns all) {
            if (all.getExceptColumns() != null || all.getReplaceExpressions() != null)
                throw denied();
            if (all instanceof AllTableColumns qualified
                    && (qualified.getTable().getSchemaName() != null
                            || qualified.getTable().getDatabase() != null
                                    && qualified.getTable().getDatabase().getDatabaseName()
                                            != null)) throw denied();
        } else if (expression instanceof TimeKeyExpression time) {
            if (!Set.of(
                            "current_date",
                            "current_time",
                            "current_timestamp",
                            "localtime",
                            "localtimestamp")
                    .contains(time.getStringValue().toLowerCase(Locale.ROOT))) throw denied();
        } else {
            inspectOther(expression);
        }
    }

    private void inspectFunction(Function function) {
        if (!FUNCTIONS.contains(function.getName().toLowerCase(Locale.ROOT))
                || function.getKeep() != null
                || function.getAttribute() != null
                || function.getHavingClause() != null
                || function.getLimit() != null) throw denied();
        inspect(function.getParameters());
        inspect(function.getNamedParameters());
        if (function.getOrderByElements() != null)
            function.getOrderByElements().forEach(order -> inspect(order.getExpression()));
    }

    private void inspectOther(Expression expression) {
        if (expression instanceof SignedExpression signed) {
            inspect(signed.getExpression());
        } else if (expression instanceof NotExpression not) {
            inspect(not.getExpression());
        } else if (expression instanceof CastExpression cast) {
            inspect(cast.getLeftExpression());
        } else if (expression instanceof ExtractExpression extract) {
            inspect(extract.getExpression());
        } else if (expression instanceof IntervalExpression interval) {
            inspect(interval.getExpression());
        } else if (expression instanceof CollateExpression collate) {
            inspect(collate.getLeftExpression());
        } else if (expression instanceof TranscodingFunction convert) {
            inspect(convert.getExpression());
        } else if (expression instanceof Between between) {
            inspect(between.getLeftExpression());
            inspect(between.getBetweenExpressionStart());
            inspect(between.getBetweenExpressionEnd());
        } else if (expression instanceof InExpression in) {
            inspect(in.getLeftExpression());
            inspect(in.getRightExpression());
        } else if (expression instanceof ExistsExpression exists) {
            inspect(exists.getRightExpression());
        } else if (expression instanceof IsNullExpression isNull) {
            inspect(isNull.getLeftExpression());
        } else if (expression instanceof IsBooleanExpression isBoolean) {
            inspect(isBoolean.getLeftExpression());
        } else if (expression instanceof CaseExpression condition) {
            inspect(condition.getSwitchExpression());
            condition.getWhenClauses().forEach(this::inspect);
            inspect(condition.getElseExpression());
        } else if (expression instanceof WhenClause when) {
            inspect(when.getWhenExpression());
            inspect(when.getThenExpression());
        } else {
            throw denied();
        }
    }

    private BusinessException denied() {
        return BusinessException.of(ErrorCode.FORBIDDEN, "Unsupported SQL expression.");
    }
}
