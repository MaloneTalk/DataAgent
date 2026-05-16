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
  return request.get<{
    code: number;
    message: string;
    data: DatasourceResponse[];
  }>('/datasource');
}

export function createDatasource(data: DatasourceRequest) {
  return request.post<{ code: number; message: string; data: boolean }>('/datasource', data);
}

export function updateDatasource(data: DatasourceRequest) {
  return request.put<{ code: number; message: string; data: boolean }>(
    `/datasource/${data.id}`,
    data,
  );
}

export function deleteDatasource(id: number) {
  return request.delete<{ code: number; message: string; data: boolean }>(`/datasource/${id}`);
}

export function activateDatasource(id: number) {
  return request.put<{ code: number; message: string; data: boolean }>(
    `/datasource/${id}/activate`,
  );
}

export function deactivateDatasource(id: number) {
  return request.put<{ code: number; message: string; data: boolean }>(
    `/datasource/${id}/deactivate`,
  );
}
