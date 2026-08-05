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
export type SessionMode = 'NEW_EACH_RUN' | 'FIXED_SESSION';

export interface ScheduledTaskRequest {
  name: string;
  prompt: string;
  scheduleType: ScheduleType;
  scheduleExpr: string;
  timezone?: string;
  enabled?: boolean;
  sessionMode?: SessionMode;
  sessionId?: string;
}

export interface ScheduledTaskResponse {
  id: number;
  name: string;
  prompt: string;
  scheduleType: ScheduleType;
  scheduleExpr: string;
  timezone: string;
  enabled: boolean;
  running: boolean;
  sessionMode: SessionMode;
  sessionId: string | null;
  nextRunAt: string;
  lastRunAt: string | null;
  lastStatus: string | null;
  lastError: string | null;
  createTime: string;
  updateTime: string;
}

export interface ScheduledTaskRunResponse {
  id: number;
  taskId: number;
  sessionId: string;
  status: string;
  reportId: number | null;
  outputSummary: string | null;
  errorMessage: string | null;
  startedAt: string;
  finishedAt: string | null;
}

export function listScheduledTasks() {
  return request.get<ApiResponse<ScheduledTaskResponse[]>>('/scheduled-agent-tasks');
}

export function createScheduledTask(data: ScheduledTaskRequest) {
  return request.post<ApiResponse<ScheduledTaskResponse>>('/scheduled-agent-tasks', data);
}

export function updateScheduledTask(id: number, data: ScheduledTaskRequest) {
  return request.put<ApiResponse<ScheduledTaskResponse>>(`/scheduled-agent-tasks/${id}`, data);
}

export function deleteScheduledTask(id: number) {
  return request.delete<ApiResponse<boolean>>(`/scheduled-agent-tasks/${id}`);
}

export function enableScheduledTask(id: number) {
  return request.post<ApiResponse<boolean>>(`/scheduled-agent-tasks/${id}/enable`);
}

export function disableScheduledTask(id: number) {
  return request.post<ApiResponse<boolean>>(`/scheduled-agent-tasks/${id}/disable`);
}

export function runScheduledTask(id: number) {
  return request.post<ApiResponse<boolean>>(`/scheduled-agent-tasks/${id}/run`);
}

export function listScheduledTaskRuns(id: number, limit = 20) {
  return request.get<ApiResponse<ScheduledTaskRunResponse[]>>(
    `/scheduled-agent-tasks/${id}/runs`,
    { params: { limit } },
  );
}
