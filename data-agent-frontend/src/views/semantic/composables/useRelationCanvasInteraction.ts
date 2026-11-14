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

import { computed, ref, type Ref } from 'vue';
import { buildRelationPath, resolveColumnAnchor } from '../relationGeometry';
import type { RelationDragCreatePayload, RelationViewportState, TableNodeLayout } from '../types';

interface DragState {
  sourceTableName: string;
  sourceColumnName: string;
  startX: number;
  startY: number;
  currentX: number;
  currentY: number;
}

interface PanState {
  startClientX: number;
  startClientY: number;
  startOffsetX: number;
  startOffsetY: number;
}

interface NodeDragState {
  tableName: string;
  startClientX: number;
  startClientY: number;
  startNodeX: number;
  startNodeY: number;
}

interface PointerLikeEvent {
  button?: number;
  clientX: number;
  clientY: number;
  stopPropagation: () => void;
  preventDefault?: () => void;
  target: unknown;
}

interface CanvasInteractionOptions {
  localNodes: Ref<TableNodeLayout[]>;
  viewport: RelationViewportState;
  toLogicalCanvasPosition: (clientX: number, clientY: number) => { x: number; y: number };
  flushPersistLayout: () => void;
  onCreateRelation: (payload: RelationDragCreatePayload) => void;
}

