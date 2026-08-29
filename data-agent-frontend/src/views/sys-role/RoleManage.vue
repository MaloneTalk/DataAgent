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
  import { ref, reactive, onMounted, watch } from 'vue';
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
  import * as sysRoleApi from '@/api/sysRole';
  import { getDatasourceList, type DatasourceResponse } from '@/api/datasource';
  import type { RoleResponse } from '@/api/sysRole';
  import request from '@/api/request';
  import { formatDateTime } from '@/views/semantic/utils';

  const loading = ref(false);
  const roles = ref<RoleResponse[]>([]);

  async function reload() {
    loading.value = true;
    try {
      roles.value = await sysRoleApi.listRoles();
    } finally {
      loading.value = false;
    }
  }

  // ── Create / Edit dialog ──
  const formVisible = ref(false);
  const isEdit = ref(false);
  const editingId = ref<number | null>(null);
  const formRef = ref<FormInstance>();
  const form = reactive({ name: '', description: '' });
  const rules: FormRules<typeof form> = {
    name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  };

  function openCreate() {
    isEdit.value = false;
    form.name = '';
    form.description = '';
    formVisible.value = true;
  }
  function openEdit(role: RoleResponse) {
    isEdit.value = true;
    editingId.value = role.id;
    form.name = role.name;
    form.description = role.description;
    formVisible.value = true;
  }
  async function onSubmitForm() {
    if (!formRef.value) return;
    if (!(await formRef.value.validate().catch(() => false))) return;
    try {
      if (isEdit.value && editingId.value != null) {
        await sysRoleApi.updateRole(editingId.value, { ...form });
        ElMessage.success('更新成功');
      } else {
        await sysRoleApi.createRole({ ...form });
        ElMessage.success('创建成功');
      }
      formVisible.value = false;
      await reload();
    } catch {
      /* toast handled */
    }
  }

  // ── Delete ──
  async function handleDelete(role: RoleResponse) {
    try {
      await ElMessageBox.confirm(`确定删除角色「${role.name}」吗？`, '提示', { type: 'warning' });
      await sysRoleApi.deleteRole(role.id);
      ElMessage.success('删除成功');
      await reload();
    } catch {
      /* cancel or error */
    }
  }

  // ── Permission dialog ──
  const permVisible = ref(false);
  const permRoleId = ref<number | null>(null);
  const permRoleName = ref('');
  const permLoading = ref(false);
  const datasources = ref<DatasourceResponse[]>([]);
  const selectedDs = ref<number | null>(null);
  const allTables = ref<string[]>([]);
  const checkedTables = ref<string[]>([]);
  const savingPerm = ref(false);

  // Column-level state: tableName → all columns, tableName → blacklisted columns
  const tableColumns = ref<Record<string, string[]>>({});
  const blacklistedColumns = ref<Record<string, string[]>>({});

  function toggleTable(tableName: string, checked: boolean) {
    if (checked) {
      if (!checkedTables.value.includes(tableName)) {
        checkedTables.value.push(tableName);
      }
    } else {
      checkedTables.value = checkedTables.value.filter(t => t !== tableName);
      delete blacklistedColumns.value[tableName];
    }
  }

  function onColumnBlacklistChange(tableName: string, vals: string[]) {
    blacklistedColumns.value[tableName] = vals;
  }

  async function openPermission(role: RoleResponse) {
    permRoleId.value = role.id;
    permRoleName.value = role.name;
    selectedDs.value = null;
    allTables.value = [];
    checkedTables.value = [];
    tableColumns.value = {};
    blacklistedColumns.value = {};
    try {
      const list = await getDatasourceList();
      datasources.value = list.data.data;
    } catch {
      datasources.value = [];
    }
    permVisible.value = true;
  }

  watch(selectedDs, async dsId => {
    if (dsId == null) {
      allTables.value = [];
      checkedTables.value = [];
      tableColumns.value = {};
      blacklistedColumns.value = {};
      return;
    }
    permLoading.value = true;
    try {
      const [tables, perms, colPerms] = await Promise.all([
        request
          .get<{ code: number; message: string; data: string[] }>(`/datasource/${dsId}/tables`)
          .then(r => r.data.data),
        sysRoleApi.getPermissions(permRoleId.value!),
        sysRoleApi.getColumnPermissions(permRoleId.value!, dsId),
      ]);
      allTables.value = tables;
      const dsPerm = perms.find(p => p.datasourceId === dsId);
      checkedTables.value = dsPerm ? dsPerm.tableNames : [];

      // Load all columns for all tables in one request
      try {
        tableColumns.value = await sysRoleApi.getAllTableColumns(dsId);
      } catch {
        tableColumns.value = {};
      }

      // Map existing column blacklist
      const blMap: Record<string, string[]> = {};
      for (const cp of colPerms) {
        blMap[cp.tableName] = cp.columnNames;
      }
      blacklistedColumns.value = blMap;
    } catch {
      allTables.value = [];
      checkedTables.value = [];
      tableColumns.value = {};
      blacklistedColumns.value = {};
    } finally {
      permLoading.value = false;
    }
  });

  async function savePerm() {
    if (permRoleId.value == null || selectedDs.value == null) return;
    savingPerm.value = true;
    try {
      // Save table whitelist
      await sysRoleApi.savePermissions(permRoleId.value, {
        datasourceId: selectedDs.value,
        tableNames: checkedTables.value,
      });

      // Save column blacklists for each checked table
      await Promise.all(
        checkedTables.value.map(tableName =>
          sysRoleApi.saveColumnPermissions(permRoleId.value!, {
            datasourceId: selectedDs.value!,
            tableName,
            columnNames: blacklistedColumns.value[tableName] || [],
          }),
        ),
      );

      ElMessage.success('权限已保存');
    } catch {
      /* toast handled */
    } finally {
      savingPerm.value = false;
    }
  }

  onMounted(reload);
