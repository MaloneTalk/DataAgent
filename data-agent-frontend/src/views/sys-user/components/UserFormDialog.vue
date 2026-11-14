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
  import { computed, nextTick, reactive, ref } from 'vue';
  import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
  import * as sysUserApi from '@/api/sysUser';
  import type { UserResponse } from '@/api/sysUser';
  import { useUserStore } from '@/stores/user';
  import type { RoleResponse } from '@/api/sysRole';

  defineProps<{ roleOptions: RoleResponse[] }>();
  const emit = defineEmits<{ saved: [] }>();
  const userStore = useUserStore();

  // ── Create / Edit dialog ──
  const formVisible = ref(false);
  const formSaving = ref(false);
  const editingId = ref<number | null>(null);
  const isEdit = computed(() => editingId.value !== null);
  const formTitle = computed(() => (isEdit.value ? '编辑用户' : '新建用户'));
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
    editingId.value = null;
    form.username = '';
    form.password = '';
    form.displayName = '';
    form.roleId = 0;
    formVisible.value = true;
    nextTick(() => formRef.value?.clearValidate());
  }

  function openEdit(user: UserResponse) {
    editingId.value = user.id;
    form.username = user.username;
    form.displayName = user.displayName;
    formVisible.value = true;
    nextTick(() => formRef.value?.clearValidate());
  }

  async function onSubmitForm() {
    if (!formRef.value || formSaving.value) return;
    if (!userStore.hasPermission(isEdit.value ? 'sys:user:update' : 'sys:user:create')) return;
    formSaving.value = true;
    try {
      const valid = await formRef.value.validate().catch(() => false);
      if (!valid) return;
      if (editingId.value != null) {
        await sysUserApi.updateUser(editingId.value, { displayName: form.displayName });
        ElMessage.success('资料保存成功');
      } else {
        await sysUserApi.createUser({ ...form });
        ElMessage.success('创建成功');
      }
      formVisible.value = false;
      emit('saved');
    } catch {
      // 请求层已 toast
    } finally {
      formSaving.value = false;
    }
  }

  defineExpose({ openCreate, openEdit });
</script>

<template>
  <el-dialog
    v-model="formVisible"
    :title="formTitle"
    width="420px"
    :close-on-click-modal="false"
    :close-on-press-escape="!formSaving"
    :show-close="!formSaving"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="isEdit ? editRules : createRules"
      :disabled="formSaving"
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
      <el-form-item v-if="!isEdit" label="角色">
        <el-select
          :disabled="!userStore.hasPermission('sys:user:assign-role')"
          v-model="form.roleId"
          placeholder="请选择角色"
          style="width: 100%"
        >
          <el-option :value="0" label="未分配" />
          <el-option v-for="r in roleOptions" :key="r.id" :value="r.id" :label="r.name" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formSaving" @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="formSaving" @click="onSubmitForm">
        {{ isEdit ? '保存资料' : '创建用户' }}
      </el-button>
    </template>
  </el-dialog>
</template>
