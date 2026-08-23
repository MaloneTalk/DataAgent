<!--
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
 -->

<script setup lang="ts">
  import { ref, onMounted } from 'vue';
  import { ElMessage } from 'element-plus';
  import {
    deleteTableExport,
    downloadTableExport,
    getTableExports,
    type TableExportResponse,
  } from '@/api/tableExport';
  import { formatDateTime } from '@/utils/dateTime';

  const sessionId = ref('');
  const exports = ref<TableExportResponse[]>([]);
  const loading = ref(false);
  const page = ref(1);
  const pageSize = ref(10);
  const total = ref(0);

  async function fetchExports() {
    loading.value = true;
    try {
      const res = await getTableExports({
        sessionId: sessionId.value.trim() || undefined,
        page: page.value,
        pageSize: pageSize.value,
      });
      const pageData = res.data.data;
      exports.value = pageData.items;
      total.value = pageData.total;
    } catch {
      exports.value = [];
      total.value = 0;
    } finally {
      loading.value = false;
    }
  }

  function handleSearch() {
    page.value = 1;
    fetchExports();
  }

  function handleReset() {
    sessionId.value = '';
    page.value = 1;
    fetchExports();
  }

  function handlePageChange(newPage: number) {
    page.value = newPage;
    fetchExports();
  }

  function handleSizeChange(newSize: number) {
    pageSize.value = newSize;
    page.value = 1;
    fetchExports();
  }

  async function handleDownload(row: TableExportResponse) {
    try {
      await downloadTableExport(row.id);
    } catch (e) {
      ElMessage.error((e as Error).message || '下载失败');
    }
  }

  async function handleDelete(row: TableExportResponse) {
    try {
      await deleteTableExport(row.id);
      ElMessage.success('删除成功');
      fetchExports();
    } catch (e) {
      ElMessage.error((e as Error).message || '删除失败');
    }
  }

  onMounted(() => {
    fetchExports();
  });
</script>

<template>
  <div class="table-export-list">
    <div class="page-header">
      <h2 class="page-title">表格导出</h2>
    </div>

    <div class="search-bar">
      <el-input
        v-model="sessionId"
        placeholder="Session ID"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="exports" style="width: 100%">
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column label="行数" width="130">
        <template #default="{ row }">{{ row.rowCount }}</template>
      </el-table-column>
      <el-table-column prop="sessionId" label="Session ID" width="260" show-overflow-tooltip />
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" @click="handleDownload(row)">下载</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && exports.length === 0" class="empty-tip">暂无导出数据</div>

    <div v-if="total > 0" class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>
