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
  import { computed, reactive, ref, watch } from 'vue';
  import type { FormInstance, FormRules } from 'element-plus';
  import type {
    LogicalTableRelationResponse,
    RelationCandidateColumnResponse,
  } from '@/api/semantic';
  import type { RelationForm, TableNodeLayout } from '../types';

  const props = defineProps<{
    visible: boolean;
    loading: boolean;
    relation: LogicalTableRelationResponse | null;
    form: RelationForm;
    nodes: TableNodeLayout[];
    sourceColumns: RelationCandidateColumnResponse[];
    targetColumns: RelationCandidateColumnResponse[];
  }>();

  const emit = defineEmits<{
    (event: 'update:visible', value: boolean): void;
    (event: 'update:form', value: RelationForm): void;
    (event: 'source-table-change', tableName: string): void;
    (event: 'target-table-change', tableName: string): void;
    (event: 'submit'): void;
    (event: 'close'): void;
  }>();

  const formRef = ref<FormInstance>();
  const localForm = reactive<RelationForm>({
    sourceTableName: '',
    sourceColumnNames: [],
    targetTableName: '',
    targetColumnNames: [],
    description: '',
    enabled: true,
  });

  const rules: FormRules<RelationForm> = {
    sourceTableName: [{ required: true, message: '请选择源表', trigger: 'change' }],
    sourceColumnNames: [{ required: true, message: '请选择源列', trigger: 'change' }],
    targetTableName: [{ required: true, message: '请选择目标表', trigger: 'change' }],
    targetColumnNames: [{ required: true, message: '请选择目标列', trigger: 'change' }],
  };

  const title = computed(() => (props.relation ? '编辑逻辑外键' : '新增逻辑外键'));

  watch(
    () => props.form,
    value => {
      Object.assign(localForm, {
        sourceTableName: value.sourceTableName,
        sourceColumnNames: [...value.sourceColumnNames],
        targetTableName: value.targetTableName,
        targetColumnNames: [...value.targetColumnNames],
        description: value.description,
        enabled: value.enabled,
      });
    },
    { immediate: true, deep: true },
  );

  watch(
    localForm,
    value => {
      emit('update:form', {
        sourceTableName: value.sourceTableName,
        sourceColumnNames: [...value.sourceColumnNames],
        targetTableName: value.targetTableName,
        targetColumnNames: [...value.targetColumnNames],
        description: value.description,
        enabled: value.enabled,
      });
    },
    { deep: true },
  );

  const handleClose = () => {
    emit('update:visible', false);
    emit('close');
  };

  const handleSubmit = async () => {
    if (!formRef.value) {
      return;
    }
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) {
      return;
    }
    emit('submit');
  };

  defineExpose({
    validate: handleSubmit,
  });
</script>

<template>
  <el-dialog :model-value="visible" :title="title" width="720px" @close="handleClose">
    <el-form ref="formRef" :model="localForm" :rules="rules" label-width="120px">
      <el-form-item label="源表" prop="sourceTableName">
        <el-select
          v-model="localForm.sourceTableName"
          filterable
          placeholder="选择源表"
          @change="(value: string | number | boolean) => emit('source-table-change', String(value))"
        >
          <el-option
            v-for="node in nodes"
            :key="node.tableName"
            :label="node.tableName"
            :value="node.tableName"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="源列" prop="sourceColumnNames">
        <el-select
          v-model="localForm.sourceColumnNames"
          multiple
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择一个或多个源列"
        >
          <el-option
            v-for="column in sourceColumns"
            :key="column.columnName"
            :label="`${column.columnName} (${column.typeName || 'UNKNOWN'})`"
            :value="column.columnName"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="目标表" prop="targetTableName">
        <el-select
          v-model="localForm.targetTableName"
          filterable
          placeholder="选择目标表"
          @change="(value: string | number | boolean) => emit('target-table-change', String(value))"
        >
          <el-option
            v-for="node in nodes"
            :key="node.tableName"
            :label="node.tableName"
            :value="node.tableName"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="目标列" prop="targetColumnNames">
        <el-select
          v-model="localForm.targetColumnNames"
          multiple
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择与源列数量一致的目标列"
        >
          <el-option
            v-for="column in targetColumns"
            :key="column.columnName"
            :label="`${column.columnName} (${column.typeName || 'UNKNOWN'})`"
            :value="column.columnName"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="关系备注">
        <el-input
          v-model="localForm.description"
          type="textarea"
          :rows="3"
          placeholder="可填写这条逻辑外键的业务说明"
        />
      </el-form-item>

      <el-form-item label="启用关系">
        <el-switch v-model="localForm.enabled" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>
