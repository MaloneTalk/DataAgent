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
  errorCode?: string;
  data: T;
  message: string;
}

export interface FieldValidationError {
  field: string;
  message: string;
}

export type FieldErrorMap = Record<string, string>;

export class ApiError extends Error {
  code?: number;
  errorCode?: string;
  details?: unknown;

  constructor(
    message: string,
    options: { code?: number; errorCode?: string; details?: unknown } = {},
  ) {
    super(message);
    this.name = 'ApiError';
    this.code = options.code;
    this.errorCode = options.errorCode;
    this.details = options.details;
    Object.setPrototypeOf(this, ApiError.prototype);
  }
}

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  // 直接读 localStorage 避免与 user store 循环依赖；store 写入时同步写 localStorage。
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** SSE/原生 fetch 链路复用：401 时清凭证并跳登录。 */
export function handleUnauthorized() {
  localStorage.removeItem('token');
  localStorage.removeItem('userInfo');
  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
}

service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    if (response.config.responseType === 'blob') {
      return response;
    }
    const res = response.data;
    if (res.code !== 200) {
      return Promise.reject(handleApiError(res, '请求失败'));
    }
    return response;
  },
  error => {
    return Promise.reject(
      handleApiError(error.response?.data, error.message || '网络错误', error.response?.status),
    );
  },
);

/** Axios 和 SSE 共用错误转换、权限变更通知及登录失效处理。 */
export function handleApiError(
  response: Partial<ApiResponse> | undefined,
  fallbackMessage: string,
  status?: number,
): ApiError {
  const responseStatus = status ?? response?.code;
  const message = responseStatus === 401 ? '登录已过期，请重新登录' : fallbackMessage;
  const error = new ApiError(resolveMessage(response, message), {
    code: response?.code ?? status,
    errorCode: response?.errorCode,
    details: response?.data,
  });
  if (responseStatus === 401 && window.location.pathname !== '/login') {
    handleUnauthorized();
  } else {
    if (responseStatus === 403) window.dispatchEvent(new Event('permissions-changed'));
    showApiError(error);
  }
  return error;
}

function resolveMessage(
  response: Partial<ApiResponse> | undefined,
  fallbackMessage: string,
): string {
  if (isFieldValidationErrors(response?.data)) {
    return response.data.map(error => `${error.field}: ${error.message}`).join('\n');
  }
  return response?.message || fallbackMessage;
}

function showApiError(error: ApiError) {
  if (!isFieldValidationErrors(error.details)) {
    ElMessage.error(error.message);
  }
}

export function getFieldErrorMap(error: unknown): FieldErrorMap {
  if (!(error instanceof ApiError) || !isFieldValidationErrors(error.details)) {
    return {};
  }
  return error.details.reduce<FieldErrorMap>((result, item) => {
    result[item.field] = item.message;
    return result;
  }, {});
}

export function isFieldValidationErrors(value: unknown): value is FieldValidationError[] {
  return (
    Array.isArray(value) &&
    value.length > 0 &&
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
