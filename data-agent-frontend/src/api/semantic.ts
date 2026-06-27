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

export interface RelationCandidateTableResponse {
  tableName: string;
  domain: string | null;
  description: string | null;
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

export interface RelationCandidateColumnResponse {
  columnName: string;
  description: string | null;
  typeName: string | null;
  primaryKey: boolean;
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
  keyword?: string;
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
  keyword?: string;
  enabled?: boolean;
  sortOrder?: 'asc' | 'desc';
}

export interface BindLogicalTableRelationRequest {
  datasourceId: number;
  sourceColumnNames: string[];
  targetTableName: string;
  targetColumnNames: string[];
  description: string;
  enabled: boolean;
}

export interface UpdateLogicalTableRelationRequest extends BindLogicalTableRelationRequest {
  relationId: number;
}

export interface UpdateLogicalTableRelationEnabledRequest {
  datasourceId: number;
  relationId: number;
  enabled: boolean;
}

export function getRelationCandidateTablePage(params: RelationCandidateTableQuery) {
  return request.get<{
    code: number;
    message: string;
    data: PageResponse<TableSemanticResponse>;
  }>('/semantic/tables', { params });
}

export function getRelationCandidateColumnPage(params: RelationCandidateColumnQuery) {
  const { tableName, ...query } = params;
  return request.get<{
    code: number;
    message: string;
    data: PageResponse<ColumnSemanticResponse>;
  }>(`/semantic/tables/columns/${encodeURIComponent(tableName)}`, {
    params: query,
  });
}

export function getLogicalRelationPage(params: LogicalRelationQuery) {
  const { tableName, ...query } = params;
  return request.get<{
    code: number;
    message: string;
    data: PageResponse<LogicalTableRelationResponse>;
  }>(`/semantic/tables/relations/${encodeURIComponent(tableName)}`, { params: query });
}

export function createLogicalRelation(tableName: string, data: BindLogicalTableRelationRequest) {
  return request.post<{ code: number; message: string; data: LogicalTableRelationResponse }>(
    `/semantic/tables/relations/${encodeURIComponent(tableName)}`,
    data,
  );
}

export function updateLogicalRelation(tableName: string, data: UpdateLogicalTableRelationRequest) {
  return request.put<{ code: number; message: string; data: LogicalTableRelationResponse }>(
    `/semantic/tables/relations/${encodeURIComponent(tableName)}`,
    data,
  );
}

export function updateLogicalRelationEnabled(
  tableName: string,
  data: UpdateLogicalTableRelationEnabledRequest,
) {
  return request.put<{ code: number; message: string; data: boolean }>(
    `/semantic/tables/relations/${encodeURIComponent(tableName)}/enabled`,
    data,
  );
}

export function deleteLogicalRelation(datasourceId: number, tableName: string, relationId: number) {
  return request.delete<{ code: number; message: string; data: boolean }>(
    `/semantic/tables/relations/${encodeURIComponent(tableName)}/${relationId}`,
    {
      params: { datasourceId },
    },
  );
}
