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
    disableScheduledTask,
    enableScheduledTask,
    listScheduledTaskRuns,
    listScheduledTasks,
    runScheduledTask,
    updateScheduledTask,
    type ScheduleType,
    type ScheduledTaskRequest,
    type ScheduledTaskResponse,
    type ScheduledTaskRunResponse,
    type SessionMode,
  } from '@/api/scheduledTask';

  interface TaskForm {
    name: string;
    prompt: string;
    scheduleType: ScheduleType;
    scheduleExpr: string;
    timezone: string;
    enabled: boolean;
    sessionMode: SessionMode;
    sessionId: string;
  }

  const rows = ref<ScheduledTaskResponse[]>([]);
  const runs = ref<ScheduledTaskRunResponse[]>([]);
  const keyword = ref('');
  const loading = ref(false);
  const runningTaskId = ref<number | null>(null);
  const submitLoading = ref(false);
  const dialogVisible = ref(false);
  const runsDrawerVisible = ref(false);
  const selectedTask = ref<ScheduledTaskResponse | null>(null);
  const formRef = ref<FormInstance>();

  const form = reactive<TaskForm>({
    name: '',
    prompt: '',
    scheduleType: 'DAILY',
    scheduleExpr: '09:00',
    timezone: 'Asia/Shanghai',
    enabled: true,
    sessionMode: 'NEW_EACH_RUN',
    sessionId: '',
  });

  const rules: FormRules<TaskForm> = {
    name: [{ required: true, message: '任务名称不能为空', trigger: 'blur' }],
    prompt: [{ required: true, message: '提示词不能为空', trigger: 'blur' }],
    scheduleType: [{ required: true, message: '请选择调度类型', trigger: 'change' }],
    scheduleExpr: [{ required: true, message: '调度表达式不能为空', trigger: 'blur' }],
    timezone: [{ required: true, message: '时区不能为空', trigger: 'blur' }],
    sessionId: [
      {
        validator: (_rule, value, callback) => {
          if (form.sessionMode === 'FIXED_SESSION' && !String(value || '').trim()) {
            callback(new Error('固定会话模式需要填写 Session ID'));
            return;
          }
          callback();
        },
        trigger: 'blur',
      },
    ],
  };

  const filteredRows = computed(() => {
    const value = keyword.value.trim().toLowerCase();
    if (!value) {
      return rows.value;
    }
    return rows.value.filter(
      item =>
        item.name.toLowerCase().includes(value) ||
        item.prompt.toLowerCase().includes(value) ||
        (item.sessionId || '').toLowerCase().includes(value),
    );
  });

  const schedulePlaceholder = computed(() => {
    if (form.scheduleType === 'DAILY') {
      return '09:00';
    }
    if (form.scheduleType === 'INTERVAL') {
      return 'PT1H';
    }
    return '0 0 9 * * *';
  });

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
      timezone: 'Asia/Shanghai',
      enabled: true,
      sessionMode: 'NEW_EACH_RUN',
      sessionId: '',
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
      timezone: row.timezone,
      enabled: row.enabled,
      sessionMode: row.sessionMode,
      sessionId: row.sessionId ?? '',
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
      timezone: form.timezone.trim(),
      enabled: form.enabled,
      sessionMode: form.sessionMode,
      sessionId: form.sessionMode === 'FIXED_SESSION' ? form.sessionId.trim() : undefined,
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
    if (row.enabled) {
      await disableScheduledTask(row.id);
      ElMessage.success('任务已停用');
    } else {
      await enableScheduledTask(row.id);
      ElMessage.success('任务已启用');
    }
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

  async function openRuns(row: ScheduledTaskResponse) {
    selectedTask.value = row;
    runsDrawerVisible.value = true;
    const response = await listScheduledTaskRuns(row.id);
    runs.value = response.data.data ?? [];
  }

  function formatTime(value: string | null) {
    return value ? value.replace('T', ' ') : '-';
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
          <el-input
            v-model="keyword"
            class="keyword-field"
            clearable
            placeholder="搜索任务 / 提示词 / Session"
          />
          <el-button :loading="loading" @click="loadTasks">刷新</el-button>
          <el-button type="primary" @click="openCreate">新建任务</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="filteredRows" class="task-table">
        <el-table-column prop="name" label="任务" min-width="160" show-overflow-tooltip />
        <el-table-column label="调度" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ scheduleLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="timezone" label="时区" width="140" />
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-space>
              <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">
                {{ row.enabled ? '启用' : '停用' }}
              </el-tag>
              <el-tag v-if="row.running" type="warning" effect="plain">运行中</el-tag>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column label="下次运行" width="180">
          <template #default="{ row }">{{ formatTime(row.nextRunAt) }}</template>
        </el-table-column>
        <el-table-column label="上次结果" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.lastStatus" effect="plain">{{ row.lastStatus }}</el-tag>
            <span v-else>-</span>
          </template>
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
            <el-button link type="primary" @click="openRuns(row)">记录</el-button>
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
        <el-form-item label="时区" prop="timezone">
          <el-input v-model="form.timezone" placeholder="Asia/Shanghai" />
        </el-form-item>
        <el-form-item label="会话模式" prop="sessionMode">
          <el-radio-group v-model="form.sessionMode">
            <el-radio-button label="NEW_EACH_RUN">每次新会话</el-radio-button>
            <el-radio-button label="FIXED_SESSION">固定会话</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="form.sessionMode === 'FIXED_SESSION'"
          label="Session ID"
          prop="sessionId"
        >
          <el-input v-model="form.sessionId" placeholder="固定复用的 Session ID" />
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

    <el-drawer
      v-model="runsDrawerVisible"
      :title="selectedTask ? `${selectedTask.name} 的运行记录` : '运行记录'"
      size="680px"
    >
      <el-table :data="runs" class="task-table">
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="sessionId" label="Session" min-width="220" show-overflow-tooltip />
        <el-table-column label="开始时间" width="180">
          <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="180">
          <template #default="{ row }">{{ formatTime(row.finishedAt) }}</template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误" min-width="220" show-overflow-tooltip />
      </el-table>
      <div v-if="runs.length === 0" class="empty-tip">暂无运行记录</div>
    </el-drawer>
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

  .keyword-field {
    width: 260px;
  }

  .task-table {
    width: 100%;
  }

  .empty-tip {
    padding: 32px 0;
    text-align: center;
    color: var(--app-text-muted);
  }
</style>
