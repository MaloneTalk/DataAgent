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
  import { ref, nextTick, watch, onMounted, computed } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { useAgentChat } from '@/composables/useAgentChat';
  import ChatMessage from '@/views/chat/components/ChatMessage.vue';
  import ChatInput from '@/views/chat/components/ChatInput.vue';
  import SessionList from '@/views/chat/components/SessionList.vue';

  const route = useRoute();
  const router = useRouter();

  const {
    messages,
    isStreaming,
    sessionId,
    pendingQuestion,
    sendMessage,
    stopStreaming,
    newSession,
    loadHistory,
  } = useAgentChat();

  const messagesContainer = ref<{ scrollTop: number; scrollHeight: number }>();
  const sessionListRef = ref<InstanceType<typeof SessionList>>();
  const showSessionList = ref(false);

  function toggleSessionList() {
    showSessionList.value = !showSessionList.value;
  }

  const activeSessionId = computed(() => {
    const sid = route.params.sessionId;
    return typeof sid === 'string' ? sid : null;
  });

  async function scrollToBottom() {
    await nextTick();
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  }

  watch(messages, () => scrollToBottom(), { deep: true });

  onMounted(async () => {
    const sid = route.params.sessionId as string | undefined;
    if (sid) {
      await loadHistory(sid);
    }
  });

  watch(
    () => route.params.sessionId,
    async newSid => {
      if (newSid && typeof newSid === 'string') {
        await loadHistory(newSid);
      }
    },
  );

  watch(isStreaming, (val, prev) => {
    if (prev && !val) {
      sessionListRef.value?.loadList();
    }
  });

  function handleSend(text: string) {
    sendMessage(text);
    if (!activeSessionId.value) {
      router.replace(`/chat/${sessionId.value}`);
    }
  }

  function handleNewSession() {
    newSession();
    router.push(`/chat/${sessionId.value}`);
  }
</script>

<template>
  <div class="chat-view">
    <div
      class="chat-view__session-panel"
      :class="{ 'chat-view__session-panel--hidden': !showSessionList }"
    >
      <SessionList
        ref="sessionListRef"
        :active-session-id="activeSessionId"
        @new-session="handleNewSession"
      />
    </div>

    <div class="chat-view__main">
      <div class="chat-view__header">
        <div class="chat-view__header-left">
          <button class="chat-view__toggle-btn" @click="toggleSessionList">
            {{ showSessionList ? '◁' : '▷' }}
          </button>
        </div>
        <el-button text @click="handleNewSession">新建会话</el-button>
      </div>

      <div ref="messagesContainer" class="chat-view__messages">
        <div v-if="messages.length === 0" class="chat-view__empty">
          <div class="chat-view__empty-text">开始对话，让 AI 帮你分析数据</div>
          <div class="chat-view__empty-hints">
            <div
              class="hint-item"
              @click="handleSend('帮我查一下上个月高价值用户都买了哪些品类的商品？')"
            >
              "帮我查一下上个月高价值用户都买了哪些品类的商品？"
            </div>
            <div class="hint-item" @click="handleSend('分析今年第一季度的销售趋势')">
              "分析今年第一季度的销售趋势"
            </div>
            <div class="hint-item" @click="handleSend('统计各地区的用户活跃情况')">
              "统计各地区的用户活跃情况"
            </div>
          </div>
        </div>

        <ChatMessage v-for="msg in messages" :key="msg.id" :message="msg" />

        <div v-if="isStreaming && messages.length === 0" class="chat-view__thinking-hint">
          思考中...
        </div>
      </div>

      <ChatInput
        :is-streaming="isStreaming"
        :pending-question="pendingQuestion"
        @send="handleSend"
        @stop="stopStreaming"
      />
    </div>
  </div>
</template>

<style scoped>
  .chat-view {
    display: flex;
    height: 100%;
    max-width: 1100px;
    margin: 0 auto;
  }

  .chat-view__session-panel {
    width: 260px;
    flex-shrink: 0;
    overflow: hidden;
    transition:
      width 0.2s,
      opacity 0.2s;
    opacity: 1;
  }

  .chat-view__session-panel--hidden {
    width: 0;
    opacity: 0;
  }

  .chat-view__main {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
    background: var(--app-bg-card);
    border-radius: 8px;
    border: 1px solid var(--app-border);
    overflow: hidden;
    transition:
      background-color 0.2s,
      border-color 0.2s;
  }

  .chat-view__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 20px;
    border-bottom: 1px solid var(--app-border);
    flex-shrink: 0;
  }

  .chat-view__header-left {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .chat-view__toggle-btn {
    background: none;
    border: none;
    font-size: 14px;
    color: var(--app-text-muted);
    padding: 4px 8px;
    border-radius: 4px;
    cursor: pointer;
    transition:
      color 0.15s,
      background-color 0.15s;
  }

  .chat-view__toggle-btn:hover {
    color: var(--app-text-primary);
    background: var(--app-bg-hover);
  }

  .chat-view__messages {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
  }

  .chat-view__empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 60px 20px;
    color: var(--app-text-muted);
  }

  .chat-view__empty-text {
    font-size: 16px;
    margin-bottom: 24px;
    color: var(--app-text-secondary);
  }

  .chat-view__empty-hints {
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 100%;
    max-width: 480px;
  }

  .hint-item {
    padding: 10px 16px;
    background: var(--app-bg-page);
    border: 1px solid var(--app-border);
    border-radius: 8px;
    font-size: 13px;
    color: var(--app-text-secondary);
    cursor: pointer;
    transition: all 0.15s;
  }

  .hint-item:hover {
    border-color: var(--app-accent);
    color: var(--app-accent);
  }

  .chat-view__thinking-hint {
    color: var(--app-text-muted);
    font-style: italic;
  }
</style>
