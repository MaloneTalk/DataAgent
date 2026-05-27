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
  import { marked } from 'marked';
  import type { ChatMessage as ChatMessageType } from '@/composables/useAgentChat';
  import TracePanel from './TracePanel.vue';

  marked.use({ gfm: true, breaks: true });

  const props = defineProps<{
    message: ChatMessageType;
  }>();

  const SUMMARY_MARKER = '\n\nSummary：\n';

  const contentParts = computed(() => {
    const idx = props.message.content.indexOf(SUMMARY_MARKER);
    if (idx === -1) {
      return { text: props.message.content, summary: '' };
    }
    return {
      text: props.message.content.slice(0, idx),
      summary: props.message.content.slice(idx + SUMMARY_MARKER.length),
    };
  });

  const renderedText = computed(() => marked.parse(contentParts.value.text) as string);
  const renderedSummary = computed(() => marked.parse(contentParts.value.summary) as string);
</script>

<template>
  <div class="chat-message" :class="`chat-message--${message.role}`">
    <div class="chat-message__bubble">
      <TracePanel v-if="message.role === 'agent'" :message="message" />
      <div v-if="contentParts.text" class="chat-message__content" v-html="renderedText"></div>
      <div v-if="contentParts.summary" class="chat-message__summary" v-html="renderedSummary"></div>
      <div
        v-if="
          message.isStreaming &&
          !contentParts.text &&
          !contentParts.summary &&
          message.traceSteps.length === 0
        "
        class="chat-message__thinking"
      >
        处理中
        <span class="chat-message__cursor">...</span>
      </div>
      <span
        v-if="message.isStreaming && (contentParts.text || contentParts.summary)"
        class="chat-message__cursor"
      >
        |
      </span>
    </div>
  </div>
</template>

<style scoped>
  .chat-message {
    display: flex;
    margin-bottom: 20px;
    animation: messageIn 0.3s ease-out;
  }

  @keyframes messageIn {
    from {
      opacity: 0;
      transform: translateY(8px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  .chat-message--user {
    justify-content: flex-end;
  }

  .chat-message--agent {
    justify-content: flex-start;
  }

  .chat-message__bubble {
    max-width: 780px;
    padding: 16px 20px;
    border-radius: 12px;
    font-size: 14px;
    line-height: 1.7;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  }

  .chat-message--user .chat-message__bubble {
    background: #1f2937;
    color: white;
    border-bottom-right-radius: 4px;
    max-width: 520px;
  }

  .chat-message--agent .chat-message__bubble {
    background: white;
    color: #1f2937;
    border-bottom-left-radius: 4px;
    border: 1px solid #f0f0f0;
  }

  .chat-message__content {
    word-break: break-word;

    :deep(p) {
      margin: 0 0 8px;

      &:last-child {
        margin-bottom: 0;
      }
    }

    :deep(strong) {
      font-weight: 700;
    }

    :deep(em) {
      font-style: italic;
    }

    :deep(ul),
    :deep(ol) {
      padding-left: 20px;
      margin: 4px 0 8px;
    }

    :deep(li) {
      margin-bottom: 2px;
    }

    :deep(code) {
      background: #f1f5f9;
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 13px;
      font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
    }

    :deep(pre) {
      background: #f8f9fa;
      border: 1px solid #e9ecef;
      border-radius: 8px;
      padding: 14px 16px;
      overflow-x: auto;
      margin: 10px 0;

      code {
        background: none;
        padding: 0;
        font-size: 13px;
        color: #1f2937;
      }
    }

    :deep(blockquote) {
      border-left: 3px solid #6b7280;
      padding-left: 12px;
      margin: 8px 0;
      color: #64748b;
    }

    :deep(h1),
    :deep(h2),
    :deep(h3),
    :deep(h4),
    :deep(h5),
    :deep(h6) {
      margin: 12px 0 6px;
      font-weight: 600;
      line-height: 1.4;
    }

    :deep(table) {
      border-collapse: collapse;
      width: 100%;
      margin: 8px 0;

      th,
      td {
        border: 1px solid #e2e8f0;
        padding: 6px 12px;
        text-align: left;
      }

      th {
        background: #f8fafc;
        font-weight: 600;
      }
    }

    :deep(hr) {
      border: none;
      border-top: 1px solid #e5e7eb;
      margin: 12px 0;
    }
  }

  .chat-message__summary {
    word-break: break-word;
    color: #1f2937;
    margin-top: 12px;
    padding-left: 12px;
    border-left: 2px solid #e5e7eb;

    :deep(p) {
      margin: 0 0 8px;

      &:last-child {
        margin-bottom: 0;
      }
    }

    :deep(strong) {
      font-weight: 700;
    }

    :deep(ul),
    :deep(ol) {
      padding-left: 20px;
      margin: 4px 0 8px;
    }
  }

  .chat-message__thinking {
    color: #94a3b8;
    font-style: italic;
  }

  .chat-message__cursor {
    display: inline-block;
    animation: blink 1s step-end infinite;
    color: #6b7280;
    font-weight: bold;
  }

  .chat-message--user .chat-message__cursor {
    color: rgba(255, 255, 255, 0.8);
  }

  @keyframes blink {
    0%,
    100% {
      opacity: 1;
    }
    50% {
      opacity: 0;
    }
  }
</style>
