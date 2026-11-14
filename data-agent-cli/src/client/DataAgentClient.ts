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

import { DEFAULT_SESSION_KEY } from '../trace/store.js';
import { CliError, ExitCode } from '../errors.js';

export interface UserPatVo {
  id: number;
  name: string;
  tokenPrefix: string;
  expiresAt: string;
  lastUsedAt?: string | null;
  revokedAt?: string | null;
  createTime: string;
}

export interface AgentToolVo {
  name: string;
  description: string;
  parameters: Record<string, unknown>;
}

export interface ToolResultVo {
  text: string;
  metadata: Record<string, unknown>;
  traceId?: string | null;
}

export interface TraceProvider {
  (sessionKey: string): Promise<{ traceparent: string; traceId: string }>;
}

export interface DataAgentClientOptions {
  baseUrl: string;
  token: string;
  timeoutMs: number;
  version: string;
  sessionId?: string;
  verbose?: boolean;
  logger?: (message: string) => void;
  traceProvider: TraceProvider;
}

interface ApiEnvelope<T> {
  code: number;
  errorCode?: string | null;
  message?: string;
  data?: T;
}

export class DataAgentClient {
  private readonly baseUrl: string;
  private readonly token: string;
  private readonly timeoutMs: number;
  private readonly version: string;
  private readonly sessionId?: string;
  private readonly verbose: boolean;
  private readonly logger?: (message: string) => void;
  private readonly traceProvider: TraceProvider;

  constructor(options: DataAgentClientOptions) {
    this.baseUrl = options.baseUrl;
    this.token = options.token;
    this.timeoutMs = options.timeoutMs;
    this.version = options.version;
    this.sessionId = options.sessionId;
    this.verbose = Boolean(options.verbose);
    this.logger = options.logger;
    this.traceProvider = options.traceProvider;
  }

  health(): Promise<UserPatVo> {
    return this.request<UserPatVo>('GET', '/agent/health');
  }

  listTools(): Promise<AgentToolVo[]> {
    return this.request<AgentToolVo[]>('GET', '/agent/tools');
  }

  async callTool(name: string, input: Record<string, unknown>): Promise<ToolResultVo> {
    const result = await this.request<ToolResultVo>(
      'POST',
      `/agent/tools/${encodeURIComponent(name)}`,
      input,
    );
    return { ...result, metadata: result.metadata ?? {} };
  }

  private async request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const sessionKey = this.sessionId ?? DEFAULT_SESSION_KEY;
    const trace = await this.traceProvider(sessionKey);
    const headers: Record<string, string> = {
      Accept: 'application/json',
      Authorization: `Bearer ${this.token}`,
      'User-Agent': `data-agent-cli/${this.version}`,
      traceparent: trace.traceparent,
    };
    if (this.sessionId) {
      headers['X-Session-Id'] = this.sessionId;
    }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    this.log(`${method} ${path}`);
    try {
      const response = await fetch(`${this.baseUrl}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal,
      });
      this.log(`${method} ${path} -> ${response.status}`);
      const text = await response.text();
      const envelope = parseEnvelope<T>(text);
      if (!response.ok) {
        throw httpError(response.status, envelope);
      }
      if (envelope === undefined) {
        throw new CliError(ExitCode.Server, 'INVALID_RESPONSE', 'Server returned empty response.');
      }
      if (envelope.code !== 200 || envelope.errorCode) {
        throw envelopeError(envelope);
      }
      return envelope.data as T;
    } catch (error) {
      if (error instanceof CliError) {
        throw error;
      }
      if (isAbortError(error)) {
        throw new CliError(
          ExitCode.Network,
          'TIMEOUT',
          `Request timed out after ${this.timeoutMs} ms.`,
        );
      }
      const message = error instanceof Error ? error.message : String(error);
      throw new CliError(ExitCode.Network, 'NETWORK_ERROR', `Request failed: ${message}`);
    } finally {
      clearTimeout(timer);
    }
  }

  private log(message: string): void {
    if (this.verbose && this.logger) {
      this.logger(message);
    }
  }
}

function parseEnvelope<T>(text: string): ApiEnvelope<T> | undefined {
  if (!text.trim()) {
    return undefined;
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new CliError(ExitCode.Server, 'INVALID_RESPONSE', 'Server returned invalid JSON.');
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new CliError(ExitCode.Server, 'INVALID_RESPONSE', 'Server returned unexpected JSON.');
  }
  return parsed as ApiEnvelope<T>;
}

function httpError<T>(status: number, envelope: ApiEnvelope<T> | undefined): CliError {
  const errorCode = envelope?.errorCode || `HTTP_${status}`;
  const message = envelope?.message || `Request failed with HTTP ${status}.`;
  return new CliError(statusExitCode(status), errorCode, message);
}

function envelopeError<T>(envelope: ApiEnvelope<T>): CliError {
  const status = typeof envelope.code === 'number' ? envelope.code : 500;
  return new CliError(
    statusExitCode(status),
    envelope.errorCode || 'REQUEST_FAILED',
    envelope.message || 'Request failed.',
  );
}

function statusExitCode(status: number): ExitCode {
  if (status === 401 || status === 403) {
    return ExitCode.Auth;
  }
  if (status === 400 || status === 409 || status === 422) {
    return ExitCode.Usage;
  }
  return ExitCode.Server;
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}
