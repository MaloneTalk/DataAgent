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
  import { ref, reactive, onMounted } from 'vue';
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
  import * as sysUserApi from '@/api/sysUser';
  import { listRoles, type RoleResponse } from '@/api/sysRole';
  import type { UserResponse } from '@/api/sysUser';

  const loading = ref(false);
  const users = ref<UserResponse[]>([]);
  const roleOptions = ref<RoleResponse[]>([]);
  function roleName(roleId: number) {
    if (roleId === 0) return '未分配';
    return roleOptions.value.find(r => r.id === roleId)?.name || String(roleId);
  }

  async function reload() {
    loading.value = true;
    try {
      users.value = await sysUserApi.listUsers();
    } finally {
      loading.value = false;
    }
  }

  // ── Create / Edit dialog ──
  const formVisible = ref(false);
  const formTitle = ref('新建用户');
  const isEdit = ref(false);
  const editingId = ref<number | null>(null);
  const formRef = ref<FormInstance>();
  const form = reactive({ username: '', password: '', displayName: '', roleId: 0 });

  const createRules: FormRules<typeof form> = {
    username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
    password: [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 6, max: 64, message: '长度 6-64', trigger: 'blur' },
    ],
    displayName: [{ required: true, message: '请输入显示名', trigger: 'blur' }],
  };

  const editRules: FormRules<{ displayName: string }> = {
    displayName: [{ required: true, message: '请输入显示名', trigger: 'blur' }],
  };

  function openCreate() {
    isEdit.value = false;
    formTitle.value = '新建用户';
    form.username = '';
    form.password = '';
    form.displayName = '';
    form.roleId = 0;
    formVisible.value = true;
  }

  function openEdit(user: UserResponse) {
    isEdit.value = true;
    formTitle.value = '编辑用户';
    editingId.value = user.id;
    form.displayName = user.displayName;
    form.roleId = user.roleId;
    formVisible.value = true;
  }

  async function onSubmitForm() {
    if (!formRef.value) return;
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;
    try {
      if (isEdit.value && editingId.value != null) {
        await sysUserApi.updateUser(editingId.value, {
          displayName: form.displayName,
          roleId: form.roleId,
        });
        ElMessage.success('更新成功');
      } else {
        await sysUserApi.createUser({ ...form });
        ElMessage.success('创建成功');
      }
      formVisible.value = false;
      await reload();
    } catch {
      // 请求层已 toast
    }
  }

  // ── Reset password dialog ──
  const pwdVisible = ref(false);
  const pwdUserId = ref<number | null>(null);
  const pwdUsername = ref('');
  const pwdForm = reactive({ newPassword: '', confirmPassword: '' });
  const pwdFormRef = ref<FormInstance>();

  function openResetPwd(user: UserResponse) {
    pwdUserId.value = user.id;
    pwdUsername.value = user.username;
    pwdForm.newPassword = '';
    pwdForm.confirmPassword = '';
    pwdVisible.value = true;
  }

  const validateConfirm = (_rule: unknown, value: string, cb: (err?: Error) => void) => {
    if (value !== pwdForm.newPassword) {
      cb(new Error('两次输入的密码不一致'));
    } else {
      cb();
    }
  };

  async function onSubmitReset() {
    if (!pwdFormRef.value || pwdUserId.value == null) return;
    const valid = await pwdFormRef.value.validate().catch(() => false);
    if (!valid) return;
    try {
      await sysUserApi.resetPassword(pwdUserId.value, pwdForm.newPassword);
      ElMessage.success('密码已重置');
      pwdVisible.value = false;
    } catch {
      // toast handled by api layer
    }
  }

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
    listRoles()
      .then(list => {
        roleOptions.value = list;
      })
      .catch(() => {});
  });
</script>

<template>
  <div class="user-manage-page">
    <div class="page-header">
      <h2>用户管理</h2>
      <el-button type="primary" @click="openCreate">新建用户</el-button>
    </div>

    <el-table v-loading="loading" :data="users" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="displayName" label="显示名" min-width="120" />
      <el-table-column label="角色" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.roleId === 1 ? '' : 'info'" size="small">
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
      <el-table-column prop="createTime" label="创建时间" width="180" align="center" />
      <el-table-column label="操作" width="280" align="center" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" @click="openResetPwd(row)">重置密码</el-button>
          <el-button
            size="small"
            :type="row.status === 1 ? 'warning' : 'success'"
            @click="onToggleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create / Edit dialog -->
    <el-dialog v-model="formVisible" :title="formTitle" width="420px" :close-on-click-modal="false">
      <el-form
        ref="formRef"
        :model="form"
        :rules="isEdit ? editRules : createRules"
        label-width="80px"
        @submit.prevent="onSubmitForm"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6-64 位" />
        </el-form-item>
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="form.displayName" placeholder="请输入显示名" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleId" placeholder="请选择角色" style="width: 100%">
            <el-option :value="0" label="未分配" />
            <el-option v-for="r in roleOptions" :key="r.id" :value="r.id" :label="r.name" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- Reset password dialog -->
    <el-dialog
      v-model="pwdVisible"
      :title="`重置密码 - ${pwdUsername}`"
      width="380px"
      :close-on-click-modal="false"
    >
      <el-form ref="pwdFormRef" :model="pwdForm" label-width="80px" @submit.prevent="onSubmitReset">
        <el-form-item
          label="新密码"
          prop="newPassword"
          :rules="[
            { required: true, message: '请输入新密码', trigger: 'blur' },
            { min: 6, max: 64, message: '长度 6-64', trigger: 'blur' },
          ]"
        >
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            show-password
            placeholder="6-64 位"
          />
        </el-form-item>
        <el-form-item
          label="确认密码"
          prop="confirmPassword"
          :rules="[
            { required: true, message: '请确认新密码', trigger: 'blur' },
            { validator: validateConfirm, trigger: 'blur' },
          ]"
        >
          <el-input
            v-model="pwdForm.confirmPassword"
            type="password"
            show-password
            placeholder="再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmitReset">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
  .user-manage-page {
    max-width: 1200px;
  }

  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
  }

  .page-header h2 {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: var(--app-text-primary);
  }
</style>
