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

import { readFile } from 'node:fs/promises';
import { CliError, ExitCode } from './errors.js';

export async function readInput(value: string): Promise<Record<string, unknown>> {
  const text = await readInputText(value);
  return parseInputText(
    text,
    value === '-' ? 'stdin' : value.startsWith('@') ? value.slice(1) : '--input',
  );
}

export function parseInputText(text: string, source: string): Record<string, unknown> {
  if (!text.trim()) {
    throw new CliError(ExitCode.Usage, 'EMPTY_INPUT', `Empty JSON input from ${source}.`);
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    throw new CliError(
      ExitCode.Usage,
      'INVALID_INPUT',
      `Invalid JSON input from ${source}: ${message}`,
    );
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new CliError(ExitCode.Usage, 'INVALID_INPUT', 'Tool input must be a JSON object.');
  }
  return parsed as Record<string, unknown>;
}

async function readInputText(value: string): Promise<string> {
  if (value === '-') {
    return readStdin();
  }
  if (value.startsWith('@')) {
    try {
      return await readFile(value.slice(1), 'utf8');
    } catch {
      throw new CliError(
        ExitCode.Usage,
        'INPUT_FILE_NOT_FOUND',
        `Cannot read input file: ${value.slice(1)}`,
      );
    }
  }
  return value;
}

async function readStdin(): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of process.stdin) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  return Buffer.concat(chunks).toString('utf8');
}
