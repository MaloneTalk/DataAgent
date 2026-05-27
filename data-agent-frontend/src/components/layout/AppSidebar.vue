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
  import { ref, computed, type Component } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { ChatDotRound, Connection, Grid } from '@element-plus/icons-vue';

  const route = useRoute();
  const router = useRouter();

  const isCollapse = ref(false);

  const iconMap: Record<string, Component> = { ChatDotRound, Connection, Grid };

  const menuItems = [
    { path: '/chat', title: '智能分析', icon: 'ChatDotRound' },
    { path: '/data-source', title: '数据源管理', icon: 'Connection' },
    { path: '/semantic-model', title: '语义模型管理', icon: 'Grid' },
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
    <el-menu :default-active="activeMenu" :collapse="isCollapse" @select="handleMenuSelect">
      <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
        <el-icon><component :is="iconMap[item.icon]" /></el-icon>
        <template #title>{{ item.title }}</template>
      </el-menu-item>
    </el-menu>
    <div class="sidebar-toggle" role="button" aria-label="折叠侧边栏" @click="toggleSidebar">
      <span>{{ isCollapse ? '»' : '«' }}</span>
    </div>
  </aside>
</template>

<style scoped>
  .app-sidebar {
    width: 200px;
    background: #ffffff;
    border-right: 1px solid #e5e7eb;
    display: flex;
    flex-direction: column;
    transition: width 0.3s;
    position: relative;
  }

  .app-sidebar.collapsed {
    width: 64px;
  }

  .app-sidebar :deep(.el-menu) {
    border-right: none;
    background-color: transparent;
  }

  .app-sidebar :deep(.el-menu-item) {
    color: #64748b;
    font-weight: 500;
    margin: 4px 8px;
    border-radius: 8px;
    transition: all 0.3s;
  }

  .app-sidebar :deep(.el-menu-item:hover) {
    background-color: #f1f5f9;
    color: #1f2937;
  }

  .app-sidebar :deep(.el-menu-item.is-active) {
    background: #1f2937;
    color: #ffffff;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  }

  .sidebar-toggle {
    position: absolute;
    bottom: 20px;
    right: 0;
    width: 100%;
    text-align: center;
    cursor: pointer;
    color: #94a3b8;
    font-size: 18px;
    transform: translateX(-50%);
    padding: 8px;
    transition: all 0.3s;
    border-radius: 8px;
    margin: 0 8px;
  }

  .sidebar-toggle:hover {
    color: #1f2937;
    background-color: #f1f5f9;
  }
</style>
