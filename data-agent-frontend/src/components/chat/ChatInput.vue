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
    <textarea
      ref="textareaRef"
      v-model="inputText"
      class="chat-input__textarea"
      :placeholder="isStreaming ? '正在回复中...' : '输入消息，Enter 发送，Shift+Enter 换行'"
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
    align-items: flex-end;
    gap: 10px;
    padding: 16px 20px;
    background: #fff;
    border-top: 1px solid #e5e7eb;
  }

  .chat-input__textarea {
    flex: 1;
    resize: none;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    padding: 10px 14px;
    font-size: 14px;
    line-height: 1.5;
    font-family: inherit;
    outline: none;
    transition: border-color 0.2s;
    max-height: 160px;
  }

  .chat-input__textarea:focus {
    border-color: #3b82f6;
  }

  .chat-input__textarea:disabled {
    background: #f8fafc;
    color: #94a3b8;
  }

  .chat-input__send-btn {
    flex-shrink: 0;
    height: 40px;
  }
</style>
