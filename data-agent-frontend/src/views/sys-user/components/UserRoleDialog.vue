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
  import { ref } from 'vue';
  import { ElMessage } from 'element-plus';
  import * as sysUserApi from '@/api/sysUser';
  import type { UserResponse } from '@/api/sysUser';
  import { useUserStore } from '@/stores/user';
  import type { RoleResponse } from '@/api/sysRole';

  defineProps<{ roleOptions: RoleResponse[] }>();
  const emit = defineEmits<{ saved: [] }>();
  const userStore = useUserStore();

  // ── Assign role dialog ──
  const roleVisible = ref(false);
  const roleSaving = ref(false);
  const roleUser = ref<UserResponse>();
  const assignedRoleId = ref(0);

  function openAssignRole(user: UserResponse) {
    roleUser.value = user;
    assignedRoleId.value = user.roleId;
    roleVisible.value = true;
  }

  async function onAssignRole() {
    if (!roleUser.value || roleSaving.value || !userStore.hasPermission('sys:user:assign-role'))
      return;
    roleSaving.value = true;
    try {
      await sysUserApi.assignRole(roleUser.value.id, assignedRoleId.value);
      ElMessage.success('角色分配成功');
      roleVisible.value = false;
      emit('saved');
    } catch {
      // 请求层已 toast
    } finally {
      roleSaving.value = false;
    }
  }

  defineExpose({ openAssignRole });
</script>

<template>
  <el-dialog
    v-model="roleVisible"
    :title="`分配角色 - ${roleUser?.username ?? ''}`"
    width="420px"
    :close-on-click-modal="false"
    :close-on-press-escape="!roleSaving"
    :show-close="!roleSaving"
  >
    <el-form label-width="80px" :disabled="roleSaving" @submit.prevent="onAssignRole">
      <el-form-item label="角色">
        <el-select v-model="assignedRoleId" placeholder="请选择角色" style="width: 100%">
          <el-option :value="0" label="未分配" />
          <el-option v-for="r in roleOptions" :key="r.id" :value="r.id" :label="r.name" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="roleSaving" @click="roleVisible = false">取消</el-button>
      <el-button type="primary" :loading="roleSaving" @click="onAssignRole">分配角色</el-button>
    </template>
  </el-dialog>
</template>
