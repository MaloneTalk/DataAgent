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
        思考中
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
    margin-bottom: 16px;
  }

  .chat-message--user {
    justify-content: flex-end;
  }

  .chat-message--agent {
    justify-content: flex-start;
  }

  .chat-message__bubble {
    max-width: 100%;
    padding: 16px 20px;
    border-radius: 12px;
    font-size: 14px;
    line-height: 1.6;
    box-shadow: var(--app-shadow-sm);
  }

  .chat-message--user .chat-message__bubble {
    background: var(--app-accent);
    color: var(--app-accent-text);
    border-bottom-right-radius: 2px;
  }

  .chat-message--agent .chat-message__bubble {
    background: var(--app-bg-card);
    color: var(--app-text-primary);
    border-bottom-left-radius: 2px;
    border: 1px solid var(--app-border);
    width: 100%;
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
      background: var(--app-bg-hover);
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 13px;
      font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
    }

    :deep(pre) {
      background: var(--app-bg-page);
      border: 1px solid var(--app-border);
      border-radius: 8px;
      padding: 12px 16px;
      overflow-x: auto;
      margin: 8px 0;

      code {
        background: none;
        padding: 0;
        font-size: 13px;
      }
    }

    :deep(blockquote) {
      border-left: 3px solid var(--app-accent);
      padding-left: 12px;
      margin: 8px 0;
      color: var(--app-text-secondary);
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
        border: 1px solid var(--app-border);
        padding: 6px 12px;
        text-align: left;
      }

      th {
        background: var(--app-bg-page);
        font-weight: 600;
      }
    }

    :deep(hr) {
      border: none;
      border-top: 1px solid var(--app-border);
      margin: 12px 0;
    }
  }

  .chat-message__summary {
    word-break: break-word;
    color: #16a34a;
    margin-top: 8px;

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

  [data-theme='dark'] .chat-message__summary {
    color: #22c55e;
  }

  .chat-message__thinking {
    color: var(--app-text-muted);
    font-style: italic;
  }

  .chat-message__cursor {
    display: inline-block;
    animation: blink 1s step-end infinite;
    color: var(--app-accent);
    font-weight: bold;
  }

  .chat-message--user .chat-message__cursor {
    color: var(--app-accent-text);
    opacity: 0.7;
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
