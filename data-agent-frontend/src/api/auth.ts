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

export interface UserInfoResponse {
  userId: number;
  username: string;
  displayName: string;
}

export interface LoginResponse {
  token: string;
  user: UserInfoResponse;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

// 沿用项目约定：泛型为完整 ApiResponse 结构，调用处取 res.data.data。
type ApiResult<T> = { code: number; message: string; data: T };

export function login(payload: LoginRequest) {
  return request.post<ApiResult<LoginResponse>>('/auth/login', payload).then(res => res.data.data);
}

export function fetchMe() {
  return request.get<ApiResult<UserInfoResponse>>('/auth/me').then(res => res.data.data);
}

export function changePassword(payload: ChangePasswordRequest) {
  return request
    .post<ApiResult<boolean>>('/auth/change-password', payload)
    .then(res => res.data.data);
}
