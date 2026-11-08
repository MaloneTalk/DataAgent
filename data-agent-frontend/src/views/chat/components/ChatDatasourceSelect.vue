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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -->

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue';
  import { getDatasourceOptions, type DatasourceOption } from '@/api/datasource';

  const datasourceIds = defineModel<number[]>({ required: true });
  const props = defineProps<{ disabled?: boolean }>();
  const datasources = ref<DatasourceOption[]>([]);

  // 空数组表示“全部可访问数据源”。
  const placeholder = computed(() => (props.disabled ? '对话进行中' : '全部可访问数据源'));

  // 不可用或已删除的数据源不在下拉选项中，补占位项避免选中值显示为空。
  const unknownIds = computed(() =>
    datasourceIds.value.filter(id => !datasources.value.some(d => d.id === id)),
  );

  async function loadDatasources() {
    try {
      datasources.value = ((await getDatasourceOptions()) ?? []).filter(d => d.status === 'ACTIVE');
    } catch {
      datasources.value = [];
    }
  }

  onMounted(loadDatasources);
</script>

<template>
  <el-select
    v-model="datasourceIds"
    :disabled="disabled"
    multiple
    collapse-tags
    collapse-tags-tooltip
    clearable
    :placeholder="placeholder"
    size="small"
    style="width: 240px"
    title="不选表示全部可访问数据源"
  >
    <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
    <el-option
      v-for="id in unknownIds"
      :key="`unknown-${id}`"
      :label="`不可用数据源 #${id}`"
      :value="id"
    />
  </el-select>
</template>
