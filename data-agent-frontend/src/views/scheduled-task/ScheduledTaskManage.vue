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
  import {
    createScheduledTask,
    deleteScheduledTask,
    listScheduledTasks,
    runScheduledTask,
    setScheduledTaskEnabled,
    updateScheduledTask,
    type ScheduledTaskRequest,
    type ScheduledTaskResponse,
  } from '@/api/scheduledTask';

  const rows = ref<ScheduledTaskResponse[]>([]);
  const loading = ref(false);
  const runningTaskId = ref<number | null>(null);
  const submitLoading = ref(false);
  const dialogVisible = ref(false);
  const selectedTask = ref<ScheduledTaskResponse | null>(null);
  const formRef = ref<FormInstance>();

  const form = reactive<ScheduledTaskRequest>({
    name: '',
    prompt: '',
    scheduleType: 'DAILY',
    scheduleExpr: '09:00',
    enabled: true,
  });

  const rules: FormRules<ScheduledTaskRequest> = {
    name: [{ required: true, message: '任务名称不能为空', trigger: 'blur' }],
    prompt: [{ required: true, message: '提示词不能为空', trigger: 'blur' }],
    scheduleType: [{ required: true, message: '请选择调度类型', trigger: 'change' }],
    scheduleExpr: [{ required: true, message: '调度表达式不能为空', trigger: 'blur' }],
  };

  const schedulePlaceholders = {
    DAILY: '09:00',
    INTERVAL: 'PT1H',
    CRON: '0 0 9 * * *',
  };

  const schedulePlaceholder = computed(() => schedulePlaceholders[form.scheduleType]);

  async function loadTasks() {
    loading.value = true;
    try {
      const response = await listScheduledTasks();
      rows.value = response.data.data ?? [];
    } finally {
      loading.value = false;
    }
  }

  function resetForm() {
    Object.assign(form, {
      name: '',
      prompt: '',
      scheduleType: 'DAILY',
      scheduleExpr: '09:00',
      enabled: true,
    });
  }

  function openCreate() {
    selectedTask.value = null;
    resetForm();
    dialogVisible.value = true;
  }

  function openEdit(row: ScheduledTaskResponse) {
    selectedTask.value = row;
    Object.assign(form, {
      name: row.name,
      prompt: row.prompt,
      scheduleType: row.scheduleType,
      scheduleExpr: row.scheduleExpr,
      enabled: row.enabled,
    });
    dialogVisible.value = true;
  }

  async function submitTask() {
    if (!formRef.value) {
      return;
    }
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) {
      return;
    }

    const payload: ScheduledTaskRequest = {
      name: form.name.trim(),
      prompt: form.prompt.trim(),
      scheduleType: form.scheduleType,
      scheduleExpr: form.scheduleExpr.trim(),
      enabled: form.enabled,
    };

    submitLoading.value = true;
    try {
      if (selectedTask.value) {
        await updateScheduledTask(selectedTask.value.id, payload);
        ElMessage.success('任务已更新');
      } else {
        await createScheduledTask(payload);
        ElMessage.success('任务已创建');
      }
      dialogVisible.value = false;
      await loadTasks();
    } finally {
      submitLoading.value = false;
    }
  }

  async function toggleEnabled(row: ScheduledTaskResponse) {
    await setScheduledTaskEnabled(row.id, !row.enabled);
    ElMessage.success(row.enabled ? '任务已停用' : '任务已启用');
    await loadTasks();
  }

  async function runNow(row: ScheduledTaskResponse) {
    runningTaskId.value = row.id;
    try {
      const response = await runScheduledTask(row.id);
      if (response.data.data) {
        ElMessage.success('已提交运行');
      } else {
        ElMessage.warning('任务正在运行，未重复提交');
      }
      await loadTasks();
    } finally {
      runningTaskId.value = null;
    }
  }

  async function removeTask(row: ScheduledTaskResponse) {
    try {
      await ElMessageBox.confirm(`确定删除任务「${row.name}」吗？`, '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      });
      await deleteScheduledTask(row.id);
      ElMessage.success('任务已删除');
      await loadTasks();
    } catch {
      // ignore cancel
    }
  }

  function scheduleLabel(row: ScheduledTaskResponse) {
    return `${row.scheduleType} / ${row.scheduleExpr}`;
  }

  onMounted(() => {
    void loadTasks();
  });
</script>

<template>
  <div class="scheduled-page">
    <section class="page-panel">
      <div class="section-header">
        <div>
          <h2>定时任务</h2>
          <p>让 Agent 按固定时间、间隔或 cron 自动执行提示词。</p>
        </div>
        <div class="section-actions">
          <el-button :loading="loading" @click="loadTasks">刷新</el-button>
          <el-button type="primary" @click="openCreate">新建任务</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" class="task-table">
        <el-table-column prop="name" label="任务" min-width="160" show-overflow-tooltip />
        <el-table-column label="调度" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ scheduleLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-space>
              <el-tag
                :type="row.running ? 'warning' : row.enabled ? 'success' : 'info'"
                effect="plain"
              >
                {{ row.running ? '运行中' : row.enabled ? '启用' : '停用' }}
              </el-tag>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column label="上次结果" width="150">
          <template #default="{ row }">
            <el-tag
              :title="row.lastError || ''"
              :type="row.lastStatus === 'SUCCESS' ? 'success' : row.lastError ? 'danger' : 'info'"
              effect="plain"
            >
              {{ row.lastStatus || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下次运行" width="180">
          <template #default="{ row }">{{ row.nextRunAt?.replace('T', ' ') || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggleEnabled(row)">
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
            <el-button link type="primary" :loading="runningTaskId === row.id" @click="runNow(row)">
              运行
            </el-button>
            <el-button link type="danger" @click="removeTask(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="selectedTask ? '编辑定时任务' : '新建定时任务'"
      width="720px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="任务名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：每日销售复盘" />
        </el-form-item>
        <el-form-item label="提示词" prop="prompt">
          <el-input
            v-model="form.prompt"
            type="textarea"
            :rows="5"
            placeholder="写给 Agent 的任务内容"
          />
        </el-form-item>
        <el-form-item label="调度类型" prop="scheduleType">
          <el-radio-group v-model="form.scheduleType">
            <el-radio-button label="DAILY">每天</el-radio-button>
            <el-radio-button label="INTERVAL">间隔</el-radio-button>
            <el-radio-button label="CRON">Cron</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="表达式" prop="scheduleExpr">
          <el-input v-model="form.scheduleExpr" :placeholder="schedulePlaceholder" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitTask">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
  .scheduled-page {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .page-panel {
    background: var(--app-bg-card);
    border: 1px solid var(--app-border);
    border-radius: 8px;
    padding: 24px;
  }

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 16px;
    margin-bottom: 20px;
  }

  .section-header h2 {
    margin: 0 0 6px;
    font-size: 20px;
    color: var(--app-text-primary);
    font-weight: 700;
  }

  .section-header p {
    margin: 0;
    color: var(--app-text-secondary);
  }

  .section-actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .task-table {
    width: 100%;
  }
</style>
