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
  import { ref, reactive, onMounted, computed } from 'vue';
  import type { FormInstance, FormRules } from 'element-plus';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import { useDatasource } from '@/composables/useDatasource';
  import type { DatasourceResponse } from '@/api/datasource';
  import { useFieldErrors } from '@/composables/useFieldErrors';

  const {
    list: dataSourceList,
    loading,
    error,
    fetchList,
    addDatasource,
    editDatasource,
    removeDatasource,
    activate,
    deactivate,
  } = useDatasource();

  const dialogVisible = ref(false);
  const submitLoading = ref(false);
  const isEdit = ref(false);
  const formRef = ref<FormInstance>();
  const form = reactive({
    id: undefined as number | undefined,
    name: '',
    type: '',
    host: '',
    port: undefined as number | undefined,
    databaseName: '',
    username: '',
    password: '',
    connectionUrl: '',
    description: '',
  });
  const { fieldErrors, clearFieldErrors, applyFieldErrors } = useFieldErrors(form);

  onMounted(() => {
    fetchList();
  });

  // value 与后端 DataSourceType.code 一致
  const dataSourceTypes = [
    { value: 'mysql', label: 'MySQL' },
    { value: 'postgresql', label: 'PostgreSQL' },
    { value: 'oracle', label: 'Oracle' },
    { value: 'clickhouse', label: 'ClickHouse' },
    { value: 'sqlserver', label: 'SQL Server' },
    { value: 'dameng', label: '达梦 DM' },
    { value: 'oceanbase', label: 'OceanBase' },
    { value: 'sqlite', label: 'SQLite' },
  ];

  const isSqlite = computed(() => form.type === 'sqlite');

  const rules = computed<FormRules>(() => ({
    name: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
    type: [{ required: true, message: '请选择数据源类型', trigger: 'change' }],
    host: isSqlite.value ? [] : [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
    port: isSqlite.value ? [] : [{ required: true, message: '请输入端口', trigger: 'blur' }],
    databaseName: isSqlite.value
      ? []
      : [{ required: true, message: '请输入数据库名', trigger: 'blur' }],
    username: isSqlite.value ? [] : [{ required: true, message: '请输入用户名', trigger: 'blur' }],
    connectionUrl: isSqlite.value
      ? [
          {
            required: true,
            message: '请输入连接URL（如 jdbc:sqlite:/path/to/db）',
            trigger: 'blur',
          },
        ]
      : [],
  }));

  const resetForm = () => {
    Object.assign(form, {
      id: undefined,
      name: '',
      type: '',
      host: '',
      port: undefined,
      databaseName: '',
      username: '',
      password: '',
      connectionUrl: '',
      description: '',
    });
  };

  const handleAdd = () => {
    resetForm();
    clearFieldErrors();
    isEdit.value = false;
    dialogVisible.value = true;
  };

  const handleEdit = (row: DatasourceResponse) => {
    clearFieldErrors();
    Object.assign(form, {
      id: row.id,
      name: row.name,
      type: row.type,
      host: row.host,
      port: row.port,
      databaseName: row.databaseName,
      username: row.username,
      password: '',
      connectionUrl: row.connectionUrl,
      description: row.description,
    });
    isEdit.value = true;
    dialogVisible.value = true;
  };

  const handleDelete = async (row: DatasourceResponse) => {
    try {
      await ElMessageBox.confirm(`确定要删除数据源: ${row.name} 吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      });
      await removeDatasource(row.id);
      ElMessage.success('删除成功');
    } catch {
      // 用户取消或错误已由拦截器处理
    }
  };

  const handleActivate = async (row: DatasourceResponse) => {
    try {
      await activate(row.id);
      ElMessage.success('激活成功');
    } catch {
      // 错误已由 request 拦截器统一处理
    }
  };

  const handleDeactivate = async (row: DatasourceResponse) => {
    try {
      await deactivate(row.id);
      ElMessage.success('禁用成功');
    } catch {
      // 错误已由 request 拦截器统一处理
    }
  };

  const handleSubmit = async () => {
    if (!formRef.value) return;
    clearFieldErrors();
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;

    submitLoading.value = true;
    try {
      if (isEdit.value) {
        await editDatasource(form);
        ElMessage.success('编辑成功');
      } else {
        await addDatasource(form);
        ElMessage.success('新增成功');
      }
      dialogVisible.value = false;
    } catch (error) {
      applyFieldErrors(error);
    } finally {
      submitLoading.value = false;
    }
  };

  const handleCancel = () => {
    dialogVisible.value = false;
  };
</script>

<template>
  <div class="data-source">
    <div class="page-header">
      <h2 class="page-title">数据源管理</h2>
      <el-button type="primary" @click="handleAdd">新增数据源</el-button>
    </div>
    <el-table v-loading="loading" :data="dataSourceList" style="width: 100%">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expand-content">
            <div class="expand-item">
              <span class="expand-label">端口：</span>
              <span>{{ row.port ?? '-' }}</span>
            </div>
            <div class="expand-item">
              <span class="expand-label">数据库名：</span>
              <span>{{ row.databaseName ?? '-' }}</span>
            </div>
            <div class="expand-item">
              <span class="expand-label">用户名：</span>
              <span>{{ row.username ?? '-' }}</span>
            </div>
            <div class="expand-item">
              <span class="expand-label">连接URL：</span>
              <span>{{ row.connectionUrl || '-' }}</span>
            </div>
            <div class="expand-item">
              <span class="expand-label">描述：</span>
              <span>{{ row.description || '-' }}</span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="type" label="类型" />
      <el-table-column prop="host" label="主机地址" />
      <el-table-column prop="status" label="激活状态">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
            {{ row.status === 'ACTIVE' ? '已激活' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button
            v-if="row.status !== 'ACTIVE'"
            link
            type="success"
            @click="handleActivate(row)"
          >
            激活
          </el-button>
          <el-button v-else link type="warning" @click="handleDeactivate(row)">禁用</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="error" class="error-tip">
      数据加载失败，
      <el-button type="primary" link @click="fetchList">点击重试</el-button>
    </div>
  </div>

  <el-dialog
    v-model="dialogVisible"
    :title="isEdit ? '编辑数据源' : '新增数据源'"
    width="600px"
    :close-on-click-modal="false"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="数据源名称" prop="name" :error="fieldErrors.name">
        <el-input v-model="form.name" placeholder="请输入数据源名称" />
      </el-form-item>
      <el-form-item label="数据源类型" prop="type" :error="fieldErrors.type">
        <el-select v-model="form.type" placeholder="请选择数据源类型">
          <el-option
            v-for="item in dataSourceTypes"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-alert
        v-if="!isEdit"
        type="info"
        :closable="false"
        show-icon
        title="后端默认仅内置 MySQL 驱动"
        description="使用 PostgreSQL / Oracle 等其他数据库前，请先在 data-agent-backend/pom.xml 中添加对应 JDBC 驱动依赖并重新构建后端，否则连接时会提示「未找到数据库驱动」。"
        class="driver-tip"
      />
      <el-form-item label="主机地址" prop="host" :error="fieldErrors.host">
        <el-input v-model="form.host" placeholder="请输入主机地址" />
      </el-form-item>
      <el-form-item label="端口" prop="port" :error="fieldErrors.port">
        <el-input-number v-model="form.port" :min="1" :max="65535" placeholder="请输入端口" />
      </el-form-item>
      <el-form-item label="数据库名" prop="databaseName" :error="fieldErrors.databaseName">
        <el-input v-model="form.databaseName" placeholder="请输入数据库名" />
      </el-form-item>
      <el-form-item label="用户名" prop="username" :error="fieldErrors.username">
        <el-input v-model="form.username" placeholder="请输入用户名" />
      </el-form-item>
      <el-form-item label="密码" prop="password" :error="fieldErrors.password">
        <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
      </el-form-item>
      <el-alert
        v-if="isSqlite"
        type="info"
        :closable="false"
        show-icon
        title="SQLite 为本地文件数据库"
        description="无需填写主机 / 端口 / 数据库名，请在「连接URL」中直接填写形如 jdbc:sqlite:/path/to/db 的文件路径。"
        class="driver-tip"
      />
      <el-form-item label="连接URL" prop="connectionUrl" :error="fieldErrors.connectionUrl">
        <el-input v-model="form.connectionUrl" placeholder="请输入连接URL（可选）" />
      </el-form-item>
      <el-form-item label="描述" prop="description" :error="fieldErrors.description">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
        {{ isEdit ? '保存' : '确定' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
  .data-source {
    padding: 0;
  }

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
  }

  .page-title {
    font-size: 20px;
    font-weight: 600;
    color: var(--app-text-primary);
    margin: 0;
  }

  .error-tip {
    text-align: center;
    padding: 16px 0;
    color: var(--app-accent);
    font-size: 14px;
  }

  .driver-tip {
    margin-bottom: 18px;
  }

  .expand-content {
    padding: 12px 48px;
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px 32px;
    font-size: 14px;
    color: var(--app-text-secondary);
  }

  .expand-item {
    display: flex;
    gap: 4px;
  }

  .expand-label {
    color: var(--app-text-muted);
    white-space: nowrap;
  }
</style>
