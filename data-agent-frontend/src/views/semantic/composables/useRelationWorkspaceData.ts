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

import { reactive, ref } from 'vue';
import { useDatasource } from '@/composables/useDatasource';
import {
  getRelationWorkspace,
  type LogicalTableRelationResponse,
  type RelationWorkspaceTableResponse,
} from '@/api/semantic';
import type { TableNodeLayout } from '../types';

const NODE_WIDTH = 280;
const HEADER_HEIGHT = 58;
const COLUMN_HEIGHT = 32;
const GAP_X = 72;
const GAP_Y = 40;
const COLUMNS_PER_ROW = 3;
const STATE_STORAGE_KEY = 'semantic-model:relation-manage-state';
const STATE_VERSION = 1;

interface RelationManageStateSnapshot {
  version: number;
  datasourceId?: number;
  page?: number;
  pageSize?: number;
}

function buildRelationLayouts(tables: RelationWorkspaceTableResponse[]): TableNodeLayout[] {
  const rowHeights: number[] = [];
  const preparedNodes = tables.map((table, index) => {
    const rowIndex = Math.floor(index / COLUMNS_PER_ROW);
    const columnIndex = index % COLUMNS_PER_ROW;
    const height = HEADER_HEIGHT + Math.max(table.columns.length, 1) * COLUMN_HEIGHT + 20;
    rowHeights[rowIndex] = Math.max(rowHeights[rowIndex] ?? 0, height);
    return { table, rowIndex, columnIndex, height };
  });

  return preparedNodes.map(node => {
    const rowOffset = rowHeights
      .slice(0, node.rowIndex)
      .reduce((sum, height) => sum + height + GAP_Y, 0);
    return {
      ...node.table,
      x: node.columnIndex * (NODE_WIDTH + GAP_X) + 32,
      y: rowOffset + 32,
      width: NODE_WIDTH,
      height: node.height,
    } satisfies TableNodeLayout;
  });
}

export function useRelationWorkspaceData() {
  const {
    list: datasourceList,
    loading: datasourceLoading,
    error: datasourceError,
    fetchList: fetchDatasourceList,
  } = useDatasource();
  const selectedDatasourceId = ref<number>();
  const relationLoading = ref(false);
  const relationError = ref('');
  const relationNodes = ref<TableNodeLayout[]>([]);
  const relationRecords = ref<LogicalTableRelationResponse[]>([]);
  const relationLoadToken = ref(0);
  const suppressDatasourceWatch = ref(false);
  const workspacePage = reactive({ page: 1, pageSize: 20, total: 0 });

  function readManageStateSnapshot(): RelationManageStateSnapshot | null {
    try {
      const raw = globalThis.localStorage.getItem(STATE_STORAGE_KEY);
      if (!raw) return null;
      const snapshot = JSON.parse(raw) as RelationManageStateSnapshot;
      return snapshot.version === STATE_VERSION ? snapshot : null;
    } catch {
      return null;
    }
  }

  function persistManageStateSnapshot() {
    const snapshot: RelationManageStateSnapshot = {
      version: STATE_VERSION,
      datasourceId: selectedDatasourceId.value,
      page: workspacePage.page,
      pageSize: workspacePage.pageSize,
    };
    try {
      globalThis.localStorage.setItem(STATE_STORAGE_KEY, JSON.stringify(snapshot));
    } catch {
      // Browser storage can be unavailable in strict privacy modes.
    }
  }

  async function loadRelationWorkspace(datasourceId: number, loadToken: number) {
    relationLoading.value = true;
    relationError.value = '';
    try {
      const workspace = await getRelationWorkspace({
        datasourceId,
        page: workspacePage.page,
        pageSize: workspacePage.pageSize,
        sortOrder: 'asc',
      });
      const nextNodes = buildRelationLayouts(workspace.nodes.items);
      if (loadToken !== relationLoadToken.value || datasourceId !== selectedDatasourceId.value)
        return;

      relationNodes.value = nextNodes;
      relationRecords.value = workspace.relations;
      workspacePage.total = workspace.nodes.total;
    } catch (error) {
      if (loadToken !== relationLoadToken.value || datasourceId !== selectedDatasourceId.value)
        return;
      relationError.value = (error as Error).message;
      relationNodes.value = [];
      relationRecords.value = [];
    } finally {
      if (loadToken === relationLoadToken.value) relationLoading.value = false;
    }
  }

  async function loadRelationData() {
    if (typeof selectedDatasourceId.value !== 'number') {
      relationNodes.value = [];
      relationRecords.value = [];
      relationError.value = '';
      return;
    }
    const datasourceId = selectedDatasourceId.value;
    const loadToken = relationLoadToken.value + 1;
    relationLoadToken.value = loadToken;
    await loadRelationWorkspace(datasourceId, loadToken);
  }

  async function initializeDatasource() {
    await fetchDatasourceList();
    const savedState = readManageStateSnapshot();
    const savedDatasource = datasourceList.value.find(item => item.id === savedState?.datasourceId);
    suppressDatasourceWatch.value = true;
    if (typeof selectedDatasourceId.value !== 'number') {
      const firstActive = datasourceList.value.find(item => item.status === 'ACTIVE');
      selectedDatasourceId.value =
        savedDatasource?.id ?? firstActive?.id ?? datasourceList.value[0]?.id;
    }
    if (typeof savedState?.page === 'number') workspacePage.page = savedState.page;
    if (typeof savedState?.pageSize === 'number') workspacePage.pageSize = savedState.pageSize;
    try {
      await loadRelationData();
    } finally {
      suppressDatasourceWatch.value = false;
      persistManageStateSnapshot();
    }
  }

  return {
    datasourceList,
    datasourceLoading,
    datasourceError,
    selectedDatasourceId,
    relationLoading,
    relationError,
    relationNodes,
    relationRecords,
    workspacePage,
    suppressDatasourceWatch,
    persistManageStateSnapshot,
    loadRelationData,
    initializeDatasource,
  };
}
