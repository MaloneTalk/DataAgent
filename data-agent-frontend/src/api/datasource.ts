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

export interface DatasourceRequest {
  id?: number;
  name: string;
  type: string;
  host?: string;
  port?: number;
  databaseName?: string;
  username?: string;
  password?: string;
  connectionUrl?: string;
  description?: string;
}

export interface DatasourceResponse {
  id: number;
  name: string;
  type: string;
  host?: string;
  port?: number;
  databaseName?: string;
  username?: string;
  connectionUrl?: string;
  status?: string;
  testStatus?: string;
  description?: string;
}

export function getDatasourceList() {
  return request.get<ApiResponse<DatasourceResponse[]>>('/datasource').then(res => res.data.data);
}

export function getDatasourceTables(id: number) {
  return request.get<ApiResponse<string[]>>(`/datasource/${id}/tables`).then(res => res.data.data);
}

export function createDatasource(data: DatasourceRequest) {
  return request.post<ApiResponse<boolean>>('/datasource', data).then(res => res.data.data);
}

export function updateDatasource(data: DatasourceRequest) {
  return request
    .put<ApiResponse<boolean>>(`/datasource/${data.id}`, data)
    .then(res => res.data.data);
}

export function deleteDatasource(id: number) {
  return request.delete<ApiResponse<boolean>>(`/datasource/${id}`).then(res => res.data.data);
}

export function activateDatasource(id: number) {
  return request.put<ApiResponse<boolean>>(`/datasource/${id}/activate`).then(res => res.data.data);
}

export function deactivateDatasource(id: number) {
  return request
    .put<ApiResponse<boolean>>(`/datasource/${id}/deactivate`)
    .then(res => res.data.data);
}
