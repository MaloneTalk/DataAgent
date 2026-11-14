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

import type { Command } from 'commander';
import {
  loadConfig,
  resolveBaseUrl,
  resolveSessionId,
  resolveTimeoutSeconds,
  resolveToken,
  type Config,
} from '../config/store.js';
import { DataAgentClient } from '../client/DataAgentClient.js';
import type { CommonOptions } from '../options.js';
import { nextTrace } from '../trace/store.js';
import { VERSION } from '../version.js';

export interface Runtime {
  client: DataAgentClient;
  config: Config;
  baseUrl: string;
  token: string;
  timeoutSeconds: number;
  sessionId?: string;
  options: CommonOptions;
}

export function addCommonOptions(command: Command): Command {
  return command
    .option('--base-url <url>', 'DataAgent base URL')
    .option('--token <pat>', 'personal access token')
    .option('--session-id <id>', 'session id sent as X-Session-Id')
    .option('--json', 'output JSON')
    .option('--timeout <seconds>', 'request timeout in seconds')
    .option('--verbose', 'print request details to stderr');
}

export async function createRuntime(options: CommonOptions): Promise<Runtime> {
  const config = await loadConfig();
  const baseUrl = resolveBaseUrl(options, config);
  const token = resolveToken(options, config);
  const timeoutSeconds = resolveTimeoutSeconds(options, config);
  const sessionId = resolveSessionId(options);
  const client = new DataAgentClient({
    baseUrl,
    token,
    timeoutMs: timeoutSeconds * 1000,
    version: VERSION,
    sessionId,
    verbose: options.verbose,
    logger: options.verbose ? line => process.stderr.write(`${line}\n`) : undefined,
    traceProvider: sessionKey => nextTrace(sessionKey),
  });
  return { client, config, baseUrl, token, timeoutSeconds, sessionId, options };
}
