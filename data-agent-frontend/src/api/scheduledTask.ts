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

export type ScheduleType = 'DAILY' | 'INTERVAL' | 'CRON';

export interface ScheduledTaskRequest {
  name: string;
  prompt: string;
  scheduleType: ScheduleType;
  scheduleExpr: string;
  enabled?: boolean;
}

export interface ScheduledTaskResponse {
  id: number;
  name: string;
  prompt: string;
  scheduleType: ScheduleType;
  scheduleExpr: string;
  enabled: boolean;
  running?: boolean;
  nextRunAt?: string | null;
  lastStatus?: string | null;
  lastError?: string | null;
}

export function listScheduledTasks() {
  return request.get<ApiResponse<ScheduledTaskResponse[]>>('/scheduled-agent-tasks');
}

export function createScheduledTask(data: ScheduledTaskRequest) {
  return request.post<ApiResponse<void>>('/scheduled-agent-tasks', data);
}

export function updateScheduledTask(id: number, data: ScheduledTaskRequest) {
  return request.put<ApiResponse<void>>(`/scheduled-agent-tasks/${id}`, data);
}

export function deleteScheduledTask(id: number) {
  return request.delete<ApiResponse<void>>(`/scheduled-agent-tasks/${id}`);
}

export function setScheduledTaskEnabled(id: number, enabled: boolean) {
  return request.patch<ApiResponse<void>>(`/scheduled-agent-tasks/${id}/enabled`, { enabled });
}

export function runScheduledTask(id: number) {
  return request.post<ApiResponse<boolean>>(`/scheduled-agent-tasks/${id}/run`);
}
