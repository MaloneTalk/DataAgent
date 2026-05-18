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
package io.github.malonetalk.service.impl.semantic;

import static io.github.malonetalk.common.SemanticConstants.SORT_ORDER_ASC;
import static io.github.malonetalk.common.SemanticConstants.SORT_ORDER_DESC;

import io.github.malonetalk.agent.datasource.ColumnInfo;
import io.github.malonetalk.dto.pagination.PageRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.entity.ResolvedColumn;
import io.github.malonetalk.entity.ResolvedTable;
import io.github.malonetalk.entity.TableInfo;
import io.github.malonetalk.service.semantic.SemanticPageService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

@Service
public class SemanticPageServiceImpl implements SemanticPageService {

    @Override
    public void validateSortOrder(String sortOrder) {
        if (sortOrder != null
                && !sortOrder.isBlank()
                && !SORT_ORDER_ASC.equalsIgnoreCase(sortOrder)
                && !SORT_ORDER_DESC.equalsIgnoreCase(sortOrder)) {
            throw new IllegalArgumentException(
                    "sortOrder must be '"
                            + SORT_ORDER_ASC
                            + "' or '"
                            + SORT_ORDER_DESC
                            + "', got: "
                            + sortOrder);
        }
    }

    @Override
    public boolean matchesKeywordPrefix(String value, String keywordPrefix) {
        if (keywordPrefix == null || keywordPrefix.isBlank()) {
            return true;
        }
        return value != null
                && value.toLowerCase(Locale.ROOT)
                        .startsWith(keywordPrefix.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public Comparator<TableInfo> buildTableComparator(String sortOrder) {
        Comparator<TableInfo> comparator =
                Comparator.comparing(table -> table.getTableName().toLowerCase(Locale.ROOT));
        return isDescending(sortOrder) ? comparator.reversed() : comparator;
    }

    @Override
    public Comparator<ColumnInfo> buildColumnComparator(String sortOrder) {
        Comparator<ColumnInfo> comparator =
                Comparator.comparing(column -> column.getColumnName().toLowerCase(Locale.ROOT));
        return isDescending(sortOrder) ? comparator.reversed() : comparator;
    }

    @Override
    public Comparator<ResolvedTable> buildResolvedTableComparator(String sortOrder) {
        Comparator<ResolvedTable> comparator =
                Comparator.comparing(table -> table.canonicalName().toLowerCase(Locale.ROOT));
        return isDescending(sortOrder) ? comparator.reversed() : comparator;
    }

    @Override
    public Comparator<ResolvedColumn> buildResolvedColumnComparator(String sortOrder) {
        Comparator<ResolvedColumn> comparator =
                Comparator.comparing(column -> column.columnName().toLowerCase(Locale.ROOT));
        return isDescending(sortOrder) ? comparator.reversed() : comparator;
    }

    @Override
    public <T> List<T> sliceItems(List<T> items, PageRequest pageRequest) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        long start = pageRequest.offset();
        int fromIndex = start >= items.size() ? items.size() : (int) start;
        int toIndex = Math.min(fromIndex + pageRequest.pageSize(), items.size());
        if (fromIndex >= toIndex) {
            return List.of();
        }
        return items.subList(fromIndex, toIndex);
    }

    @Override
    public <S, T> PageResponse<T> paginateMapped(
            List<S> sourceItems,
            PageRequest pageRequest,
            Predicate<S> filter,
            Function<S, T> mapper) {
        if (sourceItems == null || sourceItems.isEmpty()) {
            return PageResponse.empty(pageRequest);
        }
        long start = pageRequest.offset();
        long endExclusive = start + pageRequest.pageSize();
        long matchedCount = 0L;
        List<T> pageItems = new ArrayList<>();
        for (S sourceItem : sourceItems) {
            if (!filter.test(sourceItem)) {
                continue;
            }
            if (matchedCount >= start && matchedCount < endExclusive) {
                pageItems.add(mapper.apply(sourceItem));
            }
            matchedCount++;
        }
        if (matchedCount == 0L) {
            return PageResponse.empty(pageRequest);
        }
        return PageResponse.of(pageItems, matchedCount, pageRequest);
    }

    private boolean isDescending(String sortOrder) {
        return SORT_ORDER_DESC.equalsIgnoreCase(sortOrder);
    }
}
