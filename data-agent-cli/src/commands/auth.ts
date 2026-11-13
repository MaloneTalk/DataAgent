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
import { DataAgentClient } from '../client/DataAgentClient.js';
import { loadConfig, resolveBaseUrl, resolveTimeoutSeconds, saveConfig } from '../config/store.js';
import type { CommonOptions } from '../options.js';
import {
  expiryWarning,
  isJsonMode,
  printAuthStatus,
  printJson,
  printText,
  warn,
} from '../output.js';
import { promptToken } from '../prompt.js';
import { nextTrace } from '../trace/store.js';
import { VERSION } from '../version.js';
import { addCommonOptions, createRuntime } from './common.js';

export function registerAuthCommands(program: Command): void {
  const auth = program.command('auth').description('Manage PAT authentication');

  const login = addCommonOptions(auth.command('login').description('Validate and save a PAT'));
  login.action(async () => {
    const options = login.opts<CommonOptions>();
    const config = await loadConfig();
    const baseUrl = resolveBaseUrl(options, config);
    const timeoutSeconds = resolveTimeoutSeconds(options, config);
    const token = options.token ?? process.env.DATAAGENT_TOKEN ?? (await promptToken());
    const client = new DataAgentClient({
      baseUrl,
      token,
      timeoutMs: timeoutSeconds * 1000,
      version: VERSION,
      verbose: options.verbose,
      logger: options.verbose ? line => process.stderr.write(`${line}\n`) : undefined,
      traceProvider: sessionKey => nextTrace(sessionKey),
    });

    const pat = await client.health();
    await saveConfig({ baseUrl, token, timeoutSeconds });
    if (isJsonMode(options.json)) {
      printJson(pat);
    } else {
      printText(`Logged in to ${baseUrl}`);
      printAuthStatus(pat);
    }
    const warning = expiryWarning(pat);
    if (warning) {
      warn(warning);
    }
  });

  const status = addCommonOptions(auth.command('status').description('Check the current PAT'));
  status.action(async () => {
    const options = status.opts<CommonOptions>();
    const runtime = await createRuntime(options);
    const pat = await runtime.client.health();
    if (isJsonMode(options.json)) {
      printJson(pat);
    } else {
      printAuthStatus(pat);
    }
    const warning = expiryWarning(pat);
    if (warning) {
      warn(warning);
    }
  });

  const logout = addCommonOptions(auth.command('logout').description('Remove the local PAT'));
  logout.action(async () => {
    const options = logout.opts<CommonOptions>();
    const config = await loadConfig();
    await saveConfig({ ...config, token: undefined });
    if (isJsonMode(options.json)) {
      printJson({ status: 'logged_out' });
    } else {
      printText('Logged out. PAT was removed from the local config.');
    }
  });
}
