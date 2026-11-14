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
  import { getDataEvidence, type DataEvidence } from '@/api/evidence';
  import ProvenancePanel from '@/views/chat/components/ProvenancePanel.vue';

  const props = defineProps<{
    fixedSessionId?: string;
  }>();

  const sessionId = ref('');
  const answerId = ref('');
  const rows = ref<DataEvidence[]>([]);
  const loading = ref(false);
  const page = ref(1);
  const pageSize = ref(10);
  const total = ref(0);
  const selected = ref<DataEvidence | null>(null);
  const detailVisible = ref(false);

  async function fetchEvidence() {
    loading.value = true;
    try {
      const result = await getDataEvidence({
        sessionId: props.fixedSessionId || sessionId.value.trim() || undefined,
        answerId: answerId.value.trim() || undefined,
        page: page.value,
        pageSize: pageSize.value,
      });
      rows.value = result.items;
      total.value = result.total;
    } catch {
      rows.value = [];
      total.value = 0;
    } finally {
      loading.value = false;
    }
  }

  function search() {
    page.value = 1;
    fetchEvidence();
  }

  function reset() {
    if (!props.fixedSessionId) sessionId.value = '';
    answerId.value = '';
    page.value = 1;
    fetchEvidence();
  }

  function datasourceNames(row: DataEvidence) {
    return row.evidence.datasources.map(source => source.name || source.id).join('、') || '-';
  }

  function queryCount(row: DataEvidence) {
    return row.evidence.sqlExecutions.filter(sql => sql.status === 'SUCCESS').length;
  }

  function showDetail(row: DataEvidence) {
    selected.value = row;
    detailVisible.value = true;
  }

  onMounted(fetchEvidence);
</script>

<template>
  <div>
    <div v-if="!fixedSessionId" class="page-header">
      <h2 class="page-title">数据证据</h2>
    </div>

    <div class="search-bar">
      <el-input
        v-if="!fixedSessionId"
        v-model="sessionId"
        placeholder="Session ID"
        clearable
        style="width: 240px"
        @keyup.enter="search"
      />
      <el-input
        v-model="answerId"
        placeholder="Answer ID"
        clearable
        style="width: 240px"
        @keyup.enter="search"
      />
      <el-button type="primary" :loading="loading" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" style="width: 100%">
      <el-table-column prop="answerId" label="Answer ID" min-width="220" show-overflow-tooltip />
      <el-table-column
        v-if="!fixedSessionId"
        prop="sessionId"
        label="Session ID"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          {{ row.evidence.status === 'COMPLETE' ? '完整记录' : '部分记录' }}
        </template>
      </el-table-column>
      <el-table-column label="查询次数" width="100">
        <template #default="{ row }">{{ queryCount(row) }}</template>
      </el-table-column>
      <el-table-column label="数据源" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ datasourceNames(row) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && rows.length === 0" class="empty-tip">暂无数据证据</div>

    <div v-if="total > 0" class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="fetchEvidence"
        @size-change="search"
      />
    </div>

    <el-dialog
      v-model="detailVisible"
      title="数据证据详情"
      width="900px"
      top="30px"
      @closed="selected = null"
    >
      <ProvenancePanel v-if="selected" :provenance="selected.evidence" />
    </el-dialog>
  </div>
</template>
