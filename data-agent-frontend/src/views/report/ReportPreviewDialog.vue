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
  import { ref, watch, nextTick } from 'vue';
  import { marked } from 'marked';

  marked.use({ gfm: true, breaks: true });

  const props = defineProps<{
    visible: boolean;
    title: string;
    content: string;
  }>();

  const emit = defineEmits<{
    (e: 'update:visible', value: boolean): void;
  }>();

  const previewContainer = ref();
  const renderedHtml = ref('');
  const chartIds: string[] = [];

  const renderer = new marked.Renderer();
  renderer.code = function (code: string, language: string | undefined) {
    if (language === 'echarts' || language === 'json') {
      const id = 'chart_' + Math.random().toString(36).substr(2, 9);
      chartIds.push(id);
      return `<div id="${id}" class="chart-box" data-option="${encodeURIComponent(code)}"></div>`;
    }
    return `<pre><code class="language-${language || ''}">${code}</code></pre>`;
  };

  function renderContent() {
    chartIds.length = 0;
    if (!props.content) {
      renderedHtml.value = '';
      return;
    }
    renderedHtml.value = marked.parse(props.content, { renderer }) as string;
  }

  watch(
    () => props.visible,
    async newVal => {
      if (newVal) {
        renderContent();
        await nextTick();
        await nextTick();
        initCharts();
      }
    },
  );

  function initCharts() {
    const el = previewContainer.value;
    if (!el) return;
    const boxes = el.querySelectorAll('.chart-box');

    boxes.forEach(box => {
      const optionStr = box.getAttribute('data-option');
      if (!optionStr) return;

      import('echarts')
        .then(mod => {
          try {
            const option = new Function('return ' + decodeURIComponent(optionStr))();
            const chart = mod.init(box as never);
            chart.setOption(option);
            (box as unknown as Record<string, unknown>).__echart_instance = chart;
          } catch (e) {
            box.innerHTML =
              '<div class="chart-error"><b>图表渲染错误</b><br/>' +
              (e as Error).message +
              '</div>';
          }
        })
        .catch(() => {
          box.innerHTML =
            '<div class="chart-error">ECharts 库加载失败</div>';
        });
    });
  }

  function getChartImages(): Map<string, string> {
    const result = new Map<string, string>();
    const el = previewContainer.value;
    if (!el) return result;
    const chartBoxes = el.querySelectorAll('.chart-box');
    chartBoxes.forEach(box => {
      const instance = (box as unknown as Record<string, unknown>).__echart_instance;
      if (
        instance &&
        typeof (instance as Record<string, unknown>).getDataURL === 'function'
      ) {
        result.set(box.id, (instance as Record<string, () => string>).getDataURL());
      }
    });
    return result;
  }

  function getRenderedHtml(): string {
    const el = previewContainer.value;
    if (!el) return '';
    const md = el.querySelector('.markdown-preview');
    return md ? (md as { innerHTML: string }).innerHTML : '';
  }

  defineExpose({ getChartImages, getRenderedHtml });
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="900px"
    top="30px"
    :close-on-click-modal="false"
    @update:model-value="val => emit('update:visible', val)"
  >
    <div ref="previewContainer" class="report-preview">
      <div class="markdown-preview" v-html="renderedHtml"></div>
    </div>
  </el-dialog>
</template>

<style scoped>
  .report-preview {
    max-height: 70vh;
    overflow-y: auto;
  }

  .markdown-preview {
    :deep(h1) {
      font-size: 2rem;
      font-weight: 800;
      color: #1e3a8a;
      margin: 1.5rem 0 1rem;
      border-bottom: 2px solid #e5e7eb;
      padding-bottom: 0.5rem;
    }

    :deep(h2) {
      font-size: 1.5rem;
      font-weight: 700;
      color: #2563eb;
      margin: 2rem 0 0.75rem;
      border-left: 5px solid #2563eb;
      padding-left: 12px;
    }

    :deep(h3) {
      font-size: 1.25rem;
      font-weight: 600;
      margin: 1.5rem 0 0.5rem;
    }

    :deep(p) {
      margin: 0 0 0.75rem;
    }

    :deep(ul),
    :deep(ol) {
      padding-left: 1.5rem;
      margin: 0.5rem 0 0.75rem;
    }

    :deep(li) {
      margin-bottom: 0.25rem;
    }

    :deep(code) {
      background: var(--app-bg-hover, #f1f5f9);
      padding: 0.15rem 0.4rem;
      border-radius: 0.25rem;
      font-size: 0.875em;
      color: #d946ef;
      font-family: monospace;
    }

    :deep(pre) {
      background: #1e293b;
      color: #f8fafc;
      padding: 1rem;
      border-radius: 0.5rem;
      overflow-x: auto;
    }

    :deep(pre code) {
      background: transparent;
      color: inherit;
      padding: 0;
    }

    :deep(blockquote) {
      border-left: 3px solid #2563eb;
      padding-left: 12px;
      margin: 0.75rem 0;
      color: #6b7280;
    }

    :deep(table) {
      border-collapse: collapse;
      width: 100%;
      margin: 0.75rem 0;
    }

    :deep(th),
    :deep(td) {
      border: 1px solid #e5e7eb;
      padding: 6px 12px;
      text-align: left;
    }

    :deep(th) {
      background: #f3f4f6;
      font-weight: 600;
    }

    :deep(hr) {
      border: none;
      border-top: 1px solid #e5e7eb;
      margin: 1rem 0;
    }

    :deep(img) {
      max-width: 100%;
    }

    :deep(.chart-box) {
      width: 100%;
      height: 400px;
      margin: 24px 0;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      background: #fff;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
    }

    :deep(.chart-error) {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 100%;
      color: #ef4444;
      background: #fef2f2;
      border: 1px dashed #ef4444;
      border-radius: 8px;
      padding: 20px;
      text-align: center;
    }
  }
</style>
