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

import { describe, expect, it } from 'vitest';
import { DATA_QUERY_SKILL } from '../src/skill/data-query.js';

describe('data query skill', () => {
  it('contains the required workflow and no token', () => {
    expect(DATA_QUERY_SKILL).toContain('# DataAgent 问数指南');
    expect(DATA_QUERY_SKILL).toContain('data-agent-cli auth status');
    expect(DATA_QUERY_SKILL).toContain('data-agent-cli tools list');
    expect(DATA_QUERY_SKILL).toContain('data-agent-cli tools call execute_sql');
    expect(DATA_QUERY_SKILL).toContain('session_id');
    expect(DATA_QUERY_SKILL).not.toContain('pat_');
  });
});
