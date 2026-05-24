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

export interface PageResponse<T> {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  hasPrevious: boolean;
  hasNext: boolean;
  items: T[];
}

export interface TableSemanticResponse {
  id: number | null;
  tableName: string;
  domain: string | null;
  physicalTableDescription: string | null;
  tableDescription: string | null;
  isVisible: boolean;
  hasPhysicalTable: boolean;
  effective: boolean;
  invalidReason: string | null;
  updateTime: string | null;
}

export interface ColumnSemanticResponse {
  id: number | null;
  columnName: string;
  physicalColumnDescription: string | null;
  columnDescription: string | null;
  typeName: string | null;
  primaryKey: boolean;
  isVisible: boolean;
  hasPhysicalColumn: boolean;
  effective: boolean;
  invalidReason: string | null;
  updateTime: string | null;
}

export interface TableSemanticQuery {
  datasourceId?: number;
  page?: number;
  pageSize?: number;
  keywordPrefix?: string;
  sortOrder?: 'asc' | 'desc';
}

export interface ColumnSemanticQuery extends TableSemanticQuery {
  datasourceId: number;
  tableName: string;
}

export interface TableSemanticUpdateRequest {
  datasourceId: number;
  tableName: string;
  domain: string | null;
  tableDescription: string | null;
  isVisible: boolean;
}

export interface ColumnSemanticUpdateRequest {
  columnName: string;
  columnDescription: string | null;
  isVisible: boolean;
}

export interface BatchResetTableSemanticRequest {
  datasourceId: number;
  tableNames: string[];
}

export interface BatchResetColumnSemanticRequest {
  datasourceId: number;
  columnNames: string[];
}

export interface RelationCandidateTableResponse {
  tableName: string;
  domain: string | null;
  description: string | null;
}

export interface RelationCandidateColumnResponse {
  columnName: string;
  description: string | null;
  typeName: string | null;
  primaryKey: boolean;
}

export interface LogicalTableRelationResponse {
  id: number | null;
  relationKey: string;
  datasourceId: number;
  source: 'physical' | 'logical';
  sourceTableName: string;
  sourceColumnNames: string[];
  targetTableName: string;
  targetColumnNames: string[];
  relationType: string;
  description: string | null;
  enabled: boolean;
  effective: boolean;
  invalidReason: string | null;
  createTime: string | null;
  updateTime: string | null;
}

export interface RelationCandidateTableQuery {
  datasourceId: number;
  page?: number;
  pageSize?: number;
  keywordPrefix?: string;
  sortOrder?: 'asc' | 'desc';
}

export interface RelationCandidateColumnQuery extends RelationCandidateTableQuery {
  tableName: string;
}

export interface LogicalRelationQuery {
  datasourceId: number;
  tableName: string;
  page?: number;
  pageSize?: number;
  keywordPrefix?: string;
  enabled?: boolean;
  sortOrder?: 'asc' | 'desc';
}

export interface BindLogicalTableRelationRequest {
  sourceColumnNames: string[];
  targetTableName: string;
  targetColumnNames: string[];
  description: string;
  enabled: boolean;
}

export type UpdateLogicalTableRelationRequest = BindLogicalTableRelationRequest;

export interface UpdateLogicalTableRelationEnabledRequest {
  enabled: boolean;
}

export interface BatchDeleteLogicalTableRelationRequest {
  datasourceId: number;
  relationIds: number[];
}

export function getTableSemanticPage(params: TableSemanticQuery) {
  return request.get<{
    code: number;
    message: string;
    data: PageResponse<TableSemanticResponse>;
  }>('/tableinfo/semantic/tables', { params });
}

export function updateTableSemantic(data: TableSemanticUpdateRequest) {
  return request.put<{ code: number; message: string; data: boolean }>(
    '/tableinfo/semantic/tables',
    data,
  );
}

export function resetTableSemantic(datasourceId: number, tableName: string) {
  return request.delete<{ code: number; message: string; data: boolean }>(
    '/tableinfo/semantic/tables',
    {
      params: { datasourceId, tableName },
    },
  );
}

export function resetTableSemantics(data: BatchResetTableSemanticRequest) {
  return request.delete<{ code: number; message: string; data: number }>(
    '/tableinfo/semantic/tables/batch',
    {
      data,
    },
  );
}

