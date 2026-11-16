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
  import { onMounted, ref } from 'vue';
  import { getDatasourcePhysicalTables, type DatasourceTable } from '@/api/datasource';

  const visible = defineModel<boolean>({ required: true });

  const props = defineProps<{
    datasourceId: number;
    datasourceName: string;
  }>();

  const tables = ref<DatasourceTable[]>([]);
  const loading = ref(false);
  const failed = ref(false);

  const loadTables = async () => {
    if (loading.value) return;

    loading.value = true;
    failed.value = false;
    tables.value = [];

    try {
      tables.value = await getDatasourcePhysicalTables(props.datasourceId);
    } catch {
      failed.value = true;
      // 请求错误由现有 request 拦截器统一提示。
    } finally {
      loading.value = false;
    }
  };

  onMounted(() => {
    void loadTables();
  });
</script>

<template>
  <el-drawer v-model="visible" :title="`${datasourceName} 的表`" size="min(720px, 100%)">
    <div v-loading="loading" class="tables-content">
      <template v-if="failed">
        <el-alert
          title="读取表列表失败，请检查数据源连接和数据库权限"
          type="error"
          :closable="false"
          show-icon
        />
        <el-button class="retry-button" @click="loadTables">重新加载</el-button>
      </template>

      <template v-else-if="!loading">
        <p class="table-count">共 {{ tables.length }} 个表或视图</p>

        <el-table :data="tables" empty-text="当前数据库没有可见的表或视图">
          <el-table-column prop="tableName" label="名称" min-width="180" show-overflow-tooltip />

          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag :type="row.objectType === 'VIEW' ? 'warning' : 'info'">
                {{
                  row.objectType === 'VIEW'
                    ? '视图'
                    : row.objectType === 'TABLE'
                      ? '表'
                      : row.objectType
                }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="备注" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.remarks || '-' }}
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
  .tables-content {
    min-height: 160px;
  }

  .table-count {
    margin: 0 0 16px;
    color: var(--app-text-secondary);
  }

  .retry-button {
    margin-top: 16px;
  }
</style>
