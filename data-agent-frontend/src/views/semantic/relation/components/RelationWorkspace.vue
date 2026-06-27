<!--
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
 -->

<script setup lang="ts">
  import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue';
  import type { LogicalTableRelationResponse } from '@/api/semantic';
  import type {
    RelationDraftPreview,
    RelationDragCreatePayload,
    RelationViewportState,
    SemanticRelationLayoutSnapshot,
    TableNodeLayout,
  } from '../types';

  interface RelationEdge {
    id: string;
    relationId: string;
    path: string;
    label: string;
    labelX: number;
    labelY: number;
    labelWidth: number;
    enabled: boolean;
    effective?: boolean;
  }

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

  const props = defineProps<{
    loading: boolean;
    nodeLoading: boolean;
    relationError: string;
    nodes: TableNodeLayout[];
    relations: LogicalTableRelationResponse[];
    draftRelation: RelationDraftPreview | null;
  }>();

  const emit = defineEmits<{
    (event: 'refresh'): void;
    (event: 'edit-relation', relation: LogicalTableRelationResponse): void;
    (event: 'delete-relation', relation: LogicalTableRelationResponse): void;
    (
      event: 'toggle-relation-enabled',
      relation: LogicalTableRelationResponse,
      value: boolean,
    ): void;
    (event: 'drag-create-relation', payload: RelationDragCreatePayload): void;
  }>();

  const RELATION_MIN_SCALE = 0.25;
  const RELATION_MAX_SCALE = 1.8;
  const RELATION_FIT_PADDING = 32;
  const RELATION_CANVAS_PADDING = 220;
  const RELATION_CANVAS_MIN_WIDTH = 1200;
  const RELATION_CANVAS_MIN_HEIGHT = 720;
  const RELATION_LAYOUT_STORAGE_VERSION = 2;
  const RELATION_LAYOUT_STORAGE_PREFIX = 'semantic-model:relation-layout';

  const viewportRef = ref<globalThis.HTMLElement | null>(null);
  const localNodes = ref<TableNodeLayout[]>([]);
  const dragRelation = ref<DragState | null>(null);
  const hoveredDropColumn = ref<{ tableName: string; columnName: string } | null>(null);
  const selectedRelationId = ref<string | null>(null);
  const canvasPan = ref<PanState | null>(null);
  const nodeDrag = ref<NodeDragState | null>(null);
  const viewport = reactive<RelationViewportState>({
    scale: 1,
    offsetX: 0,
    offsetY: 0,
  });
  let persistLayoutTimer: ReturnType<typeof globalThis.setTimeout> | null = null;

  const layoutStorageKey = computed(() => {
    const datasourceId = props.relations[0]?.datasourceId;
    return typeof datasourceId === 'number'
      ? `${RELATION_LAYOUT_STORAGE_PREFIX}:${datasourceId}`
      : '';
  });

  const canvasBounds = computed(() => {
    const maxX = localNodes.value.reduce((acc, node) => Math.max(acc, node.x + node.width), 0);
    const maxY = localNodes.value.reduce((acc, node) => Math.max(acc, node.y + node.height), 0);
    return {
      width: Math.max(RELATION_CANVAS_MIN_WIDTH, maxX + RELATION_CANVAS_PADDING),
      height: Math.max(RELATION_CANVAS_MIN_HEIGHT, maxY + RELATION_CANVAS_PADDING),
    };
  });

  const nodeMap = computed(() => {
    const map = new Map<string, TableNodeLayout>();
    localNodes.value.forEach(node => map.set(node.tableName, node));
    return map;
  });

  const relationEdges = computed<RelationEdge[]>(() =>
    props.relations.flatMap(relation => {
      const sourceNode = nodeMap.value.get(relation.sourceTableName);
      const targetNode = nodeMap.value.get(relation.targetTableName);
      if (!sourceNode || !targetNode) {
        return [];
      }

      const sourceSide = targetNode.x >= sourceNode.x ? 'right' : 'left';
      const targetSide = sourceSide === 'right' ? 'left' : 'right';
      const sourceAnchor = resolveColumnAnchor(
        sourceNode,
        relation.sourceColumnNames[0],
        sourceSide,
      );
      const targetAnchor = resolveColumnAnchor(
        targetNode,
        relation.targetColumnNames[0],
        targetSide,
      );
      const label = relation.sourceColumnNames.length > 1 ? '多列外键' : '外键';

      return [
        {
          id: `relation-${relation.relationKey}`,
          relationId: relation.relationKey,
          path: buildRelationPath(sourceAnchor.x, sourceAnchor.y, targetAnchor.x, targetAnchor.y),
          label,
          labelX: (sourceAnchor.x + targetAnchor.x) / 2,
          labelY: (sourceAnchor.y + targetAnchor.y) / 2 - 10,
          labelWidth: Math.max(96, label.length * 18 + 22),
          enabled: relation.enabled,
          effective: relation.effective,
        },
      ];
    }),
  );

  const draftEdge = computed(() => {
    if (!props.draftRelation) {
      return null;
    }

    const sourceNode = nodeMap.value.get(props.draftRelation.sourceTableName);
    const targetNode = nodeMap.value.get(props.draftRelation.targetTableName);
    if (!sourceNode || !targetNode) {
      return null;
    }

    const sourceSide = targetNode.x >= sourceNode.x ? 'right' : 'left';
    const targetSide = sourceSide === 'right' ? 'left' : 'right';
    const sourceAnchor = resolveColumnAnchor(
      sourceNode,
      props.draftRelation.sourceColumnNames[0],
      sourceSide,
    );
    const targetAnchor = resolveColumnAnchor(
      targetNode,
      props.draftRelation.targetColumnNames[0],
      targetSide,
    );

    return {
      path: buildRelationPath(sourceAnchor.x, sourceAnchor.y, targetAnchor.x, targetAnchor.y),
      labelX: (sourceAnchor.x + targetAnchor.x) / 2,
      labelY: (sourceAnchor.y + targetAnchor.y) / 2 - 10,
    };
  });

  const dragPreview = computed(() => {
    if (!dragRelation.value) {
      return null;
    }

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

  const zoomPercent = computed(() => Math.round(viewport.scale * 100));

  watch(
    () => props.nodes,
    async nodes => {
      if (!nodes.length) {
        localNodes.value = [];
        return;
      }
      const snapshot = readLayoutSnapshot();
      const savedNodes = snapshot?.nodes ?? {};
      localNodes.value = nodes.map(node => {
        const saved = savedNodes[node.tableName];
        return saved ? { ...node, x: saved.x, y: saved.y } : { ...node };
      });
      await nextTick();
      if (snapshot?.viewport) {
        viewport.scale = snapshot.viewport.scale;
        viewport.offsetX = snapshot.viewport.offsetX;
        viewport.offsetY = snapshot.viewport.offsetY;
      } else {
        fitCanvasToViewport();
      }
    },
    { immediate: true },
  );

  watch(
    localNodes,
    () => {
      schedulePersistLayout();
    },
    { deep: true },
  );

  watch(
    () => [viewport.scale, viewport.offsetX, viewport.offsetY, layoutStorageKey.value],
    () => {
      schedulePersistLayout();
    },
  );

  onBeforeUnmount(() => {
    flushPersistLayout();
  });

  function readLayoutSnapshot(): SemanticRelationLayoutSnapshot | null {
    if (!layoutStorageKey.value) {
      return null;
    }

    try {
      const raw = globalThis.localStorage.getItem(layoutStorageKey.value);
      if (!raw) {
        return null;
      }
      const snapshot = JSON.parse(raw) as SemanticRelationLayoutSnapshot;
      if (snapshot.version !== RELATION_LAYOUT_STORAGE_VERSION) {
        globalThis.localStorage.removeItem(layoutStorageKey.value);
        return null;
      }
      return snapshot;
    } catch {
      return null;
    }
  }

  function persistLayoutSnapshot() {
    if (!layoutStorageKey.value || !localNodes.value.length) {
      return;
    }

    const snapshot: SemanticRelationLayoutSnapshot = {
      version: RELATION_LAYOUT_STORAGE_VERSION,
      nodes: Object.fromEntries(
        localNodes.value.map(node => [node.tableName, { x: node.x, y: node.y }]),
      ),
      viewport: {
        scale: viewport.scale,
        offsetX: viewport.offsetX,
        offsetY: viewport.offsetY,
      },
      updatedAt: new Date().toISOString(),
    };

    globalThis.localStorage.setItem(layoutStorageKey.value, JSON.stringify(snapshot));
  }

  function schedulePersistLayout() {
    if (persistLayoutTimer) {
      globalThis.clearTimeout(persistLayoutTimer);
    }

    persistLayoutTimer = globalThis.setTimeout(() => {
      persistLayoutTimer = null;
      persistLayoutSnapshot();
    }, 220);
  }

  function flushPersistLayout() {
    if (persistLayoutTimer) {
      globalThis.clearTimeout(persistLayoutTimer);
      persistLayoutTimer = null;
    }
    persistLayoutSnapshot();
  }

  function resolveColumnAnchor(node: TableNodeLayout, columnName: string, side: 'left' | 'right') {
    const columnIndex = node.columns.findIndex(column => column.columnName === columnName);
    const safeIndex = columnIndex >= 0 ? columnIndex : 0;
    const y = node.y + 58 + safeIndex * 32 + 16;
    const x = side === 'right' ? node.x + node.width : node.x;
    return { x, y };
  }

  function buildRelationPath(sourceX: number, sourceY: number, targetX: number, targetY: number) {
    const direction = targetX >= sourceX ? 1 : -1;
    const horizontalGap = Math.max(64, Math.min(160, Math.abs(targetX - sourceX) / 2));
    const elbowOutX = sourceX + horizontalGap * direction;
    const elbowInX = targetX - horizontalGap * direction;
    return `M ${sourceX} ${sourceY} L ${elbowOutX} ${sourceY} C ${elbowOutX + 24 * direction} ${sourceY}, ${
      elbowInX - 24 * direction
    } ${targetY}, ${elbowInX} ${targetY} L ${targetX} ${targetY}`;
  }

  function toLogicalCanvasPosition(clientX: number, clientY: number) {
    const viewportElement = viewportRef.value;
    if (!viewportElement) {
      return { x: clientX, y: clientY };
    }

    const rect = viewportElement.getBoundingClientRect();
    return {
      x: (clientX - rect.left - viewport.offsetX) / viewport.scale,
      y: (clientY - rect.top - viewport.offsetY) / viewport.scale,
    };
  }

  function resolveDropColumnAtPoint(clientX: number, clientY: number) {
    const dropElement = globalThis.document
      .elementFromPoint(clientX, clientY)
      ?.closest?.('.relation-column-item') as {
      dataset?: { tableName?: string; columnName?: string };
    } | null;

    const tableName = dropElement?.dataset?.tableName;
    const columnName = dropElement?.dataset?.columnName;
    if (!tableName || !columnName) {
      return null;
    }

    return { tableName, columnName };
  }

  function fitCanvasToViewport() {
    const viewportElement = viewportRef.value;
    if (!viewportElement) {
      return;
    }

    const availableWidth = Math.max(240, viewportElement.clientWidth - RELATION_FIT_PADDING * 2);
    const availableHeight = Math.max(240, viewportElement.clientHeight - RELATION_FIT_PADDING * 2);
    const nextScale = Math.max(
      RELATION_MIN_SCALE,
      Math.min(
        RELATION_MAX_SCALE,
        1,
        Math.min(
          availableWidth / canvasBounds.value.width,
          availableHeight / canvasBounds.value.height,
        ),
      ),
    );

    viewport.scale = Number(nextScale.toFixed(3));
    viewport.offsetX =
      (viewportElement.clientWidth - canvasBounds.value.width * viewport.scale) / 2;
    viewport.offsetY =
      (viewportElement.clientHeight - canvasBounds.value.height * viewport.scale) / 2;
  }

  function resetViewport() {
    fitCanvasToViewport();
  }

  function zoomAtCenter(factor: number) {
    const viewportElement = viewportRef.value;
    if (!viewportElement) {
      return;
    }

    const centerX = viewportElement.clientWidth / 2;
    const centerY = viewportElement.clientHeight / 2;
    const worldX = (centerX - viewport.offsetX) / viewport.scale;
    const worldY = (centerY - viewport.offsetY) / viewport.scale;
    const nextScale = Math.max(
      RELATION_MIN_SCALE,
      Math.min(RELATION_MAX_SCALE, viewport.scale * factor),
    );

    viewport.offsetX = centerX - worldX * nextScale;
    viewport.offsetY = centerY - worldY * nextScale;
    viewport.scale = Number(nextScale.toFixed(3));
  }

  function zoomIn() {
    zoomAtCenter(1.2);
  }

  function zoomOut() {
    zoomAtCenter(1 / 1.2);
  }

  function selectRelation(relationId: string) {
    selectedRelationId.value = relationId;
  }

  function handleDragColumnStart(tableName: string, columnName: string, event: PointerLikeEvent) {
    event.stopPropagation();
    const node = nodeMap.value.get(tableName);
    if (!node) {
      return;
    }

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

    if (!dragRelation.value) {
      return;
    }

    const position = toLogicalCanvasPosition(event.clientX, event.clientY);
    dragRelation.value = {
      ...dragRelation.value,
      currentX: position.x,
      currentY: position.y,
    };
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

    if (!dragRelation.value) {
      return;
    }

    const dropTarget =
      resolveDropColumnAtPoint(event.clientX, event.clientY) ?? hoveredDropColumn.value;

    if (dropTarget) {
      emit('drag-create-relation', {
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
    if (!dragRelation.value) {
      return;
    }
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

  function handleViewportWheel(event: PointerLikeEvent & { deltaY: number }) {
    event.preventDefault?.();
    const viewportElement = viewportRef.value;
    if (!viewportElement) {
      return;
    }

    const rect = viewportElement.getBoundingClientRect();
    const pointerX = event.clientX - rect.left;
    const pointerY = event.clientY - rect.top;
    const worldX = (pointerX - viewport.offsetX) / viewport.scale;
    const worldY = (pointerY - viewport.offsetY) / viewport.scale;
    const nextScale =
      event.deltaY < 0
        ? Math.min(RELATION_MAX_SCALE, viewport.scale * 1.1)
        : Math.max(RELATION_MIN_SCALE, viewport.scale / 1.1);

    viewport.offsetX = pointerX - worldX * nextScale;
    viewport.offsetY = pointerY - worldY * nextScale;
    viewport.scale = Number(nextScale.toFixed(3));
  }

  function handleViewportPointerDown(event: PointerLikeEvent) {
    if (event.button && event.button !== 0) {
      return;
    }

    const target = event.target as globalThis.Element | null;
    if (
      target?.closest('.relation-side-card, .el-dialog, .el-button, .el-switch, .relation-edge-hit')
    ) {
      return;
    }

    canvasPan.value = {
      startClientX: event.clientX,
      startClientY: event.clientY,
      startOffsetX: viewport.offsetX,
      startOffsetY: viewport.offsetY,
    };
  }

  function handleNodePointerDown(tableName: string, event: PointerLikeEvent) {
    const target = event.target as globalThis.Element | null;
    if (target?.closest('.relation-column-item, .el-button')) {
      return;
    }

    event.stopPropagation();
    const node = localNodes.value.find(item => item.tableName === tableName);
    if (!node) {
      return;
    }

    nodeDrag.value = {
      tableName,
      startClientX: event.clientX,
      startClientY: event.clientY,
      startNodeX: node.x,
      startNodeY: node.y,
    };
  }

  function isSelected(relationId: string) {
    return selectedRelationId.value === relationId;
  }

  function relationStateTagType(relation: LogicalTableRelationResponse) {
    if (!relation.enabled) {
      return 'info';
    }
    return relation.effective ? 'success' : 'danger';
  }
</script>

<template>
  <section class="relation-panel">
    <div class="section-header">
      <div>
        <h3>逻辑外键 ER 图</h3>
        <p>滚轮缩放，空白区拖动画布，从字段拖到字段创建关系。</p>
      </div>
      <div class="relation-actions">
        <el-button @click="resetViewport">重置视图</el-button>
        <el-button :loading="loading || nodeLoading" @click="emit('refresh')">刷新关系图</el-button>
      </div>
    </div>

    <div class="relation-layout">
      <div
        ref="viewportRef"
        class="relation-canvas-wrap"
        @wheel="handleViewportWheel"
        @pointerdown="handleViewportPointerDown"
      >
        <div v-if="nodeLoading" class="canvas-empty">正在加载表结构...</div>
        <div
          v-else
          class="relation-canvas"
          :style="{
            width: `${canvasBounds.width}px`,
            height: `${canvasBounds.height}px`,
            transform: `translate(${viewport.offsetX}px, ${viewport.offsetY}px) scale(${viewport.scale})`,
          }"
          @pointermove="handleCanvasPointerMove"
          @pointerup="handleCanvasPointerUp"
          @pointerleave="handleCanvasPointerUp"
        >
          <svg
            class="relation-svg"
            :width="canvasBounds.width"
            :height="canvasBounds.height"
            :viewBox="`0 0 ${canvasBounds.width} ${canvasBounds.height}`"
            preserveAspectRatio="none"
          >
            <defs>
              <marker
                id="relation-arrow"
                markerWidth="10"
                markerHeight="10"
                refX="8"
                refY="3"
                orient="auto"
              >
                <path d="M0,0 L0,6 L9,3 z" fill="#374151" />
              </marker>
            </defs>

            <g v-for="edge in relationEdges" :key="edge.id">
              <path
                class="relation-edge-hit"
                :d="edge.path"
                stroke="transparent"
                stroke-width="16"
                fill="none"
                @click.stop="selectRelation(edge.relationId)"
              />
              <path
                :d="edge.path"
                :stroke="
                  isSelected(edge.relationId)
                    ? '#1f2937'
                    : edge.enabled
                      ? edge.effective
                        ? '#374151'
                        : '#dc2626'
                      : '#d1d5db'
                "
                :stroke-width="isSelected(edge.relationId) ? 3 : 2"
                fill="none"
                marker-end="url(#relation-arrow)"
                stroke-linecap="round"
              />
              <rect
                :x="edge.labelX - edge.labelWidth / 2"
                :y="edge.labelY - 16"
                :width="edge.labelWidth"
                height="26"
                rx="13"
                :fill="isSelected(edge.relationId) ? '#f3f4f6' : '#ffffff'"
                :stroke="isSelected(edge.relationId) ? '#374151' : '#e5e7eb'"
                @click.stop="selectRelation(edge.relationId)"
              />
              <text :x="edge.labelX" :y="edge.labelY + 2" text-anchor="middle" class="edge-label">
                {{ edge.label }}
              </text>
            </g>

            <template v-if="draftEdge">
              <path
                :d="draftEdge.path"
                stroke="#f59e0b"
                stroke-width="3"
                fill="none"
                stroke-dasharray="8 6"
              />
              <text
                :x="draftEdge.labelX"
                :y="draftEdge.labelY"
                text-anchor="middle"
                class="edge-label draft-label"
              >
                草稿关系
              </text>
            </template>

            <template v-if="dragPreview">
              <path
                :d="dragPreview.path"
                stroke="#0ea5e9"
                stroke-width="3"
                fill="none"
                stroke-dasharray="10 8"
              />
              <text
                :x="dragPreview.labelX"
                :y="dragPreview.labelY"
                text-anchor="middle"
                class="edge-label drag-label"
              >
                拖拽创建
              </text>
            </template>
          </svg>

          <article
            v-for="node in localNodes"
            :key="node.tableName"
            class="relation-node"
            :class="{ 'is-node-dragging': nodeDrag?.tableName === node.tableName }"
            :style="{ left: `${node.x}px`, top: `${node.y}px`, width: `${node.width}px` }"
            @pointerdown="handleNodePointerDown(node.tableName, $event)"
          >
            <header class="relation-node-header">
              <div>
                <h4>{{ node.tableName }}</h4>
                <p>{{ node.domain || '未设置业务域' }}</p>
              </div>
            </header>
            <p class="relation-node-desc">{{ node.description || '暂无语义描述' }}</p>
            <ul class="relation-node-columns">
              <li
                v-for="column in node.columns"
                :key="column.columnName"
                class="relation-column-item"
                :data-table-name="node.tableName"
                :data-column-name="column.columnName"
                :class="{
                  'is-drag-source':
                    dragRelation?.sourceTableName === node.tableName &&
                    dragRelation?.sourceColumnName === column.columnName,
                  'is-drag-target':
                    hoveredDropColumn?.tableName === node.tableName &&
                    hoveredDropColumn?.columnName === column.columnName,
                }"
                @pointerdown="handleDragColumnStart(node.tableName, column.columnName, $event)"
                @pointerenter="handleDropColumnEnter(node.tableName, column.columnName)"
                @pointerleave="handleDropColumnLeave(node.tableName, column.columnName)"
              >
                <span class="column-name">{{ column.columnName }}</span>
                <span class="column-type">{{ column.typeName || 'UNKNOWN' }}</span>
              </li>
            </ul>
          </article>
        </div>

        <div class="canvas-controls">
          <button class="canvas-ctrl-btn" title="缩小" @click.stop="zoomOut">-</button>
          <span class="canvas-ctrl-label">{{ zoomPercent }}%</span>
          <button class="canvas-ctrl-btn" title="放大" @click.stop="zoomIn">+</button>
          <button
            class="canvas-ctrl-btn canvas-ctrl-fit"
            title="适应画布"
            @click.stop="resetViewport"
          >
            []
          </button>
        </div>
      </div>

      <aside class="relation-side">
        <div class="relation-side-card">
          <h4>已记录关系</h4>
          <div v-if="relationError" class="error-tip">{{ relationError }}</div>
          <div v-if="!relations.length && !loading" class="canvas-empty">
            当前数据源还没有逻辑外键
          </div>
          <article
            v-for="relation in relations"
            :key="relation.relationKey"
            class="relation-list-item"
            :class="{ 'is-selected': isSelected(relation.relationKey) }"
            @click="selectRelation(relation.relationKey)"
          >
            <div class="relation-list-head">
              <div class="relation-name">
                <strong>{{ relation.sourceTableName }}</strong>
                <span>→</span>
                <strong>{{ relation.targetTableName }}</strong>
              </div>
              <el-tag :type="relationStateTagType(relation)">
                {{ !relation.enabled ? '已禁用' : relation.effective ? '生效中' : '失效' }}
              </el-tag>
            </div>
            <div class="relation-columns-line">
              {{ relation.sourceColumnNames.join(', ') }} ->
              {{ relation.targetColumnNames.join(', ') }}
            </div>
            <div class="relation-description">{{ relation.description || '无备注' }}</div>
            <div v-if="relation.invalidReason" class="relation-invalid">
              {{ relation.invalidReason }}
            </div>
            <div class="relation-list-actions">
              <el-switch
                :model-value="relation.enabled"
                :disabled="relation.source === 'physical'"
                inline-prompt
                active-text="开"
                inactive-text="关"
                @click.stop
                @change="
                  (value: string | number | boolean) =>
                    emit('toggle-relation-enabled', relation, Boolean(value))
                "
              />
              <div class="relation-action-buttons">
                <el-button
                  link
                  type="primary"
                  :disabled="relation.source === 'physical'"
                  @click.stop="emit('edit-relation', relation)"
                >
                  编辑
                </el-button>
                <el-button
                  link
                  type="danger"
                  :disabled="relation.source === 'physical'"
                  @click.stop="emit('delete-relation', relation)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </article>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
  .relation-panel {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .section-header {
    display: flex;
    justify-content: space-between;
    gap: 16px;
    align-items: flex-start;
  }

  .section-header h3 {
    margin: 0 0 4px;
    color: #1f2937;
    font-size: 16px;
    font-weight: 600;
  }

  .section-header p {
    margin: 0;
    color: #64748b;
  }

  .relation-actions {
    display: flex;
    gap: 12px;
  }

  .relation-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 360px;
    gap: 16px;
  }

  .relation-canvas-wrap {
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    background:
      linear-gradient(rgba(0, 0, 0, 0.03) 1px, transparent 1px),
      linear-gradient(90deg, rgba(0, 0, 0, 0.03) 1px, transparent 1px), #fafafa;
    background-size: 24px 24px;
    overflow: hidden;
    min-height: 720px;
    position: relative;
    cursor: grab;
  }

  .relation-canvas {
    position: absolute;
    top: 0;
    left: 0;
    transform-origin: 0 0;
    will-change: transform;
  }

  .relation-svg {
    position: absolute;
    inset: 0;
    overflow: visible;
  }

  .relation-edge-hit {
    cursor: pointer;
  }

  .edge-label {
    font-size: 11px;
    fill: #6b7280;
    font-weight: 500;
  }

  .draft-label {
    fill: #92400e;
  }

  .drag-label {
    fill: #0f172a;
  }

  .relation-node {
    position: absolute;
    padding: 14px;
    border-radius: 10px;
    border: 1px solid #e5e7eb;
    background: #ffffff;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  }

  .relation-node.is-node-dragging {
    border-color: #6b7280;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
    cursor: grabbing;
  }

  .relation-node-header h4 {
    margin: 0 0 2px;
    color: #1f2937;
    font-size: 14px;
    font-weight: 600;
  }

  .relation-node-header p {
    margin: 0;
    color: #9ca3af;
    font-size: 12px;
  }

  .relation-node-desc {
    margin: 10px 0;
    color: #6b7280;
    font-size: 12px;
    line-height: 1.5;
    min-height: 32px;
  }

  .relation-node-columns {
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 4px;
    margin: 0;
    padding: 0;
  }

  .relation-column-item {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    padding: 6px 8px;
    border-radius: 6px;
    background: #f9fafb;
    border: 1px solid transparent;
    cursor: crosshair;
    font-size: 12px;
  }

  .relation-column-item.is-drag-source {
    border-color: #6b7280;
    background: #f3f4f6;
  }

  .relation-column-item.is-drag-target {
    border-color: #059669;
    background: #ecfdf5;
  }

  .column-name {
    font-weight: 500;
  }

  .column-type {
    color: #9ca3af;
    font-size: 11px;
  }

  .relation-side-card {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 16px;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    background: #ffffff;
  }

  .relation-side-card h4 {
    margin: 0;
    color: #1f2937;
    font-size: 15px;
    font-weight: 600;
  }

  .relation-list-item {
    border: 1px solid #e5e7eb;
    border-radius: 10px;
    padding: 12px;
    background: #fafafa;
    cursor: pointer;
  }

  .relation-list-item.is-selected {
    border-color: #374151;
    background: #f9fafb;
  }

  .relation-list-head {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    align-items: center;
    margin-bottom: 8px;
  }

  .relation-name {
    color: #1f2937;
  }

  .relation-columns-line {
    color: #1e293b;
    margin-bottom: 6px;
    line-height: 1.5;
  }

  .relation-description {
    color: #64748b;
    margin-bottom: 6px;
  }

  .relation-invalid,
  .error-tip {
    color: #dc2626;
  }

  .relation-list-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .relation-action-buttons {
    display: flex;
    gap: 8px;
  }

  .canvas-empty {
    min-height: 240px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #9ca3af;
    font-size: 13px;
  }

  .canvas-controls {
    position: absolute;
    bottom: 12px;
    right: 12px;
    display: flex;
    align-items: center;
    gap: 4px;
    background: #ffffff;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    padding: 4px 6px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
    z-index: 10;
  }

  .canvas-ctrl-btn {
    width: 28px;
    height: 28px;
    border: none;
    background: transparent;
    border-radius: 6px;
    font-size: 14px;
    color: #374151;
    cursor: pointer;
  }

  .canvas-ctrl-btn:hover {
    background: #f3f4f6;
  }

  .canvas-ctrl-label {
    min-width: 36px;
    text-align: center;
    color: #6b7280;
    font-size: 12px;
  }

  .canvas-ctrl-fit {
    margin-left: 4px;
    border-left: 1px solid #e5e7eb;
    padding-left: 4px;
  }

  @media (max-width: 1280px) {
    .relation-layout {
      grid-template-columns: 1fr;
    }
  }
</style>
