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

export interface ReportResponse {
  id: number;
  title: string;
  content: string;
  sessionId: string;
  createTime: string;
  updateTime: string;
}

export interface PageResponse<T> {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  hasPrevious: boolean;
  hasNext: boolean;
  items: T[];
}

export interface ReportPageQuery {
  sessionId?: string;
  page?: number;
  pageSize?: number;
  keyword?: string;
  sortOrder?: 'asc' | 'desc';
}

export function getReports(query: ReportPageQuery) {
  return request.get<{ code: number; message: string; data: PageResponse<ReportResponse> }>(
    '/reports',
    { params: query },
  );
}

export function deleteReport(id: number) {
  return request.delete<{ code: number; message: string; data: boolean }>(`/reports/${id}`);
}
