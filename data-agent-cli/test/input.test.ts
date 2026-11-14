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

import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { CliError } from '../src/errors.js';
import { parseInputText, readInput } from '../src/input.js';

const tempDirs: string[] = [];

async function tempDir(): Promise<string> {
  const dir = await mkdtemp(join(tmpdir(), 'data-agent-cli-input-'));
  tempDirs.push(dir);
  return dir;
}

afterEach(async () => {
  await Promise.all(tempDirs.splice(0).map(dir => rm(dir, { recursive: true, force: true })));
});

describe('input parsing', () => {
  it('parses inline JSON objects', () => {
    expect(parseInputText('{"datasource_id":1}', '--input')).toEqual({ datasource_id: 1 });
  });

  it('rejects arrays, null and invalid JSON', () => {
    expect(() => parseInputText('[]', '--input')).toThrow(CliError);
    expect(() => parseInputText('null', '--input')).toThrow(CliError);
    expect(() => parseInputText('{', '--input')).toThrow(CliError);
  });

  it('reads @file input', async () => {
    const dir = await tempDir();
    const file = join(dir, 'input.json');
    await writeFile(file, '{"datasource_id":2}', 'utf8');
    await expect(readInput(`@${file}`)).resolves.toEqual({ datasource_id: 2 });
  });
});
