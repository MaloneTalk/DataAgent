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
  import HelpTip from '@/components/common/HelpTip.vue';
  import DomainManage from './components/DomainManage.vue';
  import MetricManage from '@/views/metric/MetricManage.vue';
  import RelationManage from './components/RelationManage.vue';
  import TableSemanticManage from './components/TableSemanticManage.vue';

  const activeTab = ref<'domain' | 'table' | 'metric' | 'relation'>('domain');
  const domainManageRef = ref<InstanceType<typeof DomainManage>>();
  const tableManageRef = ref<InstanceType<typeof TableSemanticManage>>();

  watch(activeTab, async tab => {
    if (tab === 'domain' && domainManageRef.value) {
      await domainManageRef.value.loadDomainPage();
    } else if (tab === 'table' && tableManageRef.value) {
      await tableManageRef.value.loadPage();
    }
  });
</script>

<template>
  <div class="semantic-page">
    <div class="page-header">
      <h2 class="page-title">语义管理</h2>
    </div>

    <section class="content-card">
      <el-tabs v-model="activeTab" class="semantic-tabs">
        <el-tab-pane name="domain">
          <template #label>
            <span class="tab-label-with-help">
              数据领域管理
              <HelpTip>
                <strong>数据领域</strong> 是业务主题分类，例如会员、订单、商品、售后。领域用于把表分组，帮助 AI 先判断该去哪些表里找数据。
              </HelpTip>
            </span>
          </template>
          <DomainManage ref="domainManageRef" keyword="" sort-order="asc" />
        </el-tab-pane>
        <el-tab-pane name="table">
          <template #label>
            <span class="tab-label-with-help">
              表语义管理
              <HelpTip>
                <strong>表语义</strong> 说明一张物理表在业务上代表什么、属于哪个领域、是否允许被 AI 查询，以及它和其他表的逻辑关系。
              </HelpTip>
            </span>
          </template>
          <TableSemanticManage ref="tableManageRef" keyword="" sort-order="asc" />
        </el-tab-pane>
        <el-tab-pane name="metric">
          <template #label>
            <span class="tab-label-with-help">
              指标口径
              <HelpTip>
                <strong>指标口径</strong> 是指标的统一计算规则。它定义业务叫法、同义词、聚合表达式、过滤条件和时间字段，供 Agent 生成 SQL 时检索使用。
              </HelpTip>
            </span>
          </template>
          <MetricManage />
        </el-tab-pane>
        <el-tab-pane name="relation">
          <template #label>
            <span class="tab-label-with-help">
              逻辑外键
              <HelpTip>
                <strong>逻辑外键</strong> 是语义层里的表关联说明。即使数据库没有真实外键，也可以告诉 AI 哪些字段能 join，减少乱连表。
              </HelpTip>
            </span>
          </template>
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

  .content-card {
    background: var(--app-bg-card);
    border: 1px solid var(--app-border);
    border-radius: 8px;
    padding: 24px;
    transition:
      background-color 0.2s,
      border-color 0.2s;
  }

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .page-title {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    color: var(--app-text-primary);
  }

  .tab-label-with-help {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .semantic-tabs :deep(.el-tabs__header) {
    margin-bottom: 24px;
  }
</style>
