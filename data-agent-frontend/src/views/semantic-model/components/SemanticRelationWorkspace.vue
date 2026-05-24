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
    sourceX: number;
    sourceY: number;
    targetX: number;
    targetY: number;
    path: string;
    labelX: number;
    labelY: number;
    sourceTableName: string;
    targetTableName: string;
    sourceColumnNames: string[];
    targetColumnNames: string[];
    label: string;
    labelWidth: number;
    enabled: boolean;
    source: 'physical' | 'logical';
    effective?: boolean;
    invalidReason?: string | null;
  }

  interface DragRelationState {
    sourceTableName: string;
    sourceColumnName: string;
    startX: number;
    startY: number;
    currentX: number;
    currentY: number;
  }

  interface RelationCanvasPanState {
    startClientX: number;
    startClientY: number;
    startOffsetX: number;
    startOffsetY: number;
  }

  interface RelationNodeDragState {
    tableName: string;
    startClientX: number;
    startClientY: number;
    startNodeX: number;
    startNodeY: number;
  }

  interface SelectRelationOptions {
    scrollList?: boolean;
    focusCanvas?: boolean;
  }

  interface RelationPointerLikeEvent {
    button?: number;
    clientX: number;
    clientY: number;
    currentTarget: unknown;
    target: unknown;
    stopPropagation: () => void;
  }

  interface RelationWheelLikeEvent {
    clientX: number;
    clientY: number;
    deltaY: number;
    preventDefault: () => void;
  }

  interface ClosestCapableTarget {
    closest?: (selector: string) => unknown;
  }

  const props = defineProps<{
    datasourceId?: number;
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

  const RELATION_CANVAS_PADDING = 220;
  const RELATION_CANVAS_MIN_WIDTH = 1200;
  const RELATION_CANVAS_MIN_HEIGHT = 720;
  const RELATION_FIT_PADDING = 32;
  const RELATION_MIN_SCALE = 0.25;
  const RELATION_MAX_SCALE = 1.8;
  const RELATION_LAYOUT_STORAGE_VERSION = 2;
  const RELATION_LAYOUT_STORAGE_PREFIX = 'semantic-model:relation-layout';
  const RELATION_LIST_PAGE_SIZE = 6;

  const viewportRef = ref<{
    clientWidth: number;
    clientHeight: number;
    getBoundingClientRect: () => { left: number; top: number; width: number; height: number };
  } | null>(null);
  const viewport = reactive<RelationViewportState>({
    scale: 1,
    offsetX: 0,
    offsetY: 0,
  });
  const canvasOrigin = reactive({
    minX: 0,
    minY: 0,
  });
  const localNodes = ref<TableNodeLayout[]>([]);
  const dragRelation = ref<DragRelationState | null>(null);
  const hoveredDropColumn = ref<{ tableName: string; columnName: string } | null>(null);
  const canvasPan = ref<RelationCanvasPanState | null>(null);
  const nodeDrag = ref<RelationNodeDragState | null>(null);
  const selectedRelationId = ref<string | null>(null);
  const relationListRef = ref<{
    querySelector: (
      selector: string,
    ) => { scrollIntoView?: (options?: { block?: string; behavior?: string }) => void } | null;
  } | null>(null);
  const relationListPage = ref(1);
  let persistLayoutTimer: number | null = null;
  let autoFitFrame: number | null = null;

  const layoutStorageKey = computed(() =>
    typeof props.datasourceId === 'number'
      ? `${RELATION_LAYOUT_STORAGE_PREFIX}:${props.datasourceId}`
      : '',
  );

  const canvasBounds = computed(() => {
    const nodeExtents = resolveNodeExtents(localNodes.value);
    if (!nodeExtents) {
      return {
        minX: 0,
        minY: 0,
        offsetX: RELATION_CANVAS_PADDING,
        offsetY: RELATION_CANVAS_PADDING,
        width: RELATION_CANVAS_MIN_WIDTH,
        height: RELATION_CANVAS_MIN_HEIGHT,
      };
    }

    const minX = Math.min(canvasOrigin.minX, nodeExtents.minX);
    const minY = Math.min(canvasOrigin.minY, nodeExtents.minY);
    const maxX = nodeExtents.maxX;
    const maxY = nodeExtents.maxY;

    return {
      minX,
      minY,
      offsetX: RELATION_CANVAS_PADDING - minX,
      offsetY: RELATION_CANVAS_PADDING - minY,
      width: Math.max(RELATION_CANVAS_MIN_WIDTH, maxX - minX + RELATION_CANVAS_PADDING * 2),
      height: Math.max(RELATION_CANVAS_MIN_HEIGHT, maxY - minY + RELATION_CANVAS_PADDING * 2),
    };
  });

  const renderedNodeMap = computed(() => {
    const map = new Map<string, TableNodeLayout>();
    localNodes.value.forEach(node => {
      map.set(node.tableName, {
        ...node,
        x: node.x + canvasBounds.value.offsetX,
        y: node.y + canvasBounds.value.offsetY,
      });
    });
    return map;
  });

  const relationEdges = computed<RelationEdge[]>(() =>
    props.relations.reduce<RelationEdge[]>((edges, relation) => {
        const sourceNode = renderedNodeMap.value.get(relation.sourceTableName);
        const targetNode = renderedNodeMap.value.get(relation.targetTableName);
        if (!sourceNode || !targetNode) {
          return edges;
        }

        const geometry = resolveRelationGeometry(
          sourceNode,
          targetNode,
          relation.sourceColumnNames,
          relation.targetColumnNames,
        );
        const relationLabel = relation.sourceColumnNames.length > 1 ? '多列外键' : '外键';
        const labelWidth = Math.max(96, relationLabel.length * 18 + 22);

        edges.push({
          id: `relation-${relation.relationKey}`,
          relationId: relation.relationKey,
          sourceX: geometry.sourceX,
          sourceY: geometry.sourceY,
          targetX: geometry.targetX,
          targetY: geometry.targetY,
          path: geometry.path,
          labelX: geometry.labelX,
          labelY: geometry.labelY,
          sourceTableName: relation.sourceTableName,
          targetTableName: relation.targetTableName,
          sourceColumnNames: relation.sourceColumnNames,
          targetColumnNames: relation.targetColumnNames,
          label: relationLabel,
          labelWidth,
          enabled: relation.enabled,
          source: relation.source,
          effective: relation.effective,
          invalidReason: relation.invalidReason,
        } satisfies RelationEdge);
        return edges;
      }, []),
  );

  const relationListTotal = computed(() => props.relations.length);

  const relationListTotalPages = computed(() =>
    Math.max(1, Math.ceil(relationListTotal.value / RELATION_LIST_PAGE_SIZE)),
  );

  const pagedRelations = computed(() => {
    const startIndex = (relationListPage.value - 1) * RELATION_LIST_PAGE_SIZE;
    return props.relations.slice(startIndex, startIndex + RELATION_LIST_PAGE_SIZE);
  });

  const draftEdge = computed<RelationEdge | null>(() => {
    if (
      !props.draftRelation?.sourceTableName ||
      !props.draftRelation?.targetTableName ||
      !props.draftRelation.sourceColumnNames.length ||
      !props.draftRelation.targetColumnNames.length
    ) {
      return null;
    }

    const sourceNode = renderedNodeMap.value.get(props.draftRelation.sourceTableName);
    const targetNode = renderedNodeMap.value.get(props.draftRelation.targetTableName);
    if (!sourceNode || !targetNode) {
      return null;
    }

    const geometry = resolveRelationGeometry(
      sourceNode,
      targetNode,
      props.draftRelation.sourceColumnNames,
      props.draftRelation.targetColumnNames,
    );
    const relationLabel =
      props.draftRelation.sourceColumnNames.length > 1 ? '草稿多列外键' : '草稿外键';

    return {
      id: 'draft-edge',
      relationId: 'draft-edge',
      sourceX: geometry.sourceX,
      sourceY: geometry.sourceY,
      targetX: geometry.targetX,
      targetY: geometry.targetY,
      path: geometry.path,
      labelX: geometry.labelX,
      labelY: geometry.labelY,
      sourceTableName: props.draftRelation.sourceTableName,
      targetTableName: props.draftRelation.targetTableName,
      sourceColumnNames: [...props.draftRelation.sourceColumnNames],
      targetColumnNames: [...props.draftRelation.targetColumnNames],
      label: relationLabel,
      labelWidth: Math.max(110, relationLabel.length * 18 + 24),
      enabled: props.draftRelation.enabled,
      source: 'logical',
    };
  });

  watch(
    () => props.relations,
    relations => {
      const lastPage = Math.max(1, Math.ceil(relations.length / RELATION_LIST_PAGE_SIZE));
      if (relationListPage.value > lastPage) {
        relationListPage.value = lastPage;
      }
      if (!relations.length) {
        selectedRelationId.value = null;
        return;
      }
      if (
        typeof selectedRelationId.value === 'string' &&
        relations.some(relation => relation.relationKey === selectedRelationId.value)
      ) {
        return;
      }
      selectedRelationId.value = null;
    },
    { immediate: true },
  );

  const dragPreviewEdge = computed<RelationEdge | null>(() => {
    if (!dragRelation.value) {
      return null;
    }

    const sourceNode = renderedNodeMap.value.get(dragRelation.value.sourceTableName);
    const sourceCenterX = sourceNode
      ? sourceNode.x + sourceNode.width / 2
      : dragRelation.value.startX;
    const previewSide = dragRelation.value.currentX >= sourceCenterX ? 'right' : 'left';
    const previewAnchor =
      sourceNode && dragRelation.value.sourceColumnName
        ? resolveColumnAnchor(sourceNode, dragRelation.value.sourceColumnName, previewSide)
        : { x: dragRelation.value.startX, y: dragRelation.value.startY };

    return {
      id: 'drag-preview',
      relationId: 'drag-preview',
      sourceX: previewAnchor.x,
      sourceY: previewAnchor.y,
      targetX: dragRelation.value.currentX + canvasBounds.value.offsetX,
      targetY: dragRelation.value.currentY + canvasBounds.value.offsetY,
      path: buildPreviewPath(
        previewAnchor.x,
        previewAnchor.y,
        dragRelation.value.currentX + canvasBounds.value.offsetX,
        dragRelation.value.currentY + canvasBounds.value.offsetY,
      ),
      labelX: (previewAnchor.x + dragRelation.value.currentX + canvasBounds.value.offsetX) / 2,
      labelY: (previewAnchor.y + dragRelation.value.currentY + canvasBounds.value.offsetY) / 2 - 8,
      sourceTableName: dragRelation.value.sourceTableName,
      targetTableName: hoveredDropColumn.value?.tableName ?? '',
      sourceColumnNames: [dragRelation.value.sourceColumnName],
      targetColumnNames: hoveredDropColumn.value ? [hoveredDropColumn.value.columnName] : [],
      label: '拖拽创建关系',
      labelWidth: 132,
      enabled: true,
      source: 'logical',
    };
  });

  watch(
    layoutStorageKey,
    () => {
      const snapshot = readLayoutSnapshot();
      if (snapshot?.viewport) {
        viewport.scale = snapshot.viewport.scale;
        viewport.offsetX = snapshot.viewport.offsetX;
        viewport.offsetY = snapshot.viewport.offsetY;
        return;
      }
      resetViewport();
    },
    { immediate: true },
  );

  watch(
    () => props.nodes,
    nodes => {
      const snapshot = readLayoutSnapshot();
      const savedNodes = snapshot?.nodes ?? {};
      const restoredNodes = nodes.map(node => {
        const saved = savedNodes[node.tableName];
        return saved ? { ...node, x: saved.x, y: saved.y } : { ...node };
      });
      localNodes.value = restoredNodes;
      initializeCanvasOrigin(restoredNodes, snapshot?.canvasOrigin);
      void nextTick(() => {
        if (snapshot?.viewport) {
          return;
        }
        scheduleAutoFitViewport();
      });
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
    if (autoFitFrame) {
      globalThis.cancelAnimationFrame(autoFitFrame);
      autoFitFrame = null;
    }
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
      canvasOrigin: {
        minX: canvasOrigin.minX,
        minY: canvasOrigin.minY,
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

  function resolveNodeExtents(nodes: TableNodeLayout[]) {
    if (!nodes.length) {
      return null;
    }

    return nodes.reduce(
      (extents, node) => ({
        minX: Math.min(extents.minX, node.x),
        minY: Math.min(extents.minY, node.y),
        maxX: Math.max(extents.maxX, node.x + node.width),
        maxY: Math.max(extents.maxY, node.y + node.height),
      }),
      {
        minX: Number.POSITIVE_INFINITY,
        minY: Number.POSITIVE_INFINITY,
        maxX: Number.NEGATIVE_INFINITY,
        maxY: Number.NEGATIVE_INFINITY,
      },
    );
  }

  function initializeCanvasOrigin(
    nodes: TableNodeLayout[],
    snapshotOrigin?: SemanticRelationLayoutSnapshot['canvasOrigin'],
  ) {
    const extents = resolveNodeExtents(nodes);
    if (!extents) {
      canvasOrigin.minX = 0;
      canvasOrigin.minY = 0;
      return;
    }

    canvasOrigin.minX =
      typeof snapshotOrigin?.minX === 'number'
        ? Math.min(snapshotOrigin.minX, extents.minX)
        : extents.minX;
    canvasOrigin.minY =
      typeof snapshotOrigin?.minY === 'number'
        ? Math.min(snapshotOrigin.minY, extents.minY)
        : extents.minY;
  }

  function scheduleAutoFitViewport() {
    if (autoFitFrame) {
      globalThis.cancelAnimationFrame(autoFitFrame);
    }
    autoFitFrame = globalThis.requestAnimationFrame(() => {
      autoFitFrame = null;
      fitCanvasToViewport();
    });
  }

  function fitCanvasToViewport() {
    const viewportElement = viewportRef.value;
    if (!viewportElement) {
      return;
    }

    const width = canvasBounds.value.width;
    const height = canvasBounds.value.height;
    if (!width || !height) {
      viewport.scale = 1;
      viewport.offsetX = 0;
      viewport.offsetY = 0;
      return;
    }

    const availableWidth = Math.max(240, viewportElement.clientWidth - RELATION_FIT_PADDING * 2);
    const availableHeight = Math.max(240, viewportElement.clientHeight - RELATION_FIT_PADDING * 2);
    const nextScale = Math.max(
      RELATION_MIN_SCALE,
      Math.min(
        RELATION_MAX_SCALE,
        1,
        Math.min(availableWidth / width, availableHeight / height),
      ),
    );

    viewport.scale = Number(nextScale.toFixed(3));
    viewport.offsetX = (viewportElement.clientWidth - width * viewport.scale) / 2;
    viewport.offsetY = (viewportElement.clientHeight - height * viewport.scale) / 2;
  }

  function expandCanvasOriginToFit(nodes: TableNodeLayout[]) {
    const extents = resolveNodeExtents(nodes);
    if (!extents) {
      return;
    }

    const nextMinX = Math.min(canvasOrigin.minX, extents.minX);
    const nextMinY = Math.min(canvasOrigin.minY, extents.minY);
    const deltaMinX = canvasOrigin.minX - nextMinX;
    const deltaMinY = canvasOrigin.minY - nextMinY;

    if (!deltaMinX && !deltaMinY) {
      return;
    }

    canvasOrigin.minX = nextMinX;
    canvasOrigin.minY = nextMinY;
    viewport.offsetX -= deltaMinX * viewport.scale;
    viewport.offsetY -= deltaMinY * viewport.scale;
  }

  function resolveAnchorY(node: TableNodeLayout, columnNames: string[]) {
    const firstColumnName = columnNames[0];
    const columnIndex = node.columns.findIndex(column => column.columnName === firstColumnName);
    const safeIndex = columnIndex >= 0 ? columnIndex : 0;
    const headerHeight = 58;
    const rowHeight = 32;
    return node.y + headerHeight + safeIndex * rowHeight + rowHeight / 2;
  }

  function resolveRelationSides(sourceNode: TableNodeLayout, targetNode: TableNodeLayout) {
    const sourceCenterX = sourceNode.x + sourceNode.width / 2;
    const targetCenterX = targetNode.x + targetNode.width / 2;
    return targetCenterX >= sourceCenterX
      ? { sourceSide: 'right' as const, targetSide: 'left' as const }
      : { sourceSide: 'left' as const, targetSide: 'right' as const };
  }

  function resolveRelationGeometry(
    sourceNode: TableNodeLayout,
    targetNode: TableNodeLayout,
    sourceColumnNames: string[],
    targetColumnNames: string[],
  ) {
    const { sourceSide, targetSide } = resolveRelationSides(sourceNode, targetNode);
    const sourceAnchor = resolveColumnAnchor(sourceNode, sourceColumnNames[0], sourceSide);
    const targetAnchor = resolveColumnAnchor(targetNode, targetColumnNames[0], targetSide);
    return {
      sourceX: sourceAnchor.x,
      sourceY: sourceAnchor.y,
      targetX: targetAnchor.x,
      targetY: targetAnchor.y,
      path: buildRelationPath(sourceAnchor.x, sourceAnchor.y, targetAnchor.x, targetAnchor.y),
      labelX: (sourceAnchor.x + targetAnchor.x) / 2,
      labelY: (sourceAnchor.y + targetAnchor.y) / 2 - 10,
    };
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

  function buildPreviewPath(sourceX: number, sourceY: number, targetX: number, targetY: number) {
    const horizontalGap = Math.max(56, Math.abs(targetX - sourceX) / 2);
    const direction = targetX >= sourceX ? 1 : -1;
    const elbowOutX = sourceX + horizontalGap * direction;
    const elbowInX = targetX - horizontalGap * direction;
    return `M ${sourceX} ${sourceY} L ${elbowOutX} ${sourceY} C ${elbowOutX + 24 * direction} ${sourceY}, ${
      elbowInX - 24 * direction
    } ${targetY}, ${elbowInX} ${targetY} L ${targetX} ${targetY}`;
  }

  function resolveColumnAnchor(node: TableNodeLayout, columnName: string, side: 'left' | 'right') {
    const y = resolveAnchorY(node, [columnName]);
    const x = side === 'right' ? node.x + node.width : node.x;
    return { x, y };
  }

  function toLogicalCanvasPosition(clientX: number, clientY: number) {
    const viewportElement = viewportRef.value;
    if (!viewportElement) {
      return { x: clientX, y: clientY };
    }
    const rect = viewportElement.getBoundingClientRect();
    return {
      x: (clientX - rect.left - viewport.offsetX) / viewport.scale - canvasBounds.value.offsetX,
      y: (clientY - rect.top - viewport.offsetY) / viewport.scale - canvasBounds.value.offsetY,
    };
  }

  function clearDragRelation() {
    dragRelation.value = null;
    hoveredDropColumn.value = null;
  }

  function isRelationSelected(relationId: string) {
    return relationId === selectedRelationId.value;
  }

  function focusCanvasOnRelation(relationId: string) {
    const viewportElement = viewportRef.value;
    const edge = relationEdges.value.find(item => item.relationId === relationId);
    if (!viewportElement || !edge) {
      return;
    }

    const viewportCenterX = viewportElement.clientWidth / 2;
    const viewportCenterY = viewportElement.clientHeight / 2;
    const targetX = edge.labelX;
    const targetY = edge.labelY;

    viewport.offsetX = viewportCenterX - targetX * viewport.scale;
    viewport.offsetY = viewportCenterY - targetY * viewport.scale;
    schedulePersistLayout();
  }

  function selectRelation(relationId: string, options: SelectRelationOptions = {}) {
    const { scrollList = true, focusCanvas = false } = options;
    selectedRelationId.value = relationId;
    const relationIndex = props.relations.findIndex(relation => relation.relationKey === relationId);
    if (relationIndex >= 0) {
      relationListPage.value = Math.floor(relationIndex / RELATION_LIST_PAGE_SIZE) + 1;
    }
    if (focusCanvas) {
      focusCanvasOnRelation(relationId);
    }
    void nextTick(() => {
      if (!scrollList) {
        return;
      }
      const relationElement = relationListRef.value?.querySelector?.(
        `[data-relation-id="${relationId}"]`,
      );
      relationElement?.scrollIntoView?.({
        block: 'nearest',
        behavior: 'smooth',
      });
    });
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

  function handleDragColumnStart(
    tableName: string,
    columnName: string,
    event: RelationPointerLikeEvent,
  ) {
    event.stopPropagation();
    const node = renderedNodeMap.value.get(tableName);
    if (!node) {
      return;
    }
    const pointerPosition = toLogicalCanvasPosition(event.clientX, event.clientY);
    const sourceSide =
      pointerPosition.x + canvasBounds.value.offsetX >= node.x + node.width / 2 ? 'right' : 'left';
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

  function handleCanvasPointerMove(event: RelationPointerLikeEvent) {
    if (nodeDrag.value) {
      const deltaX = (event.clientX - nodeDrag.value.startClientX) / viewport.scale;
      const deltaY = (event.clientY - nodeDrag.value.startClientY) / viewport.scale;
      const nextNodes = localNodes.value.map(node =>
        node.tableName === nodeDrag.value?.tableName
          ? {
              ...node,
              x: nodeDrag.value.startNodeX + deltaX,
              y: nodeDrag.value.startNodeY + deltaY,
            }
          : node,
      );
      localNodes.value = nextNodes;
      expandCanvasOriginToFit(nextNodes);
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

  function handleCanvasPointerUp(event: RelationPointerLikeEvent) {
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
    if (!dropTarget) {
      clearDragRelation();
      return;
    }

    const payload: RelationDragCreatePayload = {
      sourceTableName: dragRelation.value.sourceTableName,
      sourceColumnName: dragRelation.value.sourceColumnName,
      targetTableName: dropTarget.tableName,
      targetColumnName: dropTarget.columnName,
    };
    clearDragRelation();
    emit('drag-create-relation', payload);
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

  function handleViewportWheel(event: RelationWheelLikeEvent) {
    event.preventDefault();
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
    schedulePersistLayout();
  }

  function handleViewportPointerDown(event: RelationPointerLikeEvent) {
    if (event.button && event.button !== 0) {
      return;
    }
    const target = event.target as ClosestCapableTarget | null;
    if (
      target?.closest?.(
        '.relation-node, .relation-side-card, .el-dialog, .relation-edge-hit-area, .relation-edge-label',
      )
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

  function handleNodePointerDown(
    tableName: string,
    event: RelationPointerLikeEvent,
  ) {
    const target = event.target as ClosestCapableTarget | null;
    if (target?.closest?.('.relation-column-item, .el-button')) {
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

  function resetViewport() {
    fitCanvasToViewport();
  }

  function relationStateTagType(relation: LogicalTableRelationResponse) {
    if (!relation.enabled) {
      return 'info';
    }
    return relation.effective ? 'success' : 'danger';
  }

  function handleRelationListPageChange(page: number) {
    relationListPage.value = page;
  }
</script>

<template>
  <section class="relation-panel">
    <div class="section-header">
      <div>
        <h3>逻辑外键 ER 图</h3>
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
                <path d="M0,0 L0,6 L9,3 z" fill="#1d4ed8" />
              </marker>
              <marker
                id="relation-arrow-disabled"
                markerWidth="10"
                markerHeight="10"
                refX="8"
                refY="3"
                orient="auto"
              >
                <path d="M0,0 L0,6 L9,3 z" fill="#94a3b8" />
              </marker>
              <marker
                id="relation-arrow-selected"
                markerWidth="12"
                markerHeight="12"
                refX="9"
                refY="3.5"
                orient="auto"
              >
                <path d="M0,0 L0,7 L10,3.5 z" fill="#0f766e" />
              </marker>
            </defs>

            <g v-for="edge in relationEdges" :key="edge.id">
              <path
                class="relation-edge-hit-area"
                :d="edge.path"
                stroke="transparent"
                stroke-width="16"
                fill="none"
                stroke-linecap="round"
                pointer-events="stroke"
                @click.stop="
                  selectRelation(edge.relationId, { scrollList: true, focusCanvas: false })
                "
              />
              <path
                :d="edge.path"
                :stroke="
                  isRelationSelected(edge.relationId)
                    ? '#0f766e'
                    : edge.enabled
                      ? edge.effective
                        ? '#1d4ed8'
                        : '#dc2626'
                      : '#94a3b8'
                "
                :stroke-width="isRelationSelected(edge.relationId) ? 4.5 : 3"
                fill="none"
                :marker-end="
                  isRelationSelected(edge.relationId)
                    ? 'url(#relation-arrow-selected)'
                    : edge.enabled
                      ? 'url(#relation-arrow)'
                      : 'url(#relation-arrow-disabled)'
                "
                stroke-linecap="round"
              />
            </g>

            <g
              v-for="edge in relationEdges"
              :key="`${edge.id}-label`"
              class="relation-edge-label"
              @click.stop="
                selectRelation(edge.relationId, { scrollList: true, focusCanvas: false })
              "
            >
              <rect
                :x="edge.labelX - edge.labelWidth / 2"
                :y="edge.labelY - 16"
                :width="edge.labelWidth"
                height="26"
                rx="13"
                :fill="isRelationSelected(edge.relationId) ? '#ecfeff' : 'white'"
                :stroke="
                  isRelationSelected(edge.relationId)
                    ? '#14b8a6'
                    : edge.enabled
                      ? edge.effective
                        ? '#93c5fd'
                        : '#fca5a5'
                      : '#cbd5e1'
                "
                pointer-events="all"
              />
              <text
                :x="edge.labelX"
                :y="edge.labelY + 2"
                text-anchor="middle"
                class="edge-label"
                :class="{ 'edge-label-selected': isRelationSelected(edge.relationId) }"
                pointer-events="none"
              >
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
                stroke-linecap="round"
              />
              <text
                :x="draftEdge.labelX"
                :y="draftEdge.labelY"
                text-anchor="middle"
                class="edge-label edge-label-draft"
              >
                草稿关系
              </text>
            </template>

            <template v-if="dragPreviewEdge">
              <path
                :d="dragPreviewEdge.path"
                stroke="#0ea5e9"
                stroke-width="3"
                fill="none"
                stroke-dasharray="10 8"
                stroke-linecap="round"
              />
              <text
                :x="dragPreviewEdge.labelX"
                :y="dragPreviewEdge.labelY"
                text-anchor="middle"
                class="edge-label edge-label-drag"
              >
                拖拽创建关系
              </text>
            </template>
          </svg>

          <article
            v-for="node in localNodes"
            :key="node.tableName"
            class="relation-node"
            :class="{ 'is-node-dragging': nodeDrag?.tableName === node.tableName }"
            :style="{
              left: `${node.x + canvasBounds.offsetX}px`,
              top: `${node.y + canvasBounds.offsetY}px`,
              width: `${node.width}px`,
              minHeight: `${node.height}px`,
            }"
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
      </div>

      <div class="relation-side">
        <div class="relation-side-card">
          <h4>已记录关系</h4>
          <div v-if="relationError" class="error-tip">{{ relationError }}</div>
          <div v-if="!relations.length && !loading" class="canvas-empty">
            当前数据源还没有逻辑外键
          </div>
          <template v-else>
            <div class="relation-list-summary">
              <span>共 {{ relationListTotal }} 条关系</span>
              <span>每页 {{ RELATION_LIST_PAGE_SIZE }} 条</span>
            </div>
            <div ref="relationListRef" class="relation-list">
            <article
              v-for="relation in pagedRelations"
              :key="relation.relationKey"
              class="relation-list-item"
              :class="{ 'is-relation-selected': isRelationSelected(relation.relationKey) }"
              :data-relation-id="relation.relationKey"
              @click="selectRelation(relation.relationKey, { scrollList: false, focusCanvas: true })"
            >
              <div class="relation-list-head">
                <div>
                  <strong>{{ relation.sourceTableName }}</strong>
                  <span class="relation-arrow-text">→</span>
                  <strong>{{ relation.targetTableName }}</strong>
                  <el-tag size="small" effect="plain" class="relation-source-tag">
                    {{ relation.source === 'physical' ? '物理外键' : '逻辑外键' }}
                  </el-tag>
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
                    (value: boolean | string | number) =>
                      emit('toggle-relation-enabled', relation, Boolean(value))
                  "
                />
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
            </article>
            </div>
            <div v-if="relationListTotalPages > 1" class="relation-pagination">
              <el-pagination
                background
                small
                layout="prev, pager, next"
                :current-page="relationListPage"
                :page-size="RELATION_LIST_PAGE_SIZE"
                :total="relationListTotal"
                @current-change="handleRelationListPageChange"
              />
            </div>
          </template>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
  .relation-panel {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 16px;
  }

  .section-header h3 {
    font-size: 20px;
    color: #0f172a;
    margin-bottom: 0;
  }

  .relation-actions {
    display: flex;
    gap: 12px;
  }

  .relation-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 360px;
    gap: 20px;
    align-items: start;
  }

  .relation-canvas-wrap {
    border: 1px solid #dbe7f3;
    border-radius: 18px;
    background:
      linear-gradient(rgba(14, 165, 233, 0.06) 1px, transparent 1px),
      linear-gradient(90deg, rgba(14, 165, 233, 0.06) 1px, transparent 1px), #f8fbff;
    background-size: 32px 32px;
    overflow: hidden;
    min-height: 720px;
    position: relative;
    cursor: grab;
  }

  .relation-canvas {
    position: relative;
    min-width: 100%;
    min-height: 100%;
    transform-origin: 0 0;
    transition: transform 0.08s linear;
    will-change: transform;
  }

  .relation-svg {
    position: absolute;
    inset: 0;
    overflow: visible;
  }

  .relation-edge-hit-area,
  .relation-edge-label {
    cursor: pointer;
  }

  .edge-label {
    font-size: 12px;
    fill: #334155;
    font-weight: 600;
  }

  .edge-label-selected {
    fill: #0f766e;
  }

  .edge-label-draft {
    fill: #d97706;
  }

  .edge-label-drag {
    fill: #0284c7;
  }

  .relation-node {
    position: absolute;
    padding: 16px;
    border-radius: 20px;
    border: 1px solid #cbd5e1;
    background: rgba(255, 255, 255, 0.96);
    box-shadow: 0 18px 35px rgba(15, 23, 42, 0.08);
    transition:
      box-shadow 0.18s ease,
      border-color 0.18s ease;
    user-select: none;
    cursor: grab;
  }

  .relation-node:hover {
    border-color: #93c5fd;
    box-shadow: 0 22px 40px rgba(59, 130, 246, 0.12);
  }

  .relation-node.is-node-dragging {
    border-color: #2563eb;
    box-shadow: 0 26px 48px rgba(37, 99, 235, 0.18);
    cursor: grabbing;
  }

  .relation-node-header {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;
  }

  .relation-node-header h4 {
    font-size: 17px;
    color: #0f172a;
    margin-bottom: 4px;
  }

  .relation-node-header p {
    color: #64748b;
    font-size: 13px;
  }

  .relation-node-desc {
    color: #475569;
    line-height: 1.6;
    margin-bottom: 12px;
    min-height: 40px;
  }

  .relation-node-columns {
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .relation-column-item {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    padding: 8px 10px;
    border-radius: 12px;
    background-color: #f8fafc;
    color: #1e293b;
    cursor: crosshair;
    transition:
      transform 0.18s ease,
      border-color 0.18s ease,
      background-color 0.18s ease,
      box-shadow 0.18s ease;
    border: 1px solid transparent;
  }

  .relation-column-item:hover {
    transform: translateX(2px);
    border-color: #7dd3fc;
    background-color: #ecfeff;
  }

  .relation-column-item.is-drag-source {
    border-color: #0ea5e9;
    background-color: #e0f2fe;
    box-shadow: 0 10px 18px rgba(14, 165, 233, 0.12);
  }

  .relation-column-item.is-drag-target {
    border-color: #22c55e;
    background-color: #ecfdf5;
    box-shadow: 0 10px 18px rgba(34, 197, 94, 0.12);
  }

  .column-name {
    font-weight: 600;
  }

  .column-type {
    font-size: 12px;
    color: #64748b;
  }

  .relation-side {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .relation-side-card {
    border: 1px solid #dbe7f3;
    border-radius: 18px;
    background: #fff;
    padding: 18px;
    box-shadow: 0 14px 28px rgba(15, 23, 42, 0.05);
  }

  .relation-side-card h4 {
    font-size: 18px;
    color: #0f172a;
    margin-bottom: 14px;
  }

  .relation-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .relation-list-summary {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    color: #64748b;
    font-size: 13px;
    margin-bottom: 12px;
  }

  .relation-list-item {
    border: 1px solid #e2e8f0;
    border-radius: 16px;
    padding: 14px;
    background: #f8fbff;
    cursor: pointer;
    transition:
      border-color 0.2s ease,
      box-shadow 0.2s ease,
      background 0.2s ease,
      transform 0.2s ease;
  }

  .relation-list-item:hover {
    border-color: #99f6e4;
    box-shadow: 0 12px 26px rgba(20, 184, 166, 0.08);
  }

  .relation-list-item.is-relation-selected {
    border-color: #14b8a6;
    background: linear-gradient(180deg, #f0fdfa 0%, #ecfeff 100%);
    box-shadow: 0 18px 36px rgba(20, 184, 166, 0.14);
    transform: translateY(-1px);
  }

  .relation-list-head {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    align-items: center;
    margin-bottom: 8px;
  }

  .relation-arrow-text {
    margin: 0 6px;
    color: #64748b;
  }

  .relation-source-tag {
    margin-left: 8px;
    vertical-align: middle;
  }

  .relation-columns-line {
    font-family: 'Segoe UI', sans-serif;
    color: #1e293b;
    margin-bottom: 6px;
    line-height: 1.5;
  }

  .relation-description {
    color: #64748b;
    margin-bottom: 6px;
  }

  .relation-invalid {
    color: #dc2626;
    font-size: 13px;
    margin-bottom: 8px;
  }

  .relation-list-actions {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
  }

  .relation-pagination {
    margin-top: 14px;
    display: flex;
    justify-content: center;
  }

  .error-tip {
    margin-top: 14px;
    color: #dc2626;
  }

  .canvas-empty {
    min-height: 240px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #64748b;
  }

  @media (max-width: 1280px) {
    .relation-layout {
      grid-template-columns: 1fr;
    }
  }
</style>
