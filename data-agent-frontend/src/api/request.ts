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

import axios from 'axios';
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { ElMessage } from 'element-plus';

interface ApiResponse<T = unknown> {
  code: number;
  data: T;
  message: string;
  requestId?: string;
}

export interface FieldValidationError {
  field: string;
  message: string;
}

export class ApiError extends Error {
  code?: number;
  details?: unknown;
  requestId?: string;

  constructor(
    message: string,
    options: { code?: number; details?: unknown; requestId?: string } = {},
  ) {
    super(message);
    this.name = 'ApiError';
    this.code = options.code;
    this.details = options.details;
    this.requestId = options.requestId;
    Object.setPrototypeOf(this, ApiError.prototype);
  }
}

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    return config;
  },
  error => {
    return Promise.reject(error);
  },
);

service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data;
    if (res.code !== 200) {
      const apiError = toApiError(res, '请求失败');
      ElMessage.error(apiError.message);
      return Promise.reject(apiError);
    }
    return response;
  },
  error => {
    const responseBody = error.response?.data as ApiResponse | undefined;
    const requestId = responseBody?.requestId || error.response?.headers?.['x-request-id'];
    const apiError = toApiError(responseBody, error.message || '网络错误', requestId);
    ElMessage.error(apiError.message);
    return Promise.reject(apiError);
  },
);

function toApiError(
  response: ApiResponse | undefined,
  fallbackMessage: string,
  fallbackRequestId?: string,
): ApiError {
  const details = response?.data;
  return new ApiError(resolveMessage(response, fallbackMessage), {
    code: response?.code,
    details,
    requestId: response?.requestId || fallbackRequestId,
  });
}

function resolveMessage(response: ApiResponse | undefined, fallbackMessage: string): string {
  if (isFieldValidationErrors(response?.data)) {
    return response.data.map(error => `${error.field}: ${error.message}`).join('\n');
  }
  return response?.message || fallbackMessage;
}

function isFieldValidationErrors(value: unknown): value is FieldValidationError[] {
  return (
    Array.isArray(value) &&
    value.every(
      item =>
        item !== null &&
        typeof item === 'object' &&
        typeof (item as FieldValidationError).field === 'string' &&
        typeof (item as FieldValidationError).message === 'string',
    )
  );
}

export default service;
export type { ApiResponse };
