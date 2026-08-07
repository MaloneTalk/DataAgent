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
  import { useRouter } from 'vue-router';
  import { useThemeStore } from '@/stores/theme';
  import { useUserStore } from '@/stores/user';

  const themeStore = useThemeStore();
  const userStore = useUserStore();
  const router = useRouter();

  function onLogout() {
    userStore.logout();
    router.push('/login');
  }
</script>

<template>
  <header class="app-header">
    <div class="header-left">
      <div class="logo">
        <span class="logo-text">Data Agent</span>
      </div>
    </div>
    <div class="header-right">
      <button
        class="theme-toggle"
        :title="themeStore.mode === 'light' ? '切换到深色模式' : '切换到浅色模式'"
        @click="themeStore.toggle()"
      >
        <span v-if="themeStore.mode === 'light'">🌙</span>
        <span v-else>☀️</span>
      </button>
      <span v-if="userStore.userInfo" class="user-name">
        {{ userStore.userInfo.displayName }}
      </span>
      <button class="logout-btn" title="登出" @click="onLogout">登出</button>
    </div>
  </header>
</template>

<style scoped>
  .app-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    height: 48px;
    padding: 0 20px;
    background-color: var(--app-bg-header);
    border-bottom: 1px solid var(--app-border);
    flex-shrink: 0;
    transition:
      background-color 0.2s,
      border-color 0.2s;
  }

  .header-left {
    display: flex;
    align-items: center;
  }

  .logo {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .logo-text {
    font-size: 16px;
    font-weight: 700;
    color: var(--app-text-primary);
    letter-spacing: -0.5px;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .theme-toggle {
    width: 32px;
    height: 32px;
    border: 1px solid var(--app-border);
    border-radius: 6px;
    background: var(--app-bg-card);
    font-size: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition:
      background-color 0.2s,
      border-color 0.2s;
  }

  .theme-toggle:hover {
    background: var(--app-bg-hover);
  }

  .user-name {
    font-size: 13px;
    color: var(--app-text-secondary);
    white-space: nowrap;
  }

  .logout-btn {
    height: 32px;
    padding: 0 12px;
    border: 1px solid var(--app-border);
    border-radius: 6px;
    background: var(--app-bg-card);
    color: var(--app-text-primary);
    font-size: 13px;
    cursor: pointer;
    transition:
      background-color 0.2s,
      border-color 0.2s;
  }

  .logout-btn:hover {
    background: var(--app-bg-hover);
  }
</style>
