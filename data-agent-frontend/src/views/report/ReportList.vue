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
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { getReports, deleteReport, type ReportResponse } from '@/api/report';
  import { buildReportHtml, downloadHtml } from '@/utils/reportTemplate';
  import ReportPreviewDialog from '@/views/report/ReportPreviewDialog.vue';

  const props = defineProps<{
    fixedSessionId?: string;
    embedded?: boolean;
  }>();

  const sessionId = ref('');
  const keyword = ref('');
  const reports = ref<ReportResponse[]>([]);
  const loading = ref(false);
  const page = ref(1);
  const pageSize = ref(10);
  const total = ref(0);
  const sortOrder = ref<'asc' | 'desc'>('desc');

  const previewVisible = ref(false);
  const previewTitle = ref('');
  const previewContent = ref('');
  const previewDialogRef = ref<InstanceType<typeof ReportPreviewDialog>>();

  async function fetchReports() {
    loading.value = true;
    try {
      const res = await getReports({
        sessionId: props.fixedSessionId || sessionId.value.trim() || undefined,
        keyword: keyword.value.trim() || undefined,
        page: page.value,
        pageSize: pageSize.value,
        sortOrder: sortOrder.value,
      });
      const pageData = res.data.data;
      reports.value = pageData.items;
      total.value = pageData.total;
    } catch {
      reports.value = [];
      total.value = 0;
    } finally {
      loading.value = false;
    }
  }

  function handleSearch() {
    page.value = 1;
    fetchReports();
  }

  function handleReset() {
    if (!props.fixedSessionId) {
      sessionId.value = '';
    }
    keyword.value = '';
    sortOrder.value = 'desc';
    page.value = 1;
    fetchReports();
  }

  function handlePageChange(newPage: number) {
    page.value = newPage;
    fetchReports();
  }

  function handleSizeChange(newSize: number) {
    pageSize.value = newSize;
    page.value = 1;
    fetchReports();
  }

  function handlePreview(row: ReportResponse) {
    previewTitle.value = row.title;
    previewContent.value = row.content;
    previewVisible.value = true;
  }

  async function handleDelete(row: ReportResponse) {
    try {
      await ElMessageBox.confirm(`确定要删除报告「${row.title}」吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      });
      await deleteReport(row.id);
      ElMessage.success('删除成功');
      fetchReports();
    } catch {
      // 用户取消或错误已由拦截器处理
    }
  }

  function handleExport(row: ReportResponse) {
    const html = buildReportHtml(row.title, row.content);
    downloadHtml(`${row.title || 'report'}.html`, html);
    ElMessage.success('导出成功');
  }

  function formatTime(time: string) {
    if (!time) return '-';
    try {
      const d = new Date(time);
      if (isNaN(d.getTime())) return time;
      return d.toLocaleString('zh-CN');
    } catch {
      return time;
    }
  }

  onMounted(() => {
    fetchReports();
  });
</script>

<template>
  <div class="report-list">
    <div v-if="!embedded" class="page-header">
      <h2 class="page-title">报告管理</h2>
    </div>

    <div class="search-bar">
      <el-input
        v-if="!fixedSessionId"
        v-model="sessionId"
        placeholder="Session ID"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
      />
      <el-input
        v-model="keyword"
        placeholder="关键字"
        clearable
        style="width: 200px"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="sortOrder" style="width: 100px" @change="handleSearch">
        <el-option label="降序" value="desc" />
        <el-option label="升序" value="asc" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="reports" style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="sessionId" label="Session ID" width="260" show-overflow-tooltip />
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handlePreview(row)">预览</el-button>
          <el-button link type="success" @click="handleExport(row)">导出</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && reports.length === 0" class="empty-tip">暂无报告数据</div>

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

    <ReportPreviewDialog
      ref="previewDialogRef"
      v-model:visible="previewVisible"
      :title="previewTitle"
      :content="previewContent"
    />
  </div>
</template>

<style scoped>
  .report-list {
    padding: 0;
  }

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
  }

  .page-title {
    font-size: 20px;
    font-weight: 600;
    color: var(--app-text-primary);
    margin: 0;
  }

  .search-bar {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;
  }

  .empty-tip {
    text-align: center;
    padding: 40px 0;
    color: var(--app-text-muted);
    font-size: 14px;
  }

  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
</style>
