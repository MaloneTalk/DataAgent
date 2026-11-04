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
  import { computed, onMounted, ref } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import HelpTip from '@/components/common/HelpTip.vue';
  import { useDatasource } from '@/composables/useDatasource';
  import {
    listMetrics,
    deleteMetric,
    metricCalculationTypeLabels,
    metricValueTypeLabels,
    type MetricInfo,
  } from '@/api/metric';
  import { formatDateTime } from '@/utils/dateTime';
  import MetricEditDialog from './components/MetricEditDialog.vue';

  const metricLoading = ref(false);
  const metricError = ref('');
  const metricRows = ref<MetricInfo[]>([]);
  const keyword = ref('');
  const page = ref(1);
  const pageSize = ref(10);
  const total = ref(0);

  const {
    list: datasourceList,
    loading: datasourceLoading,
    error: datasourceError,
    fetchList: fetchDatasourceList,
  } = useDatasource();
  const selectedDatasourceId = ref<number>();
  const datasourceEmpty = ref(false);

  const metricDialogVisible = ref(false);
  const selectedMetric = ref<MetricInfo | null>(null);

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
    if (selectedDatasourceId.value === undefined) {
      metricRows.value = [];
      total.value = 0;
      return;
    }
    metricLoading.value = true;
    metricError.value = '';
    try {
      const result = await listMetrics({
        datasourceId: selectedDatasourceId.value,
        page: page.value,
        pageSize: pageSize.value,
      });
      metricRows.value = result.items;
      total.value = result.total;
    } catch (error) {
      metricError.value = (error as Error).message;
      metricRows.value = [];
      total.value = 0;
    } finally {
      metricLoading.value = false;
    }
  };

  const handleDatasourceChange = () => {
    page.value = 1;
    void loadMetrics();
  };

  const handlePageChange = (newPage: number) => {
    page.value = newPage;
    void loadMetrics();
  };

  const handleSizeChange = (newSize: number) => {
    pageSize.value = newSize;
    page.value = 1;
    void loadMetrics();
  };

  const handleOpenCreate = () => {
    selectedMetric.value = null;
    metricDialogVisible.value = true;
  };

  const handleOpenEdit = (row: MetricInfo) => {
    selectedMetric.value = row;
    metricDialogVisible.value = true;
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
      if (metricRows.value.length === 1 && page.value > 1) {
        page.value -= 1;
      }
      await loadMetrics();
    } catch {
      // ignore cancel
    }
  };

  const initialize = async () => {
    await fetchDatasourceList();
    if (datasourceList.value.length === 0) {
      datasourceEmpty.value = true;
      metricRows.value = [];
      total.value = 0;
      return;
    }
    datasourceEmpty.value = false;
    const firstActive = datasourceList.value.find(item => item.status === 'ACTIVE');
    selectedDatasourceId.value = firstActive?.id ?? datasourceList.value[0]?.id;
    await loadMetrics();
  };

  onMounted(() => {
    void initialize();
  });
</script>

<template>
  <div class="metric-page">
    <div class="page-header">
      <h2 class="page-title page-title-with-help">
        指标口径
        <HelpTip>
          <strong>指标口径</strong>
          是指标的统一计算规则。它定义业务叫法、计算方式、结果类型、单位、倍率、币种、过滤条件和时间字段，供
          Agent 生成 SQL 时检索使用。
        </HelpTip>
      </h2>
    </div>

    <section>
      <div class="section-header">
        <div class="section-header-actions">
          <el-select
            v-model="selectedDatasourceId"
            class="datasource-field"
            filterable
            placeholder="选择数据源"
            :loading="datasourceLoading"
            @change="handleDatasourceChange"
          >
            <el-option
              v-for="item in datasourceList"
              :key="item.id"
              :label="`${item.name} (${item.type})`"
              :value="item.id"
            />
          </el-select>
          <el-input
            v-model="keyword"
            class="keyword-field"
            clearable
            placeholder="按名称 / key / 同义词搜索"
          />
          <el-button
            v-permission="'semantic:metric:create'"
            v-if="!datasourceEmpty"
            type="primary"
            @click="handleOpenCreate"
          >
            新增指标
          </el-button>
          <el-tag v-if="!datasourceEmpty" type="primary" effect="plain">
            共 {{ total }} 个指标
          </el-tag>
        </div>
      </div>

      <div v-if="datasourceError" class="semantic-error-tip">
        数据源加载失败：{{ datasourceError.message }}
      </div>
      <div v-else-if="datasourceEmpty" class="semantic-error-tip">
        暂无数据源，请先在「数据源管理」中添加数据源
      </div>

      <template v-else>
        <el-table v-loading="metricLoading" :data="filteredRows" class="semantic-table">
          <el-table-column prop="metricKey" label="指标 Key" min-width="160" />
          <el-table-column prop="name" label="指标名称" min-width="160" />
          <el-table-column prop="calculationType" label="计算方式" min-width="140">
            <template #default="{ row }: { row: MetricInfo }">
              {{ metricCalculationTypeLabels[row.calculationType] || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="valueType" label="结果类型" min-width="120">
            <template #default="{ row }: { row: MetricInfo }">
              {{ row.valueType ? metricValueTypeLabels[row.valueType] : '未配置' }}
            </template>
          </el-table-column>
          <el-table-column prop="unit" label="标准单位" min-width="100">
            <template #default="{ row }">{{ row.unit || '-' }}</template>
          </el-table-column>
          <el-table-column
            prop="resultMultiplier"
            label="结果倍率"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column prop="currencyCode" label="币种" min-width="90">
            <template #default="{ row }">{{ row.currencyCode || '-' }}</template>
          </el-table-column>
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
              <el-button
                v-permission="'semantic:metric:update'"
                link
                type="primary"
                @click="handleOpenEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-permission="'semantic:metric:delete'"
                link
                type="danger"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="total > 0" class="pagination-wrap">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>

        <div v-if="metricError" class="semantic-error-tip">指标口径加载失败：{{ metricError }}</div>
      </template>
    </section>

    <MetricEditDialog
      v-model="metricDialogVisible"
      :metric="selectedMetric"
      :datasource-id="selectedDatasourceId"
      @saved="loadMetrics"
    />
  </div>
</template>

<style scoped>
  .metric-page {
    display: flex;
    flex-direction: column;
  }

  .keyword-field {
    width: 220px;
  }

  .datasource-field {
    width: 200px;
  }
</style>
