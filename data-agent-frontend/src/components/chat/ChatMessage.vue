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
  import type { ChatMessage as ChatMessageType } from '@/composables/useAgentChat';
  import TracePanel from './TracePanel.vue';

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
</script>

<template>
  <div class="chat-message" :class="`chat-message--${message.role}`">
    <div class="chat-message__bubble">
      <TracePanel v-if="message.role === 'agent'" :message="message" />
      <div v-if="contentParts.text" class="chat-message__content">
        {{ contentParts.text }}
      </div>
      <div v-if="contentParts.summary" class="chat-message__summary">
        Summary：{{ '\n' }}{{ contentParts.summary }}
      </div>
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
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  }

  .chat-message--user .chat-message__bubble {
    background: #2563eb;
    color: white;
    border-bottom-right-radius: 2px;
  }

  .chat-message--agent .chat-message__bubble {
    background: white;
    color: #1f2937;
    border-bottom-left-radius: 2px;
    border: 1px solid #e5e7eb;
    width: 100%;
  }

  .chat-message__content {
    white-space: pre-wrap;
    word-break: break-word;
  }

  .chat-message__summary {
    white-space: pre-wrap;
    word-break: break-word;
    color: #16a34a;
    margin-top: 8px;
  }

  .chat-message__thinking {
    color: #94a3b8;
    font-style: italic;
  }

  .chat-message__cursor {
    display: inline-block;
    animation: blink 1s step-end infinite;
    color: #3b82f6;
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
