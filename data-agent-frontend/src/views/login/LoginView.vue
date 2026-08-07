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
  import { reactive, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
  import { useUserStore } from '@/stores/user';

  const router = useRouter();
  const userStore = useUserStore();

  const formRef = ref<FormInstance>();
  const loading = ref(false);
  const form = reactive({ username: '', password: '' });

  const rules: FormRules<typeof form> = {
    username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
    password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  };

  async function onSubmit() {
    if (!formRef.value) return;
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;
    loading.value = true;
    try {
      await userStore.login({ username: form.username, password: form.password });
      ElMessage.success('登录成功');
      router.push('/chat');
    } catch {
      // 401 已由请求层统一提示（"用户名或密码错误" 等），此处仅复位按钮。
    } finally {
      loading.value = false;
    }
  }
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-title">Data Agent</div>
      <div class="login-subtitle">请登录后使用</div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            autocomplete="username"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="login-submit" :loading="loading" @click="onSubmit">
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
  .login-page {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 100vh;
    background-color: var(--app-bg-page);
  }

  .login-card {
    width: 360px;
    padding: 32px;
    background-color: var(--app-bg-card);
    border: 1px solid var(--app-border);
    border-radius: 12px;
    box-shadow: var(--app-shadow-lg);
  }

  .login-title {
    font-size: 22px;
    font-weight: 700;
    color: var(--app-text-primary);
    letter-spacing: -0.5px;
  }

  .login-subtitle {
    margin-top: 4px;
    margin-bottom: 24px;
    font-size: 13px;
    color: var(--app-text-secondary);
  }

  .login-submit {
    width: 100%;
    margin-top: 8px;
  }
</style>
