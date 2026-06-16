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
  import { onMounted, reactive, ref, nextTick } from 'vue';
  import type { FormInstance, FormRules } from 'element-plus';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import {
    getActiveDatasourceId,
    getTableSemanticPage,
    getTableDomains,
    resetTableSemantic,
    updateTableSemantic,
    type TableSemanticInfo,
  } from '@/api/semantic';
  import ColumnSemanticManage from './ColumnSemanticManage.vue';

  interface TableEditForm {
    tableName: string;
    domain: string;
    tableDescription: string;
    isVisible: boolean;
  }

  const props = defineProps<{
    keyword: string;
    sortOrder: 'asc' | 'desc';
  }>();

  const loading = ref(false);
  const error = ref('');
  const rows = ref<TableSemanticInfo[]>([]);
  const datasourceId = ref<number | null>(null);
  const domainOptions = ref<string[]>([]);
  const page = reactive({
    page: 1,
    pageSize: 10,
    total: 0,
  });

  const dialogVisible = ref(false);
  const submitLoading = ref(false);
  const formRef = ref<FormInstance>();
  const form = reactive<TableEditForm>({
    tableName: '',
    domain: '',
    tableDescription: '',
    isVisible: true,
  });
  const rules: FormRules<TableEditForm> = {
    tableName: [{ required: true, message: '表名不能为空', trigger: 'blur' }],
  };

  const columnDrawerVisible = ref(false);
  const selectedTableForColumns = ref('');
  const columnManageRef = ref<InstanceType<typeof ColumnSemanticManage>>();

  const ensureDatasourceId = async () => {
    if (datasourceId.value !== null) {
      return datasourceId.value;
    }
    datasourceId.value = await getActiveDatasourceId();
    if (datasourceId.value === null) {
      error.value = '请先激活一个数据源';
      rows.value = [];
      page.total = 0;
    }
    return datasourceId.value;
  };

  const loadDomainOptions = async () => {
    const activeDatasourceId = await ensureDatasourceId();
    if (activeDatasourceId === null) {
      domainOptions.value = [];
      return;
    }
    try {
      const response = await getTableDomains(activeDatasourceId);
      domainOptions.value = response.data.data;
    } catch {
      domainOptions.value = [];
    }
  };

  const loadPage = async () => {
    loading.value = true;
    error.value = '';
    try {
      const activeDatasourceId = await ensureDatasourceId();
      if (activeDatasourceId === null) {
        return;
      }
      const response = await getTableSemanticPage({
        datasourceId: activeDatasourceId,
        page: page.page,
        pageSize: page.pageSize,
        keyword: props.keyword.trim() || undefined,
        sortOrder: props.sortOrder,
      });
      const pageData = response.data.data;
      rows.value = pageData.items;
      page.total = pageData.total;
    } catch (err) {
      error.value = (err as Error).message;
      rows.value = [];
      page.total = 0;
    } finally {
      loading.value = false;
    }
  };

  const handlePageChange = async (p: number) => {
    page.page = p;
    await loadPage();
  };

  const handleSizeChange = async (pageSize: number) => {
    page.pageSize = pageSize;
    page.page = 1;
    await loadPage();
  };

  const handleOpenEdit = (row: TableSemanticInfo) => {
    Object.assign(form, {
      tableName: row.tableName,
      domain: row.domain,
      tableDescription: row.tableDescription ?? row.physicalTableDescription ?? '',
      isVisible: row.isVisible,
    });
    // 加载领域选项
    loadDomainOptions();
    dialogVisible.value = true;
  };

  const handleSubmit = async () => {
    if (!formRef.value) return;
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;
    const activeDatasourceId = await ensureDatasourceId();
    if (activeDatasourceId === null) return;
    submitLoading.value = true;
    try {
      await updateTableSemantic({
        datasourceId: activeDatasourceId,
        tableName: form.tableName,
        domain: form.domain.trim() || undefined,
        tableDescription: form.tableDescription.trim() || undefined,
        isVisible: form.isVisible,
      });
      ElMessage.success('更新成功');
      dialogVisible.value = false;
      await loadPage();
    } catch (err) {
      ElMessage.error((err as Error).message);
    } finally {
      submitLoading.value = false;
    }
  };

  const handleReset = async (row: TableSemanticInfo) => {
    try {
      await ElMessageBox.confirm(`确认重置表 ${row.tableName} 的语义信息吗？`, '确认重置', {
        type: 'warning',
      });
      const activeDatasourceId = await ensureDatasourceId();
      if (activeDatasourceId === null) return;
      await resetTableSemantic(activeDatasourceId, row.tableName);
      ElMessage.success('重置成功');
      await loadPage();
    } catch (err) {
      if (err !== 'cancel') {
        ElMessage.error((err as Error).message);
      }
    }
  };

  const handleManageColumns = async (row: TableSemanticInfo) => {
    selectedTableForColumns.value = row.tableName;
    columnDrawerVisible.value = true;
    // 等待 drawer 打开
    await nextTick();
    if (columnManageRef.value) {
      await columnManageRef.value.handleTableChange(row.tableName);
    }
  };

  defineExpose({
    loadPage,
  });

  onMounted(() => {
    loadPage();
  });
</script>

<template>
  <div class="table-semantic-container">
    <div v-if="error" class="error-banner">
      <el-alert type="error" :closable="false" show-icon>{{ error }}</el-alert>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="tableName" label="表名" min-width="150" />
      <el-table-column prop="domain" label="领域" min-width="120" />
      <el-table-column label="物理描述" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.physicalTableDescription || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="语义描述" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.tableDescription || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="isVisible" label="可见性" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isVisible ? 'success' : 'info'" size="small">
            {{ row.isVisible ? '可见' : '隐藏' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="effective" label="有效性" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.effective ? 'success' : 'warning'" size="small">
            {{ row.effective ? '有效' : '无效' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleOpenEdit(row)">
            编辑
          </el-button>
          <el-button link type="primary" size="small" @click="handleManageColumns(row)">
            管理列
          </el-button>
          <el-button
            v-if="row.id !== null"
            link
            type="warning"
            size="small"
            @click="handleReset(row)"
          >
            重置
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="page.page"
        v-model:page-size="page.pageSize"
        :total="page.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      title="编辑表语义"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="表名" prop="tableName">
          <el-input v-model="form.tableName" disabled placeholder="请输入表名" />
        </el-form-item>
        <el-form-item label="领域" prop="domain">
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
        <el-form-item label="语义描述" prop="tableDescription">
          <el-input
            v-model="form.tableDescription"
            type="textarea"
            :rows="4"
            placeholder="请输入表的语义描述信息（为空时将使用物理描述）"
          />
        </el-form-item>
        <el-form-item label="可见性" prop="isVisible">
          <el-switch v-model="form.isVisible" active-text="可见" inactive-text="隐藏" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="columnDrawerVisible"
      :title="`列语义管理 - ${selectedTableForColumns}`"
      direction="rtl"
      size="65%"
    >
      <ColumnSemanticManage
        ref="columnManageRef"
        :keyword="''"
        :sort-order="'asc'"
        :table-name="selectedTableForColumns"
      />
    </el-drawer>
  </div>
</template>

<style scoped>
  .table-semantic-container {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .error-banner {
    margin-bottom: 16px;
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    padding: 16px 0;
  }
</style>
