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
  import { computed } from 'vue';
  import type { LogicalTableRelationResponse } from '@/api/semantic';
  import { formatLogicalRelationType } from '../utils';
  import type { RelationDraftPreview, RelationDragCreatePayload, TableNodeLayout } from '../types';
  import { getRelationGeometry } from '../relationGeometry';
  import { useRelationCanvasLayout } from '../composables/useRelationCanvasLayout';
  import { useRelationCanvasInteraction } from '../composables/useRelationCanvasInteraction';

  interface RelationEdge {
    relationId: number;
    path: string;
    label: string;
    labelX: number;
    labelY: number;
    labelWidth: number;
    enabled: boolean;
  }

  const props = defineProps<{
    loading: boolean;
    relationError: string;
    datasourceId: number | undefined;
    nodes: TableNodeLayout[];
    relations: LogicalTableRelationResponse[];
    draftRelation: RelationDraftPreview | null;
  }>();

  const emit = defineEmits<{
    (event: 'edit-relation', relation: LogicalTableRelationResponse): void;
    (event: 'delete-relation', relation: LogicalTableRelationResponse): void;
    (
      event: 'toggle-relation-enabled',
      relation: LogicalTableRelationResponse,
      value: boolean,
    ): void;
    (event: 'drag-create-relation', payload: RelationDragCreatePayload): void;
  }>();

  const {
    viewportRef,
    localNodes,
    viewport,
    canvasBounds,
    zoomPercent,
    resetViewport,
    zoomIn,
    zoomOut,
    toLogicalCanvasPosition,
    handleViewportWheel,
    flushPersistLayout,
  } = useRelationCanvasLayout(
    () => props.nodes,
    () => props.datasourceId,
  );

  const {
    nodeMap,
    nodeDrag,
    dragRelation,
    hoveredDropColumn,
    dragPreview,
    selectRelation,
    isSelected,
    handleDragColumnStart,
    handleCanvasPointerMove,
    handleCanvasPointerUp,
    handleDropColumnEnter,
    handleDropColumnLeave,
    handleViewportPointerDown,
    handleNodePointerDown,
  } = useRelationCanvasInteraction({
    localNodes,
    viewport,
    toLogicalCanvasPosition,
    flushPersistLayout,
    onCreateRelation: payload => emit('drag-create-relation', payload),
  });

  const relationEdges = computed<RelationEdge[]>(() =>
    props.relations.flatMap(relation => {
      const geometry = getRelationGeometry(relation, nodeMap.value);
      if (!geometry) return [];

      const relationTypeLabel = formatLogicalRelationType(relation.relationType);
      const label =
        relation.sourceColumnNames.length > 1 ? '多列' + relationTypeLabel : relationTypeLabel;
      return [
        {
          relationId: relation.id,
          ...geometry,
          label,
          labelWidth: Math.max(96, label.length * 18 + 22),
          enabled: relation.enabled,
        },
      ];
    }),
  );

  const draftEdge = computed(() =>
    props.draftRelation ? getRelationGeometry(props.draftRelation, nodeMap.value) : null,
  );

  defineExpose({ resetViewport });
</script>

<template>
  <section class="relation-panel">
    <div class="relation-layout">
      <div
        ref="viewportRef"
        class="relation-canvas-wrap"
        @wheel="handleViewportWheel"
        @pointerdown="handleViewportPointerDown"
        @pointermove="handleCanvasPointerMove"
        @pointerup="handleCanvasPointerUp"
        @pointerleave="handleCanvasPointerUp"
      >
        <div v-if="loading" class="canvas-empty">正在加载表结构...</div>
        <div
          v-else
          class="relation-canvas"
          :style="{
            width: `${canvasBounds.width}px`,
            height: `${canvasBounds.height}px`,
            transform: `translate(${viewport.offsetX}px, ${viewport.offsetY}px) scale(${viewport.scale})`,
          }"
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
                <path d="M0,0 L0,6 L9,3 z" fill="var(--app-text-secondary)" />
              </marker>
            </defs>

            <g v-for="edge in relationEdges" :key="edge.relationId">
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
                    ? 'var(--app-text-primary)'
                    : edge.enabled
                      ? 'var(--app-text-secondary)'
                      : 'var(--app-border)'
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
                :fill="isSelected(edge.relationId) ? 'var(--app-bg-hover)' : 'var(--app-bg-card)'"
                :stroke="
                  isSelected(edge.relationId) ? 'var(--app-text-primary)' : 'var(--app-border)'
                "
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
              <el-tag v-if="!node.operable" type="warning" size="small">
                {{ node.invalidReason || '不可操作' }}
              </el-tag>
            </header>
            <p class="relation-node-desc">{{ node.description || '暂无语义描述' }}</p>
            <ul class="relation-node-columns">
              <li
                v-for="column in node.columns"
                :key="column.columnName"
                class="relation-column-item"
                :data-table-name="node.tableName"
                :data-column-name="column.columnName"
                :data-operable="String(node.operable && column.operable)"
                :title="column.invalidReason || undefined"
                :class="{
                  'is-disabled': !node.operable || !column.operable,
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
        </div>
      </div>

      <aside class="relation-side">
        <div class="relation-side-card">
          <h4>已记录关系</h4>
          <div v-if="relationError" class="error-tip">{{ relationError }}</div>
          <div v-if="!relations.length && !loading" class="canvas-empty">
            当前页表还没有逻辑外键
          </div>
          <article
            v-for="relation in relations"
            :key="relation.id"
            class="relation-list-item"
            :class="{ 'is-selected': isSelected(relation.id) }"
            @click="selectRelation(relation.id)"
          >
            <div class="relation-list-head">
              <div class="relation-name">
                <strong>{{ relation.sourceTableName }}</strong>
                <span>→</span>
                <strong>{{ relation.targetTableName }}</strong>
              </div>
              <el-tag :type="relation.enabled ? 'success' : 'info'">
                {{ !relation.enabled ? '已禁用' : '生效中' }}
              </el-tag>
            </div>
            <div class="relation-columns-line">
              {{ relation.sourceColumnNames.join(', ') }} ->
              {{ relation.targetColumnNames.join(', ') }}
            </div>
            <div class="relation-type-line">
              {{ formatLogicalRelationType(relation.relationType) }}
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

<style scoped src="./RelationWorkspace.css"></style>
