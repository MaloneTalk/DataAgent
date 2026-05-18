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

export type ChatStreamEventType = 'summary' | 'tool_call' | 'tool_result' | 'thinking' | 'text';

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

export interface ChatStreamEvent {
  type: ChatStreamEventType;
  messageId: string | null;
  isLast: boolean;
  content: string | null;
  toolCall: ToolCallInfo | null;
  toolResult: ToolResultInfo | null;
}

export interface ChatRequest {
  sessionId: string;
  message: string;
}

export async function* streamChat(
  request: ChatRequest,
  abortSignal?: AbortSignal,
): AsyncGenerator<ChatStreamEvent> {
  const response = await fetch('/api/agent/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
    signal: abortSignal,
  });

  if (!response.ok) {
    throw new Error(`Chat stream failed: ${response.status} ${response.statusText}`);
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
