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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import request from './request';
import type { ApiResponse } from './request';

export interface CsvParseOptions {
  encoding: string;
  delimiter: string;
  header: boolean;
  emptyAsNull: boolean;
}

export interface CsvColumn {
  index: number;
  name: string;
  type: string;
  length: number;
  precision: number;
  scale: number;
}

export interface CsvImport {
  id: string;
  datasourceId: number;
  displayName: string;
  description: string;
  originalFilename: string;
  physicalTableName: string;
  status: string;
  processedRows: number;
  rowCount: number;
  errorMessage: string;
  createTime: string;
  columns: CsvColumn[];
  options: CsvParseOptions;
  preview: string[][];
}

export interface CsvImportPage {
  items: CsvImport[];
  total: number;
  page: number;
  pageSize: number;
}

export function previewCsv(file: File, options: CsvParseOptions) {
  const data = new FormData();
  data.append('file', file);
  Object.entries(options).forEach(([key, value]) => data.append(key, String(value)));
  return request
    .post<ApiResponse<CsvImport>>('/csv-imports/preview', data, { timeout: 120000 })
    .then(res => res.data.data);
}

export function repreviewCsv(id: string, options: CsvParseOptions) {
  return request
    .put<ApiResponse<CsvImport>>(`/csv-imports/${id}/preview`, options)
    .then(res => res.data.data);
}

export function confirmCsv(task: CsvImport) {
  return request
    .post<ApiResponse<CsvImport>>(`/csv-imports/${task.id}/confirm`, {
      displayName: task.displayName,
      description: task.description,
      columns: task.columns,
    })
    .then(res => res.data.data);
}

export function getCsvImports(page: number, pageSize: number) {
  return request
    .get<ApiResponse<CsvImportPage>>('/csv-imports', { params: { page, pageSize } })
    .then(res => res.data.data);
}

export function syncCsv(id: string) {
  return request.post(`/csv-imports/${id}/sync`);
}

export function deleteCsv(id: string) {
  return request.delete(`/csv-imports/${id}`);
}
