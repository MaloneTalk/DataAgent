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
 * limitations under the License.
 -->

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { ElMessage } from 'element-plus';
  import * as api from '@/api/sysRole';
  const props = defineProps<{ roleId: number | null }>();
  const visible = defineModel<boolean>({ required: true });
  const catalog = ref<api.PermissionDefinition[]>([]);
  const selected = ref<string[]>([]);
  const loading = ref(false);
  const saving = ref(false);
  const groups = computed(() => [...new Set(catalog.value.map(p => p.module))]);
  watch([visible, () => props.roleId], async ([value, roleId], _previous, onCleanup) => {
    let current = true;
    onCleanup(() => {
      current = false;
    });
    if (!value || roleId == null) return;
    loading.value = true;
    selected.value = [];
    catalog.value = [];
    try {
      const [definitions, codes] = await Promise.all([
        api.getPermissionCatalog(),
        api.getCapabilities(roleId),
      ]);
      if (!current) return;
      catalog.value = definitions;
      selected.value = codes.filter(code =>
        definitions.some(p => p.code === code && !p.superAdminOnly),
      );
    } finally {
      if (current) loading.value = false;
    }
  });
  async function save() {
    if (props.roleId == null) return;
    saving.value = true;
    try {
      await api.saveCapabilities(props.roleId, selected.value);
      ElMessage.success('功能权限已保存');
      visible.value = false;
    } finally {
      saving.value = false;
    }
  }
</script>
<template>
  <el-dialog v-model="visible" title="角色功能权限" width="760px" :close-on-click-modal="false">
    <div v-loading="loading">
      <p>未勾选的能力默认拒绝。授权管理、角色分配、表结构同步及 Python 执行仅限超级管理员。</p>
      <el-checkbox-group v-model="selected">
        <section v-for="group in groups" :key="group" style="margin-bottom: 12px">
          <strong>{{ group }}</strong>
          <br />
          <el-checkbox
            v-for="item in catalog.filter(p => p.module === group)"
            :key="item.code"
            :value="item.code"
            :disabled="item.superAdminOnly"
            :title="item.code"
          >
            {{ item.name }}{{ item.superAdminOnly ? '（仅超管）' : '' }}
          </el-checkbox>
        </section>
      </el-checkbox-group>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :disabled="loading || catalog.length === 0"
        :loading="saving"
        @click="save"
      >
        保存
      </el-button>
    </template>
  </el-dialog>
</template>
