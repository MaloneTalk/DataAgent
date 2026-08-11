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

import { ApiError, handleUnauthorized } from './request';

export type ChatStreamEventType =
  | 'summary'
  | 'tool_call'
  | 'tool_result'
  | 'thinking'
  | 'text'
  | 'question'
  | 'report'
  | 'error';

export interface ToolCallInfo {
  id: string;
  name: string;
  input: Record<string, unknown>;
}

export interface ToolResultInfo {
  id: string;
  name: string;
  output: string;
}

export interface ToolResultInput {
  toolCallId: string;
  toolName: string;
  output: string;
}

export interface ChatStreamEvent {
  type: ChatStreamEventType;
  messageId: string | null;
  isLast: boolean;
  content: string | null;
  toolCall: ToolCallInfo | null;
  toolResult: ToolResultInfo | null;
  errorCode: string | null;
}

export interface SessionInfo {
  sessionId: string;
  title: string;
  createdAt: string;
  lastActiveAt: string;
  datasourceId: number | null;
  datasourceName: string | null;
}

export interface ChatRequest {
  sessionId: string;
  message?: string;
  toolResults?: ToolResultInput[];
  datasourceId?: number;
}

export interface TurnItem {
  role: string;
  content: string;
  traceSteps: ChatStreamEvent[];
}

interface ApiErrorBody {
  code?: number;
  errorCode?: string;
  message?: string;
  data?: unknown;
}

async function resolveApiError(response: Response, fallback: string): Promise<ApiError> {
  try {
    const body = (await response.json()) as ApiErrorBody;
    return new ApiError(body.message || fallback, {
      code: body.code,
      errorCode: body.errorCode,
      details: body.data,
    });
  } catch {
    return new ApiError(fallback, {
      code: response.status,
    });
  }
}

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('token');
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

async function fetchJson<T>(url: string, fallback: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: { ...authHeaders(), ...(init?.headers as Record<string, string>) },
  });
  if (response.status === 401) {
    handleUnauthorized();
    throw new ApiError('登录已过期，请重新登录', { code: 401 });
  }
  if (!response.ok) {
    throw await resolveApiError(response, `${fallback}: ${response.status} ${response.statusText}`);
  }
  const body = await response.json();
  if (body.code !== 200) {
    throw new ApiError(body.message || fallback, {
      code: body.code,
      errorCode: body.errorCode,
      details: body.data,
    });
  }
  return body.data as T;
}

export async function fetchSessionList(): Promise<SessionInfo[]> {
  return fetchJson<SessionInfo[]>('/api/agent/sessions', 'Failed to fetch session list');
}

export async function fetchSessionHistory(sessionId: string): Promise<TurnItem[]> {
  return fetchJson<TurnItem[]>(
    `/api/agent/session/${encodeURIComponent(sessionId)}/history`,
    'Failed to fetch session history',
  );
}

export async function clearSession(sessionId: string): Promise<void> {
  await fetchJson<boolean>(
    `/api/agent/session/${encodeURIComponent(sessionId)}`,
    'Failed to delete session',
    { method: 'DELETE' },
  );
}

export async function* streamChat(
  request: ChatRequest,
  abortSignal?: AbortSignal,
): AsyncGenerator<ChatStreamEvent> {
  const response = await fetch('/api/agent/chat/stream', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(request),
    signal: abortSignal,
  });

  if (response.status === 401) {
    handleUnauthorized();
    throw new ApiError('登录已过期，请重新登录', { code: 401 });
  }

  if (!response.ok) {
    throw await resolveApiError(
      response,
      `Chat stream failed: ${response.status} ${response.statusText}`,
    );
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error('ReadableStream not supported');
  }

  const decoder = new TextDecoder();
  let buffer = '';

  function parseLine(line: string): ChatStreamEvent | null {
    // Spring ServerSentEventHttpMessageWriter uses "data:" without a space
    let json: string;
    if (line.startsWith('data: ')) {
      json = line.slice(6);
    } else if (line.startsWith('data:')) {
      json = line.slice(5);
    } else {
      return null;
    }
    try {
      return JSON.parse(json) as ChatStreamEvent;
    } catch {
      return null;
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      // SSE lines can end with \n, \r, or \r\n
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? '';

      for (const line of lines) {
        const event = parseLine(line);
        if (event) yield event;
      }
    }

    // flush remaining buffer content
    if (buffer) {
      const event = parseLine(buffer);
      if (event) yield event;
    }
  } finally {
    reader.releaseLock();
  }
}
