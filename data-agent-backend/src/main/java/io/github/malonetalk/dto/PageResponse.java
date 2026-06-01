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
package io.github.malonetalk.dto;

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

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    public PageResponse {
        items = items == null ? Collections.emptyList() : List.copyOf(items);
    }

    public static int resolvePage(Integer page) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        if (resolvedPage < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1.");
        }
        return resolvedPage;
    }

    public static int resolvePageSize(Integer pageSize) {
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPageSize < 1) {
            throw new IllegalArgumentException("pageSize must be greater than or equal to 1.");
        }
        if (resolvedPageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be less than or equal to 100.");
        }
        return resolvedPageSize;
    }

    public static <T> PageResponse<T> of(List<T> items, long total, int page, int pageSize) {
        long safeTotal = Math.max(0L, total);
        int totalPages = safeTotal == 0L ? 0 : (int) ((safeTotal + pageSize - 1) / pageSize);
        return new PageResponse<>(
                page,
                pageSize,
                safeTotal,
                totalPages,
                totalPages > 0 && page > 1,
                totalPages > 0 && page < totalPages,
                items);
    }

    public static <T> PageResponse<T> empty(int page, int pageSize) {
        return new PageResponse<>(page, pageSize, 0, 0, false, false, Collections.emptyList());
    }
}
