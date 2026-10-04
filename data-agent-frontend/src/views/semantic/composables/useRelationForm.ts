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

import { computed, reactive, ref, type Ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useFieldErrors } from '@/composables/useFieldErrors';
import {
  createLogicalRelation,
  deleteLogicalRelation,
  updateLogicalRelation,
  updateLogicalRelationEnabled,
  type BindLogicalTableRelationRequest,
  type LogicalTableRelationResponse,
  type RelationWorkspaceColumnResponse,
  type UpdateLogicalTableRelationRequest,
} from '@/api/semantic';
import type { RelationDragCreatePayload, RelationForm, TableNodeLayout } from '../types';

interface RelationFormOptions {
  selectedDatasourceId: Ref<number | undefined>;
  relationNodes: Ref<TableNodeLayout[]>;
  loadRelationData: () => Promise<void>;
}

export function useRelationForm(options: RelationFormOptions) {
  const { selectedDatasourceId, relationNodes, loadRelationData } = options;
  const selectedRelation = ref<LogicalTableRelationResponse | null>(null);
  const relationDialogVisible = ref(false);
  const relationSubmitLoading = ref(false);
  const relationForm = reactive<RelationForm>({
    sourceTableName: '',
    sourceColumnNames: [],
    targetTableName: '',
    targetColumnNames: [],
    relationType: '',
    description: '',
    enabled: true,
  });
  const {
    fieldErrors: relationFieldErrors,
    clearFieldErrors: clearRelationFieldErrors,
    applyFieldErrors: applyRelationFieldErrors,
  } = useFieldErrors(relationForm);

  const draftRelation = computed(() => {
    if (
      !relationForm.sourceTableName ||
      !relationForm.targetTableName ||
      relationForm.sourceColumnNames.length === 0 ||
      relationForm.targetColumnNames.length === 0
    )
      return null;
    return {
      sourceTableName: relationForm.sourceTableName,
      sourceColumnNames: [...relationForm.sourceColumnNames],
      targetTableName: relationForm.targetTableName,
      targetColumnNames: [...relationForm.targetColumnNames],
      relationType: relationForm.relationType,
      enabled: relationForm.enabled,
    };
  });

  function findRelationColumns(tableName: string): RelationWorkspaceColumnResponse[] {
    return relationNodes.value.find(node => node.tableName === tableName)?.columns ?? [];
  }
  const relationSourceColumns = computed(() => findRelationColumns(relationForm.sourceTableName));
  const relationTargetColumns = computed(() => findRelationColumns(relationForm.targetTableName));

  function resetRelationForm() {
    clearRelationFieldErrors();
    Object.assign(relationForm, {
      sourceTableName: '',
      sourceColumnNames: [],
      targetTableName: '',
      targetColumnNames: [],
      relationType: '',
      description: '',
      enabled: true,
    });
    selectedRelation.value = null;
  }

  function handleSourceTableChange() {
    relationForm.sourceColumnNames = [];
  }

  function handleTargetTableChange() {
    relationForm.targetColumnNames = [];
  }

  function handleDragCreateRelation(payload: RelationDragCreatePayload) {
    clearRelationFieldErrors();
    if (payload.sourceTableName === payload.targetTableName) {
      ElMessage.warning('不能把关系拖回同一张表');
      return;
    }
    Object.assign(relationForm, {
      sourceTableName: payload.sourceTableName,
      sourceColumnNames: [payload.sourceColumnName],
      targetTableName: payload.targetTableName,
      targetColumnNames: [payload.targetColumnName],
      relationType: '',
      description: '',
      enabled: true,
    });
    selectedRelation.value = null;
    relationDialogVisible.value = true;
  }

  function handleEditRelation(relation: LogicalTableRelationResponse) {
    clearRelationFieldErrors();
    if (relation.source === 'physical') {
      ElMessage.warning('物理外键仅展示，不支持直接编辑');
      return;
    }
    if (typeof relation.id !== 'number') {
      ElMessage.error('当前逻辑外键缺少有效标识，无法编辑');
      return;
    }
    selectedRelation.value = relation;
    Object.assign(relationForm, {
      sourceTableName: relation.sourceTableName,
      sourceColumnNames: [...relation.sourceColumnNames],
      targetTableName: relation.targetTableName,
      targetColumnNames: [...relation.targetColumnNames],
      relationType: relation.relationType === 'foreign_key' ? '' : relation.relationType,
      description: relation.description ?? '',
      enabled: relation.enabled,
    });
    relationDialogVisible.value = true;
  }

  async function handleSubmitRelation() {
    clearRelationFieldErrors();
    if (typeof selectedDatasourceId.value !== 'number') return;
    if (relationForm.sourceTableName === relationForm.targetTableName) {
      ElMessage.warning('源表和目标表不能相同');
      return;
    }
    if (relationForm.sourceColumnNames.length !== relationForm.targetColumnNames.length) {
      ElMessage.warning('源列与目标列数量必须一致');
      return;
    }
    if (!relationForm.relationType) {
      ElMessage.warning('请选择关系方式');
      return;
    }

    relationSubmitLoading.value = true;
    try {
      const payload: BindLogicalTableRelationRequest | UpdateLogicalTableRelationRequest = {
        datasourceId: selectedDatasourceId.value,
        sourceColumnNames: [...relationForm.sourceColumnNames],
        targetTableName: relationForm.targetTableName,
        targetColumnNames: [...relationForm.targetColumnNames],
        relationType: relationForm.relationType,
        description: relationForm.description.trim(),
        enabled: relationForm.enabled,
      };
      if (selectedRelation.value) {
        const relationId = selectedRelation.value.id;
        if (typeof relationId !== 'number') {
          ElMessage.error('当前逻辑外键缺少有效标识，无法更新');
          return;
        }
        await updateLogicalRelation(relationForm.sourceTableName, { ...payload, relationId });
        ElMessage.success('逻辑外键已更新');
      } else {
        await createLogicalRelation(relationForm.sourceTableName, payload);
        ElMessage.success('逻辑外键已创建');
      }
      relationDialogVisible.value = false;
      resetRelationForm();
      await loadRelationData();
    } catch (error) {
      applyRelationFieldErrors(error);
    } finally {
      relationSubmitLoading.value = false;
    }
  }

  async function handleDeleteRelation(relation: LogicalTableRelationResponse) {
    if (relation.source === 'physical') {
      ElMessage.warning('物理外键仅展示，不支持删除');
      return;
    }
    if (typeof relation.id !== 'number' || typeof selectedDatasourceId.value !== 'number') {
      ElMessage.error('当前逻辑外键缺少有效标识，无法删除');
      return;
    }
    try {
      await ElMessageBox.confirm(
        `确定要删除逻辑外键 ${relation.sourceTableName} -> ${relation.targetTableName} 吗？`,
        '提示',
        { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
      );
      await deleteLogicalRelation(
        selectedDatasourceId.value,
        relation.sourceTableName,
        relation.id,
      );
      ElMessage.success('逻辑外键已删除');
      await loadRelationData();
    } catch {
      // ignore cancel
    }
  }

  async function handleToggleRelationEnabled(
    relation: LogicalTableRelationResponse,
    value: boolean,
  ) {
    if (relation.source === 'physical') {
      ElMessage.warning('物理外键始终由数据库结构决定，不能在这里启停');
      return;
    }
    if (typeof relation.id !== 'number' || typeof selectedDatasourceId.value !== 'number') {
      ElMessage.error('当前逻辑外键缺少有效标识，无法更新状态');
      return;
    }
    await updateLogicalRelationEnabled(relation.sourceTableName, {
      datasourceId: selectedDatasourceId.value,
      relationId: relation.id,
      enabled: value,
    });
    ElMessage.success(value ? '逻辑外键已启用' : '逻辑外键已禁用');
    await loadRelationData();
  }

  return {
    selectedRelation,
    relationDialogVisible,
    relationSubmitLoading,
    relationForm,
    relationFieldErrors,
    relationSourceColumns,
    relationTargetColumns,
    draftRelation,
    resetRelationForm,
    handleSourceTableChange,
    handleTargetTableChange,
    handleDragCreateRelation,
    handleEditRelation,
    handleSubmitRelation,
    handleDeleteRelation,
    handleToggleRelationEnabled,
  };
}
