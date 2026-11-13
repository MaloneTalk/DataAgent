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
  import { computed, onMounted, reactive, ref } from 'vue';
  import type { FormInstance, FormRules } from 'element-plus';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { createUserPat, getUserPats, revokeUserPat, type UserPat } from '@/api/pat';
  import { formatDateTime } from '@/utils/dateTime';

  const expiryOptions = [
    { value: 30, label: '30 天' },
    { value: 90, label: '90 天' },
    { value: 180, label: '180 天' },
    { value: 365, label: '365 天' },
  ];

  const pats = ref<UserPat[]>([]);
  const loading = ref(false);
  const error = ref(false);

  const createDialogVisible = ref(false);
  const createLoading = ref(false);
  const formRef = ref<FormInstance>();
  const form = reactive({
    name: '',
    expiryDays: 90,
  });

  const rules: FormRules = {
    name: [{ required: true, message: '请输入令牌名称', trigger: 'blur' }],
    expiryDays: [{ required: true, message: '请选择有效期', trigger: 'change' }],
  };

  const createdDialogVisible = ref(false);
  const createdToken = ref('');
  const createdPat = ref<UserPat | null>(null);

  const expiryPreview = computed(() => expiresAtFromDays(form.expiryDays));

  onMounted(fetchList);

  async function fetchList() {
    loading.value = true;
    error.value = false;
    try {
      pats.value = await getUserPats();
    } catch {
      error.value = true;
    } finally {
      loading.value = false;
    }
  }

  function patStatus(pat: UserPat): { label: string; type: 'success' | 'warning' | 'info' } {
    if (pat.revokedAt) return { label: '已吊销', type: 'info' };
    if (isExpired(pat)) return { label: '已过期', type: 'warning' };
    return { label: '有效', type: 'success' };
  }

  function isExpired(pat: UserPat): boolean {
    return new Date(pat.expiresAt).getTime() <= Date.now();
  }

  function canRevoke(pat: UserPat): boolean {
    return !pat.revokedAt && !isExpired(pat);
  }

  function handleCreate() {
    form.name = '';
    form.expiryDays = 90;
    formRef.value?.clearValidate();
    createDialogVisible.value = true;
  }

  async function submitCreate() {
    if (!formRef.value) return;
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;

    createLoading.value = true;
    try {
      const result = await createUserPat({
        name: form.name.trim(),
        expiresAt: expiresAtFromDays(form.expiryDays),
      });
      createDialogVisible.value = false;
      createdToken.value = result.token;
      createdPat.value = result.pat;
      createdDialogVisible.value = true;
      await fetchList();
    } finally {
      createLoading.value = false;
    }
  }

  async function handleRevoke(row: UserPat) {
    try {
      await ElMessageBox.confirm(`确定要吊销令牌「${row.name}」吗？`, '提示', {
        confirmButtonText: '吊销',
        cancelButtonText: '取消',
        type: 'warning',
      });
      await revokeUserPat(row.id);
      await fetchList();
      ElMessage.success('已吊销');
    } catch {
      // 用户取消或错误已由 request 拦截器处理
    }
  }

  async function copyToken() {
    try {
      await navigator.clipboard.writeText(createdToken.value);
      ElMessage.success('已复制到剪贴板');
    } catch {
      ElMessage.error('复制失败，请手动选择复制');
    }
  }

  function closeCreatedDialog() {
    createdDialogVisible.value = false;
    createdToken.value = '';
    createdPat.value = null;
  }

  function expiresAtFromDays(days: number): string {
    const date = new Date();
    date.setDate(date.getDate() + days);
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` + `T23:59:59`;
  }
</script>

<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">API 访问令牌</h2>
      <el-button type="primary" @click="handleCreate">新建令牌</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="令牌只在创建时显示一次"
      description="请创建后立即复制保存。丢失后只能吊销并重新创建。每个用户最多保留 10 个有效令牌。"
      class="pat-tip"
    />

    <el-table v-loading="loading" :data="pats" style="width: 100%">
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="Token 前缀" min-width="160">
        <template #default="{ row }">
          <code class="token-prefix">{{ row.tokenPrefix }}…</code>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="过期时间" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.expiresAt) }}</template>
      </el-table-column>
      <el-table-column label="最后使用" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.lastUsedAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="patStatus(row).type">{{ patStatus(row).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button v-if="canRevoke(row)" link type="danger" @click="handleRevoke(row)">
            吊销
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="error" class="error-tip">
      数据加载失败，
      <el-button type="primary" link @click="fetchList">点击重试</el-button>
    </div>

    <el-dialog v-model="createDialogVisible" title="新建访问令牌" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="例如 macbook-cli / cursor" maxlength="64" />
        </el-form-item>
        <el-form-item label="有效期" prop="expiryDays">
          <el-radio-group v-model="form.expiryDays">
            <el-radio-button v-for="item in expiryOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="到期时间">
          <span class="expiry-preview">{{ formatDateTime(expiryPreview) }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="createdDialogVisible"
      title="令牌创建成功"
      width="560px"
      :close-on-click-modal="false"
      @closed="closeCreatedDialog"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="请立即复制并妥善保存"
        description="关闭此窗口后将无法再次查看该 Token。"
        class="created-tip"
      />
      <div v-if="createdPat" class="created-meta">
        <div>
          <span class="meta-label">名称：</span>
          {{ createdPat.name }}
        </div>
        <div>
          <span class="meta-label">过期时间：</span>
          {{ formatDateTime(createdPat.expiresAt) }}
        </div>
      </div>
      <div class="token-box">
        <code>{{ createdToken }}</code>
      </div>
      <template #footer>
        <el-button type="primary" @click="copyToken">复制 Token</el-button>
        <el-button @click="closeCreatedDialog">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
  .pat-tip {
    margin-bottom: 16px;
  }

  .token-prefix {
    padding: 2px 6px;
    border-radius: 4px;
    background: var(--app-bg-hover);
    color: var(--app-text-secondary);
    font-size: 12px;
  }

  .error-tip {
    text-align: center;
    padding: 16px 0;
    color: var(--app-accent);
    font-size: 14px;
  }

  .expiry-preview {
    color: var(--app-text-secondary);
  }

  .created-tip {
    margin-bottom: 16px;
  }

  .created-meta {
    display: grid;
    gap: 6px;
    margin-bottom: 12px;
    color: var(--app-text-secondary);
    font-size: 13px;
  }

  .meta-label {
    color: var(--app-text-muted);
  }

  .token-box {
    padding: 12px;
    border: 1px solid var(--app-border);
    border-radius: 6px;
    background: var(--app-bg-hover);
    word-break: break-all;
  }

  .token-box code {
    font-size: 13px;
    color: var(--app-text-primary);
  }
</style>
