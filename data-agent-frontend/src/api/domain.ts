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
 */

import request from './request';
import type { ApiResponse } from './request';

export interface PageResponse<T> {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  hasPrevious: boolean;
  hasNext: boolean;
  items: T[];
}

export interface DomainInfo {
  id: number;
  name: string;
  description: string | null;
  createTime: string;
  updateTime: string;
}

export interface DomainPageQuery {
  page?: number;
  pageSize?: number;
  keyword?: string;
  sortOrder?: 'asc' | 'desc';
}

export interface DomainCreateRequest {
  name: string;
  description?: string;
}

export interface DomainUpdateRequest {
  name: string;
  description?: string;
}

export function getDomainPage(query: DomainPageQuery) {
  return request.get<ApiResponse<PageResponse<DomainInfo>>>('/domains', { params: query });
}

export function getDomainById(id: number) {
  return request.get<ApiResponse<DomainInfo>>(`/domains/${id}`);
}

export function createDomain(data: DomainCreateRequest) {
  return request.post<ApiResponse<DomainInfo>>('/domains', data);
}

export function updateDomain(id: number, data: DomainUpdateRequest) {
  return request.put<ApiResponse<DomainInfo>>(`/domains/${id}`, data);
}

export function deleteDomain(id: number) {
  return request.delete<ApiResponse<boolean>>(`/domains/${id}`);
}
