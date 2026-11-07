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
  import { computed, onBeforeUnmount, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { useUserStore } from '@/stores/user';
  import { deleteCsv, getCsvImports, syncCsv, type CsvImport } from '@/api/csvImport';
  import CsvUploadDialog from './CsvUploadDialog.vue';

  const props = defineProps<{ modelValue: boolean }>();
  const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>();
  const visible = computed({
    get: () => props.modelValue,
    set: value => emit('update:modelValue', value),
  });
  const user = useUserStore();
  const router = useRouter();
  const rows = ref<CsvImport[]>([]);
  const total = ref(0);
  const page = ref(1);
  const loading = ref(false);
  const uploadVisible = ref(false);
  const initial = ref<CsvImport>();
  const operating = ref('');
  let timer: ReturnType<typeof setTimeout> | undefined;
  const pending = new Set(['QUEUED', 'IMPORTING', 'DATA_READY', 'SYNCING', 'CLEANING', 'DELETING']);
  const statusLabels: Record<string, string> = {
    PREVIEWED: '待确认',
    QUEUED: '排队中',
    IMPORTING: '导入中',
    DATA_READY: '同步语义中',
    SYNCING: '同步语义中',
    CLEANING: '清理中',
    CLEANUP_FAILED: '清理失败',
    READY: '已就绪',
    IMPORT_FAILED: '导入失败',
    SYNC_FAILED: '语义同步失败',
    DELETING: '删除中',
    DELETE_FAILED: '删除失败',
    EXPIRED: '文件已过期',
  };

  function stopPolling() {
    clearTimeout(timer);
  }

  async function load() {
    stopPolling();
    if (!visible.value || loading.value) return;
    loading.value = true;
    try {
      const result = await getCsvImports(page.value, 10);
      rows.value = result.items;
      total.value = result.total;
      if (visible.value && rows.value.some(row => pending.has(row.status)))
        timer = setTimeout(load, 3000);
    } catch {
      // The request interceptor displays the error; refresh explicitly to retry.
    } finally {
      loading.value = false;
    }
  }

  watch(
    () => props.modelValue,
    open => {
      if (open) {
        page.value = 1;
        load();
      } else stopPolling();
    },
  );
  onBeforeUnmount(stopPolling);

  function upload(row?: CsvImport) {
    initial.value = row;
    uploadVisible.value = true;
  }

  async function retry(row: CsvImport) {
    operating.value = row.id;
    try {
      await syncCsv(row.id);
      await load();
    } catch {
      /* The request interceptor displays the error. */
    } finally {
      operating.value = '';
    }
  }

  async function remove(row: CsvImport) {
    try {
      await ElMessageBox.confirm(
        `将删除「${row.displayName}」及其导入数据，无法恢复。`,
        '删除 CSV',
        { type: 'warning' },
      );
    } catch {
      return;
    }
    operating.value = row.id;
    try {
      await deleteCsv(row.id);
      ElMessage.success('已删除');
      if (rows.value.length === 1 && page.value > 1) page.value--;
      await load();
    } catch {
      /* The request interceptor displays the error. */
    } finally {
      operating.value = '';
    }
  }

  function semantic() {
    visible.value = false;
    router.push('/semantic/table');
  }
</script>

<template>
  <el-drawer v-model="visible" title="CSV 分析空间" size="min(1200px, 95vw)">
    <el-alert
      title="每个 CSV 文件导入为一张表。完成后可补充语义，并在角色授权中配置表的访问权限。"
      type="info"
      :closable="false"
    />
    <div class="csv-actions">
      <el-button v-permission="'data:csv:import'" type="primary" @click="upload()">
        导入 CSV
      </el-button>
      <el-button :loading="loading" @click="load">刷新</el-button>
      <el-button v-permission="'semantic:table:read'" @click="semantic">管理表语义</el-button>
    </div>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="displayName" label="表名称" min-width="150" show-overflow-tooltip />
      <el-table-column
        prop="originalFilename"
        label="原文件"
        min-width="150"
        show-overflow-tooltip
      />
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-tag
            :type="
              row.status === 'READY' ? 'success' : row.status.includes('FAILED') ? 'danger' : 'info'
            "
          >
            {{ statusLabels[row.status] ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="记录数" width="110">
        <template #default="{ row }">
          {{ row.status === 'READY' ? row.rowCount : row.processedRows }}
        </template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="错误说明" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="190">
        <template #default="{ row }">
          <el-button
            v-if="['PREVIEWED', 'IMPORT_FAILED'].includes(row.status)"
            v-permission="'data:csv:import'"
            link
            type="primary"
            @click="upload(row)"
          >
            调整并导入
          </el-button>
          <el-button
            v-if="
              row.status === 'SYNC_FAILED' &&
              user.hasPermission('data:csv:import') &&
              user.hasPermission('semantic:table:sync')
            "
            link
            type="primary"
            :loading="operating === row.id"
            @click="retry(row)"
          >
            重试同步
          </el-button>
          <el-button
            v-if="!pending.has(row.status)"
            v-permission="'data:csv:delete'"
            link
            type="danger"
            :disabled="operating === row.id"
            @click="remove(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :page-size="10"
      :total="total"
      layout="prev, pager, next, total"
      class="csv-pagination"
      @current-change="load"
    />
    <CsvUploadDialog v-model="uploadVisible" :initial="initial" @imported="load" />
  </el-drawer>
</template>

<style scoped>
  .csv-actions {
    display: flex;
    gap: 8px;
    margin: 18px 0;
  }
  .csv-pagination {
    margin-top: 18px;
    justify-content: flex-end;
  }
</style>
