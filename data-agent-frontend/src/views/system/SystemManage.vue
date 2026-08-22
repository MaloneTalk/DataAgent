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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 -->

<script setup lang="ts">
  import { ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import RoleManage from '@/views/sys-role/RoleManage.vue';
  import UserManage from '@/views/sys-user/UserManage.vue';

  type SystemTab = 'user' | 'role';

  const route = useRoute();
  const router = useRouter();
  const activeTab = ref<SystemTab>(resolveTab(route.query.tab));

  function resolveTab(value: unknown): SystemTab {
    return value === 'role' ? 'role' : 'user';
  }

  watch(
    () => route.query.tab,
    tab => {
      activeTab.value = resolveTab(tab);
    },
  );

  watch(activeTab, tab => {
    if (route.query.tab !== tab) {
      router.replace({ path: '/system', query: { tab } });
    }
  });
</script>

<template>
  <div class="system-page">
    <div class="page-header">
      <h2 class="page-title">系统管理</h2>
    </div>

    <section class="content-card">
      <el-tabs v-model="activeTab" class="system-tabs">
        <el-tab-pane label="用户管理" name="user">
          <UserManage />
        </el-tab-pane>
        <el-tab-pane label="角色管理" name="role">
          <RoleManage />
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped>
  .system-page {
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

  .system-tabs :deep(.el-tabs__header) {
    margin-bottom: 24px;
  }
</style>
