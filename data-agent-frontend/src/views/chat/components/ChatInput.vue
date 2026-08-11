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
  import { ref, computed, nextTick, watch } from 'vue';

  import type { PendingQuestion } from '@/composables/useAgentChat';

  const props = defineProps<{
    isStreaming: boolean;
    pendingQuestion: PendingQuestion | null;
    // Previous user messages from the chat, used as input history for Up/Down navigation.
    // Ordered oldest → newest.
    userMessages: string[];
  }>();

  const emit = defineEmits<{
    send: [text: string];
    stop: [];
  }>();

  const inputText = ref('');
  const textareaRef = ref<{
    value: string;
    style: { height: string };
    scrollHeight: number;
    selectionStart: number;
    selectionEnd: number;
  }>();

  // -1 = not browsing; 0 = newest user message, increasing = older
  const historyIndex = ref(-1);

  // Full original message at the current history position (used by Tab confirmation)
  const historyFull = computed(() => {
    if (historyIndex.value < 0) return '';
    const msgs = props.userMessages;
    const idx = msgs.length - 1 - historyIndex.value;
    return idx >= 0 && idx < msgs.length ? msgs[idx] : '';
  });

  // Truncated version shown as placeholder: max 20 chars or cut at first newline,
  // whichever comes first. Appends "…" whenever anything was dropped.
  const historyPreview = computed(() => {
    const raw = historyFull.value;
    if (!raw) return null;
    const nl = raw.indexOf('\n');
    const cut = nl === -1 ? raw.length : nl;
    const limit = Math.min(50, cut);
    const truncated = raw.slice(0, limit);
    return truncated.length < raw.length ? truncated + '…' : truncated;
  });

  watch(inputText, () => {
    nextTick(autoResize);
  });

  function handleSend() {
    const text = inputText.value.trim();
    if (!text || props.isStreaming) return;
    historyIndex.value = -1;
    emit('send', text);
    inputText.value = '';
  }

  function handleKeydown(e: {
    key: string;
    shiftKey: boolean;
    ctrlKey: boolean;
    altKey: boolean;
    metaKey: boolean;
    preventDefault: () => void;
  }) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (props.isStreaming) {
        emit('stop');
      } else {
        handleSend();
      }
      return;
    }

    const hasModifier = e.shiftKey || e.ctrlKey || e.altKey || e.metaKey;
    if (hasModifier) return;

    const el = textareaRef.value;
    if (!el) return;

    // History preview via Up/Down:
    // - When input is empty → always works
    // - When input has text → only when cursor is at the boundary
    //   (Up at start of text, Down at end of text)
    // The history item shows as placeholder (not real value); Tab to confirm.

    if (e.key === 'ArrowUp') {
      if (props.userMessages.length === 0) return;
      const len = el.value.length;
      const cursorAtStart = el.selectionStart === 0 && el.selectionEnd === 0;
      if (len > 0 && !cursorAtStart) return;
      e.preventDefault();
      const next = historyIndex.value === -1 ? 0 : historyIndex.value + 1;
      historyIndex.value = Math.min(next, props.userMessages.length - 1);
      return;
    }

    if (e.key === 'ArrowDown') {
      if (historyIndex.value === -1) return;
      const len = el.value.length;
      const cursorAtEnd = el.selectionStart === len && el.selectionEnd === len;
      if (len > 0 && !cursorAtEnd) return;
      e.preventDefault();
      historyIndex.value = Math.max(historyIndex.value - 1, -1);
      return;
    }

    // Tab: confirm the current history preview, filling the full original text into the input
    if (e.key === 'Tab' && historyPreview.value !== null) {
      e.preventDefault();
      inputText.value = historyFull.value;
      historyIndex.value = -1;
      nextTick(autoResize);
      return;
    }

    // Escape: dismiss the history preview
    if (e.key === 'Escape' && historyPreview.value !== null) {
      e.preventDefault();
      historyIndex.value = -1;
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
        historyPreview !== null
          ? historyPreview + '  (Tab 确认)'
          : props.pendingQuestion && !isStreaming
            ? '输入你的回答，Enter 发送...'
            : isStreaming
              ? '正在回复中...'
              : '输入消息，Enter 发送，Shift+Enter 换行'
      "
      :disabled="isStreaming"
      rows="1"
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
