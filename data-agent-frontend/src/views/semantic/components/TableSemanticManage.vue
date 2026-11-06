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
  import { onMounted, ref } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import HelpTip from '@/components/common/HelpTip.vue';
  import { formatDateTime } from '@/utils/dateTime';
  import {
    getActiveDatasourceId,
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

  const datasourceId = ref<number | null>(null);
  const editingTable = ref<TableSemanticInfo | null>(null);

  const columnDrawerVisible = ref(false);
  const selectedTableForColumns = ref('');
  const columnManageRef = ref<InstanceType<typeof ColumnSemanticManage>>();

  const syncDialogVisible = ref(false);
  const refreshingPhysicalStatus = ref(false);
  let datasourceRequest: Promise<number | null> | undefined;

  const ensureDatasourceId = async () => {
    if (datasourceId.value !== null) {
      return datasourceId.value;
    }
    datasourceRequest ??= getActiveDatasourceId().finally(() => {
      datasourceRequest = undefined;
    });
    datasourceId.value = await datasourceRequest;
    return datasourceId.value;
  };

  const { loading, error, rows, page, loadPage, handlePageChange, handleSizeChange } =
    useSemanticPage<TableSemanticInfo>(async pagination => {
      const activeDatasourceId = await ensureDatasourceId();
      if (activeDatasourceId === null) throw new Error('请先激活一个数据源');
      return getTableSemanticPage({ datasourceId: activeDatasourceId, ...pagination });
    });

  const handleReset = async (row: TableSemanticInfo) => {
    try {
      await ElMessageBox.confirm(
        `确认重置表 ${row.tableName} 的语义信息吗？若该表当前已标记为物理缺失，还会删除该表及其列的语义记录和所有关联到该表的逻辑关系；重新同步不会恢复这些关系。`,
        '确认重置',
        {
          type: 'warning',
        },
      );
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
    if ((await ensureDatasourceId()) === null) return;
    selectedTableForColumns.value = row.tableName;
    columnDrawerVisible.value = true;
  };

  const handleColumnsDrawerOpened = () => {
    void columnManageRef.value?.handleTableChange(selectedTableForColumns.value);
  };

  const handleOpenSync = async () => {
    const activeDatasourceId = await ensureDatasourceId();
    if (activeDatasourceId === null) {
      return;
    }
    syncDialogVisible.value = true;
  };

  const handleRefreshPhysicalStatus = async () => {
    const activeDatasourceId = await ensureDatasourceId();
    if (activeDatasourceId === null) {
      return;
    }
    refreshingPhysicalStatus.value = true;
    try {
      const result = await refreshPhysicalStatus(activeDatasourceId);
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

  onMounted(() => {
    loadPage();
  });
</script>

<template>
  <section class="table-panel">
    <div class="page-header">
      <h2 class="page-title page-title-with-help">
        表语义
        <HelpTip>
          <strong>表语义</strong>
          说明一张物理表在业务上代表什么、属于哪个领域、是否允许被 AI
          查询，以及它和其他表的逻辑关系。数据粒度用于说明表中一行代表什么。
        </HelpTip>
      </h2>
    </div>

    <div class="section-header">
      <div class="section-header-actions">
        <el-tag type="primary" effect="plain">共 {{ page.total }} 张表</el-tag>
        <el-button
          v-permission="'semantic:table:sync'"
          :loading="refreshingPhysicalStatus"
          @click="handleRefreshPhysicalStatus"
        >
          刷新物理状态
        </el-button>
        <el-button v-permission="'semantic:table:sync'" type="primary" @click="handleOpenSync">
          同步表
        </el-button>
      </div>
    </div>

    <div v-if="error" class="error-banner">
      <el-alert type="error" :closable="false" show-icon>{{ error }}</el-alert>
    </div>

    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="tableName" label="表名" min-width="150" />
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
      <ColumnSemanticManage ref="columnManageRef" :datasource-id="datasourceId" />
    </el-drawer>

    <SyncPhysicalTableDialog
      v-model="syncDialogVisible"
      :datasource-id="datasourceId"
      @synced="handleSyncCompleted"
    />
  </section>
</template>

<style scoped>
  .error-banner {
    margin-bottom: 16px;
  }

  .pagination-wrap {
    margin-top: 20px;
  }
</style>
