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

import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import type {
  RelationViewportState,
  SemanticRelationLayoutSnapshot,
  TableNodeLayout,
} from '../types';

const RELATION_MIN_SCALE = 0.25;
const RELATION_MAX_SCALE = 1.8;
const RELATION_FIT_PADDING = 32;
const RELATION_CANVAS_PADDING = 220;
const RELATION_CANVAS_MIN_WIDTH = 1200;
const RELATION_CANVAS_MIN_HEIGHT = 720;
const RELATION_LAYOUT_STORAGE_VERSION = 2;
const RELATION_LAYOUT_STORAGE_PREFIX = 'semantic-model:relation-layout';

export function useRelationCanvasLayout(
  nodes: () => TableNodeLayout[],
  datasourceId: () => number | undefined,
) {
  const viewportRef = ref<globalThis.HTMLElement | null>(null);
  const localNodes = ref<TableNodeLayout[]>([]);
  const viewport = reactive<RelationViewportState>({ scale: 1, offsetX: 0, offsetY: 0 });
  let persistLayoutTimer: ReturnType<typeof globalThis.setTimeout> | null = null;
  let viewportResizeObserver: globalThis.ResizeObserver | null = null;
  let hadViewportDimensions = false;

  const layoutStorageKey = computed(() => {
    const id = datasourceId();
    return typeof id === 'number' ? `${RELATION_LAYOUT_STORAGE_PREFIX}:${id}` : '';
  });

  const canvasBounds = computed(() => {
    const maxX = localNodes.value.reduce((acc, node) => Math.max(acc, node.x + node.width), 0);
    const maxY = localNodes.value.reduce((acc, node) => Math.max(acc, node.y + node.height), 0);
    return {
      width: Math.max(RELATION_CANVAS_MIN_WIDTH, maxX + RELATION_CANVAS_PADDING),
      height: Math.max(RELATION_CANVAS_MIN_HEIGHT, maxY + RELATION_CANVAS_PADDING),
    };
  });
  const zoomPercent = computed(() => Math.round(viewport.scale * 100));

  function readLayoutSnapshot(): SemanticRelationLayoutSnapshot | null {
    if (!layoutStorageKey.value) return null;
    try {
      const raw = globalThis.localStorage.getItem(layoutStorageKey.value);
      if (!raw) return null;
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
    if (!layoutStorageKey.value || !localNodes.value.length) return;
    const previousSnapshot = readLayoutSnapshot();
    const snapshot: SemanticRelationLayoutSnapshot = {
      version: RELATION_LAYOUT_STORAGE_VERSION,
      nodes: {
        ...(previousSnapshot?.nodes ?? {}),
        ...Object.fromEntries(
          localNodes.value.map(node => [node.tableName, { x: node.x, y: node.y }]),
        ),
      },
      viewport: {
        scale: viewport.scale,
        offsetX: viewport.offsetX,
        offsetY: viewport.offsetY,
      },
    };
    try {
      globalThis.localStorage.setItem(layoutStorageKey.value, JSON.stringify(snapshot));
    } catch {
      // Browser storage can be unavailable in strict privacy modes.
    }
  }

  function schedulePersistLayout() {
    if (persistLayoutTimer) globalThis.clearTimeout(persistLayoutTimer);
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

  function fitCanvasToViewport() {
    const viewportElement = viewportRef.value;
    if (!viewportElement) return;
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

  function zoomAtCenter(factor: number) {
    const viewportElement = viewportRef.value;
    if (!viewportElement) return;
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

  function toLogicalCanvasPosition(clientX: number, clientY: number) {
    const viewportElement = viewportRef.value;
    if (!viewportElement) return { x: clientX, y: clientY };
    const rect = viewportElement.getBoundingClientRect();
    return {
      x: (clientX - rect.left - viewport.offsetX) / viewport.scale,
      y: (clientY - rect.top - viewport.offsetY) / viewport.scale,
    };
  }

  function handleViewportWheel(event: globalThis.WheelEvent) {
    event.preventDefault();
    const viewportElement = viewportRef.value;
    if (!viewportElement) return;
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

  watch(
    nodes,
    async nextNodes => {
      if (!nextNodes.length) {
        localNodes.value = [];
        return;
      }
      const snapshot = readLayoutSnapshot();
      const savedNodes = snapshot?.nodes ?? {};
      localNodes.value = nextNodes.map(node => {
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

  watch(localNodes, schedulePersistLayout, { deep: true });
  watch(
    () => [viewport.scale, viewport.offsetX, viewport.offsetY, layoutStorageKey.value],
    schedulePersistLayout,
  );

  onMounted(() => {
    if (!viewportRef.value) return;
    viewportResizeObserver = new globalThis.ResizeObserver(entries => {
      const entry = entries[0];
      if (!entry) return;
      const hasDims = entry.contentRect.width > 0 && entry.contentRect.height > 0;
      if (hasDims && !hadViewportDimensions && localNodes.value.length > 0) fitCanvasToViewport();
      hadViewportDimensions = hasDims;
    });
    viewportResizeObserver.observe(viewportRef.value);
  });

  onBeforeUnmount(() => {
    viewportResizeObserver?.disconnect();
    flushPersistLayout();
  });

  return {
    viewportRef,
    localNodes,
    viewport,
    canvasBounds,
    zoomPercent,
    resetViewport: fitCanvasToViewport,
    zoomIn: () => zoomAtCenter(1.2),
    zoomOut: () => zoomAtCenter(1 / 1.2),
    toLogicalCanvasPosition,
    handleViewportWheel,
    flushPersistLayout,
  };
}
