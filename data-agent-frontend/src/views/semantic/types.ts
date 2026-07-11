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

export interface RelationForm {
  sourceTableName: string;
  sourceColumnNames: string[];
  targetTableName: string;
  targetColumnNames: string[];
  description: string;
  enabled: boolean;
}

export interface RelationDraftPreview {
  sourceTableName: string;
  sourceColumnNames: string[];
  targetTableName: string;
  targetColumnNames: string[];
  enabled: boolean;
}

export interface RelationTableNode {
  tableName: string;
  domain: string | null;
  description: string | null;
  operable: boolean;
  invalidReason: string | null;
}

export interface RelationColumnNode {
  columnName: string;
  description: string | null;
  typeName: string | null;
  primaryKey: boolean | null;
  operable: boolean;
  invalidReason: string | null;
}

export interface TableNodeLayout extends RelationTableNode {
  x: number;
  y: number;
  width: number;
  height: number;
  columns: RelationColumnNode[];
}

export interface RelationViewportState {
  scale: number;
  offsetX: number;
  offsetY: number;
}

export interface RelationDragCreatePayload {
  sourceTableName: string;
  sourceColumnName: string;
  targetTableName: string;
  targetColumnName: string;
}

export interface SemanticRelationLayoutSnapshot {
  version?: number;
  nodes: Record<string, { x: number; y: number }>;
  viewport: RelationViewportState;
  canvasOrigin?: {
    minX: number;
    minY: number;
  };
  updatedAt: string;
}
