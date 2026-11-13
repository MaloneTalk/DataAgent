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

import { chmod, mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { CliError, ExitCode } from '../errors.js';
import type { CommonOptions } from '../options.js';

export const DEFAULT_BASE_URL = 'http://localhost:8080';
export const DEFAULT_TIMEOUT_SECONDS = 60;

export interface Config {
  baseUrl: string;
  token?: string;
  timeoutSeconds?: number;
}

export function defaultConfigDir(env: NodeJS.ProcessEnv = process.env): string {
  if (process.platform === 'win32') {
    return join(env.APPDATA ?? join(homedir(), 'AppData', 'Roaming'), 'data-agent-cli');
  }
  return join(env.XDG_CONFIG_HOME ?? join(homedir(), '.config'), 'data-agent-cli');
}

export function configFilePath(dir = defaultConfigDir()): string {
  return join(dir, 'config.json');
}

export function stateFilePath(dir = defaultConfigDir()): string {
  return join(dir, 'state.json');
}

export async function loadConfig(dir = defaultConfigDir()): Promise<Config> {
  const path = configFilePath(dir);
  let text: string;
  try {
    text = await readFile(path, 'utf8');
  } catch (error) {
    if (isNotFound(error)) {
      return { baseUrl: DEFAULT_BASE_URL };
    }
    throw new CliError(ExitCode.Usage, 'CONFIG_READ_FAILED', `Cannot read config: ${path}`);
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new CliError(ExitCode.Usage, 'CONFIG_INVALID', `Invalid JSON config: ${path}`);
  }
  if (!isRecord(parsed)) {
    throw new CliError(ExitCode.Usage, 'CONFIG_INVALID', `Config must be an object: ${path}`);
  }

  return {
    baseUrl:
      typeof parsed.baseUrl === 'string' && parsed.baseUrl.trim()
        ? parsed.baseUrl.trim()
        : DEFAULT_BASE_URL,
    token:
      typeof parsed.token === 'string' && parsed.token.trim() ? parsed.token.trim() : undefined,
    timeoutSeconds:
      typeof parsed.timeoutSeconds === 'number' && parsed.timeoutSeconds > 0
        ? parsed.timeoutSeconds
        : undefined,
  };
}

export async function saveConfig(config: Config, dir = defaultConfigDir()): Promise<void> {
  const path = configFilePath(dir);
  await mkdir(dirname(path), { recursive: true, mode: 0o700 });
  const temporary = `${path}.${process.pid}.tmp`;
  const body = JSON.stringify(config, null, 2) + '\n';
  await writeFile(temporary, body, { mode: 0o600 });
  await rename(temporary, path);
  try {
    await chmod(path, 0o600);
  } catch {
    // Windows 上 chmod 可能不支持。
  }
}

export function normalizeBaseUrl(value: string): string {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new CliError(ExitCode.Usage, 'INVALID_BASE_URL', `Invalid base URL: ${value}`);
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new CliError(ExitCode.Usage, 'INVALID_BASE_URL', 'Base URL must use http or https.');
  }
  return url.toString().replace(/\/+$/, '');
}

export function resolveBaseUrl(options: CommonOptions, config: Config): string {
  return normalizeBaseUrl(
    options.baseUrl ?? config.baseUrl ?? process.env.DATAAGENT_BASE_URL ?? DEFAULT_BASE_URL,
  );
}

export function resolveToken(options: CommonOptions, config: Config): string {
  const token = options.token ?? config.token ?? process.env.DATAAGENT_TOKEN;
  if (!token) {
    throw new CliError(
      ExitCode.Auth,
      'UNAUTHORIZED',
      'No PAT configured. Run `data-agent-cli auth login`.',
    );
  }
  return token;
}

export function resolveTimeoutSeconds(options: CommonOptions, config: Config): number {
  const raw = options.timeout ?? config.timeoutSeconds ?? DEFAULT_TIMEOUT_SECONDS;
  const seconds = Number(raw);
  if (!Number.isFinite(seconds) || seconds <= 0) {
    throw new CliError(ExitCode.Usage, 'INVALID_TIMEOUT', 'Timeout must be a positive number.');
  }
  return seconds;
}

export function resolveSessionId(options: CommonOptions): string | undefined {
  const sessionId = options.sessionId ?? process.env.DATAAGENT_SESSION_ID;
  return sessionId?.trim() || undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNotFound(error: unknown): boolean {
  return typeof error === 'object' && error !== null && 'code' in error && error.code === 'ENOENT';
}