export function getColumnSemanticPage(params: ColumnSemanticQuery) {
  const { tableName, ...query } = params;
  return request.get<{
    code: number;
    message: string;
    data: PageResponse<ColumnSemanticResponse>;
  }>(`/tableinfo/${encodeURIComponent(tableName)}/semantic/columns`, { params: query });
}

export function updateColumnSemantic(
  datasourceId: number,
  tableName: string,
  data: ColumnSemanticUpdateRequest,
) {
  return request.put<{ code: number; message: string; data: boolean }>(
    `/tableinfo/${encodeURIComponent(tableName)}/semantic/columns`,
    data,
    { params: { datasourceId } },
  );
}

export function resetColumnSemantic(datasourceId: number, tableName: string, columnName: string) {
  return request.delete<{ code: number; message: string; data: boolean }>(
    `/tableinfo/${encodeURIComponent(tableName)}/semantic/columns`,
    {
      params: { datasourceId, columnName },
    },
  );
}

export function resetColumnSemantics(
  datasourceId: number,
  tableName: string,
  data: BatchResetColumnSemanticRequest,
) {
  return request.delete<{ code: number; message: string; data: number }>(
    `/tableinfo/${encodeURIComponent(tableName)}/semantic/columns/batch`,
    {
      data: {
        datasourceId,
        columnNames: data.columnNames,
      },
    },
  );
}

export function getRelationCandidateTablePage(params: RelationCandidateTableQuery) {
  return request.get<{
    code: number;
    message: string;
    data: PageResponse<RelationCandidateTableResponse>;
  }>('/tableinfo/semantic/relations/candidate/tables', { params });
}

export function getRelationCandidateColumnPage(params: RelationCandidateColumnQuery) {
  const { tableName, ...query } = params;
  return request.get<{
    code: number;
    message: string;
    data: PageResponse<RelationCandidateColumnResponse>;
  }>(`/tableinfo/semantic/relations/candidate/${encodeURIComponent(tableName)}/columns`, {
    params: query,
  });
}

export function getLogicalRelationPage(params: LogicalRelationQuery) {
  const { tableName, ...query } = params;
  return request.get<{
    code: number;
    message: string;
    data: PageResponse<LogicalTableRelationResponse>;
  }>(`/tableinfo/semantic/relations/${encodeURIComponent(tableName)}`, { params: query });
}

export function createLogicalRelation(
  datasourceId: number,
  tableName: string,
  data: BindLogicalTableRelationRequest,
) {
  return request.post<{ code: number; message: string; data: LogicalTableRelationResponse }>(
    `/tableinfo/semantic/relations/${encodeURIComponent(tableName)}`,
    data,
    { params: { datasourceId } },
  );
}

export function updateLogicalRelation(
  datasourceId: number,
  tableName: string,
  relationId: number,
  data: UpdateLogicalTableRelationRequest,
) {
  return request.put<{ code: number; message: string; data: LogicalTableRelationResponse }>(
    `/tableinfo/semantic/relations/${encodeURIComponent(tableName)}/${relationId}`,
    data,
    { params: { datasourceId } },
  );
}

export function updateLogicalRelationEnabled(
  datasourceId: number,
  tableName: string,
  relationId: number,
  data: UpdateLogicalTableRelationEnabledRequest,
) {
  return request.put<{ code: number; message: string; data: boolean }>(
    `/tableinfo/semantic/relations/${encodeURIComponent(tableName)}/${relationId}/enabled`,
    data,
    { params: { datasourceId } },
  );
}

export function deleteLogicalRelation(datasourceId: number, tableName: string, relationId: number) {
  return request.delete<{ code: number; message: string; data: boolean }>(
    `/tableinfo/semantic/relations/${encodeURIComponent(tableName)}/${relationId}`,
    {
      params: { datasourceId },
    },
  );
}

export function deleteLogicalRelations(
  tableName: string,
  data: BatchDeleteLogicalTableRelationRequest,
) {
  return request.delete<{ code: number; message: string; data: number }>(
    `/tableinfo/semantic/relations/${encodeURIComponent(tableName)}/batch`,
    {
      data,
    },
  );
}
