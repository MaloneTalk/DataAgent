/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import { onScopeDispose, reactive, ref, shallowRef } from 'vue';

interface Pagination {
  page: number;
  pageSize: number;
}

export function useSemanticPage<T>(
  fetchPage: (pagination: Pagination) => Promise<{ items: T[]; total: number }>,
) {
  const loading = ref(false);
  const error = ref('');
  const rows = shallowRef<T[]>([]);
  const page = reactive({ page: 1, pageSize: 10, total: 0 });
  let requestId = 0;
  let disposed = false;

  const loadPage = async () => {
    if (disposed) return;
    const currentRequestId = ++requestId;
    loading.value = true;
    error.value = '';
    rows.value = [];
    try {
      const result = await fetchPage({ page: page.page, pageSize: page.pageSize });
      if (currentRequestId !== requestId) return;
      rows.value = result.items;
      page.total = result.total;
    } catch (err) {
      if (currentRequestId !== requestId) return;
      page.total = 0;
      error.value = err instanceof Error ? err.message : '加载失败';
    } finally {
      if (currentRequestId === requestId) loading.value = false;
    }
  };

  const handlePageChange = async (value: number) => {
    page.page = value;
    await loadPage();
  };

  const handleSizeChange = async (value: number) => {
    page.pageSize = value;
    page.page = 1;
    await loadPage();
  };

  onScopeDispose(() => {
    disposed = true;
    requestId++;
  });

  return { loading, error, rows, page, loadPage, handlePageChange, handleSizeChange };
}
