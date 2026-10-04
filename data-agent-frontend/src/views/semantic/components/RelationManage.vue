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
  import { onMounted, ref, watch } from 'vue';
  import HelpTip from '@/components/common/HelpTip.vue';
  import RelationEditDialog from './RelationEditDialog.vue';
  import RelationWorkspace from './RelationWorkspace.vue';
  import { useRelationWorkspaceData } from '../composables/useRelationWorkspaceData';
  import { useRelationForm } from '../composables/useRelationForm';

  const {
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
  } = useRelationWorkspaceData();

  const {
    selectedRelation,
    relationDialogVisible,
    relationSubmitLoading,
    relationForm,
    relationFieldErrors,
    relationSourceColumns,
    relationTargetColumns,
    draftRelation,
    resetRelationForm,
    handleSourceTableChange,
    handleTargetTableChange,
    handleDragCreateRelation,
    handleEditRelation,
    handleSubmitRelation,
    handleDeleteRelation,
    handleToggleRelationEnabled,
  } = useRelationForm({ selectedDatasourceId, relationNodes, loadRelationData });

  const relationWorkspaceRef = ref<InstanceType<typeof RelationWorkspace>>();

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

  function handleResetViewport() {
    relationWorkspaceRef.value?.resetViewport();
  }

  onMounted(() => {
    void initializeDatasource();
  });

  watch(selectedDatasourceId, async value => {
    if (typeof value !== 'number' || suppressDatasourceWatch.value) return;
    workspacePage.page = 1;
    persistManageStateSnapshot();
    resetRelationForm();
    await loadRelationData();
  });
</script>

<template>
  <div class="relation-manage-page">
    <div class="page-header">
      <h2 class="page-title page-title-with-help">
        逻辑外键
        <HelpTip>
          <strong>逻辑外键</strong>
          是语义层里的表关联说明。即使数据库没有真实外键，也可以告诉 AI 哪些字段能
          join，减少乱连表。
        </HelpTip>
      </h2>
    </div>

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
          <el-button @click="handleResetViewport">重置视图</el-button>
        </div>
      </div>
      <div v-if="datasourceError" class="semantic-error-tip">
        数据源加载失败：{{ datasourceError.message }}
      </div>
    </section>

    <section class="content-card">
      <RelationWorkspace
        ref="relationWorkspaceRef"
        :loading="relationLoading"
        :relation-error="relationError"
        :datasource-id="selectedDatasourceId"
        :nodes="relationNodes"
        :relations="relationRecords"
        :draft-relation="draftRelation"
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
      @close="resetRelationForm"
    />
  </div>
</template>

<style scoped>
  .relation-manage-page {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .toolbar-card,
  .content-card {
    background: var(--app-bg-card);
    border: 1px solid var(--app-border);
    border-radius: 8px;
    padding: 24px;
    transition:
      background-color 0.2s,
      border-color 0.2s;
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

  @media (max-width: 1024px) {
    .toolbar-grid {
      grid-template-columns: 1fr;
    }

    .toolbar-actions {
      justify-content: flex-start;
    }
  }
</style>
