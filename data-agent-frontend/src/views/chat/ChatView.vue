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
  import ChatMessage from '@/components/chat/ChatMessage.vue';
  import ChatInput from '@/components/chat/ChatInput.vue';
  import SessionList from '@/components/chat/SessionList.vue';

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
    router.push('/chat');
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
          <el-button class="chat-view__toggle-btn" text @click="toggleSessionList">
            <el-icon :size="18">
              <Fold v-if="showSessionList" />
              <Expand v-else />
            </el-icon>
          </el-button>
        </div>
        <el-button text @click="handleNewSession">新建会话</el-button>
      </div>

      <div ref="messagesContainer" class="chat-view__messages">
        <div v-if="messages.length === 0" class="chat-view__empty">
          <div class="chat-view__empty-icon">💬</div>
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

        <div v-if="isStreaming && messages.length === 0" class="chat-view__empty">思考中...</div>
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
  }

  .chat-view__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 20px;
    border-bottom: 1px solid #e5e7eb;
    flex-shrink: 0;
  }

  .chat-view__header-left {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .chat-view__toggle-btn {
    font-size: 14px;
    color: #94a3b8;
    padding: 4px;
  }

  .chat-view__toggle-btn:hover {
    color: #3b82f6;
    background: #f1f5f9;
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
    color: #94a3b8;
  }

  .chat-view__empty-icon {
    font-size: 48px;
    margin-bottom: 16px;
  }

  .chat-view__empty-text {
    font-size: 16px;
    margin-bottom: 24px;
    color: #64748b;
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
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    font-size: 13px;
    color: #64748b;
    cursor: pointer;
    transition: all 0.2s;
  }

  .hint-item:hover {
    background: #eff6ff;
    border-color: #3b82f6;
    color: #3b82f6;
  }
</style>
