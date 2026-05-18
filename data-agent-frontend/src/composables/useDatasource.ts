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

import { ref } from 'vue';
import {
  getDatasourceList,
  createDatasource,
  updateDatasource,
  deleteDatasource,
  activateDatasource,
  deactivateDatasource,
  type DatasourceRequest,
  type DatasourceResponse,
} from '@/api/datasource';

export function useDatasource() {
  const list = ref<DatasourceResponse[]>([]);
  const loading = ref(false);
  const error = ref<Error | null>(null);

  const fetchList = async () => {
    loading.value = true;
    error.value = null;
    try {
      const res = await getDatasourceList();
      list.value = res.data.data;
    } catch (e) {
      error.value = e as Error;
    } finally {
      loading.value = false;
    }
  };

  const addDatasource = async (data: DatasourceRequest) => {
    await createDatasource(data);
    await fetchList();
  };

  const editDatasource = async (data: DatasourceRequest) => {
    await updateDatasource(data);
    await fetchList();
  };

  const removeDatasource = async (id: number) => {
    await deleteDatasource(id);
    await fetchList();
  };

  const activate = async (id: number) => {
    await activateDatasource(id);
    await fetchList();
  };

  const deactivate = async (id: number) => {
    await deactivateDatasource(id);
    await fetchList();
  };

  return {
    list,
    loading,
    error,
    fetchList,
    addDatasource,
    editDatasource,
    removeDatasource,
    activate,
    deactivate,
  };
}
