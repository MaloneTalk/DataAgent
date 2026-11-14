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
import type { BooleanVo } from './types';

export interface UserPat {
  id: number;
  name: string;
  tokenPrefix: string;
  expiresAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
  createTime: string;
}

export interface UserPatCreated {
  pat: UserPat;
  token: string;
}

export interface CreateUserPatRequest {
  name: string;
  expiresAt: string;
}

export function createUserPat(payload: CreateUserPatRequest) {
  return request
    .post<ApiResponse<UserPatCreated>>('/auth/pats', payload)
    .then(res => res.data.data);
}

export function getUserPats() {
  return request.get<ApiResponse<UserPat[]>>('/auth/pats').then(res => res.data.data);
}

export function revokeUserPat(id: number) {
  return request
    .delete<ApiResponse<BooleanVo>>(`/auth/pats/${id}`)
    .then(res => res.data.data.value);
}
