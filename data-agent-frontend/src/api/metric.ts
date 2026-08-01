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

export interface MetricInfo {
  id: number;
  datasourceId: number;
  metricKey: string;
  name: string;
  aliases: string | null;
  measureExpr: string | null;
  filters: string | null;
  timeField: string | null;
  description: string | null;
  createTime: string;
  updateTime: string;
}

export interface MetricUpsertRequest {
  metricKey: string;
  name: string;
  aliases?: string;
  measureExpr?: string;
  filters?: string;
  timeField?: string;
  description?: string;
}

export function listMetrics() {
  return request.get<ApiResponse<MetricInfo[]>>('/metric');
}

export function createMetric(data: MetricUpsertRequest) {
  return request.post<ApiResponse<MetricInfo>>('/metric', data);
}

export function updateMetric(id: number, data: MetricUpsertRequest) {
  return request.put<ApiResponse<MetricInfo>>(`/metric/${id}`, data);
}

export function deleteMetric(id: number) {
  return request.delete<ApiResponse<boolean>>(`/metric/${id}`);
}
