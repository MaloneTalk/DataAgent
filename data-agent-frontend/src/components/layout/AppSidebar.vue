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
  import { ref, computed } from 'vue';
  import { useRoute, useRouter } from 'vue-router';

  const route = useRoute();
  const router = useRouter();

  const isCollapse = ref(false);

  const menuItems = [
    { path: '/chat', title: 'AI 智能分析' },
    { path: '/data-source', title: '数据源管理' },
    {
      path: '/semantic',
      title: '语义管理',
      children: [
        { path: '/semantic/domain', title: '数据领域' },
        { path: '/semantic/table', title: '表语义' },
        { path: '/semantic/metric', title: '指标口径' },
        { path: '/semantic/relation', title: '逻辑外键' },
      ],
    },
    {
      path: '/asset',
      title: '资产管理',
      children: [
        { path: '/asset/report', title: '报告管理' },
        { path: '/asset/dashboard', title: '看板管理' },
        { path: '/asset/table-export', title: '表格导出' },
      ],
    },
    {
      path: '/system',
      title: '系统管理',
      children: [
        { path: '/system/user', title: '用户管理' },
        { path: '/system/role', title: '角色管理' },
      ],
    },
  ];

  const activeMenu = computed(() => route.path);

  const handleMenuSelect = (path: string) => {
    router.push(path);
  };

  const toggleSidebar = () => {
    isCollapse.value = !isCollapse.value;
  };
</script>

<template>
  <aside class="app-sidebar" :class="{ collapsed: isCollapse }">
    <el-menu :default-active="activeMenu" :collapse="isCollapse" router @select="handleMenuSelect">
      <template v-for="item in menuItems" :key="item.path">
        <el-sub-menu v-if="item.children" :index="item.path">
          <template #title>
            <span>{{ item.title }}</span>
          </template>
          <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path">
            <span>{{ child.title }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item v-else :index="item.path">
          <span>{{ item.title }}</span>
        </el-menu-item>
      </template>
    </el-menu>
    <div class="sidebar-toggle" @click="toggleSidebar">
      <span>{{ isCollapse ? '»' : '«' }}</span>
    </div>
  </aside>
</template>

<style scoped>
  .app-sidebar {
    width: 200px;
    background-color: var(--app-bg-sidebar);
    border-right: 1px solid var(--app-border);
    display: flex;
    flex-direction: column;
    transition:
      width 0.2s,
      background-color 0.2s,
      border-color 0.2s;
    position: relative;
    flex-shrink: 0;
  }

  .app-sidebar.collapsed {
    width: 64px;
  }

  .app-sidebar :deep(.el-menu) {
    border-right: none;
    background-color: transparent;
  }

  .app-sidebar :deep(.el-menu-item),
  .app-sidebar :deep(.el-sub-menu__title) {
    width: calc(100% - 16px);
    box-sizing: border-box;
    color: var(--app-text-secondary);
    font-weight: 500;
    font-size: 13px;
    margin: 2px 8px;
    border-radius: 6px;
    height: 38px;
    line-height: 38px;
    cursor: pointer;
    transition: all 0.15s;
  }

  .app-sidebar :deep(.el-menu-item:hover),
  .app-sidebar :deep(.el-sub-menu__title:hover) {
    background-color: var(--app-bg-hover);
    color: var(--app-text-primary);
  }

  .app-sidebar :deep(.el-menu-item.is-active) {
    background-color: var(--app-accent);
    color: var(--app-accent-text);
  }

  .app-sidebar :deep(.el-menu-item.is-active:hover) {
    background-color: var(--app-accent);
    color: var(--app-accent-text);
  }

  .sidebar-toggle {
    position: absolute;
    bottom: 16px;
    left: 0;
    width: 100%;
    text-align: center;
    cursor: pointer;
    color: var(--app-text-muted);
    font-size: 16px;
    padding: 6px;
    transition: color 0.2s;
    user-select: none;
  }

  .sidebar-toggle:hover {
    color: var(--app-text-primary);
  }
</style>
