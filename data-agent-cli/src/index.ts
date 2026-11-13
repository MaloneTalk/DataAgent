#!/usr/bin/env node
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

import { Command } from 'commander';
import { registerAuthCommands } from './commands/auth.js';
import { registerSkillCommand } from './commands/skill.js';
import { registerToolsCommands } from './commands/tools.js';
import { exitCodeOf } from './errors.js';
import { printError } from './output.js';
import { AUTHOR, CONTRIBUTORS, VERSION } from './version.js';

function versionText(): string {
  const label = (person: { name: string; url?: string }) =>
    person.url ? `${person.name} <${person.url}>` : person.name;
  const lines = [VERSION, '', 'Author:'];
  if (AUTHOR) {
    lines.push(label(AUTHOR));
  }
  if (CONTRIBUTORS.length > 0) {
    lines.push(...CONTRIBUTORS.map(person => `    ${label(person)}`));
  }
  return lines.join('\n');
}

async function main(): Promise<void> {
  const program = new Command();
  program
    .name('data-agent-cli')
    .description('Call DataAgent agent tools with a personal access token')
    .version(versionText(), '-v, --version')
    .showSuggestionAfterError();

  registerAuthCommands(program);
  registerToolsCommands(program);
  registerSkillCommand(program);

  await program.parseAsync(process.argv);
}

main().catch(error => {
  const json = process.argv.includes('--json') || !process.stdout.isTTY;
  printError(error, json);
  process.exitCode = exitCodeOf(error);
});
