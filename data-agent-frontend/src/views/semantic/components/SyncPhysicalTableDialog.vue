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
  import { computed, nextTick, reactive, ref, watch } from 'vue';
  import { ElMessage } from 'element-plus';
  import {
    getPhysicalTableCandidatePage,
    syncTableSemantics,
    type PhysicalTableCandidateResponse,
  } from '@/api/semantic';
  import { buildSyncSummary } from '../utils';

  const props = defineProps<{
    modelValue: boolean;
    datasourceId: number | null;
  }>();

  const emit = defineEmits<{
    (e: 'update:modelValue', value: boolean): void;
    (e: 'synced', selectedTableNames: string[]): void;
  }>();

  const visible = computed({
    get: () => props.modelValue,
    set: value => emit('update:modelValue', value),
  });

  const loading = ref(false);
  const submitting = ref(false);
  const keyword = ref('');
  const rows = ref<PhysicalTableCandidateResponse[]>([]);
  const selectedTableNameSet = ref<Set<string>>(new Set());
  const page = reactive({
    page: 1,
    pageSize: 10,
    total: 0,
  });
  const syncTableRef = ref();

  const resetState = () => {
    keyword.value = '';
    rows.value = [];
    selectedTableNameSet.value = new Set();
    page.page = 1;
    page.pageSize = 10;
    page.total = 0;
  };

  const syncCurrentPageSelection = async () => {
    await nextTick();
    syncTableRef.value?.clearSelection?.();
    rows.value.forEach(row => {
      syncTableRef.value?.toggleRowSelection(
        row,
        selectedTableNameSet.value.has(row.tableName.trim().toLowerCase()),
      );
    });
  };

  const loadCandidates = async () => {
    if (props.datasourceId === null) {
      rows.value = [];
      page.total = 0;
      return;
    }
    loading.value = true;
    try {
      const response = await getPhysicalTableCandidatePage({
        datasourceId: props.datasourceId,
        page: page.page,
        pageSize: page.pageSize,
        keyword: keyword.value.trim() || undefined,
        sortOrder: 'asc',
      });
      const pageData = response.data.data;
      rows.value = pageData.items;
      page.total = pageData.total;
      await syncCurrentPageSelection();
    } catch (err) {
      rows.value = [];
      page.total = 0;
      ElMessage.error((err as Error).message);
    } finally {
      loading.value = false;
    }
  };

  const handleSelectionChange = (selection: PhysicalTableCandidateResponse[]) => {
    const currentPageNames = rows.value.map(item => item.tableName.trim().toLowerCase());
    currentPageNames.forEach(name => selectedTableNameSet.value.delete(name));
    selection.forEach(item => selectedTableNameSet.value.add(item.tableName.trim().toLowerCase()));
  };

  const handlePageChange = async (currentPage: number) => {
    page.page = currentPage;
    await loadCandidates();
  };

  const handleSizeChange = async (pageSize: number) => {
    page.pageSize = pageSize;
    page.page = 1;
    await loadCandidates();
  };

  const handleConfirm = async () => {
    if (selectedTableNameSet.value.size === 0) {
      ElMessage.warning('请至少选择一张物理表');
      return;
    }
    if (props.datasourceId === null) {
      return;
    }
    submitting.value = true;
    try {
      const selectedTableNames = Array.from(selectedTableNameSet.value);
      const response = await syncTableSemantics(props.datasourceId, selectedTableNames);
      ElMessage.success(buildSyncSummary(response.data.data));
      emit('synced', selectedTableNames);
      visible.value = false;
    } catch (err) {
      ElMessage.error((err as Error).message);
    } finally {
      submitting.value = false;
    }
  };

  watch(
    () => props.modelValue,
    async currentVisible => {
      if (!currentVisible) {
        return;
      }
      resetState();
      await loadCandidates();
    },
  );
</script>

<template>
  <el-dialog v-model="visible" title="同步物理表" width="860px" :close-on-click-modal="false">
    <div class="sync-toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="按表名搜索"
        @keyup.enter="loadCandidates"
      />
      <el-button @click="loadCandidates">查询</el-button>
    </div>

    <el-table
      ref="syncTableRef"
      v-loading="loading"
      :data="rows"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="48" />
      <el-table-column prop="tableName" label="物理表名" min-width="220" />
      <el-table-column label="语义层" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.synced ? 'info' : 'success'" size="small">
            {{ row.synced ? '已存在' : '待新增' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap sync-pagination">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :current-page="page.page"
        :page-size="page.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="page.total"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleConfirm">开始同步</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
  .sync-toolbar {
    display: grid;
    grid-template-columns: minmax(240px, 1fr) auto;
    gap: 12px;
    margin-bottom: 16px;
  }

  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }

  .sync-pagination {
    margin-top: 16px;
  }
</style>
