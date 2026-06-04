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
  import { onMounted, reactive, ref } from 'vue';
  import type { FormInstance, FormRules } from 'element-plus';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import {
    getDomainPage,
    createDomain,
    updateDomain,
    deleteDomain,
    type DomainInfo,
  } from '@/api/domain';

  interface DomainEditForm {
    name: string;
    description: string;
  }

  const props = defineProps<{
    keyword: string;
    sortOrder: 'asc' | 'desc';
  }>();

  const domainLoading = ref(false);
  const domainError = ref('');
  const domainRows = ref<DomainInfo[]>([]);
  const domainPage = reactive({
    page: 1,
    pageSize: 10,
    total: 0,
  });

  const domainDialogVisible = ref(false);
  const domainSubmitLoading = ref(false);
  const domainFormRef = ref<FormInstance>();
  const domainForm = reactive<DomainEditForm>({
    name: '',
    description: '',
  });
  const selectedDomain = ref<DomainInfo | null>(null);

  const domainRules: FormRules<DomainEditForm> = {
    name: [{ required: true, message: '领域名称不能为空', trigger: 'blur' }],
  };

  const loadDomainPage = async () => {
    domainLoading.value = true;
    domainError.value = '';
    try {
      const response = await getDomainPage({
        page: domainPage.page,
        pageSize: domainPage.pageSize,
        keyword: props.keyword.trim() || undefined,
        sortOrder: props.sortOrder,
      });
      const pageData = response.data.data;
      domainRows.value = pageData.items;
      domainPage.total = pageData.total;
    } catch (error) {
      domainError.value = (error as Error).message;
      domainRows.value = [];
      domainPage.total = 0;
    } finally {
      domainLoading.value = false;
    }
  };

  const handleDomainPageChange = async (page: number) => {
    domainPage.page = page;
    await loadDomainPage();
  };

  const handleDomainSizeChange = async (pageSize: number) => {
    domainPage.pageSize = pageSize;
    domainPage.page = 1;
    await loadDomainPage();
  };

  const handleOpenDomainCreate = () => {
    selectedDomain.value = null;
    Object.assign(domainForm, {
      name: '',
      description: '',
    });
    domainDialogVisible.value = true;
  };

  const handleOpenDomainEdit = (row: DomainInfo) => {
    selectedDomain.value = row;
    Object.assign(domainForm, {
      name: row.name,
      description: row.description ?? '',
    });
    domainDialogVisible.value = true;
  };

  const handleSubmitDomain = async () => {
    if (!domainFormRef.value) {
      return;
    }

    const valid = await domainFormRef.value.validate().catch(() => false);
    if (!valid) {
      return;
    }

    domainSubmitLoading.value = true;
    try {
      const payload = {
        name: domainForm.name.trim(),
        description: domainForm.description.trim() || undefined,
      };

      if (selectedDomain.value) {
        await updateDomain(selectedDomain.value.id, payload);
        ElMessage.success('领域已更新');
      } else {
        await createDomain(payload);
        ElMessage.success('领域已创建');
      }

      domainDialogVisible.value = false;
      await loadDomainPage();
    } finally {
      domainSubmitLoading.value = false;
    }
  };

  const handleDeleteDomain = async (row: DomainInfo) => {
    try {
      await ElMessageBox.confirm(`确定要删除领域 ${row.name} 吗？`, '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      });
      await deleteDomain(row.id);
      ElMessage.success('领域已删除');
      await loadDomainPage();
    } catch {
      // ignore cancel
    }
  };

  const formatTime = (value: string | null) => {
    if (!value) {
      return '-';
    }
    return value.replace('T', ' ');
  };

  // 暴露方法给父组件
  defineExpose({
    loadDomainPage,
  });

  onMounted(() => {
    void loadDomainPage();
  });
</script>

<template>
  <section class="table-panel">
    <div class="section-header">
      <div>
        <h3>数据领域列表</h3>
        <p>管理全局数据领域标签，可用于表语义配置。</p>
      </div>
      <div class="section-header-actions">
        <el-button type="primary" @click="handleOpenDomainCreate">新增领域</el-button>
        <el-tag type="primary" effect="plain">共 {{ domainPage.total }} 个领域</el-tag>
      </div>
    </div>

    <el-table v-loading="domainLoading" :data="domainRows" class="semantic-table">
      <el-table-column prop="name" label="领域名称" min-width="200" />
      <el-table-column prop="description" label="描述" min-width="300" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.description || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.updateTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleOpenDomainEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDeleteDomain(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="domainError" class="error-tip">领域加载失败：{{ domainError }}</div>

    <div class="pagination-wrap">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :current-page="domainPage.page"
        :page-size="domainPage.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="domainPage.total"
        @current-change="handleDomainPageChange"
        @size-change="handleDomainSizeChange"
      />
    </div>

    <el-dialog
      v-model="domainDialogVisible"
      :title="selectedDomain ? '编辑领域' : '新增领域'"
      width="640px"
    >
      <el-form ref="domainFormRef" :model="domainForm" :rules="domainRules" label-width="110px">
        <el-form-item label="领域名称" prop="name">
          <el-input v-model="domainForm.name" placeholder="例如：会员、订单、商品" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="domainForm.description"
            type="textarea"
            :rows="4"
            placeholder="请输入领域的业务说明"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="domainDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="domainSubmitLoading" @click="handleSubmitDomain">
          保存
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 16px;
    margin-bottom: 20px;
  }

  .section-header h3 {
    font-size: 16px;
    color: #1f2937;
    margin-bottom: 6px;
    font-weight: 600;
  }

  .section-header p {
    color: #64748b;
  }

  .section-header-actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .semantic-table {
    width: 100%;
  }

  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }

  .error-tip {
    margin-top: 14px;
    color: #dc2626;
  }
</style>
