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
  import { onMounted, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import HelpTip from '@/components/common/HelpTip.vue';
  import { useDatasource } from '@/composables/useDatasource';
  import { formatDateTime } from '@/utils/dateTime';
  import {
    getTableSemanticPage,
    refreshPhysicalStatus,
    resetTableSemantic,
    type TableSemanticInfo,
  } from '@/api/semantic';
  import { useSemanticPage } from '../composables/useSemanticPage';
  import TableSemanticEditDialog from './TableSemanticEditDialog.vue';
  import ColumnSemanticManage from './ColumnSemanticManage.vue';
  import SyncPhysicalTableDialog from './SyncPhysicalTableDialog.vue';
  import { buildSyncSummary, physicalStatusSyncSummaryFields } from '../utils';

  const route = useRoute();
  const router = useRouter();
  const {
    list: datasourceList,
    loading: datasourceLoading,
    error: datasourceError,
    fetchList: fetchDatasourceList,
  } = useDatasource();
  const datasourceId = ref<number | null>(null);
  const editingTable = ref<TableSemanticInfo | null>(null);

  const columnDrawerVisible = ref(false);
  const selectedTableForColumns = ref('');
  const columnManageRef = ref<InstanceType<typeof ColumnSemanticManage>>();

  const syncDialogVisible = ref(false);
  const refreshingPhysicalStatus = ref(false);

  const { loading, error, rows, page, loadPage, handlePageChange, handleSizeChange } =
    useSemanticPage<TableSemanticInfo>(async pagination => {
      if (datasourceId.value === null) return { items: [], total: 0 };
      return getTableSemanticPage({ datasourceId: datasourceId.value, ...pagination });
    });

  const handleDatasourceChange = async () => {
    editingTable.value = null;
    columnDrawerVisible.value = false;
    selectedTableForColumns.value = '';
    syncDialogVisible.value = false;
    page.page = 1;
    page.total = 0;
    if (datasourceId.value !== null && route.query.datasourceId !== String(datasourceId.value)) {
      void router.replace({ query: { ...route.query, datasourceId: datasourceId.value } });
    }
    await loadPage();
  };

  watch(
    () => route.query.datasourceId,
    value => {
      const datasource = datasourceList.value.find(item => String(item.id) === value);
      if (!datasource || datasource.id === datasourceId.value) return;
      datasourceId.value = datasource.id;
      void handleDatasourceChange();
    },
  );

  const handleReset = async (row: TableSemanticInfo) => {
    const selectedDatasourceId = datasourceId.value;
    if (selectedDatasourceId === null) return;
    try {
      await ElMessageBox.confirm(
        `确认重置表 ${row.tableName} 的语义信息吗？若该表当前已标记为物理缺失，还会删除该表及其列的语义记录和所有关联到该表的逻辑关系；重新同步不会恢复这些关系。`,
        '确认重置',
        {
          type: 'warning',
        },
      );
      await resetTableSemantic(selectedDatasourceId, row.tableName);
      ElMessage.success('重置成功');
      await loadPage();
    } catch (err) {
      if (err !== 'cancel') {
        ElMessage.error((err as Error).message);
      }
    }
  };

  const handleManageColumns = (row: TableSemanticInfo) => {
    if (datasourceId.value === null) return;
    selectedTableForColumns.value = row.tableName;
    columnDrawerVisible.value = true;
  };

  const handleColumnsDrawerOpened = () => {
    void columnManageRef.value?.handleTableChange(selectedTableForColumns.value);
  };

  const handleOpenSync = () => {
    if (datasourceId.value === null) return;
    syncDialogVisible.value = true;
  };

  const handleRefreshPhysicalStatus = async () => {
    const selectedDatasourceId = datasourceId.value;
    if (selectedDatasourceId === null) return;
    refreshingPhysicalStatus.value = true;
    try {
      const result = await refreshPhysicalStatus(selectedDatasourceId);
      ElMessage.success(buildSyncSummary(result, physicalStatusSyncSummaryFields));
      await loadPage();
      if (columnDrawerVisible.value && selectedTableForColumns.value && columnManageRef.value) {
        await columnManageRef.value.handleTableChange(selectedTableForColumns.value);
      }
    } catch (err) {
      ElMessage.error((err as Error).message);
    } finally {
      refreshingPhysicalStatus.value = false;
    }
  };

  const handleSyncCompleted = async (selectedTableNames: string[]) => {
    await loadPage();
    const normalizedSelectedTableNameSet = new Set(
      selectedTableNames.map(tableName => tableName.trim().toLowerCase()),
    );
    if (
      columnDrawerVisible.value &&
      selectedTableForColumns.value &&
      normalizedSelectedTableNameSet.has(selectedTableForColumns.value.trim().toLowerCase()) &&
      columnManageRef.value
    ) {
      await columnManageRef.value.handleTableChange(selectedTableForColumns.value);
    }
  };

  onMounted(async () => {
    await fetchDatasourceList();
    const requested = datasourceList.value.find(
      item => String(item.id) === route.query.datasourceId,
    );
    const firstActive = datasourceList.value.find(item => item.status === 'ACTIVE');
    datasourceId.value = requested?.id ?? firstActive?.id ?? datasourceList.value[0]?.id ?? null;
    await loadPage();
  });
</script>

<template>
  <section class="table-panel">
    <div class="page-header">
      <h2 class="page-title page-title-with-help">
        表语义
        <HelpTip>
          <strong>表语义</strong>
          说明表或普通视图在业务上代表什么、属于哪个领域、是否对 AI
          可见，以及它和其他对象的逻辑关系。数据粒度用于说明一行代表什么。
        </HelpTip>
      </h2>
    </div>

    <div class="section-header">
      <div class="section-header-actions">
        <el-select
          v-model="datasourceId"
          class="datasource-field"
          filterable
          placeholder="选择数据源"
          :loading="datasourceLoading"
          :disabled="datasourceLoading || refreshingPhysicalStatus"
          @change="handleDatasourceChange"
        >
          <el-option
            v-for="item in datasourceList"
            :key="item.id"
            :label="`${item.name} (${item.type})`"
            :value="item.id"
          />
        </el-select>
        <el-tag type="primary" effect="plain">共 {{ page.total }} 个表或视图</el-tag>
        <el-button
          v-permission="'semantic:table:sync'"
          :loading="refreshingPhysicalStatus"
          :disabled="datasourceId === null"
          @click="handleRefreshPhysicalStatus"
        >
          刷新物理状态
        </el-button>
        <el-button
          v-permission="'semantic:table:sync'"
          type="primary"
          :disabled="datasourceId === null || refreshingPhysicalStatus"
          @click="handleOpenSync"
        >
          同步表或视图
        </el-button>
      </div>
    </div>

    <div v-if="datasourceError || error" class="error-banner">
      <el-alert type="error" :closable="false" show-icon>
        {{ datasourceError ? `数据源加载失败：${datasourceError.message}` : error }}
      </el-alert>
    </div>

    <el-table
      v-loading="datasourceLoading || loading"
      :data="rows"
      :empty-text="datasourceId === null ? '暂无可访问的数据源' : '暂无表语义'"
    >
      <el-table-column prop="tableName" label="对象名称" min-width="150" />
      <el-table-column label="类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.objectType === 'VIEW' ? 'warning' : 'info'" size="small">
            {{ row.objectType === 'VIEW' ? '普通视图' : '表' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="domain" label="领域" min-width="120" />
      <el-table-column label="语义描述" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.tableDescription || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="数据粒度" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.dataGranularity || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="isVisible" label="可见性" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isVisible ? 'success' : 'info'" size="small">
            {{ row.isVisible ? '可见' : '隐藏' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="物理状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="row.hasPhysicalTable ? 'success' : 'warning'" size="small">
            {{ row.hasPhysicalTable ? '存在' : '缺失' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.updateTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'semantic:table:update'"
            link
            type="primary"
            size="small"
            @click="editingTable = row"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'semantic:column:read'"
            link
            type="primary"
            size="small"
            @click="handleManageColumns(row)"
          >
            管理列
          </el-button>
          <el-button
            v-permission="'semantic:table:update'"
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

    <div class="pagination-wrap">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :current-page="page.page"
        :page-size="page.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="page.total"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <TableSemanticEditDialog
      v-if="editingTable && datasourceId !== null"
      :table="editingTable"
      :datasource-id="datasourceId"
      @close="editingTable = null"
      @saved="loadPage"
    />

    <el-drawer
      v-model="columnDrawerVisible"
      :title="`列语义管理 - ${selectedTableForColumns}`"
      direction="rtl"
      size="90%"
      @opened="handleColumnsDrawerOpened"
    >
      <ColumnSemanticManage
        v-if="columnDrawerVisible"
        ref="columnManageRef"
        :datasource-id="datasourceId"
      />
    </el-drawer>

    <SyncPhysicalTableDialog
      :key="datasourceId ?? 0"
      v-model="syncDialogVisible"
      :datasource-id="datasourceId"
      @synced="handleSyncCompleted"
    />
  </section>
</template>

<style scoped>
  .datasource-field {
    width: 240px;
  }

  .error-banner {
    margin-bottom: 16px;
  }

  .pagination-wrap {
    margin-top: 20px;
  }
</style>
