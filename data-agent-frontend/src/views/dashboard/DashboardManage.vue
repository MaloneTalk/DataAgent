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
  import { onMounted, ref } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import {
    deleteDashboardCard,
    getDashboardCards,
    refreshDashboardCards,
    type DashboardCardRefreshResponse,
    type DashboardCardResponse,
    type QueryResult,
  } from '@/api/dashboard';
  import DashboardCardChart from './DashboardCardChart.vue';

  const cards = ref<DashboardCardResponse[]>([]);
  const loading = ref(false);
  const results = ref<Record<number, QueryResult>>({});
  const errors = ref<Record<number, string>>({});
  const dataViews = ref<Record<number, boolean>>({});

  function applyRefreshResults(data: Record<number, DashboardCardRefreshResponse>) {
    const nextResults = { ...results.value };
    const nextErrors = { ...errors.value };
    for (const [id, item] of Object.entries(data)) {
      if (item.result) {
        nextResults[Number(id)] = item.result;
        delete nextErrors[Number(id)];
      } else {
        delete nextResults[Number(id)];
        nextErrors[Number(id)] = item.errorMessage ?? '刷新失败';
      }
    }
    results.value = nextResults;
    errors.value = nextErrors;
  }

  async function refreshCardResults(ids: number[]) {
    if (ids.length === 0) return;
    const res = await refreshDashboardCards(ids);
    applyRefreshResults(res.data.data ?? {});
  }

  async function loadDashboard() {
    loading.value = true;
    try {
      const cardRes = await getDashboardCards();
      cards.value = cardRes.data.data ?? [];
      results.value = {};
      errors.value = {};
      await refreshCardResults(cards.value.map(card => card.id));
    } finally {
      loading.value = false;
    }
  }

  async function removeCard(card: DashboardCardResponse) {
    const confirmed = await ElMessageBox.confirm(`确定删除「${card.title}」吗？`, '提示', {
      type: 'warning',
    })
      .then(() => true)
      .catch(() => false);
    if (!confirmed) return;
    await deleteDashboardCard(card.id);
    ElMessage.success('删除成功');
    await loadDashboard();
  }

  function isDataView(cardId: number) {
    return dataViews.value[cardId] ?? false;
  }

  function toggleChartView(cardId: number, value: boolean | string | number) {
    dataViews.value = { ...dataViews.value, [cardId]: !value };
  }

  onMounted(loadDashboard);
</script>

<template>
  <main v-loading="loading" class="dashboard-page">
    <div class="page-header">
      <h2>看板管理</h2>
    </div>

    <el-empty
      v-if="!loading && cards.length === 0"
      description="暂无卡片，从聊天结果保存一个试试"
    />

    <div v-else class="card-grid">
      <section v-for="card in cards" :key="card.id" class="dashboard-card">
        <div class="card-header">
          <div>
            <h3>{{ card.title }}</h3>
            <p>数据源 #{{ card.datasourceId }} · {{ card.chartType }}</p>
          </div>
          <div class="card-actions">
            <el-switch
              v-if="card.chartType !== 'table'"
              :model-value="!isDataView(card.id)"
              active-text="图表"
              inactive-text="数据"
              inline-prompt
              @change="toggleChartView(card.id, $event)"
            />
            <el-button link type="primary" @click="refreshCardResults([card.id])">刷新</el-button>
            <el-button link type="danger" @click="removeCard(card)">删除</el-button>
          </div>
        </div>
        <DashboardCardChart
          :card="card"
          :result="results[card.id]"
          :error="errors[card.id]"
          :force-table="isDataView(card.id)"
        />
        <details class="sql-detail">
          <summary>SQL</summary>
          <pre>{{ card.sqlText }}</pre>
        </details>
      </section>
    </div>
  </main>
</template>

<style scoped>
  .dashboard-page {
    height: 100%;
    overflow: auto;
  }

  .page-header,
  .card-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .page-header {
    margin-bottom: 16px;
  }

  h2,
  h3,
  p {
    margin: 0;
  }

  h2 {
    font-size: 20px;
    color: var(--app-text-primary);
  }

  h3 {
    font-size: 15px;
    color: var(--app-text-primary);
  }

  p {
    margin-top: 4px;
    font-size: 12px;
    color: var(--app-text-muted);
  }

  .card-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
    gap: 16px;
  }

  .dashboard-card {
    border: 1px solid var(--app-border);
    border-radius: 8px;
    background: var(--app-bg-card);
    padding: 16px;
    min-width: 0;
  }

  .card-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    white-space: nowrap;
  }

  .sql-detail {
    margin-top: 12px;
    color: var(--app-text-muted);
    font-size: 12px;
  }

  .sql-detail pre {
    white-space: pre-wrap;
    word-break: break-word;
    background: var(--app-bg-page);
    border: 1px solid var(--app-border);
    border-radius: 6px;
    padding: 10px;
  }
</style>
