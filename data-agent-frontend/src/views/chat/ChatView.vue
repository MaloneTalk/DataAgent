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
  import { ElMessage } from 'element-plus';
  import { createDashboardCard, type ChartType } from '@/api/dashboard';
  import { useAgentChat } from '@/composables/useAgentChat';
  import { fetchSessionList } from '@/api/agent';
  import { getDatasourceList, type DatasourceResponse } from '@/api/datasource';
  import ChatMessage from '@/views/chat/components/ChatMessage.vue';
  import ChatInput from '@/views/chat/components/ChatInput.vue';
  import SessionList from '@/views/chat/components/SessionList.vue';
  import ReportPreviewDialog from '@/views/report/ReportPreviewDialog.vue';
  import ReportList from '@/views/report/ReportList.vue';

  const route = useRoute();
  const router = useRouter();

  const {
    messages,
    isStreaming,
    sessionId,
    datasourceId,
    pendingQuestion,
    lastReportContent,
    sendMessage,
    stopStreaming,
    newSession,
    loadHistory,
  } = useAgentChat();

  const messagesContainer = ref<{ scrollTop: number; scrollHeight: number }>();
  const sessionListRef = ref<InstanceType<typeof SessionList>>();
  const showSessionList = ref(false);
  const reportDialogVisible = ref(false);
  const reportListKey = ref(0);
  const previewVisible = ref(false);
  const previewContent = ref('');
  const datasources = ref<DatasourceResponse[]>([]);
  const saveCardVisible = ref(false);
  const saveCardLoading = ref(false);
  const saveCardForm = ref({
    title: '',
    sqlText: '',
    chartType: 'table' as ChartType,
  });

  // 已发过消息 = 绑定已落库 = 不可再切换数据源（锁定语义）。
  const isBound = computed(() => messages.value.length > 0);

  // 用户在当前会话中发过的消息文本（用于输入框上下键历史导航）
  const userMessages = computed(() =>
    messages.value.filter(m => m.role === 'user').map(m => m.content),
  );

  // 未绑定的旧会话实际跟随全局激活源，展示其名字以免用户误以为可自由选源。
  const activeDatasourceName = computed(() => {
    if (datasourceId.value != null) return null;
    return datasources.value.find(d => d.status === 'ACTIVE')?.name ?? null;
  });

  const dsPlaceholder = computed(() => {
    if (isBound.value) {
      return activeDatasourceName.value
        ? `跟随全局激活源（${activeDatasourceName.value}）`
        : '跟随全局激活源';
    }
    return '选择数据源';
  });

  // 绑定源已被删除时下拉列表里没有对应项，补一个占位项。
  const boundUnknown = computed(() => {
    if (datasourceId.value == null) return false;
    return !datasources.value.some(d => d.id === datasourceId.value);
  });

  async function loadDatasources() {
    try {
      const res = await getDatasourceList();
      datasources.value = res.data.data ?? [];
    } catch {
      datasources.value = [];
    }
  }

  // 重进会话时从会话列表回显绑定源；无绑定（旧会话/新会话未落库）也要重置为 null，
  // 避免残留上一个会话的绑定。
  async function resolveBoundDatasource(sid: string) {
    try {
      const list = await fetchSessionList();
      const found = list.find(s => s.sessionId === sid);
      datasourceId.value = found?.datasourceId ?? null;
    } catch {
      // 会话列表加载失败不阻断会话展示。
    }
  }

  function showReportPreview(content: string) {
    previewContent.value = content;
    previewVisible.value = true;
  }

  watch(lastReportContent, content => {
    if (content) {
      showReportPreview(content);
    }
  });

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
    loadDatasources();
    const sid = route.params.sessionId as string | undefined;
    if (sid) {
      await loadHistory(sid);
      await resolveBoundDatasource(sid);
    }
  });

  watch(
    () => route.params.sessionId,
    async newSid => {
      // Avoid triggering loadHistory when router.replace updates URL on first message
      if (newSid && typeof newSid === 'string' && newSid !== sessionId.value) {
        await loadHistory(newSid);
        await resolveBoundDatasource(newSid);
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

  function handleSaveSqlCard(sql: string) {
    saveCardForm.value = {
      title: '查询卡片',
      sqlText: sql,
      chartType: 'table',
    };
    saveCardVisible.value = true;
  }

  async function submitSaveCard() {
    const title = saveCardForm.value.title.trim();
    if (!title) {
      ElMessage.error('卡片标题不能为空');
      return;
    }
    saveCardLoading.value = true;
    try {
      await createDashboardCard({
        title,
        sessionId: sessionId.value,
        sqlText: saveCardForm.value.sqlText,
        chartType: saveCardForm.value.chartType,
      });
      ElMessage.success('已保存到默认看板');
      saveCardVisible.value = false;
    } finally {
      saveCardLoading.value = false;
    }
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
        @session-deleted="sessionListRef?.loadList()"
      />
    </div>

    <div class="chat-view__main">
      <div class="chat-view__header">
        <div class="chat-view__header-left">
          <button class="chat-view__toggle-btn" @click="toggleSessionList">
            {{ showSessionList ? '◁' : '▷' }}
          </button>
        </div>
        <div class="chat-view__header-right">
          <el-select
            v-model="datasourceId"
            :disabled="isBound"
            :placeholder="dsPlaceholder"
            size="small"
            style="width: 180px"
            :title="isBound ? '会话已绑定数据源，不可切换' : undefined"
          >
            <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
            <el-option
              v-if="boundUnknown"
              :key="datasourceId"
              :label="`已删除数据源 #${datasourceId}`"
              :value="datasourceId"
            />
          </el-select>
          <el-button
            text
            @click="
              reportDialogVisible = true;
              reportListKey++;
            "
          >
            会话报告
          </el-button>
          <el-button text @click="handleNewSession">新建会话</el-button>
        </div>
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

        <ChatMessage
          v-for="msg in messages"
          :key="msg.id"
          :message="msg"
          @preview-report="showReportPreview"
          @save-sql-card="handleSaveSqlCard"
        />

        <div v-if="isStreaming && messages.length === 0" class="chat-view__thinking-hint">
          思考中...
        </div>
      </div>

      <ChatInput
        :key="sessionId"
        :is-streaming="isStreaming"
        :pending-question="pendingQuestion"
        :user-messages="userMessages"
        @send="handleSend"
        @stop="stopStreaming"
      />

      <ReportPreviewDialog
        v-model:visible="previewVisible"
        title="报告预览"
        :content="previewContent"
      />

      <el-dialog
        v-model="reportDialogVisible"
        title="会话报告"
        width="960px"
        top="30px"
        destroy-on-close
      >
        <ReportList :key="reportListKey" :fixed-session-id="sessionId" embedded />
      </el-dialog>

      <el-dialog v-model="saveCardVisible" title="保存为卡片" width="620px">
        <el-form label-width="90px">
          <el-form-item label="标题">
            <el-input v-model="saveCardForm.title" />
          </el-form-item>
          <el-form-item label="图表类型">
            <el-select v-model="saveCardForm.chartType" style="width: 100%">
              <el-option label="表格" value="table" />
              <el-option label="指标卡" value="metric" />
              <el-option label="柱状图" value="bar" />
            </el-select>
          </el-form-item>
          <el-form-item label="SQL">
            <el-input v-model="saveCardForm.sqlText" type="textarea" :rows="5" disabled />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="saveCardVisible = false">取消</el-button>
          <el-button type="primary" :loading="saveCardLoading" @click="submitSaveCard">
            保存
          </el-button>
        </template>
      </el-dialog>
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

  .chat-view__header-right {
    display: flex;
    align-items: center;
    gap: 4px;
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
