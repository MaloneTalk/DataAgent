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

import request, { type ApiResponse } from './request';

export type ChartType = 'table' | 'metric' | 'bar';

export interface QueryResult {
  columns: string[];
  rows: Record<string, unknown>[];
  totalRows: number;
  truncated: boolean;
}

export interface DashboardCardResponse {
  id: number;
  title: string;
  datasourceId: number;
  sqlText: string;
  chartType: ChartType;
}

export interface DashboardCardCreateRequest {
  title: string;
  datasourceId: number;
  sqlText: string;
  chartType: ChartType;
}

export function getDashboardCards() {
  return request.get<ApiResponse<DashboardCardResponse[]>>('/dashboard-cards');
}

export function createDashboardCard(data: DashboardCardCreateRequest) {
  return request.post<ApiResponse<DashboardCardResponse>>('/dashboard-cards', data);
}

export function deleteDashboardCard(id: number) {
  return request.delete<ApiResponse<void>>(`/dashboard-cards/${id}`);
}

export function refreshDashboardCard(id: number) {
  return request.post<ApiResponse<QueryResult>>(`/dashboard-cards/${id}/refresh`);
}