</script>

<template>
  <div class="role-manage-page">
    <div class="page-header">
      <h2 class="page-title">角色管理</h2>
    </div>

    <div class="table-actions">
      <el-button type="primary" @click="openCreate">新建角色</el-button>
    </div>

    <el-table v-loading="loading" :data="roles" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="name" label="角色名称" min-width="140" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="创建时间" width="180" align="center">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" align="center" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="primary" @click="openPermission(row)">权限</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create / Edit dialog -->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑角色' : '新建角色'"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
        @submit.prevent="onSubmitForm"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- Permission dialog -->
    <el-dialog
      v-model="permVisible"
      :title="`表权限 & 列权限 — ${permRoleName}`"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form label-width="80px">
        <el-form-item label="数据源">
          <el-select v-model="selectedDs" placeholder="选择数据源" clearable style="width: 100%">
            <el-option v-for="ds in datasources" :key="ds.id" :value="ds.id" :label="ds.name" />
          </el-select>
        </el-form-item>
      </el-form>
      <div
        v-if="selectedDs"
        v-loading="permLoading"
        style="margin-top: 8px; max-height: 50vh; overflow-y: auto"
      >
        <div
          v-if="!permLoading && allTables.length === 0"
          style="color: var(--app-text-muted); padding: 16px 0"
        >
          该数据源下暂无物理表
        </div>
        <div
          v-for="tableName in allTables"
          :key="tableName"
          style="
            margin-bottom: 8px;
            border: 1px solid var(--app-border);
            border-radius: 6px;
            padding: 8px 12px;
          "
        >
          <el-checkbox
            :model-value="checkedTables.includes(tableName)"
            @change="(val: boolean) => toggleTable(tableName, val)"
            style="font-weight: 600"
          >
            {{ tableName }}
          </el-checkbox>
          <div
            v-if="
              checkedTables.includes(tableName) &&
              tableColumns[tableName] &&
              tableColumns[tableName].length > 0
            "
            style="
              margin-top: 6px;
              margin-left: 24px;
              padding: 6px 0;
              border-top: 1px dashed var(--app-border);
            "
          >
            <div style="font-size: 12px; color: var(--app-text-muted); margin-bottom: 4px">
              隐藏列（勾选即对该角色不可见）：
            </div>
            <el-checkbox-group
              :model-value="blacklistedColumns[tableName] || []"
              @update:model-value="(vals: string[]) => onColumnBlacklistChange(tableName, vals)"
            >
              <el-checkbox
                v-for="col in tableColumns[tableName]"
                :key="col"
                :value="col"
                style="margin-right: 24px; margin-bottom: 2px"
              >
                {{ col }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
          <div
            v-else-if="checkedTables.includes(tableName)"
            style="
              margin-top: 4px;
              margin-left: 24px;
              font-size: 12px;
              color: var(--app-text-muted);
            "
          >
            该表无列信息
          </div>
        </div>
        <div style="margin-top: 16px">
          <el-button type="primary" :loading="savingPerm" @click="savePerm">保存权限</el-button>
        </div>
      </div>
      <div v-else style="color: var(--app-text-muted); padding: 16px 0">请先选择一个数据源</div>
    </el-dialog>
  </div>
</template>

<style scoped>
  .role-manage-page {
    width: 100%;
  }
  .table-actions {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 12px;
    margin-bottom: 20px;
  }
</style>
