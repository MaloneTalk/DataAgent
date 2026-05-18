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
    { path: '/chat', title: 'AI 智能分析', icon: 'ChatDotRound' },
    { path: '/data-source', title: '数据源管理', icon: 'Connection' },
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
      <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
        <span>{{ item.title }}</span>
      </el-menu-item>
    </el-menu>
    <div class="sidebar-toggle" @click="toggleSidebar">
      <span>{{ isCollapse ? '»' : '«' }}</span>
    </div>
  </aside>
</template>

<style scoped>
  .app-sidebar {
    width: 200px;
    background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
    border-right: 1px solid #e2e8f0;
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
    color: #3b82f6;
  }

  .app-sidebar :deep(.el-menu-item.is-active) {
    background: linear-gradient(90deg, #3b82f6 0%, #2563eb 100%);
    color: #ffffff;
    box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
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
    color: #3b82f6;
    background-color: #f1f5f9;
  }
</style>
