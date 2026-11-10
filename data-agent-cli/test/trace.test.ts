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

import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { nextTrace, TRACE_IDLE_MS } from '../src/trace/store.js';

const tempDirs: string[] = [];
const originalTraceparent = process.env.TRACEPARENT;

async function tempDir(): Promise<string> {
  const dir = await mkdtemp(join(tmpdir(), 'data-agent-cli-trace-'));
  tempDirs.push(dir);
  return dir;
}

afterEach(async () => {
  process.env.TRACEPARENT = originalTraceparent;
  await Promise.all(tempDirs.splice(0).map(dir => rm(dir, { recursive: true, force: true })));
});

describe('trace store', () => {
  it('reuses trace id within the idle window and rotates after it', async () => {
    const dir = await tempDir();
    const now = Date.parse('2026-11-10T10:00:00Z');
    const first = await nextTrace('run-1', dir, now);
    const second = await nextTrace('run-1', dir, now + 60_000);
    const third = await nextTrace('run-1', dir, now + 60_000 + TRACE_IDLE_MS + 1);

    expect(second.traceId).toBe(first.traceId);
    expect(third.traceId).not.toBe(first.traceId);
    expect(first.traceparent).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/);
  });

  it('passes through a valid external TRACEPARENT', async () => {
    process.env.TRACEPARENT = '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01';
    const result = await nextTrace('run-2', await tempDir());
    expect(result).toEqual({
      traceparent: process.env.TRACEPARENT,
      traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
    });
  });
});
