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
  import { computed, onMounted, ref } from 'vue';
  import { getDatasourceOptions, type DatasourceOption } from '@/api/datasource';

  const datasourceId = defineModel<number | null>({ required: true });
  const props = defineProps<{ messageCount: number }>();
  const datasources = ref<DatasourceOption[]>([]);

  // 已发过消息 = 绑定已落库 = 不可再切换数据源（锁定语义）。
  const isBound = computed(() => props.messageCount > 0);

  // 未绑定的旧会话实际跟随全局激活源，展示其名字以免用户误以为可自由选源。
  const activeDatasourceName = computed(() => {
    if (datasourceId.value != null) return null;
    return datasources.value.find(d => d.status === 'ACTIVE')?.name ?? null;
  });

  const dsPlaceholder = computed(() => {
    if (isBound.value) {
      return activeDatasourceName.value
        ? `跟随全局激活源（${activeDatasourceName.value}）`
        : '跟随全局激活源';
    }
    return '选择数据源';
  });

  // 绑定源已被删除时下拉列表里没有对应项，补一个占位项。
  const boundUnknown = computed(() => {
    if (datasourceId.value == null) return false;
    return !datasources.value.some(d => d.id === datasourceId.value);
  });

  async function loadDatasources() {
    try {
      datasources.value = (await getDatasourceOptions()) ?? [];
    } catch {
      datasources.value = [];
    }
  }

  onMounted(loadDatasources);
</script>

<template>
  <el-select
    v-model="datasourceId"
    :disabled="isBound"
    :placeholder="dsPlaceholder"
    size="small"
    style="width: 180px"
    :title="isBound ? '会话已绑定数据源，不可切换' : undefined"
  >
    <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
    <el-option
      v-if="boundUnknown"
      :key="datasourceId"
      :label="`已删除数据源 #${datasourceId}`"
      :value="datasourceId"
    />
  </el-select>
</template>
