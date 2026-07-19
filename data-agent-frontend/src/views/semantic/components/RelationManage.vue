<!--
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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 -->

<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { useDatasource } from '@/composables/useDatasource';
  import type { DatasourceResponse } from '@/api/datasource';
  import { getFieldErrorMap } from '@/api/request';
  import {
    createLogicalRelation,
    deleteLogicalRelation,
    getColumnSemanticPage,
    getRelationWorkspace,
    updateLogicalRelation,
    updateLogicalRelationEnabled,
    type BindLogicalTableRelationRequest,
    type LogicalTableRelationResponse,
    type UpdateLogicalTableRelationRequest,
  } from '@/api/semantic';
  import RelationEditDialog from './RelationEditDialog.vue';
  import RelationWorkspace from './RelationWorkspace.vue';
  import type {
    RelationColumnNode,
    RelationDragCreatePayload,
    RelationForm,
    RelationTableNode,
    TableNodeLayout,
  } from '../types';

  const NODE_WIDTH = 280;
  const HEADER_HEIGHT = 58;
  const COLUMN_HEIGHT = 32;
  const GAP_X = 72;
  const GAP_Y = 40;
  const COLUMNS_PER_ROW = 3;
  const RELATION_MANAGE_STATE_STORAGE_KEY = 'semantic-model:relation-manage-state';
  const RELATION_MANAGE_STATE_VERSION = 1;

  interface RelationManageStateSnapshot {
    version: number;
    datasourceId?: number;
    page?: number;
    pageSize?: number;
    updatedAt: string;
  }

  const {
    list: datasourceList,
    loading: datasourceLoading,
    error: datasourceError,
    fetchList: fetchDatasourceList,
  } = useDatasource();

  const selectedDatasourceId = ref<number>();
  const relationLoading = ref(false);
  const relationNodeLoading = ref(false);
  const relationError = ref('');
  const relationNodes = ref<TableNodeLayout[]>([]);
  const relationRecords = ref<LogicalTableRelationResponse[]>([]);
  const selectedRelation = ref<LogicalTableRelationResponse | null>(null);
  const relationLoadToken = ref(0);
  const workspacePage = reactive({
    page: 1,
    pageSize: 20,
    total: 0,
  });

  const relationDialogVisible = ref(false);
  const relationSubmitLoading = ref(false);
  const relationForm = reactive<RelationForm>({
    sourceTableName: '',
    sourceColumnNames: [],
    targetTableName: '',
    targetColumnNames: [],
    description: '',
    enabled: true,
  });
  const relationFieldErrors = reactive<Record<string, string>>({});
  const relationSourceColumns = ref<RelationColumnNode[]>([]);
  const relationTargetColumns = ref<RelationColumnNode[]>([]);
  const suppressRelationTableWatch = ref(false);
  const suppressDatasourceWatch = ref(false);

  const activeDatasource = computed<DatasourceResponse | undefined>(() =>
    datasourceList.value.find(item => item.id === selectedDatasourceId.value),
  );

  const canQuery = computed(() => typeof selectedDatasourceId.value === 'number');

  const draftRelation = computed(() => {
    if (
      !relationForm.sourceTableName ||
      !relationForm.targetTableName ||
      relationForm.sourceColumnNames.length === 0 ||
      relationForm.targetColumnNames.length === 0
    ) {
      return null;
    }
    return {
      sourceTableName: relationForm.sourceTableName,
      sourceColumnNames: [...relationForm.sourceColumnNames],
      targetTableName: relationForm.targetTableName,
      targetColumnNames: [...relationForm.targetColumnNames],
      enabled: relationForm.enabled,
    };
  });

  function buildRelationLayouts(
    tables: RelationTableNode[],
    columnsMap: Map<string, RelationColumnNode[]>,
  ) {
    const rowHeights: number[] = [];

    const preparedNodes = tables.map((table, index) => {
      const rowIndex = Math.floor(index / COLUMNS_PER_ROW);
      const columnIndex = index % COLUMNS_PER_ROW;
      const columns = columnsMap.get(table.tableName) ?? [];
      const height = HEADER_HEIGHT + Math.max(columns.length, 1) * COLUMN_HEIGHT + 20;
      rowHeights[rowIndex] = Math.max(rowHeights[rowIndex] ?? 0, height);

      return {
        table,
        columns,
        rowIndex,
        columnIndex,
        height,
      };
    });

    return preparedNodes.map(node => {
      const rowOffset = rowHeights
        .slice(0, node.rowIndex)
        .reduce((sum, height) => sum + height + GAP_Y, 0);

      return {
        ...node.table,
        columns: node.columns,
        x: node.columnIndex * (NODE_WIDTH + GAP_X) + 32,
        y: rowOffset + 32,
        width: NODE_WIDTH,
        height: node.height,
      } satisfies TableNodeLayout;
    });
  }

  function readManageStateSnapshot(): RelationManageStateSnapshot | null {
    try {
      const raw = globalThis.localStorage.getItem(RELATION_MANAGE_STATE_STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const snapshot = JSON.parse(raw) as RelationManageStateSnapshot;
      return snapshot.version === RELATION_MANAGE_STATE_VERSION ? snapshot : null;
    } catch {
      return null;
    }
  }

  function persistManageStateSnapshot() {
    const snapshot: RelationManageStateSnapshot = {
      version: RELATION_MANAGE_STATE_VERSION,
      datasourceId: selectedDatasourceId.value,
      page: workspacePage.page,
      pageSize: workspacePage.pageSize,
      updatedAt: new Date().toISOString(),
    };

    try {
      globalThis.localStorage.setItem(RELATION_MANAGE_STATE_STORAGE_KEY, JSON.stringify(snapshot));
    } catch {
      // Browser storage can be unavailable in strict privacy modes.
    }
  }

  async function fetchRelationColumns(tableName: string) {
    if (typeof selectedDatasourceId.value !== 'number') {
      return [];
    }
    const localNode = relationNodes.value.find(node => node.tableName === tableName);
    if (localNode) {
      return localNode.columns;
    }
    const response = await getColumnSemanticPage(tableName, {
      datasourceId: selectedDatasourceId.value,
      page: 1,
      pageSize: 100,
      sortOrder: 'asc',
    });
    const items = response.data.data.items;

    return items.map(
      (item): RelationColumnNode => ({
        columnName: item.columnName,
        description: item.columnDescription ?? item.physicalColumnDescription,
        typeName: item.typeName,
        primaryKey: item.primaryKey,
        operable: item.effective,
        invalidReason: item.invalidReason,
      }),
    );
  }

  async function loadRelationWorkspace(datasourceId: number, loadToken: number) {
    relationNodeLoading.value = true;
    relationLoading.value = true;
    relationError.value = '';
    try {
      const response = await getRelationWorkspace({
        datasourceId,
        page: workspacePage.page,
        pageSize: workspacePage.pageSize,
        sortOrder: 'asc',
      });
      const workspace = response.data.data;
      const tables = workspace.nodes.items.map(
        (item): RelationTableNode => ({
          tableName: item.tableName,
          domain: item.domain,
          description: item.description,
          operable: item.operable,
          invalidReason: item.invalidReason,
        }),
      );

      const columnsMap = new Map<string, RelationColumnNode[]>();
      workspace.nodes.items.forEach(table => {
        columnsMap.set(table.tableName, table.columns);
      });

      const nextNodes = buildRelationLayouts(tables, columnsMap);
      if (loadToken !== relationLoadToken.value || datasourceId !== selectedDatasourceId.value) {
        return;
      }

      relationNodes.value = nextNodes;
      relationRecords.value = workspace.relations;
      workspacePage.total = workspace.nodes.total;
    } catch (error) {
      if (loadToken !== relationLoadToken.value || datasourceId !== selectedDatasourceId.value) {
        return;
      }
      relationError.value = (error as Error).message;
      relationNodes.value = [];
      relationRecords.value = [];
    } finally {
      if (loadToken === relationLoadToken.value) {
        relationNodeLoading.value = false;
        relationLoading.value = false;
      }
    }
  }

  async function loadRelationData() {
    if (!canQuery.value || typeof selectedDatasourceId.value !== 'number') {
      relationNodes.value = [];
      relationRecords.value = [];
      relationError.value = '';
      return;
    }

    const datasourceId = selectedDatasourceId.value;
    const loadToken = relationLoadToken.value + 1;
    relationLoadToken.value = loadToken;

    try {
      await loadRelationWorkspace(datasourceId, loadToken);
    } catch (error) {
      if (loadToken !== relationLoadToken.value || datasourceId !== selectedDatasourceId.value) {
        return;
      }
      relationError.value = (error as Error).message;
      relationNodes.value = [];
      relationRecords.value = [];
      relationNodeLoading.value = false;
      relationLoading.value = false;
    }
  }

  async function handleWorkspacePageChange(page: number) {
    workspacePage.page = page;
    persistManageStateSnapshot();
    resetRelationForm();
    await loadRelationData();
  }

  async function handleWorkspaceSizeChange(pageSize: number) {
    workspacePage.pageSize = pageSize;
    workspacePage.page = 1;
    persistManageStateSnapshot();
    resetRelationForm();
    await loadRelationData();
  }

  function resetRelationForm() {
    clearRelationFieldErrors();
    Object.assign(relationForm, {
      sourceTableName: '',
      sourceColumnNames: [],
      targetTableName: '',
      targetColumnNames: [],
      description: '',
      enabled: true,
    });
    relationSourceColumns.value = [];
    relationTargetColumns.value = [];
    selectedRelation.value = null;
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
    if (typeof savedState?.page === 'number') {
      workspacePage.page = savedState.page;
    }
    if (typeof savedState?.pageSize === 'number') {
      workspacePage.pageSize = savedState.pageSize;
    }
    try {
      await loadRelationData();
    } finally {
      suppressDatasourceWatch.value = false;
      persistManageStateSnapshot();
    }
  }

  async function handleSourceTableChange(tableName: string) {
    if (suppressRelationTableWatch.value) {
      return;
    }
    relationForm.sourceColumnNames = [];
    relationSourceColumns.value = tableName ? await fetchRelationColumns(tableName) : [];
  }

  async function handleTargetTableChange(tableName: string) {
    if (suppressRelationTableWatch.value) {
      return;
    }
    relationForm.targetColumnNames = [];
    relationTargetColumns.value = tableName ? await fetchRelationColumns(tableName) : [];
  }

  async function handleDragCreateRelation(payload: RelationDragCreatePayload) {
    clearRelationFieldErrors();
    if (payload.sourceTableName === payload.targetTableName) {
      ElMessage.warning('不能把关系拖回同一张表');
      return;
    }

    suppressRelationTableWatch.value = true;
    try {
      Object.assign(relationForm, {
        sourceTableName: payload.sourceTableName,
        sourceColumnNames: [payload.sourceColumnName],
        targetTableName: payload.targetTableName,
        targetColumnNames: [payload.targetColumnName],
        description: '',
        enabled: true,
      });

      const [sourceColumns, targetColumns] = await Promise.all([
        fetchRelationColumns(payload.sourceTableName),
        fetchRelationColumns(payload.targetTableName),
      ]);

      relationSourceColumns.value = sourceColumns;
      relationTargetColumns.value = targetColumns;
      selectedRelation.value = null;
      relationDialogVisible.value = true;
    } finally {
      suppressRelationTableWatch.value = false;
    }
  }

  async function handleEditRelation(relation: LogicalTableRelationResponse) {
    clearRelationFieldErrors();
    if (relation.source === 'physical') {
      ElMessage.warning('物理外键仅展示，不支持直接编辑');
      return;
    }
    if (typeof relation.id !== 'number') {
      ElMessage.error('当前逻辑外键缺少有效标识，无法编辑');
      return;
    }

    selectedRelation.value = relation;
    relationDialogVisible.value = true;
    suppressRelationTableWatch.value = true;

    try {
      Object.assign(relationForm, {
        sourceTableName: relation.sourceTableName,
        sourceColumnNames: [...relation.sourceColumnNames],
        targetTableName: relation.targetTableName,
        targetColumnNames: [...relation.targetColumnNames],
        description: relation.description ?? '',
        enabled: relation.enabled,
      });

      const [sourceColumns, targetColumns] = await Promise.all([
        fetchRelationColumns(relation.sourceTableName),
        fetchRelationColumns(relation.targetTableName),
      ]);

      relationSourceColumns.value = sourceColumns;
      relationTargetColumns.value = targetColumns;
    } finally {
      suppressRelationTableWatch.value = false;
    }
  }

  async function handleSubmitRelation() {
    clearRelationFieldErrors();
    if (typeof selectedDatasourceId.value !== 'number') {
      return;
    }
    if (relationForm.sourceTableName === relationForm.targetTableName) {
      ElMessage.warning('源表和目标表不能相同');
      return;
    }
    if (relationForm.sourceColumnNames.length !== relationForm.targetColumnNames.length) {
      ElMessage.warning('源列与目标列数量必须一致');
      return;
    }

    relationSubmitLoading.value = true;

    try {
      const payload: BindLogicalTableRelationRequest | UpdateLogicalTableRelationRequest = {
        datasourceId: selectedDatasourceId.value,
        sourceColumnNames: [...relationForm.sourceColumnNames],
        targetTableName: relationForm.targetTableName,
        targetColumnNames: [...relationForm.targetColumnNames],
        description: relationForm.description.trim(),
        enabled: relationForm.enabled,
      };

      if (selectedRelation.value) {
        const relationId = selectedRelation.value.id;
        if (typeof relationId !== 'number') {
          ElMessage.error('当前逻辑外键缺少有效标识，无法更新');
          return;
        }

        await updateLogicalRelation(relationForm.sourceTableName, {
          ...payload,
          relationId,
        });
        ElMessage.success('逻辑外键已更新');
      } else {
        await createLogicalRelation(relationForm.sourceTableName, payload);
        ElMessage.success('逻辑外键已创建');
      }

      relationDialogVisible.value = false;
      resetRelationForm();
      await loadRelationData();
    } catch (error) {
      applyRelationFieldErrors(error);
    } finally {
      relationSubmitLoading.value = false;
    }
  }

  function clearRelationFieldErrors() {
    Object.keys(relationFieldErrors).forEach(key => delete relationFieldErrors[key]);
  }

  function applyRelationFieldErrors(error: unknown) {
    clearRelationFieldErrors();
    Object.assign(relationFieldErrors, getFieldErrorMap(error));
  }

  async function handleDeleteRelation(relation: LogicalTableRelationResponse) {
    if (relation.source === 'physical') {
      ElMessage.warning('物理外键仅展示，不支持删除');
      return;
    }
    if (typeof relation.id !== 'number' || typeof selectedDatasourceId.value !== 'number') {
      ElMessage.error('当前逻辑外键缺少有效标识，无法删除');
      return;
    }

    try {
      await ElMessageBox.confirm(
        `确定要删除逻辑外键 ${relation.sourceTableName} -> ${relation.targetTableName} 吗？`,
        '提示',
        {
          type: 'warning',
          confirmButtonText: '确定',
          cancelButtonText: '取消',
        },
      );

      await deleteLogicalRelation(
        selectedDatasourceId.value,
        relation.sourceTableName,
        relation.id,
      );
      ElMessage.success('逻辑外键已删除');
      await loadRelationData();
    } catch {
      // ignore cancel
    }
  }

  async function handleToggleRelationEnabled(
    relation: LogicalTableRelationResponse,
    value: boolean,
  ) {
    if (relation.source === 'physical') {
      ElMessage.warning('物理外键始终由数据库结构决定，不能在这里启停');
      return;
    }
    if (typeof relation.id !== 'number' || typeof selectedDatasourceId.value !== 'number') {
      ElMessage.error('当前逻辑外键缺少有效标识，无法更新状态');
      return;
    }

    await updateLogicalRelationEnabled(relation.sourceTableName, {
      datasourceId: selectedDatasourceId.value,
      relationId: relation.id,
      enabled: value,
    });
    ElMessage.success(value ? '逻辑外键已启用' : '逻辑外键已禁用');
    await loadRelationData();
  }

  function handleDialogClose() {
    resetRelationForm();
  }

  onMounted(() => {
    void initializeDatasource();
  });

  watch(selectedDatasourceId, async value => {
    if (typeof value !== 'number') {
      return;
    }
    if (suppressDatasourceWatch.value) {
      return;
    }
    workspacePage.page = 1;
    persistManageStateSnapshot();
    resetRelationForm();
    await loadRelationData();
  });

  watch(relationForm, clearRelationFieldErrors, { deep: true });
</script>

<template>
  <div class="relation-manage-page">
    <section class="hero-card">
      <div>
        <h2 class="hero-title">逻辑外键管理</h2>
        <p class="hero-desc">维护表间关系，支持在 ER 图中直接查看和拖拽创建逻辑外键。</p>
      </div>
      <div class="hero-meta">
        <span>当前数据源</span>
        <strong>{{ activeDatasource?.name ?? '未选择' }}</strong>
      </div>
    </section>

    <section class="toolbar-card">
      <div class="toolbar-grid">
        <el-select
          v-model="selectedDatasourceId"
          class="toolbar-field"
          filterable
          placeholder="选择数据源"
          :loading="datasourceLoading"
        >
          <el-option
            v-for="item in datasourceList"
            :key="item.id"
            :label="`${item.name} (${item.type})`"
            :value="item.id"
          />
        </el-select>
        <div class="toolbar-actions">
          <el-button
            type="primary"
            :loading="relationLoading || relationNodeLoading"
            @click="loadRelationData"
          >
            刷新关系
          </el-button>
        </div>
      </div>
      <div v-if="datasourceError" class="error-tip">
        数据源加载失败：{{ datasourceError.message }}
      </div>
    </section>

    <section class="content-card">
      <RelationWorkspace
        :loading="relationLoading"
        :node-loading="relationNodeLoading"
        :relation-error="relationError"
        :datasource-id="selectedDatasourceId"
        :nodes="relationNodes"
        :relations="relationRecords"
        :draft-relation="draftRelation"
        @refresh="loadRelationData"
        @edit-relation="handleEditRelation"
        @delete-relation="handleDeleteRelation"
        @toggle-relation-enabled="handleToggleRelationEnabled"
        @drag-create-relation="handleDragCreateRelation"
      />
      <div class="relation-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="workspacePage.page"
          :page-size="workspacePage.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="workspacePage.total"
          @current-change="handleWorkspacePageChange"
          @size-change="handleWorkspaceSizeChange"
        />
      </div>
    </section>

    <RelationEditDialog
      v-model:visible="relationDialogVisible"
      :loading="relationSubmitLoading"
      :relation="selectedRelation"
      :form="relationForm"
      :field-errors="relationFieldErrors"
      :nodes="relationNodes"
      :source-columns="relationSourceColumns"
      :target-columns="relationTargetColumns"
      @update:form="value => Object.assign(relationForm, value)"
      @source-table-change="handleSourceTableChange"
      @target-table-change="handleTargetTableChange"
      @submit="handleSubmitRelation"
      @close="handleDialogClose"
    />
  </div>
</template>

<style scoped>
  .relation-manage-page {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .hero-card,
  .toolbar-card,
  .content-card {
    background: var(--app-bg-card);
    border: 1px solid var(--app-border);
    border-radius: 8px;
    transition:
      background-color 0.2s,
      border-color 0.2s;
  }

  .hero-card {
    display: flex;
    justify-content: space-between;
    gap: 24px;
    padding: 24px 32px;
    background: var(--app-gradient-hero);
  }

  .hero-title {
    margin: 0 0 8px;
    color: var(--app-text-primary);
    font-size: 20px;
    font-weight: 700;
  }

  .hero-desc {
    max-width: 680px;
    margin: 0;
    color: var(--app-text-secondary);
    line-height: 1.7;
  }

  .hero-meta {
    min-width: 180px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    padding: 16px 20px;
    border-radius: 8px;
    background: var(--app-bg-page);
    border: 1px solid var(--app-border);
    color: var(--app-text-secondary);
    transition:
      background-color 0.2s,
      border-color 0.2s;
  }

  .hero-meta strong {
    margin-top: 6px;
    color: var(--app-text-primary);
    font-size: 16px;
  }

  .toolbar-card,
  .content-card {
    padding: 24px;
  }

  .toolbar-grid {
    display: grid;
    grid-template-columns: minmax(280px, 420px) auto;
    gap: 16px;
    align-items: center;
  }

  .toolbar-field {
    width: 100%;
  }

  .toolbar-actions {
    display: flex;
    justify-content: flex-end;
  }

  .relation-pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 18px;
  }

  .error-tip {
    margin-top: 14px;
    color: var(--app-accent);
  }

  @media (max-width: 1024px) {
    .hero-card {
      flex-direction: column;
    }

    .toolbar-grid {
      grid-template-columns: 1fr;
    }

    .toolbar-actions {
      justify-content: flex-start;
    }
  }
</style>
