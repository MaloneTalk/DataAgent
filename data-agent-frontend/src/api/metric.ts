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

export const metricCalculationTypeLabels = {
  EXPRESSION: '普通表达式',
  DISTINCT_COUNT: '去重计数',
  RATIO: '分子分母计算',
  SNAPSHOT: '快照',
} as const;

export const metricValueTypeLabels = {
  NUMBER: '普通数值',
  MONEY: '金额',
  PERCENT: '百分比',
} as const;

export type MetricCalculationType = keyof typeof metricCalculationTypeLabels;
export type MetricValueType = keyof typeof metricValueTypeLabels;

export interface MetricInfo {
  id: number;
  datasourceId: number;
  metricKey: string;
  name: string;
  aliases: string | null;
  measureExpr: string | null;
  filters: string | null;
  timeField: string | null;
  calculationType: MetricCalculationType;
  valueType: MetricValueType | null;
  unit: string | null;
  /** 十进制字符串，避免 DECIMAL(24,12) 经 JavaScript number 丢失精度。 */
  resultMultiplier: string;
  currencyCode: string | null;
  description: string | null;
  createTime: string;
  updateTime: string;
}

export interface MetricCreateRequest extends MetricUpdateRequest {
  datasourceId: number;
  metricKey: string;
}

/** 更新不允许修改 metricKey。 */
export interface MetricUpdateRequest {
  name: string;
  aliases?: string;
  measureExpr?: string;
  filters?: string;
  timeField?: string;
  calculationType?: MetricCalculationType;
  valueType?: MetricValueType;
  unit?: string;
  resultMultiplier?: string;
  currencyCode?: string;
  description?: string;
}

/** 对应后端 MetricQueryDto。 */
export interface MetricQueryParams {
  datasourceId: number;
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
  return request.delete<ApiResponse<BooleanVo>>(`/metric/${id}`).then(res => res.data.data);
}
