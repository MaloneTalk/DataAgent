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
  import { RouterView } from 'vue-router';
  import AppHeader from './components/layout/AppHeader.vue';
  import AppSidebar from './components/layout/AppSidebar.vue';
</script>

<template>
  <div class="app-container">
    <AppHeader />
    <div class="app-main">
      <AppSidebar />
      <main class="app-content" :class="{ 'app-content--flush': $route.path.startsWith('/chat') }">
        <RouterView v-slot="{ Component }">
          <Suspense timeout="0">
            <component :is="Component" :key="$route.fullPath" />
            <template #fallback>
              <div class="route-loading">
                <div class="route-loading__spinner" />
              </div>
            </template>
          </Suspense>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
  .app-container {
    display: flex;
    flex-direction: column;
    height: 100vh;
  }

  .app-main {
    display: flex;
    flex: 1;
    overflow: hidden;
  }

  .app-content {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    background-color: #f5f7fa;
    min-width: 0;
    min-height: 0;
  }

  .app-content--flush {
    padding: 0;
    overflow: hidden;
  }

  .route-loading {
    min-height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .route-loading__spinner {
    width: 28px;
    height: 28px;
    border-radius: 999px;
    border: 3px solid #dbe4ee;
    border-top-color: #64748b;
    animation: route-spin 0.8s linear infinite;
  }

  @keyframes route-spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
