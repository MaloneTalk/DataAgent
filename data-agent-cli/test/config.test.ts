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

import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import {
  DEFAULT_BASE_URL,
  DEFAULT_TIMEOUT_SECONDS,
  loadConfig,
  resolveBaseUrl,
  resolveTimeoutSeconds,
  resolveToken,
  saveConfig,
} from '../src/config/store.js';

const tempDirs: string[] = [];
const originalToken = process.env.DATAAGENT_TOKEN;
const originalBaseUrl = process.env.DATAAGENT_BASE_URL;

async function tempDir(): Promise<string> {
  const dir = await mkdtemp(join(tmpdir(), 'data-agent-cli-config-'));
  tempDirs.push(dir);
  return dir;
}

afterEach(async () => {
  process.env.DATAAGENT_TOKEN = originalToken;
  process.env.DATAAGENT_BASE_URL = originalBaseUrl;
  await Promise.all(tempDirs.splice(0).map(dir => rm(dir, { recursive: true, force: true })));
});

describe('config store', () => {
  it('uses defaults when config does not exist', async () => {
    const config = await loadConfig(await tempDir());
    expect(config.baseUrl).toBe(DEFAULT_BASE_URL);
    expect(config.token).toBeUndefined();
    expect(config.timeoutSeconds).toBeUndefined();
  });

  it('persists config with restricted permissions', async () => {
    const dir = await tempDir();
    await saveConfig({ baseUrl: 'http://example.com', token: 'pat_123', timeoutSeconds: 30 }, dir);

    const config = await loadConfig(dir);
    expect(config).toEqual({
      baseUrl: 'http://example.com',
      token: 'pat_123',
      timeoutSeconds: 30,
    });
    const body = await readFile(join(dir, 'config.json'), 'utf8');
    expect(body).toContain('"token": "pat_123"');
  });

  it('resolves token as --token > config > env', async () => {
    process.env.DATAAGENT_TOKEN = 'env-token';
    const config = { baseUrl: DEFAULT_BASE_URL, token: 'config-token' };
    expect(resolveToken({}, config)).toBe('config-token');
    expect(resolveToken({ token: 'flag-token' }, config)).toBe('flag-token');
    expect(resolveToken({}, { baseUrl: DEFAULT_BASE_URL })).toBe('env-token');
  });

  it('resolves base URL and timeout with documented precedence', async () => {
    process.env.DATAAGENT_BASE_URL = 'http://env.example.com';
    const config = { baseUrl: 'http://config.example.com' };
    expect(resolveBaseUrl({}, config)).toBe('http://config.example.com');
    expect(resolveBaseUrl({ baseUrl: 'http://flag.example.com' }, config)).toBe(
      'http://flag.example.com',
    );
    expect(resolveTimeoutSeconds({}, config)).toBe(DEFAULT_TIMEOUT_SECONDS);
    expect(resolveTimeoutSeconds({ timeout: '15' }, config)).toBe(15);
    expect(resolveTimeoutSeconds({}, { ...config, timeoutSeconds: 45 })).toBe(45);
  });
});
