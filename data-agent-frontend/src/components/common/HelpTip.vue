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
  withDefaults(
    defineProps<{
      placement?: string;
    }>(),
    {
      placement: 'top',
    },
  );
</script>

<template>
  <el-tooltip :placement="placement" effect="dark" popper-class="app-help-tip__popper">
    <template #content>
      <div class="app-help-tip__content">
        <slot />
      </div>
    </template>
    <span class="app-help-tip" aria-label="帮助说明" tabindex="0">?</span>
  </el-tooltip>
</template>

<style>
  .app-help-tip {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    border: 1px solid var(--app-border);
    border-radius: 50%;
    color: var(--app-text-muted);
    font-size: 12px;
    line-height: 1;
    cursor: help;
    user-select: none;
  }

  .app-help-tip:hover,
  .app-help-tip:focus {
    color: var(--app-text-primary);
    border-color: var(--app-text-secondary);
    outline: none;
  }

  .app-help-tip__content {
    max-width: 320px;
    line-height: 1.7;
  }

  .app-help-tip__content ul {
    margin: 4px 0 0 16px;
  }
</style>
