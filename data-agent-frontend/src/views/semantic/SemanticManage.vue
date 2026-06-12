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
  import { ref, watch } from 'vue';
  import DomainManage from './components/DomainManage.vue';
  import TableSemanticManage from './components/TableSemanticManage.vue';
  import ColumnSemanticManage from './components/ColumnSemanticManage.vue';

  const activeTab = ref<'domain' | 'table' | 'column'>('domain');
  const keyword = ref('');
  const sortOrder = ref<'asc' | 'desc'>('asc');
  const domainManageRef = ref<InstanceType<typeof DomainManage>>();
  const tableManageRef = ref<InstanceType<typeof TableSemanticManage>>();
  const columnManageRef = ref<InstanceType<typeof ColumnSemanticManage>>();

  const handleSearch = async () => {
    if (activeTab.value === 'domain' && domainManageRef.value) {
      await domainManageRef.value.loadDomainPage();
    } else if (activeTab.value === 'table' && tableManageRef.value) {
      await tableManageRef.value.loadPage();
    } else if (activeTab.value === 'column' && columnManageRef.value) {
      await columnManageRef.value.loadPage();
    }
  };

  watch(activeTab, async tab => {
    if (tab === 'domain' && domainManageRef.value) {
      await domainManageRef.value.loadDomainPage();
    } else if (tab === 'table' && tableManageRef.value) {
      await tableManageRef.value.loadPage();
    } else if (tab === 'column' && columnManageRef.value) {
      await columnManageRef.value.loadPage();
    }
  });
</script>

<template>
  <div class="semantic-page">
    <section class="hero-card">
      <div>
        <h2 class="hero-title">语义管理</h2>
        <p class="hero-desc">管理数据领域标签、表语义信息和列语义信息，用于分类和组织表结构。</p>
      </div>
    </section>

    <section class="toolbar-card">
      <div class="toolbar-grid">
        <el-input
          v-model="keyword"
          class="toolbar-field"
          clearable
          placeholder="按名称搜索"
          @keyup.enter="handleSearch"
        />
        <el-segmented
          v-model="sortOrder"
          :options="[
            { label: '名称升序', value: 'asc' },
            { label: '名称降序', value: 'desc' },
          ]"
          @change="handleSearch"
        />
        <div class="toolbar-actions">
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </div>
      </div>
    </section>

    <section class="content-card">
      <el-tabs v-model="activeTab" class="semantic-tabs">
        <el-tab-pane label="数据领域管理" name="domain">
          <DomainManage ref="domainManageRef" :keyword="keyword" :sort-order="sortOrder" />
        </el-tab-pane>
        <el-tab-pane label="表语义管理" name="table">
          <TableSemanticManage ref="tableManageRef" :keyword="keyword" :sort-order="sortOrder" />
        </el-tab-pane>
        <el-tab-pane label="列语义管理" name="column">
          <ColumnSemanticManage ref="columnManageRef" :keyword="keyword" :sort-order="sortOrder" />
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped>
  .semantic-page {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .hero-card,
  .toolbar-card,
  .content-card {
    background: #ffffff;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  }

  .hero-card {
    padding: 24px 32px;
    background: linear-gradient(145deg, #ffffff 0%, #f9fafb 100%);
  }

  .hero-title {
    font-size: 22px;
    color: #1f2937;
    margin-bottom: 8px;
  }

  .hero-desc {
    max-width: 680px;
    color: #6b7280;
    line-height: 1.7;
  }

  .toolbar-card,
  .content-card {
    padding: 24px;
  }

  .toolbar-grid {
    display: grid;
    grid-template-columns: minmax(240px, 1fr) auto auto;
    gap: 16px;
    align-items: center;
  }

  .toolbar-field {
    width: 100%;
  }

  .toolbar-actions {
    display: flex;
    gap: 12px;
  }

  .semantic-tabs :deep(.el-tabs__header) {
    margin-bottom: 24px;
  }

  @media (max-width: 1024px) {
    .toolbar-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