export function useRelationCanvasInteraction(options: CanvasInteractionOptions) {
  const { localNodes, viewport, toLogicalCanvasPosition, flushPersistLayout, onCreateRelation } =
    options;
  const dragRelation = ref<DragState | null>(null);
  const hoveredDropColumn = ref<{ tableName: string; columnName: string } | null>(null);
  const selectedRelationId = ref<number | null>(null);
  const canvasPan = ref<PanState | null>(null);
  const nodeDrag = ref<NodeDragState | null>(null);

  const nodeMap = computed(() => {
    const map = new Map<string, TableNodeLayout>();
    localNodes.value.forEach(node => map.set(node.tableName, node));
    return map;
  });

  const dragPreview = computed(() => {
    if (!dragRelation.value) return null;
    return {
      path: buildRelationPath(
        dragRelation.value.startX,
        dragRelation.value.startY,
        dragRelation.value.currentX,
        dragRelation.value.currentY,
      ),
      labelX: (dragRelation.value.startX + dragRelation.value.currentX) / 2,
      labelY: (dragRelation.value.startY + dragRelation.value.currentY) / 2 - 10,
    };
  });

  function resolveDropColumnAtPoint(clientX: number, clientY: number) {
    const dropElement = globalThis.document
      .elementFromPoint(clientX, clientY)
      ?.closest?.('.relation-column-item') as {
      dataset?: { tableName?: string; columnName?: string; operable?: string };
    } | null;

    const tableName = dropElement?.dataset?.tableName;
    const columnName = dropElement?.dataset?.columnName;
    if (!tableName || !columnName || dropElement?.dataset?.operable !== 'true') return null;
    return { tableName, columnName };
  }

  function handleDragColumnStart(tableName: string, columnName: string, event: PointerLikeEvent) {
    event.stopPropagation();
    const node = nodeMap.value.get(tableName);
    if (!node) return;
    const column = node.columns.find(item => item.columnName === columnName);
    if (!node.operable || !column?.operable) return;

    const pointerPosition = toLogicalCanvasPosition(event.clientX, event.clientY);
    const sourceSide = pointerPosition.x >= node.x + node.width / 2 ? 'right' : 'left';
    const anchor = resolveColumnAnchor(node, columnName, sourceSide);
    dragRelation.value = {
      sourceTableName: tableName,
      sourceColumnName: columnName,
      startX: anchor.x,
      startY: anchor.y,
      currentX: pointerPosition.x,
      currentY: pointerPosition.y,
    };
  }

  function handleCanvasPointerMove(event: PointerLikeEvent) {
    if (nodeDrag.value) {
      const deltaX = (event.clientX - nodeDrag.value.startClientX) / viewport.scale;
      const deltaY = (event.clientY - nodeDrag.value.startClientY) / viewport.scale;
      localNodes.value = localNodes.value.map(node =>
        node.tableName === nodeDrag.value?.tableName
          ? {
              ...node,
              x: nodeDrag.value.startNodeX + deltaX,
              y: nodeDrag.value.startNodeY + deltaY,
            }
          : node,
      );
      return;
    }

    if (canvasPan.value) {
      viewport.offsetX =
        canvasPan.value.startOffsetX + (event.clientX - canvasPan.value.startClientX);
      viewport.offsetY =
        canvasPan.value.startOffsetY + (event.clientY - canvasPan.value.startClientY);
      return;
    }

    if (!dragRelation.value) return;
    const position = toLogicalCanvasPosition(event.clientX, event.clientY);
    dragRelation.value = { ...dragRelation.value, currentX: position.x, currentY: position.y };
  }

  function handleCanvasPointerUp(event: PointerLikeEvent) {
    if (nodeDrag.value) {
      nodeDrag.value = null;
      flushPersistLayout();
      return;
    }
    if (canvasPan.value) {
      canvasPan.value = null;
      flushPersistLayout();
      return;
    }
    if (!dragRelation.value) return;

    const dropTarget =
      resolveDropColumnAtPoint(event.clientX, event.clientY) ?? hoveredDropColumn.value;
    if (dropTarget) {
      onCreateRelation({
        sourceTableName: dragRelation.value.sourceTableName,
        sourceColumnName: dragRelation.value.sourceColumnName,
        targetTableName: dropTarget.tableName,
        targetColumnName: dropTarget.columnName,
      });
    }
    dragRelation.value = null;
    hoveredDropColumn.value = null;
  }

  function handleDropColumnEnter(tableName: string, columnName: string) {
    if (!dragRelation.value) return;
    const node = nodeMap.value.get(tableName);
    const column = node?.columns.find(item => item.columnName === columnName);
    if (!node?.operable || !column?.operable) return;
    hoveredDropColumn.value = { tableName, columnName };
  }

  function handleDropColumnLeave(tableName: string, columnName: string) {
    if (
      hoveredDropColumn.value?.tableName === tableName &&
      hoveredDropColumn.value?.columnName === columnName
    ) {
      hoveredDropColumn.value = null;
    }
  }

  function handleViewportPointerDown(event: PointerLikeEvent) {
    if (event.button && event.button !== 0) return;
    const target = event.target as globalThis.Element | null;
    if (
      target?.closest(
        '.relation-side-card, .el-dialog, .el-button, .el-switch, .relation-edge-hit, .canvas-controls',
      )
    )
      return;

    event.preventDefault?.();
    canvasPan.value = {
      startClientX: event.clientX,
      startClientY: event.clientY,
      startOffsetX: viewport.offsetX,
      startOffsetY: viewport.offsetY,
    };
  }

  function handleNodePointerDown(tableName: string, event: PointerLikeEvent) {
    const target = event.target as globalThis.Element | null;
    if (target?.closest('.relation-column-item, .el-button')) return;
    event.stopPropagation();
    event.preventDefault?.();
    const node = localNodes.value.find(item => item.tableName === tableName);
    if (!node) return;
    nodeDrag.value = {
      tableName,
      startClientX: event.clientX,
      startClientY: event.clientY,
      startNodeX: node.x,
      startNodeY: node.y,
    };
  }

  return {
    nodeMap,
    nodeDrag,
    dragRelation,
    hoveredDropColumn,
    dragPreview,
    selectRelation: (relationId: number) => {
      selectedRelationId.value = relationId;
    },
    isSelected: (relationId: number) => selectedRelationId.value === relationId,
    handleDragColumnStart,
    handleCanvasPointerMove,
    handleCanvasPointerUp,
    handleDropColumnEnter,
    handleDropColumnLeave,
    handleViewportPointerDown,
    handleNodePointerDown,
  };
}
