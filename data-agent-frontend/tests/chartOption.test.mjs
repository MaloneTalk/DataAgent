/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { URL } from 'node:url';
import ts from 'typescript';

const source = readFileSync(new URL('../src/utils/chartOption.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 },
});
const { buildChartOption } = await import(`data:text/javascript,${encodeURIComponent(outputText)}`);
const spec = {
  chartType: 'bar',
  title: 'Amounts',
  xField: 'id',
  yFields: ['amount'],
  seriesField: '',
};

test('empty groups have separate legends from groups named after a measure', () => {
  const option = buildChartOption({
    spec: { ...spec, seriesField: 'group' },
    rows: [
      { id: 'Jan', group: '', amount: 10 },
      { id: 'Jan', group: 'amount', amount: 20 },
    ],
  });
  assert.deepEqual(
    option.series.map(series => series.name),
    [' · amount', 'amount · amount'],
  );
  assert.deepEqual(
    option.series.map(series => series.data),
    [[10], [20]],
  );
});

test('category charts retain large identifiers and gaps between grouped line values', () => {
  const option = buildChartOption({
    spec: { ...spec, chartType: 'line', seriesField: 'group' },
    rows: [
      { id: '9007199254740992', group: 'A', amount: 10 },
      { id: '9007199254740993', group: 'B', amount: 20 },
      { id: '9007199254740994', group: 'A', amount: null },
    ],
  });
  assert.deepEqual(option.xAxis.data, ['9007199254740992', '9007199254740993', '9007199254740994']);
  assert.deepEqual(
    option.series.map(series => series.data),
    [
      [10, null, null],
      [null, 20, null],
    ],
  );
});

test('fields shared by dimensions and measures still render numeric values', () => {
  const rows = [{ id: '10' }, { id: '20' }];
  for (const chartType of ['bar', 'line', 'pie', 'scatter']) {
    const option = buildChartOption({ spec: { ...spec, chartType, yFields: ['id'] }, rows });
    assert.deepEqual(
      option.series[0].data,
      chartType === 'pie'
        ? [
            { name: '10', value: 10 },
            { name: '20', value: 20 },
          ]
        : chartType === 'scatter'
          ? [
              [10, 10],
              [20, 20],
            ]
          : [10, 20],
    );
  }
});
