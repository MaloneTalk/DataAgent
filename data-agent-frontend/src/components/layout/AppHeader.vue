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
  import { useThemeStore } from '@/stores/theme';

  const themeStore = useThemeStore();
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
</style>
