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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import type { SyncTableSemanticsResponse } from '@/api/semantic';

export type SyncSummaryField = keyof Omit<SyncTableSemanticsResponse, 'results'>;

const syncSummaryFieldLabels: Record<SyncSummaryField, { label: string; unit: string }> = {
  addedTables: { label: '新增表', unit: '张' },
  reactivatedTables: { label: '恢复表', unit: '张' },
  updatedTables: { label: '更新表', unit: '张' },
  missingTablesMarked: { label: '标记缺失表', unit: '张' },
  addedColumns: { label: '新增列', unit: '个' },
  reactivatedColumns: { label: '恢复列', unit: '个' },
  updatedColumns: { label: '更新列', unit: '个' },
  missingColumnsMarked: { label: '标记缺失列', unit: '个' },
};

export const allSyncSummaryFields: SyncSummaryField[] = [
  'addedTables',
  'reactivatedTables',
  'updatedTables',
  'missingTablesMarked',
  'addedColumns',
  'reactivatedColumns',
  'updatedColumns',
  'missingColumnsMarked',
];

export const physicalStatusSyncSummaryFields: SyncSummaryField[] = [
  'missingTablesMarked',
  'missingColumnsMarked',
];

export const buildSyncSummary = (
  result: SyncTableSemanticsResponse,
  fields: readonly SyncSummaryField[] = allSyncSummaryFields,
) =>
  fields
    .map(field => {
      const { label, unit } = syncSummaryFieldLabels[field];
      return `${label} ${result[field]} ${unit}`;
    })
    .join('，');

export const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return '-';
  }
  const matched = value.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})/);
  return matched ? `${matched[1]} ${matched[2]}` : value.replace('T', ' ');
};
