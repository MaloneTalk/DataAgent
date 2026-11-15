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
  import { computed, onMounted, reactive, ref } from 'vue';
  import type { FormInstance, FormRules } from 'element-plus';
  import { ElMessage } from 'element-plus';
  import { useFieldErrors } from '@/composables/useFieldErrors';
  import { useUserStore } from '@/stores/user';
  import { getDomainNames } from '@/api/domain';
  import {
    getColumnSemanticPage,
    updateTableSemantic,
    type ColumnSemanticInfo,
    type TableSemanticInfo,
  } from '@/api/semantic';

  interface TableEditForm {
    tableName: string;
    domain: string;
    tableDescription: string;
    dataGranularity: string;
    businessKeyColumns: string[];
    isVisible: boolean;
  }

  const props = defineProps<{ datasourceId: number; table: TableSemanticInfo }>();
  const emit = defineEmits<{ close: []; saved: [] }>();
  const domainOptions = ref<string[]>([]);
  const userStore = useUserStore();
  const columnOptions = ref<ColumnSemanticInfo[]>([]);
  const columnsLoading = ref(true);
  const columnsError = ref('');
  const canEditBusinessKey = computed(
    () =>
      props.table.businessKeyColumns !== null &&
      userStore.hasPermission('semantic:column:read') &&
      !columnsLoading.value &&
      !columnsError.value,
  );

  const dialogVisible = ref(true);
  const submitLoading = ref(false);
  const formRef = ref<FormInstance>();
  const form = reactive<TableEditForm>({
    tableName: props.table.tableName,
    domain: props.table.domain ?? '',
    tableDescription: props.table.tableDescription ?? '',
    dataGranularity: props.table.dataGranularity ?? '',
    businessKeyColumns: [...(props.table.businessKeyColumns ?? [])],
    isVisible: props.table.isVisible,
  });
  const { fieldErrors, clearFieldErrors, applyFieldErrors } = useFieldErrors(form);
  const rules: FormRules<TableEditForm> = {
    tableName: [{ required: true, message: '表名不能为空', trigger: 'blur' }],
  };

  const handleSubmit = async () => {
    if (!formRef.value) return;
    clearFieldErrors();
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;
    submitLoading.value = true;
    try {
      await updateTableSemantic({
        datasourceId: props.datasourceId,
        tableName: form.tableName,
        domain: form.domain.trim() || undefined,
        tableDescription: form.tableDescription.trim() || undefined,
        dataGranularity: form.dataGranularity.trim() || undefined,
        businessKeyColumns: canEditBusinessKey.value ? form.businessKeyColumns : undefined,
        isVisible: form.isVisible,
      });
      ElMessage.success('更新成功');
      dialogVisible.value = false;
      emit('saved');
    } catch (err) {
      applyFieldErrors(err);
    } finally {
      submitLoading.value = false;
    }
  };

  const loadColumns = async () => {
    try {
      if (!userStore.hasPermission('semantic:column:read')) return;
      let page = 1;
      while (true) {
        const result = await getColumnSemanticPage(props.table.tableName, {
          datasourceId: props.datasourceId,
          page: page++,
          pageSize: 100,
        });
        columnOptions.value.push(...result.items.filter(column => column.hasPhysicalColumn));
        if (!result.hasNext) break;
      }
    } catch {
      columnsError.value = '字段加载失败，请关闭后重新打开编辑窗口';
    } finally {
      columnsLoading.value = false;
    }
  };

  const loadDomains = async () => {
    if (!userStore.hasPermission('semantic:domain:read')) return;
    try {
      domainOptions.value = await getDomainNames();
    } catch {
      domainOptions.value = [];
    }
  };

  onMounted(() => Promise.all([loadDomains(), loadColumns()]));
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="编辑表语义"
    width="600px"
    :close-on-click-modal="false"
    :close-on-press-escape="!submitLoading"
    :show-close="!submitLoading"
    @closed="emit('close')"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="表名" prop="tableName" :error="fieldErrors.tableName">
        <el-input v-model="form.tableName" disabled placeholder="请输入表名" />
      </el-form-item>
      <el-form-item label="领域" prop="domain" :error="fieldErrors.domain">
        <el-select
          v-model="form.domain"
          placeholder="请选择领域"
          filterable
          allow-create
          default-first-option
          clearable
        >
          <el-option
            v-for="domain in domainOptions"
            :key="domain"
            :label="domain"
            :value="domain"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="语义描述" prop="tableDescription" :error="fieldErrors.tableDescription">
        <el-input
          v-model="form.tableDescription"
          type="textarea"
          :rows="4"
          placeholder="请输入表的语义描述信息"
        />
      </el-form-item>
      <el-form-item label="数据粒度" prop="dataGranularity" :error="fieldErrors.dataGranularity">
        <el-input
          v-model="form.dataGranularity"
          type="textarea"
          :rows="2"
          placeholder="说明一行代表什么，例如：每行代表一份订单"
        />
      </el-form-item>
      <el-form-item
        label="业务唯一键"
        prop="businessKeyColumns"
        :error="fieldErrors.businessKeyColumns"
      >
        <el-select
          v-model="form.businessKeyColumns"
          multiple
          filterable
          clearable
          placeholder="可选；选择一列或一组共同唯一的字段"
          :loading="columnsLoading"
          :disabled="!canEditBusinessKey"
        >
          <el-option
            v-for="column in columnOptions"
            :key="column.columnName"
            :label="column.columnName"
            :value="column.columnName"
          />
        </el-select>
        <div class="key-help">
          管理员声明这些字段共同唯一标识一行；不创建数据库约束，也不自动校验数据唯一性。
        </div>
        <el-text v-if="table.businessKeyInvalidReason" type="warning">
          {{ table.businessKeyInvalidReason }}
        </el-text>
        <el-text v-if="columnsError" type="danger">{{ columnsError }}</el-text>
        <el-text v-else-if="!columnsLoading && !canEditBusinessKey" type="info">
          需要列语义读取权限及全部键字段的数据权限才能编辑。
        </el-text>
      </el-form-item>
      <el-form-item label="可见性" prop="isVisible" :error="fieldErrors.isVisible">
        <el-switch v-model="form.isVisible" active-text="可见" inactive-text="隐藏" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitLoading" @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
  .key-help {
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.6;
    margin-top: 6px;
  }
</style>
