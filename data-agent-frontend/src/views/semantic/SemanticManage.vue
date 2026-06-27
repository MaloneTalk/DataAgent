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
  import { computed, ref, watch } from 'vue';
  import DomainManage from './components/DomainManage.vue';
  import RelationManage from './relation/RelationManage.vue';

  const activeTab = ref<'domain' | 'relation'>('domain');
  const keyword = ref('');
  const sortOrder = ref<'asc' | 'desc'>('asc');
  const domainManageRef = ref<InstanceType<typeof DomainManage>>();

  const showSearchToolbar = computed(() => activeTab.value === 'domain');

  const handleSearch = async () => {
    if (activeTab.value === 'domain' && domainManageRef.value) {
      await domainManageRef.value.loadDomainPage();
    }
  };

  watch(activeTab, async tab => {
    if (tab === 'domain' && domainManageRef.value) {
      await domainManageRef.value.loadDomainPage();
    }
  });
</script>

<template>
  <div class="semantic-page">
    <section class="hero-card">
      <div>
        <h2 class="hero-title">语义管理</h2>
        <p class="hero-desc">统一维护数据领域和逻辑外键配置，保留当前页面结构并按功能拆分实现。</p>
      </div>
    </section>

    <section v-if="showSearchToolbar" class="toolbar-card">
      <div class="toolbar-grid">
        <el-input
          v-model="keyword"
          class="toolbar-field"
          clearable
          placeholder="按领域名称搜索"
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
        <el-tab-pane label="逻辑外键" name="relation">
          <RelationManage />
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
    margin: 0 0 8px;
    font-size: 22px;
    color: #1f2937;
  }

  .hero-desc {
    max-width: 720px;
    margin: 0;
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
