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

/** Conservative expression lineage for deletion: unknown references block the operation. */
@Service
public class MetricTableReferenceService {
    public boolean mayReference(MetricInfoPo metric, String table, Set<String> knownTables) {
        if (Optional.ofNullable(metric.getMeasureExpr())
                .filter(value -> !value.isBlank())
                .isEmpty()) return true;
        Set<String> references = new HashSet<>();
        Consumer<Table> collect =
                source -> {
                    String name =
                            Optional.ofNullable(source)
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
            return true;
        }
        return references.isEmpty()
                || references.contains(SemanticUtils.normalizeIdentifier(table));
    }
}
