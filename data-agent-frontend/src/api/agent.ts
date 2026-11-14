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

import request, { handleApiError, type ApiResponse } from './request';

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
  chart?: ChartPayload | null;
}

export interface ChartPayload {
  spec: {
    chartType: 'bar' | 'line' | 'pie' | 'scatter';
    title: string;
    xField: string;
    yFields: string[];
    seriesField: string;
  };
  rows: Record<string, string | number | boolean | null>[];
}

export interface AnswerProvenance {
  status: string;
  datasources: { id: number; name: string | null; databaseName: string | null }[];
  sqlExecutions: {
    callId: string;
    sql: string;
    executedSql: string | null;
    status: string;
    returnedRows: number | null;
    truncated: boolean | null;
  }[];
  metricReferences: {
    key: string;
    name: string;
    measureExpr: string | null;
    filters: string | null;
  }[];
  pythonExecutions: {
    callId: string;
    code: string;
    status: string;
  }[];
  tables: {
    name: string;
    objectType?: 'TABLE' | 'VIEW' | null;
    domain: string | null;
    description: string | null;
    columns: { name: string; description: string | null }[];
  }[];
  warnings: string[];
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
  datasources: Array<{ id: number; name: string | null }>;
}

export interface ChatRequest {
  sessionId: string;
  message?: string;
  toolResults?: ToolResultInput[];
  datasourceIds?: number[];
}

export interface TurnItem {
  role: string;
  content: string;
  traceSteps: ChatStreamEvent[];
}

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('token');
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

export async function createSession(): Promise<string> {
  const response = await request.post<ApiResponse<string>>('/agent/session');
  return response.data.data;
}

export async function fetchSessionList(): Promise<SessionInfo[]> {
  const response = await request.get<ApiResponse<SessionInfo[]>>('/agent/sessions');
  return response.data.data;
}

export async function fetchSessionHistory(sessionId: string): Promise<TurnItem[]> {
  const response = await request.get<ApiResponse<TurnItem[]>>(
    `/agent/session/${encodeURIComponent(sessionId)}/history`,
  );
  return response.data.data;
}

export async function clearSession(sessionId: string): Promise<void> {
  await request.delete<ApiResponse<boolean>>(`/agent/session/${encodeURIComponent(sessionId)}`);
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

  if (!response.ok) {
    const body = (await response.json().catch(() => undefined)) as Partial<ApiResponse> | undefined;
    throw handleApiError(
      body,
      `Chat stream failed: ${response.status} ${response.statusText}`,
      response.status,
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
