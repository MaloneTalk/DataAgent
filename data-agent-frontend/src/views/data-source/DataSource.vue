<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import { useDatasource } from "@/composables/useDatasource";
import type { DatasourceResponse } from "@/api/datasource";

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
  name: "",
  type: "",
  host: "",
  port: undefined as number | undefined,
  databaseName: "",
  username: "",
  password: "",
  connectionUrl: "",
  description: "",
});

onMounted(() => {
  fetchList();
});

const rules: FormRules = {
  name: [{ required: true, message: "请输入数据源名称", trigger: "blur" }],
  type: [{ required: true, message: "请选择数据源类型", trigger: "change" }],
  host: [{ required: true, message: "请输入主机地址", trigger: "blur" }],
  port: [{ required: true, message: "请输入端口", trigger: "blur" }],
  databaseName: [
    { required: true, message: "请输入数据库名", trigger: "blur" },
  ],
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
};

const dataSourceTypes = [
  { value: "MySQL", label: "MySQL" },
  { value: "PostgreSQL", label: "PostgreSQL" },
  { value: "Oracle", label: "Oracle" },
];

const resetForm = () => {
  Object.assign(form, {
    id: undefined,
    name: "",
    type: "",
    host: "",
    port: undefined,
    databaseName: "",
    username: "",
    password: "",
    connectionUrl: "",
    description: "",
  });
};

const handleAdd = () => {
  resetForm();
  isEdit.value = false;
  dialogVisible.value = true;
};

const handleEdit = (row: DatasourceResponse) => {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    type: row.type,
    host: row.host,
    port: row.port,
    databaseName: row.databaseName,
    username: row.username,
    password: row.password,
    connectionUrl: row.connectionUrl,
    description: row.description,
  });
  isEdit.value = true;
  dialogVisible.value = true;
};

const handleDelete = async (row: DatasourceResponse) => {
  try {
    await ElMessageBox.confirm(`确定要删除数据源: ${row.name} 吗？`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
    await removeDatasource(row.id);
    ElMessage.success("删除成功");
  } catch {
    // 用户取消或错误已由拦截器处理
  }
};

const handleActivate = async (row: DatasourceResponse) => {
  try {
    await activate(row.id);
    ElMessage.success("激活成功");
  } catch {
    // 错误已由 request 拦截器统一处理
  }
};

const handleDeactivate = async (row: DatasourceResponse) => {
  try {
    await deactivate(row.id);
    ElMessage.success("禁用成功");
  } catch {
    // 错误已由 request 拦截器统一处理
  }
};

const handleSubmit = async () => {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  submitLoading.value = true;
  try {
    if (isEdit.value) {
      await editDatasource(form);
      ElMessage.success("编辑成功");
    } else {
      await addDatasource(form);
      ElMessage.success("新增成功");
    }
    dialogVisible.value = false;
  } catch {
    // 错误已由 request 拦截器统一处理
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
              <span>{{ row.port ?? "-" }}</span>
            </div>
            <div class="expand-item">
              <span class="expand-label">数据库名：</span>
              <span>{{ row.databaseName ?? "-" }}</span>
            </div>
            <div class="expand-item">
              <span class="expand-label">用户名：</span>
              <span>{{ row.username ?? "-" }}</span>
            </div>
            <div class="expand-item">
              <span class="expand-label">连接URL：</span>
              <span>{{ row.connectionUrl || "-" }}</span>
            </div>
            <div class="expand-item">
              <span class="expand-label">描述：</span>
              <span>{{ row.description || "-" }}</span>
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
            {{ row.status === "ACTIVE" ? "已激活" : "已禁用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)"
            >编辑</el-button
          >
          <el-button
            v-if="row.status !== 'ACTIVE'"
            link
            type="success"
            @click="handleActivate(row)"
          >
            激活
          </el-button>
          <el-button v-else link type="warning" @click="handleDeactivate(row)">
            禁用
          </el-button>
          <el-button link type="danger" @click="handleDelete(row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <div v-if="error" class="error-tip">
      数据加载失败，<el-button type="primary" link @click="fetchList"
        >点击重试</el-button
      >
    </div>
  </div>

  <el-dialog
    v-model="dialogVisible"
    :title="isEdit ? '编辑数据源' : '新增数据源'"
    width="600px"
    :close-on-click-modal="false"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="数据源名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入数据源名称" />
      </el-form-item>
      <el-form-item label="数据源类型" prop="type">
        <el-select v-model="form.type" placeholder="请选择数据源类型">
          <el-option
            v-for="item in dataSourceTypes"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="主机地址" prop="host">
        <el-input v-model="form.host" placeholder="请输入主机地址" />
      </el-form-item>
      <el-form-item label="端口" prop="port">
        <el-input-number
          v-model="form.port"
          :min="1"
          :max="65535"
          placeholder="请输入端口"
        />
      </el-form-item>
      <el-form-item label="数据库名" prop="databaseName">
        <el-input v-model="form.databaseName" placeholder="请输入数据库名" />
      </el-form-item>
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" placeholder="请输入用户名" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="请输入密码"
          show-password
        />
      </el-form-item>
      <el-form-item label="连接URL">
        <el-input
          v-model="form.connectionUrl"
          placeholder="请输入连接URL（可选）"
        />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="请输入描述"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button
        type="primary"
        :loading="submitLoading"
        @click="handleSubmit"
        >{{ isEdit ? "保存" : "确定" }}</el-button
      >
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
  color: #303133;
  margin: 0;
}

.error-tip {
  text-align: center;
  padding: 16px 0;
  color: #f56c6c;
  font-size: 14px;
}

.expand-content {
  padding: 12px 48px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 32px;
  font-size: 14px;
  color: #606266;
}

.expand-item {
  display: flex;
  gap: 4px;
}

.expand-label {
  color: #909399;
  white-space: nowrap;
}
</style>
