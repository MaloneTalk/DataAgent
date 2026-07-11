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
  import { ref } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { getReportsBySessionId, deleteReport, type ReportResponse } from '@/api/report';
  import { buildExportHtml, downloadHtml } from '@/utils/exportHtml';
  import ReportPreviewDialog from '@/views/report/ReportPreviewDialog.vue';

  const sessionId = ref('');
  const reports = ref<ReportResponse[]>([]);
  const loading = ref(false);

  const previewVisible = ref(false);
  const previewTitle = ref('');
  const previewContent = ref('');
  const previewDialogRef = ref<InstanceType<typeof ReportPreviewDialog>>();

  async function handleSearch() {
    const sid = sessionId.value.trim();
    if (!sid) {
      ElMessage.warning('请输入 Session ID');
      return;
    }
    loading.value = true;
    try {
      const res = await getReportsBySessionId(sid);
      const data = res.data.data;
      reports.value = Array.isArray(data) ? data : [data];
    } catch {
      reports.value = [];
    } finally {
      loading.value = false;
    }
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
      reports.value = reports.value.filter(r => r.id !== row.id);
    } catch {
      // 用户取消或错误已由拦截器处理
    }
  }

  function handleExport(row: ReportResponse) {
    import('marked').then(async ({ marked }) => {
      marked.use({ gfm: true, breaks: true });

      let chartImages = new Map<string, string>();
      let renderedHtml: string;

      if (previewDialogRef.value && previewVisible.value && previewTitle.value === row.title) {
        chartImages = previewDialogRef.value.getChartImages();
        renderedHtml = previewDialogRef.value.getRenderedHtml();
      } else {
        const renderer = new marked.Renderer();
        const ids: string[] = [];
        renderer.code = function (code: string, language: string | undefined) {
          if (language === 'echarts' || language === 'json') {
            const id = 'chart_' + Math.random().toString(36).substr(2, 9);
            ids.push(id);
            return `<div id="${id}" class="chart-box" data-option="${encodeURIComponent(code)}"></div>`;
          }
          return `<pre><code class="language-${language || ''}">${code}</code></pre>`;
        };
        renderedHtml = marked.parse(row.content, { renderer }) as string;
      }

      const html = buildExportHtml(row.title, renderedHtml, chartImages);
      downloadHtml(`${row.title || 'report'}.html`, html);
      ElMessage.success('导出成功');
    });
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
</script>

<template>
  <div class="report-list">
    <div class="page-header">
      <h2 class="page-title">报告管理</h2>
    </div>

    <div class="search-bar">
      <el-input
        v-model="sessionId"
        placeholder="输入 Session ID 查询报告"
        clearable
        style="width: 360px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
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

    <div v-if="!loading && reports.length === 0 && sessionId" class="empty-tip">
      暂无报告数据
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
</style>
