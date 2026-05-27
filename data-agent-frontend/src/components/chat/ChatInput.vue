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

  const props = defineProps<{
    isStreaming: boolean;
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

  interface KeydownLike {
    key: string;
    shiftKey: boolean;
    isComposing?: boolean;
    keyCode?: number;
    preventDefault: () => void;
  }

  function handleKeydown(e: KeydownLike) {
    if (e.key !== 'Enter' || e.shiftKey) return;
    if (e.isComposing || e.keyCode === 229) return;
    e.preventDefault();
    if (props.isStreaming) {
      emit('stop');
    } else {
      handleSend();
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
    <div class="chat-input__wrapper">
      <textarea
        ref="textareaRef"
        v-model="inputText"
        class="chat-input__textarea"
        :placeholder="isStreaming ? '正在处理中...' : '输入你的问题，Enter 发送'"
        :disabled="isStreaming"
        rows="1"
        @input="autoResize"
        @keydown="handleKeydown"
      />
      <el-button
        v-if="!isStreaming"
        class="chat-input__send-btn"
        :disabled="!inputText.trim()"
        @click="handleSend"
      >
        发送
      </el-button>
      <el-button
        v-else
        type="danger"
        class="chat-input__send-btn chat-input__stop-btn"
        @click="emit('stop')"
      >
        停止
      </el-button>
    </div>
  </div>
</template>

<style scoped>
  .chat-input {
    padding: 16px 24px 20px;
    background: #fff;
    border-top: 1px solid #f0f0f0;
    box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.03);
  }

  .chat-input__wrapper {
    display: flex;
    align-items: flex-end;
    gap: 10px;
    max-width: 860px;
    margin: 0 auto;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    padding: 8px 12px;
    transition:
      border-color 0.2s,
      box-shadow 0.2s;
  }

  .chat-input__wrapper:focus-within {
    border-color: #94a3b8;
    box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.1);
  }

  .chat-input__textarea {
    flex: 1;
    resize: none;
    border: none;
    background: transparent;
    padding: 8px 4px;
    font-size: 14px;
    line-height: 1.5;
    font-family: inherit;
    outline: none;
    max-height: 160px;
  }

  .chat-input__textarea:disabled {
    color: #94a3b8;
  }

  .chat-input__send-btn {
    flex-shrink: 0;
    height: 36px;
    padding: 0 16px;
    border-radius: 8px;
    font-weight: 500;
    background: #1f2937;
    color: white;
    border: none;
    transition: all 0.15s;
  }

  .chat-input__send-btn:hover:not(:disabled) {
    background: #374151;
  }

  .chat-input__send-btn:disabled {
    background: #e5e7eb;
    color: #9ca3af;
  }

  .chat-input__stop-btn {
    background: #dc2626;
  }

  .chat-input__stop-btn:hover {
    background: #b91c1c;
  }
</style>
