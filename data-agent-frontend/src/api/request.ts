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

service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 直接读 localStorage 避免与 user store 循环依赖；store 写入时同步写 localStorage。
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  },
);

function clearAuthAndRedirectLogin() {
  localStorage.removeItem('token');
  localStorage.removeItem('userInfo');
  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
}

/** SSE/原生 fetch 链路复用：401 时清凭证并跳登录。 */
export function handleUnauthorized() {
  clearAuthAndRedirectLogin();
}

service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data;
    if (res.code !== 200) {
      const apiError = toApiError(res, '请求失败');
      showApiError(apiError);
      return Promise.reject(apiError);
    }
    return response;
  },
  error => {
    const status = error.response?.status;
    // 401：token 缺失/过期/用户禁用。在登录页之外 → 清凭证跳登录；在登录页 → 显示后端文案（如"用户名或密码错误"）。
    if (status === 401) {
      const responseBody = error.response?.data as ApiResponse | undefined;
      const apiError = toApiError(responseBody, '登录已过期，请重新登录');
      if (window.location.pathname !== '/login') {
        clearAuthAndRedirectLogin();
      } else {
        showApiError(apiError);
      }
      return Promise.reject(apiError);
    }
    const responseBody = error.response?.data as ApiResponse | undefined;
    const apiError = toApiError(responseBody, error.message || '网络错误');
    showApiError(apiError);
    return Promise.reject(apiError);
  },
);

function toApiError(response: ApiResponse | undefined, fallbackMessage: string): ApiError {
  const details = response?.data;
  return new ApiError(resolveMessage(response, fallbackMessage), {
    code: response?.code,
    errorCode: response?.errorCode,
    details,
  });
}

function resolveMessage(response: ApiResponse | undefined, fallbackMessage: string): string {
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
