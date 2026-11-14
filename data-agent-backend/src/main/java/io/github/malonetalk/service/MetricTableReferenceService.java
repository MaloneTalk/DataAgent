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
import io.github.malonetalk.model.po.MetricInfoPo;
import io.github.malonetalk.utils.SemanticUtils;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import org.springframework.stereotype.Service;

/** Conservative expression lineage for deletion and CSV metric reference validation. */
@Service
public class MetricTableReferenceService {
    public boolean mayReference(MetricInfoPo metric, String table, Set<String> knownTables) {
        return references(metric, knownTables)
                .map(references -> references.contains(SemanticUtils.normalizeIdentifier(table)))
                .orElse(true);
    }

    public void requireReadyTables(MetricInfoPo metric, Set<String> readyTables) {
        if (references(metric, readyTables).isEmpty())
            throw BusinessException.of(
                    ErrorCode.CSV_TABLE_NOT_READY, "CSV 指标必须明确引用已就绪的表，请使用表名限定字段并检查相关导入任务。");
    }

    private Optional<Set<String>> references(MetricInfoPo metric, Set<String> knownTables) {
        if (Optional.ofNullable(metric.getMeasureExpr())
                .filter(value -> !value.isBlank())
                .isEmpty()) return Optional.empty();
        Set<String> references = new HashSet<>();
        Consumer<Table> collect =
                source -> {
                    String name =
                            Optional.ofNullable(source)
                                    .filter(table -> table.getSchemaName() == null)
                                    .filter(
                                            table ->
                                                    table.getDatabase() == null
                                                            || table.getDatabase().getDatabaseName()
                                                                    == null)
                                    .map(Table::getName)
                                    .map(SemanticUtils::normalizeIdentifier)
                                    .filter(knownTables::contains)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Unknown metric table"));
                    references.add(name);
                };
        var inspector =
                new SqlExpressionInspector(
                        column -> collect.accept(column.getTable()),
                        select -> {
                            throw new IllegalArgumentException(
                                    "Metric subquery lineage is unknown");
                        },
                        all -> {
                            if (all instanceof AllTableColumns qualified)
                                collect.accept(qualified.getTable());
                            else throw new IllegalArgumentException("Unqualified metric wildcard");
                        });
        try {
            // Parse fields separately: a qualified filter cannot resolve an unqualified measure.
            // String literals and comments are never treated as table references.
            for (String expression :
                    Stream.of(metric.getMeasureExpr(), metric.getFilters(), metric.getTimeField())
                            .flatMap(value -> Optional.ofNullable(value).stream())
                            .filter(value -> !value.isBlank())
                            .toList()) {
                inspector.inspect(CCJSqlParserUtil.parseExpression(expression, false));
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return references.isEmpty() ? Optional.empty() : Optional.of(Set.copyOf(references));
    }
}
