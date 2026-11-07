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
  import { ref, onMounted } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import * as sysUserApi from '@/api/sysUser';
  import { listRoles, type RoleResponse } from '@/api/sysRole';
  import type { UserResponse } from '@/api/sysUser';
  import { useUserStore } from '@/stores/user';
  import { formatDateTime } from '@/utils/dateTime';
  import UserFormDialog from './components/UserFormDialog.vue';
  import UserRoleDialog from './components/UserRoleDialog.vue';
  import UserPasswordDialog from './components/UserPasswordDialog.vue';

  const userStore = useUserStore();

  const loading = ref(false);
  const users = ref<UserResponse[]>([]);
  const page = ref(1);
  const pageSize = ref(10);
  const total = ref(0);
  const roleOptions = ref<RoleResponse[]>([]);
  function roleName(roleId: number) {
    if (roleId === 0) return '未分配';
    return roleOptions.value.find(r => r.id === roleId)?.name || String(roleId);
  }

  async function reload() {
    loading.value = true;
    try {
      const result = await sysUserApi.listUsers({ page: page.value, pageSize: pageSize.value });
      users.value = result.items;
      total.value = result.total;
    } finally {
      loading.value = false;
    }
  }

  function handlePageChange(newPage: number) {
    page.value = newPage;
    reload();
  }

  function handleSizeChange(newSize: number) {
    pageSize.value = newSize;
    page.value = 1;
    reload();
  }

  const formDialogRef = ref<InstanceType<typeof UserFormDialog>>();
  const roleDialogRef = ref<InstanceType<typeof UserRoleDialog>>();
  const passwordDialogRef = ref<InstanceType<typeof UserPasswordDialog>>();

  // ── Status toggle ──
  async function onToggleStatus(user: UserResponse) {
    const newStatus = user.status === 1 ? 0 : 1;
    const action = newStatus === 1 ? '启用' : '禁用';
    try {
      await ElMessageBox.confirm(`确定要${action}用户「${user.displayName}」吗？`, '操作确认', {
        type: 'warning',
      });
      await sysUserApi.updateStatus(user.id, newStatus);
      ElMessage.success(`${action}成功`);
      await reload();
    } catch {
      // cancel or error
    }
  }

  onMounted(() => {
    reload();
    if (!userStore.hasPermission(['sys:role:read', 'sys:user:assign-role'])) return;
    listRoles()
      .then(list => {
        roleOptions.value = list;
      })
      .catch(() => {});
  });
</script>

<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
    </div>

    <div class="table-actions">
      <el-button
        v-permission="'sys:user:create'"
        type="primary"
        @click="formDialogRef?.openCreate()"
      >
        新建用户
      </el-button>
    </div>

    <el-table v-loading="loading" :data="users" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="displayName" label="显示名" min-width="120" />
      <el-table-column label="角色" width="130" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.superAdmin" type="danger" size="small">超级管理员</el-tag>
          <el-tag v-else :type="row.roleId === 1 ? '' : 'info'" size="small">
            {{ roleName(row.roleId) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180" align="center">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="360" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'sys:user:update'"
            size="small"
            @click="formDialogRef?.openEdit(row)"
          >
            编辑资料
          </el-button>
          <el-button
            v-permission="'sys:user:assign-role'"
            size="small"
            @click="roleDialogRef?.openAssignRole(row)"
          >
            分配角色
          </el-button>
          <el-button
            v-permission="'sys:user:reset-password'"
            size="small"
            @click="passwordDialogRef?.openResetPwd(row)"
          >
            重置密码
          </el-button>
          <el-button
            v-permission="'sys:user:set-status'"
            size="small"
            :type="row.status === 1 ? 'warning' : 'success'"
            @click="onToggleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="total > 0" class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <UserFormDialog ref="formDialogRef" :role-options="roleOptions" @saved="reload" />
    <UserRoleDialog ref="roleDialogRef" :role-options="roleOptions" @saved="reload" />
    <UserPasswordDialog ref="passwordDialogRef" />
  </div>
</template>
