/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import request from './request';
import type { ApiResponse } from './request';
import type { AnswerProvenance } from './agent';
import type { PageResponse } from './types';

export interface DataEvidence {
  answerId: string;
  sessionId: string;
  evidence: AnswerProvenance;
}

export interface DataEvidencePageQuery {
  sessionId?: string;
  answerId?: string;
  page?: number;
  pageSize?: number;
}

export function getDataEvidence(query: DataEvidencePageQuery) {
  return request
    .get<ApiResponse<PageResponse<DataEvidence>>>('/agent/evidence', { params: query })
    .then(res => res.data.data);
}
