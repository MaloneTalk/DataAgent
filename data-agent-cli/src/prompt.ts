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

import readline from 'node:readline';
import { CliError, ExitCode } from './errors.js';

type WritableInterface = readline.Interface & {
  _writeToOutput?: (text: string) => void;
};

export async function promptToken(): Promise<string> {
  if (!process.stdin.isTTY) {
    throw new CliError(
      ExitCode.Usage,
      'TOKEN_REQUIRED',
      'No PAT provided. Use --token or DATAAGENT_TOKEN in non-interactive mode.',
    );
  }

  process.stdout.write('PAT: ');
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: true,
  });
  const writable = rl as WritableInterface;
  const originalWrite = writable._writeToOutput;
  writable._writeToOutput = () => {};

  const token = await new Promise<string>(resolve => {
    rl.question('', answer => resolve(answer));
  });
  writable._writeToOutput = originalWrite;
  rl.close();
  process.stdout.write('\n');

  if (!token.trim()) {
    throw new CliError(ExitCode.Usage, 'TOKEN_REQUIRED', 'PAT must not be empty.');
  }
  return token.trim();
}
