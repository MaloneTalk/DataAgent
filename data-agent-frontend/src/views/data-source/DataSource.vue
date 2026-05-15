<script setup lang="ts">
import { ref } from "vue";

interface DataSource {
  id: string;
  name: string;
  type: string;
  host: string;
  status: string;
}

const dataSourceList = ref<DataSource[]>([
  {
    id: "1",
    name: "MySQL数据库",
    type: "MySQL",
    host: "localhost:3306",
    status: "online",
  },
  {
    id: "2",
    name: "PostgreSQL",
    type: "PostgreSQL",
    host: "localhost:5432",
    status: "online",
  },
  {
    id: "3",
    name: "Redis缓存",
    type: "Redis",
    host: "localhost:6379",
    status: "offline",
  },
]);

const handleAdd = () => {};

const handleEdit = (row: DataSource) => {};

const handleDelete = (row: DataSource) => {};
</script>

<template>
  <div class="data-source">
    <div class="page-header">
      <h2 class="page-title">数据源管理</h2>
      <el-button type="primary" @click="handleAdd">新增数据源</el-button>
    </div>
    <el-table :data="dataSourceList" style="width: 100%">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="type" label="类型" />
      <el-table-column prop="host" label="主机地址" />
      <el-table-column prop="status" label="状态">
        <template #default="{ row }">
          <el-tag :type="row.status === 'online' ? 'success' : 'danger'">
            {{ row.status === "online" ? "在线" : "离线" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)"
            >编辑</el-button
          >
          <el-button link type="danger" @click="handleDelete(row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </div>
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
</style>
