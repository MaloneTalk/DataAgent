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
import { DATA_QUERY_SKILL } from '../skill/data-query.js';

export function registerSkillCommand(program: Command): void {
  program
    .command('skill')
    .description('Print the agent guide for data query workflows')
    .action(() => {
      process.stdout.write(
        DATA_QUERY_SKILL.endsWith('\n') ? DATA_QUERY_SKILL : DATA_QUERY_SKILL + '\n',
      );
    });
}
