/*
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
 */

import { ref, shallowRef } from 'vue';
import {
  streamChat,
  fetchSessionHistory,
  createSession,
  cancelChat,
  type ChatStreamEventType,
  type ToolResultInfo,
} from '@/api/agent';
import { ApiError } from '@/api/request';
import { INTERACTIVE_TOOLS, isInteractiveTool } from '@/utils/interactiveTools';

export interface PendingQuestion {
  toolCallId: string;
  toolName: string;
  question: string;
  datasourceIds: number[];
}

export interface TraceStep {
  type: ChatStreamEventType;
  content: string | null;
  toolCall: { id: string; name: string; input: Record<string, unknown> } | null;
  toolResult: ToolResultInfo | null;
  errorCode: string | null;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'agent';
  content: string;
  traceSteps: TraceStep[];
  isStreaming: boolean;
  timestamp: number;
}

let seq = 0;
function nextId(): string {
  return `msg_${Date.now()}_${++seq}`;
}

export function useAgentChat(initialSessionId?: string) {
  const messages = shallowRef<ChatMessage[]>([]);
  const isStreaming = ref(false);
  const isStopping = ref(false);
  const sessionId = ref<string | null>(initialSessionId || null);
  let sessionCreation: Promise<string> | null = null;
  const abortController = shallowRef<AbortController | null>(null);
  const pendingQuestion = ref<PendingQuestion | null>(null);
  const selectedDatasourceIds = ref<number[]>([]);
  const lastReportContent = ref<string | null>(null);

  function addUserMessage(text: string): ChatMessage {
    const msg: ChatMessage = {
      id: nextId(),
      role: 'user',
      content: text,
      traceSteps: [],
      isStreaming: false,
      timestamp: Date.now(),
    };
    messages.value = [...messages.value, msg];
    return msg;
  }

  function addAgentMessage(): ChatMessage {
    const msg: ChatMessage = {
      id: nextId(),
      role: 'agent',
      content: '',
      traceSteps: [],
      isStreaming: true,
      timestamp: Date.now(),
    };
    messages.value = [...messages.value, msg];
    return msg;
  }

  function updateAgentMessage(msgId: string, updater: (msg: ChatMessage) => void) {
    messages.value = messages.value.map(m => {
      if (m.id !== msgId) return m;
      const cloned = { ...m, traceSteps: [...m.traceSteps] };
      updater(cloned);
      return cloned;
    });
  }

  async function loadHistory(sid: string) {
    newSession();
    sessionId.value = sid;

    const turns = await fetchSessionHistory(sid);
    messages.value = turns.map(turn => ({
      id: nextId(),
      role: (turn.role === 'USER' ? 'user' : 'agent') as 'user' | 'agent',
      content: turn.content,
      traceSteps: turn.traceSteps,
      isStreaming: false,
      timestamp: Date.now(),
    }));
  }

  async function ensureSession(): Promise<string> {
    if (sessionId.value) return sessionId.value;
    if (!sessionCreation) {
      sessionCreation = createSession()
        .then(sid => {
          sessionId.value = sid;
          return sid;
        })
        .finally(() => {
          sessionCreation = null;
        });
    }
    return sessionCreation;
  }

  async function sendMessage(text: string) {
    if (isStreaming.value || !text.trim()) return;

    const pq = pendingQuestion.value;
    const datasourceIds = [...(pq?.datasourceIds ?? selectedDatasourceIds.value)];
    const currentSessionId = await ensureSession();
    addUserMessage(text);
    const agentMsg = addAgentMessage();
    isStreaming.value = true;

    const controller = new AbortController();
    abortController.value = controller;
    let summaryStarted = false;

    pendingQuestion.value = null;

    const request =
      pq != null
        ? {
            sessionId: currentSessionId,
            datasourceIds,
            toolResults: [{ toolCallId: pq.toolCallId, toolName: pq.toolName, output: text }],
          }
        : {
            sessionId: currentSessionId,
            datasourceIds,
            message: text,
          };

    try {
      for await (const event of streamChat(request, controller.signal)) {
        if (controller.signal.aborted) break;
        if (isStopping.value) continue;
        updateAgentMessage(agentMsg.id, msg => {
          if (event.type === 'text' && event.content) {
            msg.content += event.content;
          }

          if (event.type === 'summary' && event.content) {
            if (!summaryStarted) {
              msg.content += '\n\nSummary:\n' + event.content;
              summaryStarted = true;
            } else {
              msg.content += event.content;
            }
          }

          if (event.type === 'error') {
            msg.content += `\n\n请求失败: ${event.content ?? '请稍后重试'}`;
            msg.traceSteps = [...msg.traceSteps, toTraceStep(event)];
          }

          if (event.type === 'thinking') {
            const lastStep = msg.traceSteps[msg.traceSteps.length - 1];
            if (lastStep && lastStep.type === 'thinking') {
              lastStep.content = (lastStep.content ?? '') + (event.content ?? '');
            } else {
              msg.traceSteps = [...msg.traceSteps, toTraceStep(event)];
            }
          }

          if (
            event.type === 'tool_call' ||
            event.type === 'tool_result' ||
            event.type === 'report'
          ) {
            msg.traceSteps = [...msg.traceSteps, toTraceStep(event)];

            if (event.type === 'tool_call' && isInteractiveTool(event.toolCall?.name)) {
              const def = INTERACTIVE_TOOLS[event.toolCall!.name];
              pendingQuestion.value = {
                toolCallId: event.toolCall!.id,
                toolName: event.toolCall!.name,
                question: (event.toolCall!.input[def.questionField] as string) ?? '',
                datasourceIds: [...datasourceIds],
              };
            }

            if (event.type === 'report') {
              lastReportContent.value = event.toolResult?.output ?? null;
            }
          }
        });
      }
    } catch (e) {
      if ((e as Error).name !== 'AbortError') {
        updateAgentMessage(agentMsg.id, msg => {
          const errorCode =
            e instanceof ApiError ? (e.errorCode ?? 'REQUEST_FAILED') : 'REQUEST_FAILED';
          const content = (e as Error).message;
          msg.content += `\n\n请求失败: ${content}`;
          msg.traceSteps.push({
            type: 'error',
            content,
            errorCode,
            toolCall: null,
            toolResult: null,
          });
        });
      }
    } finally {
      if (!isStopping.value) {
        updateAgentMessage(agentMsg.id, msg => {
          msg.isStreaming = false;
        });
        if (abortController.value === controller) {
          isStreaming.value = false;
          abortController.value = null;
        }
      }
    }
  }

  function toTraceStep(event: {
    type: ChatStreamEventType;
    content: string | null;
    toolCall: TraceStep['toolCall'];
    toolResult: TraceStep['toolResult'];
    errorCode?: string | null;
  }): TraceStep {
    if (event.errorCode === 'FORBIDDEN') window.dispatchEvent(new Event('permissions-changed'));
    return {
      type: event.type,
      content: event.content,
      toolCall: event.toolCall,
      toolResult: event.toolResult,
      errorCode: event.errorCode ?? null,
    };
  }

  async function stopStreaming() {
    const controller = abortController.value;
    const sid = sessionId.value;
    const messageId = messages.value[messages.value.length - 1]?.id;
    if (!controller || !sid || !messageId || isStopping.value) return;
    isStopping.value = true;
    let errorCode = 'REQUEST_CANCELLED';
    let content = '已取消，数据库任务已结束。';
    try {
      await cancelChat(sid);
    } catch (e) {
      errorCode =
        e instanceof ApiError
          ? (e.errorCode ?? 'SQL_TERMINATION_UNCONFIRMED')
          : 'SQL_TERMINATION_UNCONFIRMED';
      content = `取消未确认：${(e as Error).message}`;
    } finally {
      controller.abort();
      updateAgentMessage(messageId, msg => {
        msg.content += `\n\n${content}`;
        msg.traceSteps.push({
          type: 'error',
          content,
          errorCode,
          toolCall: null,
          toolResult: null,
        });
        msg.isStreaming = false;
      });
      if (abortController.value === controller) {
        isStreaming.value = false;
        isStopping.value = false;
        abortController.value = null;
        pendingQuestion.value = null;
      }
    }
  }

  function newSession() {
    void stopStreaming();
    abortController.value?.abort();
    abortController.value = null;
    isStreaming.value = false;
    isStopping.value = false;
    messages.value = [];
    sessionId.value = null;
    pendingQuestion.value = null;
    selectedDatasourceIds.value = [];
  }

  return {
    messages,
    isStreaming,
    isStopping,
    sessionId,
    pendingQuestion,
    selectedDatasourceIds,
    lastReportContent,
    loadHistory,
    ensureSession,
    sendMessage,
    stopStreaming,
    newSession,
  };
}
