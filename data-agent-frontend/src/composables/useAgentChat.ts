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
import { streamChat, fetchSessionHistory, type ChatStreamEventType } from '@/api/agent';

export interface TraceStep {
  type: ChatStreamEventType;
  content: string | null;
  toolCall: { id: string; name: string; input: Record<string, unknown> } | null;
  toolResult: { id: string; name: string; output: string } | null;
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

function generateSessionId(): string {
  return `session_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

export function useAgentChat(initialSessionId?: string) {
  const messages = shallowRef<ChatMessage[]>([]);
  const isStreaming = ref(false);
  const sessionId = ref(initialSessionId || generateSessionId());
  const abortController = shallowRef<AbortController | null>(null);

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
    stopStreaming();
    messages.value = [];
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

  async function sendMessage(text: string) {
    if (isStreaming.value || !text.trim()) return;

    addUserMessage(text);
    const agentMsg = addAgentMessage();
    isStreaming.value = true;

    const controller = new AbortController();
    abortController.value = controller;
    let summaryStarted = false;

    try {
      for await (const event of streamChat(
        { sessionId: sessionId.value, message: text },
        controller.signal,
      )) {
        updateAgentMessage(agentMsg.id, msg => {
          // text: append to content
          if (event.type === 'text' && event.content) {
            msg.content += event.content;
          }

          // summary: merge into content with a marker so the template can split and render green
          if (event.type === 'summary' && event.content) {
            if (!summaryStarted) {
              msg.content += '\n\nSummary：\n' + event.content;
              summaryStarted = true;
            } else {
              msg.content += event.content;
            }
          }

          // thinking: merge consecutive thought chunks into one step
          if (event.type === 'thinking') {
            const lastStep = msg.traceSteps[msg.traceSteps.length - 1];
            if (lastStep && lastStep.type === 'thinking') {
              lastStep.content = (lastStep.content ?? '') + (event.content ?? '');
            } else {
              msg.traceSteps = [
                ...msg.traceSteps,
                {
                  type: event.type,
                  content: event.content,
                  toolCall: event.toolCall,
                  toolResult: event.toolResult,
                },
              ];
            }
          }

          // tool_call / tool_result: push as individual trace steps
          if (event.type === 'tool_call' || event.type === 'tool_result') {
            msg.traceSteps = [
              ...msg.traceSteps,
              {
                type: event.type,
                content: event.content,
                toolCall: event.toolCall,
                toolResult: event.toolResult,
              },
            ];
          }

          if (event.isLast) {
            msg.isStreaming = false;
          }
        });
      }
    } catch (e) {
      if ((e as Error).name !== 'AbortError') {
        updateAgentMessage(agentMsg.id, msg => {
          msg.content = msg.content || `请求失败: ${(e as Error).message}`;
          msg.isStreaming = false;
        });
      }
    } finally {
      updateAgentMessage(agentMsg.id, msg => {
        msg.isStreaming = false;
      });
      isStreaming.value = false;
      abortController.value = null;
    }
  }

  function stopStreaming() {
    abortController.value?.abort();
  }

  function clearMessages() {
    messages.value = [];
  }

  function newSession() {
    stopStreaming();
    clearMessages();
    sessionId.value = generateSessionId();
  }

  return {
    messages,
    isStreaming,
    sessionId,
    loadHistory,
    sendMessage,
    stopStreaming,
    clearMessages,
    newSession,
  };
}
