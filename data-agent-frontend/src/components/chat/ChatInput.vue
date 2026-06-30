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
  import { ref, nextTick } from 'vue';

  import type { PendingQuestion } from '@/composables/useAgentChat';

  const props = defineProps<{
    isStreaming: boolean;
    pendingQuestion: PendingQuestion | null;
  }>();

  const emit = defineEmits<{
    send: [text: string];
    stop: [];
  }>();

  const inputText = ref('');
  const textareaRef = ref<{ style: { height: string }; scrollHeight: number }>();

  function handleSend() {
    const text = inputText.value.trim();
    if (!text || props.isStreaming) return;
    emit('send', text);
    inputText.value = '';
    nextTick(() => {
      if (textareaRef.value) {
        textareaRef.value.style.height = 'auto';
      }
    });
  }

  function handleKeydown(e: { key: string; shiftKey: boolean; preventDefault: () => void }) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (props.isStreaming) {
        emit('stop');
      } else {
        handleSend();
      }
    }
  }

  function autoResize() {
    const el = textareaRef.value;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 160) + 'px';
  }
</script>

<template>
  <div class="chat-input">
    <div v-if="props.pendingQuestion && !isStreaming" class="chat-input__question-banner">
      <span class="chat-input__question-label">Agent 提问：</span>
      <span class="chat-input__question-text">{{ props.pendingQuestion.question }}</span>
    </div>
    <textarea
      ref="textareaRef"
      v-model="inputText"
      class="chat-input__textarea"
      :placeholder="
        props.pendingQuestion && !isStreaming
          ? '输入你的回答，Enter 发送...'
          : isStreaming
            ? '正在回复中...'
            : '输入消息，Enter 发送，Shift+Enter 换行'
      "
      :disabled="isStreaming"
      rows="1"
      @input="autoResize"
      @keydown="handleKeydown"
    />
    <el-button
      v-if="!isStreaming"
      type="primary"
      class="chat-input__send-btn"
      :disabled="!inputText.trim()"
      @click="handleSend"
    >
      发送
    </el-button>
    <el-button v-else type="danger" class="chat-input__send-btn" @click="emit('stop')">
      停止
    </el-button>
  </div>
</template>

<style scoped>
  .chat-input {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-end;
    gap: 10px;
    padding: 16px 20px;
    background: var(--app-bg-card);
    border-top: 1px solid var(--app-border);
    transition:
      background-color 0.2s,
      border-color 0.2s;
  }

  .chat-input__question-banner {
    width: 100%;
    padding: 8px 14px;
    background: var(--app-bg-page);
    border: 1px solid var(--app-border);
    border-radius: 8px;
    font-size: 13px;
    color: var(--app-text-secondary);
  }

  .chat-input__question-label {
    font-weight: 600;
  }

  .chat-input__question-text {
    color: var(--app-text-primary);
  }

  .chat-input__textarea {
    flex: 1;
    resize: none;
    border: 1px solid var(--app-border);
    border-radius: 8px;
    padding: 10px 14px;
    font-size: 14px;
    line-height: 1.5;
    font-family: inherit;
    outline: none;
    background: var(--app-bg-input);
    color: var(--app-text-primary);
    transition:
      border-color 0.15s,
      background-color 0.2s,
      color 0.2s;
    max-height: 160px;
  }

  .chat-input__textarea:focus {
    border-color: var(--app-accent);
  }

  .chat-input__textarea:disabled {
    background: var(--app-bg-page);
    color: var(--app-text-muted);
  }

  .chat-input__send-btn {
    flex-shrink: 0;
    height: 40px;
  }
</style>
