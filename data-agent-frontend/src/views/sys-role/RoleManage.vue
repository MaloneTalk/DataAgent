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
  import { computed, ref, reactive, onMounted } from 'vue';
  import RoleDataScopeDialog from './RoleDataScopeDialog.vue';
  import RoleCapabilitiesDialog from './RoleCapabilitiesDialog.vue';
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
  import * as sysRoleApi from '@/api/sysRole';
  import type { RoleResponse } from '@/api/sysRole';
  import { formatDateTime } from '@/utils/dateTime';

  const capabilitiesVisible = ref(false);
  const capabilitiesRoleId = ref<number | null>(null);

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
  const editingId = ref<number | null>(null);
  const isEdit = computed(() => editingId.value !== null);
  const formRef = ref<FormInstance>();
  const form = reactive({ name: '', description: '' });
  const rules: FormRules<typeof form> = {
    name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  };

  function openCreate() {
    editingId.value = null;
    form.name = '';
    form.description = '';
    formVisible.value = true;
  }
  function openEdit(role: RoleResponse) {
    editingId.value = role.id;
    form.name = role.name;
    form.description = role.description;
    formVisible.value = true;
  }
  async function onSubmitForm() {
    if (!formRef.value) return;
    if (!(await formRef.value.validate().catch(() => false))) return;
    try {
      if (editingId.value != null) {
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

  const permVisible = ref(false);
  const permRoleId = ref<number | null>(null);
  const permRoleName = ref('');
  function openPermission(role: RoleResponse) {
    permRoleId.value = role.id;
    permRoleName.value = role.name;
    permVisible.value = true;
  }

  onMounted(reload);
</script>

<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">角色管理</h2>
    </div>

    <div class="table-actions">
      <el-button v-permission="'sys:role:create'" type="primary" @click="openCreate">
        新建角色
      </el-button>
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
      <el-table-column label="操作" width="340" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'sys:role:update'" size="small" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button
            v-permission="'sys:role:grant'"
            size="small"
            type="primary"
            @click="
              capabilitiesRoleId = row.id;
              capabilitiesVisible = true;
            "
          >
            功能权限
          </el-button>
          <el-button v-permission="'sys:role:grant'" size="small" @click="openPermission(row)">
            数据范围
          </el-button>
          <el-button
            v-permission="'sys:role:delete'"
            size="small"
            type="danger"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <RoleCapabilitiesDialog v-model="capabilitiesVisible" :role-id="capabilitiesRoleId" />
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

    <RoleDataScopeDialog v-model="permVisible" :role-id="permRoleId" :role-name="permRoleName" />
  </div>
</template>
