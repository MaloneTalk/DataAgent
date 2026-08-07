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

export interface UserResponse {
  id: number;
  username: string;
  displayName: string;
  roleId: number; // 1=管理员 0=普通用户
  status: number; // 1=启用 0=禁用
  createTime: string;
}

export interface UserCreateRequest {
  username: string;
  password: string;
  displayName: string;
  roleId: number;
}

export interface UserUpdateRequest {
  displayName: string;
  roleId: number | null;
}

type ApiResult<T> = { code: number; message: string; data: T };

export function listUsers() {
  return request.get<ApiResult<UserResponse[]>>('/sys/user').then(res => res.data.data);
}

export function createUser(payload: UserCreateRequest) {
  return request.post<ApiResult<UserResponse>>('/sys/user', payload).then(res => res.data.data);
}

export function updateUser(id: number, payload: UserUpdateRequest) {
  return request
    .put<ApiResult<UserResponse>>(`/sys/user/${id}`, payload)
    .then(res => res.data.data);
}

export function resetPassword(id: number, newPassword: string) {
  return request
    .put<ApiResult<boolean>>(`/sys/user/${id}/password`, { newPassword })
    .then(res => res.data.data);
}

export function updateStatus(id: number, status: number) {
  return request
    .put<ApiResult<boolean>>(`/sys/user/${id}/status`, null, {
      params: { status },
    })
    .then(res => res.data.data);
}
