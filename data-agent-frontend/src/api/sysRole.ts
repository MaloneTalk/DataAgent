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
 * limitations under the License.
 */

import request from './request';
import type { ApiResponse } from './request';

export interface RoleResponse {
  id: number;
  name: string;
  description: string;
  createTime: string;
}

export interface RoleRequest {
  name: string;
  description: string;
}

export interface TablePermissionResponse {
  datasourceId: number;
  tableNames: string[];
}

export interface SaveTablePermissionRequest {
  datasourceId: number;
  tableNames: string[];
}

export interface ColumnPermissionResponse {
  tableName: string;
  columnNames: string[];
}

export interface SaveColumnPermissionRequest {
  datasourceId: number;
  tableName: string;
  columnNames: string[];
}

export function listRoles() {
  return request.get<ApiResponse<RoleResponse[]>>('/sys/role').then(res => res.data.data);
}

export function createRole(payload: RoleRequest) {
  return request.post<ApiResponse<RoleResponse>>('/sys/role', payload).then(res => res.data.data);
}

export function updateRole(id: number, payload: RoleRequest) {
  return request
    .put<ApiResponse<RoleResponse>>(`/sys/role/${id}`, payload)
    .then(res => res.data.data);
}

export function deleteRole(id: number) {
  return request.delete<ApiResponse<boolean>>(`/sys/role/${id}`).then(res => res.data.data);
}

export function getPermissions(roleId: number) {
  return request
    .get<ApiResponse<TablePermissionResponse[]>>(`/sys/role/${roleId}/permissions`)
    .then(res => res.data.data);
}

export function savePermissions(roleId: number, payload: SaveTablePermissionRequest) {
  return request
    .put<ApiResponse<boolean>>(`/sys/role/${roleId}/permissions`, payload)
    .then(res => res.data.data);
}

export function getColumnPermissions(roleId: number, datasourceId: number) {
  return request
    .get<ApiResponse<ColumnPermissionResponse[]>>(`/sys/role/${roleId}/columns`, {
      params: { datasourceId },
    })
    .then(res => res.data.data);
}

export function saveColumnPermissions(roleId: number, payload: SaveColumnPermissionRequest) {
  return request
    .put<ApiResponse<boolean>>(`/sys/role/${roleId}/columns`, payload)
    .then(res => res.data.data);
}

/** 一次性获取数据源下所有表的列名，避免 N 次请求。 */
export function getAllTableColumns(datasourceId: number) {
  return request
    .get<ApiResponse<Record<string, string[]>>>(`/datasource/${datasourceId}/columns`)
    .then(res => res.data.data);
}
