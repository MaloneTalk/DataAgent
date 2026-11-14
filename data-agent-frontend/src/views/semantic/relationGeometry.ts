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

import type { RelationDraftPreview, TableNodeLayout } from './types';

export function resolveColumnAnchor(
  node: TableNodeLayout,
  columnName: string,
  side: 'left' | 'right',
) {
  const columnIndex = node.columns.findIndex(column => column.columnName === columnName);
  const safeIndex = columnIndex >= 0 ? columnIndex : 0;
  const y = node.y + 58 + safeIndex * 32 + 16;
  const x = side === 'right' ? node.x + node.width : node.x;
  return { x, y };
}

export function buildRelationPath(
  sourceX: number,
  sourceY: number,
  targetX: number,
  targetY: number,
) {
  const direction = targetX >= sourceX ? 1 : -1;
  const horizontalGap = Math.max(64, Math.min(160, Math.abs(targetX - sourceX) / 2));
  const elbowOutX = sourceX + horizontalGap * direction;
  const elbowInX = targetX - horizontalGap * direction;
  return `M ${sourceX} ${sourceY} L ${elbowOutX} ${sourceY} C ${elbowOutX + 24 * direction} ${sourceY}, ${
    elbowInX - 24 * direction
  } ${targetY}, ${elbowInX} ${targetY} L ${targetX} ${targetY}`;
}

export function getRelationGeometry(
  relation: RelationDraftPreview,
  nodeMap: Map<string, TableNodeLayout>,
) {
  const sourceNode = nodeMap.get(relation.sourceTableName);
  const targetNode = nodeMap.get(relation.targetTableName);
  if (!sourceNode || !targetNode) {
    return null;
  }

  const sourceSide = targetNode.x >= sourceNode.x ? 'right' : 'left';
  const targetSide = sourceSide === 'right' ? 'left' : 'right';
  const sourceAnchor = resolveColumnAnchor(sourceNode, relation.sourceColumnNames[0], sourceSide);
  const targetAnchor = resolveColumnAnchor(targetNode, relation.targetColumnNames[0], targetSide);

  return {
    path: buildRelationPath(sourceAnchor.x, sourceAnchor.y, targetAnchor.x, targetAnchor.y),
    labelX: (sourceAnchor.x + targetAnchor.x) / 2,
    labelY: (sourceAnchor.y + targetAnchor.y) / 2 - 10,
  };
}
