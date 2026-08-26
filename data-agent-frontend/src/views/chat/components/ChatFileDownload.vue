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
  import { ref } from 'vue';
  import { downloadTableExport } from '@/api/tableExport';

  const props = defineProps<{
    id: string;
  }>();

  const loading = ref(false);
  async function download() {
    loading.value = true;
    try {
      await downloadTableExport(props.id);
    } catch {
      // request interceptor shows the error message.
    } finally {
      loading.value = false;
    }
  }
</script>

<template>
  <button type="button" class="chat-file-download" :disabled="loading" @click="download">
    <span class="chat-file-download__icon">CSV</span>
    <span class="chat-file-download__body">
      <span class="chat-file-download__name">{{ id }}.csv</span>
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
