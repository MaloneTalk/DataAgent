/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import { randomBytes } from 'node:crypto';
import { chmod, mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import { defaultConfigDir, stateFilePath } from '../config/store.js';

export const DEFAULT_SESSION_KEY = '__default__';
export const TRACE_IDLE_MS = 30 * 60 * 1000;

const TRACEPARENT_PATTERN = /^00-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}$/i;

export interface TraceResult {
  traceparent: string;
  traceId: string;
}

interface TraceEntry {
  traceId: string;
  lastUsedAt: string;
}

interface TraceState {
  traces: Record<string, TraceEntry>;
}

export async function nextTrace(
  sessionKey = DEFAULT_SESSION_KEY,
  dir = defaultConfigDir(),
  now = Date.now(),
): Promise<TraceResult> {
  const incoming = process.env.TRACEPARENT;
  const incomingMatch = incoming?.match(TRACEPARENT_PATTERN);
  if (incomingMatch && !/^0+$/.test(incomingMatch[1])) {
    return { traceparent: incomingMatch[0], traceId: incomingMatch[1].toLowerCase() };
  }

  const state = await loadTraceState(dir);
  const previous = state.traces[sessionKey];
  const previousTime = previous ? Date.parse(previous.lastUsedAt) : Number.NaN;
  const traceId =
    previous && Number.isFinite(previousTime) && now - previousTime <= TRACE_IDLE_MS
      ? previous.traceId
      : randomHex(16);

  state.traces[sessionKey] = {
    traceId,
    lastUsedAt: new Date(now).toISOString(),
  };
  await saveTraceState(state, dir);

  const spanId = randomHex(8);
  return { traceparent: `00-${traceId}-${spanId}-01`, traceId };
}

async function loadTraceState(dir: string): Promise<TraceState> {
  try {
    const parsed = JSON.parse(await readFile(stateFilePath(dir), 'utf8')) as unknown;
    if (isRecord(parsed) && isRecord(parsed.traces)) {
      return { traces: parsed.traces as Record<string, TraceEntry> };
    }
  } catch {
    // 状态文件缺失或损坏时从空状态开始，不阻塞 CLI。
  }
  return { traces: {} };
}

async function saveTraceState(state: TraceState, dir: string): Promise<void> {
  const path = stateFilePath(dir);
  await mkdir(dirname(path), { recursive: true, mode: 0o700 });
  const temporary = `${path}.${process.pid}.tmp`;
  await writeFile(temporary, JSON.stringify(state, null, 2) + '\n', { mode: 0o600 });
  await rename(temporary, path);
  try {
    await chmod(path, 0o600);
  } catch {
    // Windows 上 chmod 可能不支持。
  }
}

function randomHex(bytes: number): string {
  return randomBytes(bytes).toString('hex');
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
