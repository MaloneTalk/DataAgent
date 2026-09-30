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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import request from './request';
import type { ApiResponse } from './request';
import type { BooleanVo, PageResponse } from './types';

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

export interface MetricCreateRequest {
  metricKey: string;
  name: string;
  aliases?: string;
  measureExpr?: string;
  filters?: string;
  timeField?: string;
  description?: string;
}

/** 更新不允许修改 metricKey。 */
export interface MetricUpdateRequest {
  name: string;
  aliases?: string;
  measureExpr?: string;
  filters?: string;
  timeField?: string;
  description?: string;
}

/** 对应后端 BaseBatchQueryDto。 */
export interface MetricQueryParams {
  page?: number;
  pageSize?: number;
  sortOrder?: 'asc' | 'desc';
}

export function listMetrics(params?: MetricQueryParams) {
  return request
    .get<ApiResponse<PageResponse<MetricInfo>>>('/metric', { params })
    .then(res => res.data.data);
}

export function createMetric(data: MetricCreateRequest) {
  return request.post<ApiResponse<MetricInfo>>('/metric', data).then(res => res.data.data);
}

export function updateMetric(id: number, data: MetricUpdateRequest) {
  return request.put<ApiResponse<MetricInfo>>(`/metric/${id}`, data).then(res => res.data.data);
}

export function deleteMetric(id: number) {
  return request
    .delete<ApiResponse<BooleanVo>>(`/metric/${id}`)
    .then(res => res.data.data);
}
