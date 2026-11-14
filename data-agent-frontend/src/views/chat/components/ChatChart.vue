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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 -->

<script setup lang="ts">
  import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
  import { init, use, type ECharts } from 'echarts/core';
  import { BarChart, LineChart, PieChart, ScatterChart } from 'echarts/charts';
  import {
    AriaComponent,
    GridComponent,
    LegendComponent,
    TooltipComponent,
  } from 'echarts/components';
  import { CanvasRenderer } from 'echarts/renderers';
  import type { ChartPayload } from '@/api/agent';
  import { buildChartOption } from '@/utils/chartOption';

  use([
    BarChart,
    LineChart,
    PieChart,
    ScatterChart,
    AriaComponent,
    GridComponent,
    LegendComponent,
    TooltipComponent,
    CanvasRenderer,
  ]);

  const props = defineProps<{ chart: ChartPayload }>();
  const container = ref<HTMLElement | null>(null);
  const error = ref('');
  let instance: ECharts | undefined;
  let observer: InstanceType<typeof window.ResizeObserver> | undefined;

  function render() {
    if (!instance) return;
    try {
      error.value = '';
      instance.setOption(buildChartOption(props.chart), { notMerge: true });
    } catch {
      error.value = '图表渲染失败，请调整图表类型或字段后重试。';
    }
  }

  onMounted(() => {
    if (!container.value) return;
    instance = init(container.value);
    observer = new window.ResizeObserver(() => instance?.resize());
    observer.observe(container.value);
    render();
  });
  watch(() => props.chart, render);
  onBeforeUnmount(() => {
    observer?.disconnect();
    instance?.dispose();
  });
</script>

<template>
  <div class="chat-chart">
    <h4>{{ chart.spec.title }}</h4>
    <p v-if="error" class="chat-chart__error" role="alert">{{ error }}</p>
    <div ref="container" class="chat-chart__canvas" role="img" :aria-label="chart.spec.title"></div>
  </div>
</template>

<style scoped>
  .chat-chart {
    min-width: 0;
    margin: 12px 0;
    padding: 12px;
    border: 1px solid var(--app-border);
    border-radius: 8px;
  }

  .chat-chart h4 {
    margin: 0 0 12px;
    font-size: 15px;
  }

  .chat-chart__canvas {
    width: 100%;
    height: 360px;
  }

  .chat-chart__error {
    color: var(--el-color-danger);
  }
</style>
