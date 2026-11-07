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
  import { nextTick, reactive, ref } from 'vue';
  import { ElMessage, type FormInstance } from 'element-plus';
  import * as sysUserApi from '@/api/sysUser';
  import type { UserResponse } from '@/api/sysUser';
  import { useUserStore } from '@/stores/user';

  const userStore = useUserStore();

  // ── Reset password dialog ──
  const pwdVisible = ref(false);
  const pwdSaving = ref(false);
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
    nextTick(() => pwdFormRef.value?.clearValidate());
  }

  const validateConfirm = (_rule: unknown, value: string, cb: (err?: Error) => void) => {
    if (value !== pwdForm.newPassword) {
      cb(new Error('两次输入的密码不一致'));
    } else {
      cb();
    }
  };

  async function onSubmitReset() {
    if (!pwdFormRef.value || pwdUserId.value == null || pwdSaving.value) return;
    if (!userStore.hasPermission('sys:user:reset-password')) return;
    pwdSaving.value = true;
    try {
      const valid = await pwdFormRef.value.validate().catch(() => false);
      if (!valid) return;
      await sysUserApi.resetPassword(pwdUserId.value, pwdForm.newPassword);
      ElMessage.success('密码已重置');
      pwdVisible.value = false;
    } catch {
      // toast handled by api layer
    } finally {
      pwdSaving.value = false;
    }
  }

  defineExpose({ openResetPwd });
</script>

<template>
  <el-dialog
    v-model="pwdVisible"
    :title="`重置密码 - ${pwdUsername}`"
    width="380px"
    :close-on-click-modal="false"
    :close-on-press-escape="!pwdSaving"
    :show-close="!pwdSaving"
  >
    <el-form
      :disabled="pwdSaving"
      ref="pwdFormRef"
      :model="pwdForm"
      label-width="80px"
      @submit.prevent="onSubmitReset"
    >
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
      <el-button :disabled="pwdSaving" @click="pwdVisible = false">取消</el-button>
      <el-button type="primary" :loading="pwdSaving" @click="onSubmitReset">确定</el-button>
    </template>
  </el-dialog>
</template>
