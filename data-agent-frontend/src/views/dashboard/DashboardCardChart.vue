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
  import type { DashboardCardResponse, QueryResult } from '@/api/dashboard';
  import { computed } from 'vue';

  const props = defineProps<{
    card: DashboardCardResponse;
    result?: QueryResult;
  }>();

  const metricValue = computed(() => {
    const field = props.result?.columns[0];
    return field ? props.result?.rows[0]?.[field] : '-';
  });

  const tableRows = computed(() => props.result?.rows.slice(0, 20) ?? []);
  const tableColumns = computed(() => props.result?.columns ?? []);

  const bars = computed(() => {
    const xField = props.result?.columns[0];
    const yField = props.result?.columns[1];
    if (!props.result || !xField || !yField) return [];
    const values = props.result.rows.map(row => Number(row[yField] ?? 0));
    const max = Math.max(...values.map(value => Math.abs(value)), 1);
    return props.result.rows.map((row, index) => ({
      label: String(row[xField] ?? ''),
      value: values[index],
      percent: Math.round((Math.abs(values[index]) / max) * 100),
    }));
  });
</script>

<template>
  <div v-if="!result" class="chart-empty">暂无数据</div>
  <div v-else-if="card.chartType === 'metric'" class="metric-value">{{ metricValue }}</div>
  <el-table v-else-if="card.chartType === 'table'" :data="tableRows" size="small" max-height="260">
    <el-table-column
      v-for="column in tableColumns"
      :key="column"
      :prop="column"
      :label="column"
      min-width="120"
      show-overflow-tooltip
    />
  </el-table>
  <div v-else class="bar-chart">
    <div v-for="point in bars" :key="point.label" class="bar-item">
      <div class="bar-track">
        <div class="bar-fill" :style="{ height: `${point.percent}%` }"></div>
      </div>
      <div class="bar-label" :title="point.label">{{ point.label }}</div>
      <div class="bar-value">{{ point.value }}</div>
    </div>
  </div>
</template>

<style scoped>
  .metric-value {
    min-height: 180px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 34px;
    font-weight: 700;
    color: var(--app-text-primary);
  }

  .chart-empty {
    min-height: 180px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--app-text-muted);
  }

  .bar-chart {
    height: 260px;
    display: flex;
    align-items: flex-end;
    gap: 10px;
    overflow-x: auto;
    padding-top: 16px;
  }

  .bar-item {
    width: 56px;
    flex: 0 0 56px;
    text-align: center;
    color: var(--app-text-secondary);
    font-size: 12px;
  }

  .bar-track {
    height: 180px;
    display: flex;
    align-items: flex-end;
    justify-content: center;
    background: var(--app-bg-page);
    border: 1px solid var(--app-border);
    border-radius: 6px;
    overflow: hidden;
  }

  .bar-fill {
    width: 100%;
    min-height: 2px;
    background: var(--app-accent);
  }

  .bar-label {
    margin-top: 8px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .bar-value {
    color: var(--app-text-muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
</style>
