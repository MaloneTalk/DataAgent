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
  import {
    createLogicalRelation,
    deleteLogicalRelation,
    getLogicalRelationPage,
    getRelationCandidateColumnPage,
    getRelationCandidateTablePage,
    updateLogicalRelation,
    updateLogicalRelationEnabled,
    type BindLogicalTableRelationRequest,
    type ColumnSemanticResponse,
    type LogicalTableRelationResponse,
    type RelationCandidateColumnResponse,
    type RelationCandidateTableResponse,
    type TableSemanticResponse,
    type UpdateLogicalTableRelationRequest,
  } from '@/api/semantic';
  import RelationEditDialog from './components/RelationEditDialog.vue';
  import RelationWorkspace from './components/RelationWorkspace.vue';
  import type {
    RelationDragCreatePayload,
    RelationForm,
    TableNodeLayout,
  } from './types';

  const BULK_FETCH_PAGE_SIZE = 100;
  const NODE_WIDTH = 280;
  const HEADER_HEIGHT = 58;
  const COLUMN_HEIGHT = 32;
  const GAP_X = 72;
  const GAP_Y = 40;
  const COLUMNS_PER_ROW = 3;

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
  const relationSourceColumns = ref<RelationCandidateColumnResponse[]>([]);
  const relationTargetColumns = ref<RelationCandidateColumnResponse[]>([]);
  const suppressRelationTableWatch = ref(false);

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

  async function fetchAllPages<T>(
    loader: (
      page: number,
      pageSize: number,
    ) => Promise<{
      data: {
        data: {
          items: T[];
          totalPages: number;
        };
      };
    }>,
  ) {
    const items: T[] = [];
    let page = 1;
    let totalPages = 1;

    while (page <= totalPages) {
      const response = await loader(page, BULK_FETCH_PAGE_SIZE);
      const pageData = response.data.data;
      items.push(...pageData.items);
      totalPages = Math.max(pageData.totalPages, 1);
      page += 1;
    }

    return items;
  }

  function buildRelationLayouts(
    tables: RelationCandidateTableResponse[],
    columnsMap: Map<string, RelationCandidateColumnResponse[]>,
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

  async function fetchRelationColumns(tableName: string) {
    if (typeof selectedDatasourceId.value !== 'number') {
      return [];
    }
    const items = await fetchAllPages<ColumnSemanticResponse>((page, pageSize) =>
      getRelationCandidateColumnPage({
        datasourceId: selectedDatasourceId.value as number,
        tableName,
        page,
        pageSize,
        sortOrder: 'asc',
      }),
    );

    return items.map(
      (item): RelationCandidateColumnResponse => ({
        columnName: item.columnName,
        description: item.columnDescription ?? item.physicalColumnDescription,
        typeName: item.typeName,
        primaryKey: item.primaryKey,
      }),
    );
  }

  async function loadRelationNodes(
    datasourceId: number,
    loadToken: number,
  ): Promise<TableNodeLayout[]> {
    relationNodeLoading.value = true;
    try {
      const tableItems = await fetchAllPages<TableSemanticResponse>((page, pageSize) =>
        getRelationCandidateTablePage({
          datasourceId,
          page,
          pageSize,
          sortOrder: 'asc',
        }),
      );
      const tables = tableItems.map(
        (item): RelationCandidateTableResponse => ({
          tableName: item.tableName,
          domain: item.domain,
          description: item.tableDescription ?? item.physicalTableDescription,
        }),
      );

      const columnResponses = await Promise.all(
        tables.map(table => fetchRelationColumns(table.tableName)),
      );

      const columnsMap = new Map<string, RelationCandidateColumnResponse[]>();
      tables.forEach((table, index) => {
        columnsMap.set(table.tableName, columnResponses[index]);
      });

      const nextNodes = buildRelationLayouts(tables, columnsMap);
      if (loadToken !== relationLoadToken.value || datasourceId !== selectedDatasourceId.value) {
        return nextNodes;
      }

      relationNodes.value = nextNodes;
      return nextNodes;
    } finally {
      if (loadToken === relationLoadToken.value) {
        relationNodeLoading.value = false;
      }
    }
  }

  async function loadAllRelations(datasourceId: number, nodes: TableNodeLayout[], loadToken: number) {
    relationLoading.value = true;
    relationError.value = '';

    try {
      const responses = await Promise.all(
        nodes.map(node =>
          fetchAllPages<LogicalTableRelationResponse>((page, pageSize) =>
            getLogicalRelationPage({
              datasourceId,
              tableName: node.tableName,
              page,
              pageSize,
              sortOrder: 'desc',
            }),
          ),
        ),
      );

      if (loadToken !== relationLoadToken.value || datasourceId !== selectedDatasourceId.value) {
        return;
      }

      relationRecords.value = responses.flatMap(items => items);
    } catch (error) {
      if (loadToken !== relationLoadToken.value || datasourceId !== selectedDatasourceId.value) {
        return;
      }
      relationError.value = (error as Error).message;
      relationRecords.value = [];
    } finally {
      if (loadToken === relationLoadToken.value) {
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
      const nodes = await loadRelationNodes(datasourceId, loadToken);
      if (loadToken !== relationLoadToken.value || datasourceId !== selectedDatasourceId.value) {
        return;
      }
      await loadAllRelations(datasourceId, nodes, loadToken);
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

  function resetRelationForm() {
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
    if (typeof selectedDatasourceId.value !== 'number') {
      const firstActive = datasourceList.value.find(item => item.status === 'ACTIVE');
      selectedDatasourceId.value = firstActive?.id ?? datasourceList.value[0]?.id;
    }
    await loadRelationData();
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
    } finally {
      relationSubmitLoading.value = false;
    }
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

      await deleteLogicalRelation(selectedDatasourceId.value, relation.sourceTableName, relation.id);
      ElMessage.success('逻辑外键已删除');
      await loadRelationData();
    } catch {
      // ignore cancel
    }
  }

  async function handleToggleRelationEnabled(relation: LogicalTableRelationResponse, value: boolean) {
    if (relation.source === 'physical') {
      ElMessage.warning('物理外键始终由数据库结构决定，不能在这里启停');
      return;
    }
    if (typeof relation.id !== 'number' || typeof selectedDatasourceId.value !== 'number') {
      ElMessage.error('当前逻辑外键缺少有效标识，无法更新状态');
      return;
    }

    await updateLogicalRelationEnabled(
      relation.sourceTableName,
      {
        datasourceId: selectedDatasourceId.value,
        relationId: relation.id,
        enabled: value,
      },
    );
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
    resetRelationForm();
    await loadRelationData();
  });
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
          <el-button type="primary" :loading="relationLoading || relationNodeLoading" @click="loadRelationData">
            刷新关系
          </el-button>
        </div>
      </div>
      <div v-if="datasourceError" class="error-tip">数据源加载失败：{{ datasourceError.message }}</div>
    </section>

    <section class="content-card">
      <RelationWorkspace
        :loading="relationLoading"
        :node-loading="relationNodeLoading"
        :relation-error="relationError"
        :nodes="relationNodes"
        :relations="relationRecords"
        :draft-relation="draftRelation"
        @refresh="loadRelationData"
        @edit-relation="handleEditRelation"
        @delete-relation="handleDeleteRelation"
        @toggle-relation-enabled="handleToggleRelationEnabled"
        @drag-create-relation="handleDragCreateRelation"
      />
    </section>

    <RelationEditDialog
      v-model:visible="relationDialogVisible"
      :loading="relationSubmitLoading"
      :relation="selectedRelation"
      :form="relationForm"
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
    background: #ffffff;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  }

  .hero-card {
    display: flex;
    justify-content: space-between;
    gap: 24px;
    padding: 24px 32px;
    background: linear-gradient(145deg, #ffffff 0%, #f9fafb 100%);
  }

  .hero-title {
    margin: 0 0 8px;
    color: #1f2937;
    font-size: 22px;
  }

  .hero-desc {
    max-width: 680px;
    margin: 0;
    color: #6b7280;
    line-height: 1.7;
  }

  .hero-meta {
    min-width: 180px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    padding: 16px 20px;
    border-radius: 10px;
    background: #f9fafb;
    border: 1px solid #e5e7eb;
    color: #6b7280;
  }

  .hero-meta strong {
    margin-top: 6px;
    color: #1f2937;
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

  .error-tip {
    margin-top: 14px;
    color: #dc2626;
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
