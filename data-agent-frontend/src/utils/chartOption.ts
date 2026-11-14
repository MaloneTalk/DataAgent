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

import type { EChartsOption, SeriesOption } from 'echarts';
import type { ChartPayload } from '@/api/agent';

export function buildChartOption({ spec, rows }: ChartPayload): EChartsOption {
  const { chartType, xField, yFields, seriesField } = spec;
  const common: EChartsOption = {
    aria: { enabled: true },
    tooltip: { trigger: 'item', renderMode: 'richText' },
    legend: { type: 'scroll', top: 0 },
  };

  if (chartType === 'pie') {
    return {
      ...common,
      series: [
        {
          type: 'pie',
          radius: ['30%', '65%'],
          data: rows.map(row => ({ name: String(row[xField]), value: Number(row[yFields[0]]) })),
        },
      ],
    };
  }

  const categories = [...new Set(rows.map(row => String(row[xField])))];
  const groups = new Map<string, ChartPayload['rows']>();
  for (const row of rows) {
    const group = seriesField ? String(row[seriesField]) : '';
    if (!groups.has(group)) groups.set(group, []);
    groups.get(group)!.push(row);
  }

  const series: SeriesOption[] = [...groups].flatMap(([group, groupRows]) => {
    const byCategory = new Map(groupRows.map(row => [String(row[xField]), row]));
    return yFields.map(field => ({
      type: chartType,
      name: seriesField ? `${group} · ${field}` : field,
      data:
        chartType === 'scatter'
          ? groupRows.map(row => [Number(row[xField]), Number(row[field])])
          : categories.map(category => {
              const value = byCategory.get(category)?.[field];
              return value == null ? null : Number(value);
            }),
    }));
  });

  return {
    ...common,
    tooltip: { trigger: chartType === 'scatter' ? 'item' : 'axis', renderMode: 'richText' },
    grid: { left: 20, right: 20, top: 45, bottom: 20, containLabel: true },
    xAxis:
      chartType === 'scatter'
        ? { type: 'value', name: xField }
        : { type: 'category', data: categories, axisLabel: { hideOverlap: true } },
    yAxis: { type: 'value', name: yFields.length === 1 ? yFields[0] : undefined },
    series,
  };
}
