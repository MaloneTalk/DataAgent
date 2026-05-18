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
package io.github.malonetalk.service.semantic;

import io.github.malonetalk.dto.pagination.PageRequest;
import io.github.malonetalk.dto.pagination.PageResponse;
import io.github.malonetalk.entity.ResolvedColumn;
import io.github.malonetalk.entity.ResolvedTable;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public interface SemanticPageService {

    void validateSortOrder(String sortOrder);

    boolean matchesKeywordPrefix(String value, String keywordPrefix);

    Comparator<ResolvedTable> buildResolvedTableComparator(String sortOrder);

    Comparator<ResolvedColumn> buildResolvedColumnComparator(String sortOrder);

    <T> List<T> sliceItems(List<T> items, PageRequest pageRequest);

    <S, T> PageResponse<T> paginateMapped(
            List<S> sourceItems,
            PageRequest pageRequest,
            Predicate<S> filter,
            Function<S, T> mapper);
}
