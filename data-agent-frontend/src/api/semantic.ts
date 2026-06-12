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
import { getDatasourceList } from './datasource';
import type { PageResponse } from './domain';

export interface TableSemanticInfo {
  id: number | null;
  tableName: string;
  domain: string;
  physicalTableDescription: string | null;
  tableDescription: string | null;
  isVisible: boolean;
  hasPhysicalTable: boolean;
  effective: boolean;
  invalidReason: string | null;
  updateTime: string | null;
}

export interface ColumnSemanticInfo {
  id: number | null;
  columnName: string;
  physicalColumnDescription: string | null;
  columnDescription: string | null;
  typeName: string | null;
  primaryKey: boolean | null;
  isVisible: boolean;
  hasPhysicalColumn: boolean;
  effective: boolean;
  invalidReason: string | null;
  updateTime: string | null;
}

export interface TableSemanticPageQuery {
  datasourceId: number;
  page?: number;
  pageSize?: number;
  keyword?: string;
  sortOrder?: 'asc' | 'desc';
}

export type ColumnSemanticPageQuery = TableSemanticPageQuery;

export interface TableSemanticUpdateRequest {
  datasourceId: number;
  tableName: string;
  domain?: string;
  tableDescription?: string;
  isVisible: boolean;
}

export interface ColumnSemanticUpdateRequest {
  datasourceId: number;
  columnName: string;
  columnDescription?: string;
  isVisible: boolean;
}

export async function getActiveDatasourceId() {
  const response = await getDatasourceList();
  return response.data.data.find(datasource => datasource.status === 'ACTIVE')?.id ?? null;
}

export function getTableSemanticPage(query: TableSemanticPageQuery) {
  return request.get<ApiResponse<PageResponse<TableSemanticInfo>>>('/semantic/tables', {
    params: query,
  });
}

export function getTableSemanticNames(datasourceId: number) {
  return request.get<ApiResponse<string[]>>('/semantic/tables/names', {
    params: { datasourceId },
  });
}

export function updateTableSemantic(data: TableSemanticUpdateRequest) {
  return request.put<ApiResponse<boolean>>('/semantic/tables', data);
}

export function resetTableSemantic(datasourceId: number, tableName: string) {
  return request.delete<ApiResponse<boolean>>('/semantic/tables', {
    params: { datasourceId, tableName },
  });
}

export function getColumnSemanticPage(tableName: string, query: ColumnSemanticPageQuery) {
  return request.get<ApiResponse<PageResponse<ColumnSemanticInfo>>>(
    `/semantic/tables/columns/${encodeURIComponent(tableName)}`,
    { params: query },
  );
}

export function updateColumnSemantic(tableName: string, data: ColumnSemanticUpdateRequest) {
  return request.put<ApiResponse<boolean>>(
    `/semantic/tables/columns/${encodeURIComponent(tableName)}`,
    data,
  );
}

export function resetColumnSemantic(datasourceId: number, tableName: string, columnName: string) {
  return request.delete<ApiResponse<boolean>>(
    `/semantic/tables/columns/${encodeURIComponent(tableName)}`,
    { params: { datasourceId, columnName } },
  );
}
