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
  import { computed } from 'vue';
  import { buildReportHtml, downloadHtml } from '@/utils/reportTemplate';
  import { ElMessage } from 'element-plus';

  const props = defineProps<{
    visible: boolean;
    title: string;
    content: string;
  }>();

  const emit = defineEmits<{
    (e: 'update:visible', value: boolean): void;
  }>();

  const iframeSrcdoc = computed(() => {
    return buildReportHtml(props.title, props.content);
  });

  function onVisibleChange(val: boolean) {
    emit('update:visible', val);
  }

  function handleExport() {
    const html = buildReportHtml(props.title, props.content);
    downloadHtml(`${props.title || 'report'}.html`, html);
    ElMessage.success('导出成功');
  }

  defineExpose({});
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="920px"
    top="30px"
    :close-on-click-modal="false"
    @update:model-value="onVisibleChange"
  >
    <div class="preview-toolbar">
      <el-button type="primary" @click="handleExport">导出</el-button>
    </div>
    <div class="preview-container">
      <iframe
        v-if="visible"
        :srcdoc="iframeSrcdoc"
        class="preview-iframe"
        sandbox="allow-scripts"
      />
    </div>
  </el-dialog>
</template>

<style scoped>
  .preview-toolbar {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 12px;
  }

  .preview-container {
    width: 100%;
    height: 70vh;
    overflow: hidden;
  }

  .preview-iframe {
    width: 100%;
    height: 100%;
    border: none;
  }
</style>
