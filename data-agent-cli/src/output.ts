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

import { toErrorPayload } from './errors.js';
import type { AgentToolVo, ToolResultVo, UserPatVo } from './client/DataAgentClient.js';

export function isJsonMode(flag: boolean | undefined): boolean {
  return Boolean(flag) || !process.stdout.isTTY;
}

export function printJson(value: unknown): void {
  process.stdout.write(JSON.stringify(value, null, 2) + '\n');
}

export function printText(value: string): void {
  process.stdout.write(value.endsWith('\n') ? value : value + '\n');
}

export function printError(error: unknown, json: boolean): void {
  const payload = toErrorPayload(error);
  if (json) {
    printJson(payload);
  } else {
    printText(`${payload.errorCode}: ${payload.message}`);
  }
}

export function warn(message: string): void {
  process.stderr.write(`warning: ${message}\n`);
}

export function printTools(tools: AgentToolVo[]): void {
  if (tools.length === 0) {
    printText('No tools available.');
    return;
  }
  const nameWidth = Math.max('NAME'.length, ...tools.map(tool => tool.name.length));
  const lines = [
    `${'NAME'.padEnd(nameWidth)}  REQUIRED   DESCRIPTION`,
    `${'-'.repeat(nameWidth)}  --------   -----------`,
  ];
  for (const tool of tools) {
    const required = requiredParams(tool);
    lines.push(
      `${tool.name.padEnd(nameWidth)}  ${(required.join(',') || '-').padEnd(8)}   ${
        tool.description || '-'
      }`,
    );
  }
  printText(lines.join('\n'));
}

export function printToolDescription(tool: AgentToolVo): void {
  printText(
    [
      `Name: ${tool.name}`,
      `Description: ${tool.description || '-'}`,
      'Parameters:',
      JSON.stringify(tool.parameters, null, 2),
    ].join('\n'),
  );
}

export function printToolResult(result: ToolResultVo): void {
  printText(result.text);
}

export function printAuthStatus(pat: UserPatVo): void {
  printText(
    [
      `Token: ${pat.name} (${pat.tokenPrefix}...)`,
      `Expires: ${pat.expiresAt}`,
      `Last used: ${pat.lastUsedAt ?? '-'}`,
      `Created: ${pat.createTime}`,
    ].join('\n'),
  );
}

export function expiryWarning(pat: UserPatVo, thresholdDays = 7): string | null {
  const expiresAt = Date.parse(pat.expiresAt);
  if (!Number.isFinite(expiresAt)) {
    return null;
  }
  const remainingDays = (expiresAt - Date.now()) / 86_400_000;
  if (remainingDays < 0) {
    return `PAT "${pat.name}" has expired. Create a new one in /settings/tokens.`;
  }
  if (remainingDays <= thresholdDays) {
    return `PAT "${pat.name}" expires in ${Math.ceil(remainingDays)} day(s).`;
  }
  return null;
}

export function requiredParams(tool: AgentToolVo): string[] {
  const required = tool.parameters.required;
  return Array.isArray(required)
    ? required.filter((value): value is string => typeof value === 'string')
    : [];
}
