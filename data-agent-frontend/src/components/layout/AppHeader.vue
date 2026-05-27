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
  import { DataLine, User } from '@element-plus/icons-vue';
</script>

<template>
  <header class="app-header">
    <div class="header-left">
      <div class="logo">
        <el-icon :size="20" class="logo-icon"><DataLine /></el-icon>
        <span class="logo-text">Data Agent</span>
      </div>
    </div>
    <div class="header-right">
      <el-dropdown trigger="click">
        <div class="user-info">
          <el-avatar :size="30" class="user-avatar">
            <el-icon><User /></el-icon>
          </el-avatar>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item>个人中心</el-dropdown-item>
            <el-dropdown-item divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped>
  .app-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    height: 56px;
    padding: 0 20px;
    background-color: #fff;
    border-bottom: 1px solid #f0f0f0;
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

  .logo-icon {
    color: #1f2937;
  }

  .logo-text {
    font-size: 15px;
    font-weight: 600;
    color: #1f2937;
    letter-spacing: -0.01em;
  }

  .header-right {
    display: flex;
    align-items: center;
  }

  .user-info {
    cursor: pointer;
    transition: opacity 0.15s;
  }

  .user-info:hover {
    opacity: 0.75;
  }

  .user-avatar {
    background: #f3f4f6;
    color: #4b5563;
  }
</style>
