<!--
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
 -->

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue';
  import type { FormInstance, FormRules } from 'element-plus';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import {
    listMetrics,
    createMetric,
    updateMetric,
    deleteMetric,
    type MetricInfo,
    type MetricUpsertRequest,
  } from '@/api/metric';
  import { formatDateTime } from '@/views/semantic/utils';

  interface MetricEditForm {
    metricKey: string;
    name: string;
    aliases: string;
    measureExpr: string;
    filters: string;
    timeField: string;
    description: string;
  }

  const metricLoading = ref(false);
  const metricError = ref('');
  const metricRows = ref<MetricInfo[]>([]);
  const keyword = ref('');

  const metricDialogVisible = ref(false);
  const metricSubmitLoading = ref(false);
  const metricFormRef = ref<FormInstance>();
  const metricForm = reactive<MetricEditForm>({
    metricKey: '',
    name: '',
    aliases: '',
    measureExpr: '',
    filters: '',
    timeField: '',
    description: '',
  });
  const selectedMetric = ref<MetricInfo | null>(null);

  const metricRules: FormRules<MetricEditForm> = {
    metricKey: [{ required: true, message: '指标 key 不能为空', trigger: 'blur' }],
    name: [{ required: true, message: '指标名称不能为空', trigger: 'blur' }],
  };

  const filteredRows = computed(() => {
    const kw = keyword.value.trim().toLowerCase();
    if (!kw) {
      return metricRows.value;
    }
    return metricRows.value.filter(
      r =>
        (r.name || '').toLowerCase().includes(kw) ||
        (r.metricKey || '').toLowerCase().includes(kw) ||
        (r.aliases || '').toLowerCase().includes(kw),
    );
  });

  const loadMetrics = async () => {
    metricLoading.value = true;
    metricError.value = '';
    try {
      const response = await listMetrics();
      metricRows.value = response.data.data ?? [];
    } catch (error) {
      metricError.value = (error as Error).message;
      metricRows.value = [];
    } finally {
      metricLoading.value = false;
    }
  };

  const handleOpenCreate = () => {
    selectedMetric.value = null;
    Object.assign(metricForm, {
      metricKey: '',
      name: '',
      aliases: '',
      measureExpr: '',
      filters: '',
      timeField: '',
      description: '',
    });
    metricDialogVisible.value = true;
  };

  const handleOpenEdit = (row: MetricInfo) => {
    selectedMetric.value = row;
    Object.assign(metricForm, {
      metricKey: row.metricKey,
      name: row.name,
      aliases: row.aliases ?? '',
      measureExpr: row.measureExpr ?? '',
      filters: row.filters ?? '',
      timeField: row.timeField ?? '',
      description: row.description ?? '',
    });
    metricDialogVisible.value = true;
  };

  const handleSubmit = async () => {
    if (!metricFormRef.value) {
      return;
    }

    const valid = await metricFormRef.value.validate().catch(() => false);
    if (!valid) {
      return;
    }

    metricSubmitLoading.value = true;
    try {
      const payload: MetricUpsertRequest = {
        metricKey: metricForm.metricKey.trim(),
        name: metricForm.name.trim(),
        aliases: metricForm.aliases.trim() || undefined,
        measureExpr: metricForm.measureExpr.trim() || undefined,
        filters: metricForm.filters.trim() || undefined,
        timeField: metricForm.timeField.trim() || undefined,
        description: metricForm.description.trim() || undefined,
      };

      if (selectedMetric.value) {
        await updateMetric(selectedMetric.value.id, payload);
        ElMessage.success('指标口径已更新');
      } else {
        await createMetric(payload);
        ElMessage.success('指标口径已创建');
      }

      metricDialogVisible.value = false;
      await loadMetrics();
    } finally {
      metricSubmitLoading.value = false;
    }
  };

  const handleDelete = async (row: MetricInfo) => {
    try {
      await ElMessageBox.confirm(`确定要删除指标口径 ${row.name} 吗？`, '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      });
      await deleteMetric(row.id);
      ElMessage.success('指标口径已删除');
      await loadMetrics();
    } catch {
      // ignore cancel
    }
  };

  onMounted(() => {
    void loadMetrics();
  });
</script>

<template>
  <div class="metric-page">
    <section>
      <div class="section-header">
        <div class="section-header-actions">
          <el-input
            v-model="keyword"
            class="keyword-field"
            clearable
            placeholder="按名称 / key / 同义词搜索"
          />
          <el-button type="primary" @click="handleOpenCreate">新增指标</el-button>
          <el-tag type="primary" effect="plain">共 {{ metricRows.length }} 个指标</el-tag>
        </div>
      </div>

      <el-table v-loading="metricLoading" :data="filteredRows" class="semantic-table">
        <el-table-column prop="metricKey" label="指标 Key" min-width="160" />
        <el-table-column prop="name" label="指标名称" min-width="160" />
        <el-table-column prop="aliases" label="同义词" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.aliases || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="measureExpr" label="度量" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.measureExpr || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="filters" label="过滤" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.filters || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="timeField" label="时间字段" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.timeField || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.description || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.updateTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleOpenEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="metricError" class="error-tip">指标口径加载失败：{{ metricError }}</div>
    </section>

    <el-dialog
      v-model="metricDialogVisible"
      :title="selectedMetric ? '编辑指标口径' : '新增指标口径'"
      width="680px"
    >
      <el-form ref="metricFormRef" :model="metricForm" :rules="metricRules" label-width="110px">
        <el-form-item label="指标 Key" prop="metricKey">
          <el-input
            v-model="metricForm.metricKey"
            :disabled="!!selectedMetric"
            placeholder="稳定标识,如 sales,不可重复"
          />
        </el-form-item>
        <el-form-item label="指标名称" prop="name">
          <el-input v-model="metricForm.name" placeholder="官方中文名,如 销售额" />
        </el-form-item>
        <el-form-item label="同义词">
          <el-input v-model="metricForm.aliases" placeholder="逗号分隔,如 GMV,营收,流水" />
        </el-form-item>
        <el-form-item label="度量">
          <el-input v-model="metricForm.measureExpr" placeholder="如 SUM(paid_amount)" />
        </el-form-item>
        <el-form-item label="过滤条件">
          <el-input v-model="metricForm.filters" placeholder="如 status='paid' AND is_test=0" />
        </el-form-item>
        <el-form-item label="时间字段">
          <el-input v-model="metricForm.timeField" placeholder="如 settle_time" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="metricForm.description"
            type="textarea"
            :rows="3"
            placeholder="业务口径说明 / 规则"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metricDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="metricSubmitLoading" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
  .metric-page {
    display: flex;
    flex-direction: column;
  }

  .section-header {
    display: flex;
    justify-content: flex-end;
    align-items: flex-start;
    gap: 16px;
    margin-bottom: 20px;
  }

  .section-header-actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .keyword-field {
    width: 220px;
  }

  .semantic-table {
    width: 100%;
  }

  .error-tip {
    margin-top: 14px;
    color: var(--app-accent);
  }
</style>
