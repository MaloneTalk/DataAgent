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
  import { computed, ref } from 'vue';
  import type { FormInstance, FormRules } from 'element-plus';
  import type { LogicalTableRelationResponse } from '@/api/semantic';
  import { logicalRelationTypeOptions } from '../utils';
  import type { RelationColumnNode, RelationForm, TableNodeLayout } from '../types';

  const props = defineProps<{
    visible: boolean;
    loading: boolean;
    relation: LogicalTableRelationResponse | null;
    form: RelationForm;
    nodes: TableNodeLayout[];
    sourceColumns: RelationColumnNode[];
    targetColumns: RelationColumnNode[];
    fieldErrors: Record<string, string>;
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

  const rules: FormRules<RelationForm> = {
    sourceTableName: [{ required: true, message: '请选择源表', trigger: 'change' }],
    sourceColumnNames: [{ required: true, message: '请选择源列', trigger: 'change' }],
    targetTableName: [{ required: true, message: '请选择目标表', trigger: 'change' }],
    targetColumnNames: [{ required: true, message: '请选择目标列', trigger: 'change' }],
    relationType: [{ required: true, message: '请选择关系方式', trigger: 'change' }],
  };

  const title = computed(() => (props.relation ? '编辑逻辑外键' : '新增逻辑外键'));

  function updateForm<K extends keyof RelationForm>(field: K, value: RelationForm[K]) {
    emit('update:form', { ...props.form, [field]: value });
  }

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
</script>

<template>
  <el-dialog :model-value="visible" :title="title" width="720px" @close="handleClose">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="源表" prop="sourceTableName" :error="fieldErrors.sourceTableName">
        <el-select
          :model-value="form.sourceTableName"
          filterable
          placeholder="选择源表"
          @update:model-value="
            (value: string | number | boolean) => updateForm('sourceTableName', String(value))
          "
          @change="(value: string | number | boolean) => emit('source-table-change', String(value))"
        >
          <el-option
            v-for="node in nodes"
            :key="node.tableName"
            :label="node.tableName"
            :value="node.tableName"
            :disabled="!node.operable"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="源列" prop="sourceColumnNames" :error="fieldErrors.sourceColumnNames">
        <el-select
          :model-value="form.sourceColumnNames"
          multiple
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择源列"
          @update:model-value="(value: string[]) => updateForm('sourceColumnNames', value)"
        >
          <el-option
            v-for="column in sourceColumns"
            :key="column.columnName"
            :label="`${column.columnName} (${column.typeName || 'UNKNOWN'})`"
            :value="column.columnName"
            :disabled="!column.operable"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="目标表" prop="targetTableName" :error="fieldErrors.targetTableName">
        <el-select
          :model-value="form.targetTableName"
          filterable
          placeholder="选择目标表"
          @update:model-value="
            (value: string | number | boolean) => updateForm('targetTableName', String(value))
          "
          @change="(value: string | number | boolean) => emit('target-table-change', String(value))"
        >
          <el-option
            v-for="node in nodes"
            :key="node.tableName"
            :label="node.tableName"
            :value="node.tableName"
            :disabled="!node.operable"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="目标列" prop="targetColumnNames" :error="fieldErrors.targetColumnNames">
        <el-select
          :model-value="form.targetColumnNames"
          multiple
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择目标列"
          @update:model-value="(value: string[]) => updateForm('targetColumnNames', value)"
        >
          <el-option
            v-for="column in targetColumns"
            :key="column.columnName"
            :label="`${column.columnName} (${column.typeName || 'UNKNOWN'})`"
            :value="column.columnName"
            :disabled="!column.operable"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="关系方式" prop="relationType" :error="fieldErrors.relationType">
        <el-select
          :model-value="form.relationType"
          placeholder="选择关系方式"
          @update:model-value="
            (value: RelationForm['relationType']) => updateForm('relationType', value)
          "
        >
          <el-option
            v-for="option in logicalRelationTypeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="关系备注" prop="description" :error="fieldErrors.description">
        <el-input
          :model-value="form.description"
          type="textarea"
          :rows="3"
          placeholder="可选填写这条逻辑外键的业务说明"
          @update:model-value="(value: string) => updateForm('description', value)"
        />
      </el-form-item>

      <el-form-item label="启用关系" prop="enabled" :error="fieldErrors.enabled">
        <el-switch
          :model-value="form.enabled"
          @update:model-value="(value: boolean) => updateForm('enabled', value)"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>
