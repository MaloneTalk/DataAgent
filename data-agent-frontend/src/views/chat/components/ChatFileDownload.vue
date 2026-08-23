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
  import { computed, ref } from 'vue';
  import { ElMessage } from 'element-plus';
  import request from '@/api/request';
  import { downloadBlob } from '@/utils/download';

  interface ChatDownloadFile {
    id: string;
    url: string;
    name: string;
  }

  const props = defineProps<{
    file: ChatDownloadFile;
  }>();

  const loading = ref(false);
  const fileType = computed(() => {
    const match = props.file.name.match(/\.([^.]+)$/);
    return (match?.[1] || 'csv').toUpperCase();
  });

  function requestPath(url: string) {
    return url.startsWith('/api/') ? url.slice('/api'.length) : url;
  }

  async function fetchBlob() {
    if (props.file.url.startsWith('/api/')) {
      const response = await request.get<Blob>(requestPath(props.file.url), {
        responseType: 'blob',
      });
      return response.data;
    }
    const response = await fetch(props.file.url);
    if (!response.ok) {
      throw new Error(`Download failed: ${response.status}`);
    }
    return response.blob();
  }

  async function download() {
    loading.value = true;
    try {
      downloadBlob(props.file.name, await fetchBlob());
    } catch (e) {
      ElMessage.error((e as Error).message || '下载失败');
    } finally {
      loading.value = false;
    }
  }
</script>

<template>
  <button type="button" class="chat-file-download" :disabled="loading" @click="download">
    <span class="chat-file-download__icon">{{ fileType }}</span>
    <span class="chat-file-download__body">
      <span class="chat-file-download__name">{{ file.name }}</span>
      <span class="chat-file-download__hint">{{ loading ? '下载中' : '点击下载' }}</span>
    </span>
  </button>
</template>

<style scoped>
  .chat-file-download {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    max-width: 360px;
    margin: 8px 0 0;
    padding: 10px 12px;
    border: 1px solid var(--app-border);
    border-radius: 8px;
    background: var(--app-bg-page);
    color: var(--app-text-primary);
    cursor: pointer;
    text-align: left;
    transition:
      background-color 0.15s,
      border-color 0.15s;
  }

  .chat-file-download:hover:not(:disabled) {
    background: var(--app-bg-hover);
    border-color: var(--app-text-muted);
  }

  .chat-file-download:disabled {
    cursor: default;
    opacity: 0.7;
  }

  .chat-file-download__icon {
    flex-shrink: 0;
    width: 36px;
    height: 36px;
    border-radius: 6px;
    background: var(--app-accent);
    color: var(--app-accent-text);
    font-size: 11px;
    font-weight: 700;
    line-height: 36px;
    text-align: center;
  }

  .chat-file-download__body {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .chat-file-download__name {
    overflow: hidden;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .chat-file-download__hint {
    color: var(--app-text-muted);
    font-size: 12px;
  }
</style>
