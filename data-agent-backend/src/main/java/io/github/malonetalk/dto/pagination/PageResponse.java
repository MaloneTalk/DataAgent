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
package io.github.malonetalk.dto.pagination;

import java.util.Collections;
import java.util.List;

public record PageResponse<T>(
        int page,
        int pageSize,
        long total,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext,
        List<T> items) {

    public PageResponse {
        items = items == null ? Collections.emptyList() : List.copyOf(items);
    }

    public static <T> PageResponse<T> of(List<T> items, long total, PageRequest pageRequest) {
        long safeTotal = Math.max(0L, total);
        int totalPages =
                safeTotal == 0L
                        ? 0
                        : (int) ((safeTotal + pageRequest.pageSize() - 1) / pageRequest.pageSize());
        return new PageResponse<>(
                pageRequest.page(),
                pageRequest.pageSize(),
                safeTotal,
                totalPages,
                totalPages > 0 && pageRequest.page() > 1,
                totalPages > 0 && pageRequest.page() < totalPages,
                items);
    }

    public static <T> PageResponse<T> empty(PageRequest pageRequest) {
        return new PageResponse<>(
                pageRequest.page(),
                pageRequest.pageSize(),
                0,
                0,
                false,
                false,
                Collections.emptyList());
    }
}
