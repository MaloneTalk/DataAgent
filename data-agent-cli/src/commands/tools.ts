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
import { CliError, ExitCode } from '../errors.js';
import { readInput } from '../input.js';
import type { CallOptions, CommonOptions } from '../options.js';
import {
  isJsonMode,
  printJson,
  printToolDescription,
  printToolResult,
  printTools,
} from '../output.js';
import { addCommonOptions, createRuntime } from './common.js';

export function registerToolsCommands(program: Command): void {
  const tools = program.command('tools').description('Discover and call agent tools');

  const list = addCommonOptions(tools.command('list').description('List available tools'));
  list.action(async () => {
    const options = list.opts<CommonOptions>();
    const runtime = await createRuntime(options);
    const result = await runtime.client.listTools();
    if (isJsonMode(options.json)) {
      printJson(result);
    } else {
      printTools(result);
    }
  });

  const describe = addCommonOptions(
    tools.command('describe <name>').description('Show a tool definition'),
  );
  describe.action(async (name: string) => {
    const options = describe.opts<CommonOptions>();
    const runtime = await createRuntime(options);
    const result = await runtime.client.listTools();
    const tool = result.find(candidate => candidate.name === name);
    if (!tool) {
      throw new CliError(ExitCode.Usage, 'TOOL_NOT_FOUND', `Unknown tool: ${name}`);
    }
    if (isJsonMode(options.json)) {
      printJson(tool);
    } else {
      printToolDescription(tool);
    }
  });

  const call = addCommonOptions(
    tools.command('call <name>').description('Call a tool with JSON input'),
  );
  call.requiredOption('--input <json>', 'JSON object, @file, or - for stdin');
  call.action(async (name: string) => {
    const options = call.opts<CallOptions>();
    const runtime = await createRuntime(options);
    const input = await readInput(options.input);
    const result = await runtime.client.callTool(name, input);
    if (isJsonMode(options.json)) {
      printJson(result);
    } else {
      printToolResult(result);
    }
    if (typeof result.metadata.errorCode === 'string' && result.metadata.errorCode) {
      process.exitCode = ExitCode.Server;
    }
  });
}
