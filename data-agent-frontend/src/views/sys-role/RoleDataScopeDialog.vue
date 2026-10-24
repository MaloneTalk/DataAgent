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
  import { ref, watch } from 'vue';
  import { ElMessage } from 'element-plus';
  import * as sysRoleApi from '@/api/sysRole';
  import {
    getDatasourceList,
    getDatasourceTables,
    type DatasourceResponse,
  } from '@/api/datasource';
  // ── Permission dialog ──
  const permVisible = defineModel<boolean>({ required: true });
  const props = defineProps<{ roleId: number | null; roleName: string }>();
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

  function resetPermissions() {
    allTables.value = [];
    checkedTables.value = [];
    tableColumns.value = {};
    blacklistedColumns.value = {};
  }

  watch(permVisible, async value => {
    if (!value) return;
    selectedDs.value = null;
    resetPermissions();
    try {
      datasources.value = await getDatasourceList();
    } catch {
      datasources.value = [];
    }
  });

  watch(selectedDs, async (dsId, _previous, onCleanup) => {
    let current = true;
    onCleanup(() => {
      current = false;
    });
    if (dsId == null) {
      resetPermissions();
      return;
    }
    permLoading.value = true;
    try {
      const [tables, perms, colPerms, columns] = await Promise.all([
        getDatasourceTables(dsId),
        sysRoleApi.getPermissions(props.roleId!),
        sysRoleApi.getColumnPermissions(props.roleId!, dsId),
        sysRoleApi.getAllTableColumns(dsId).catch(() => ({})),
      ]);
      if (!current) return;
      allTables.value = tables;
      const dsPerm = perms.find(p => p.datasourceId === dsId);
      checkedTables.value = dsPerm ? dsPerm.tableNames : [];

      tableColumns.value = columns;
      blacklistedColumns.value = Object.fromEntries(
        colPerms.map(p => [p.tableName, p.columnNames]),
      );
    } catch {
      if (!current) return;
      resetPermissions();
    } finally {
      if (current) permLoading.value = false;
    }
  });

  async function savePerm() {
    if (props.roleId == null || selectedDs.value == null) return;
    const roleId = props.roleId;
    const datasourceId = selectedDs.value;
    const tableNames = [...checkedTables.value];
    const hidden = Object.fromEntries(
      Object.entries(blacklistedColumns.value).map(([table, columns]) => [table, [...columns]]),
    );
    savingPerm.value = true;
    try {
      // Save table whitelist
      await sysRoleApi.savePermissions(roleId, {
        datasourceId,
        tableNames,
      });

      // Save column blacklists for each checked table
      await Promise.all(
        tableNames.map(tableName =>
          sysRoleApi.saveColumnPermissions(roleId, {
            datasourceId,
            tableName,
            columnNames: hidden[tableName] || [],
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
</script>
<template>
  <!-- Permission dialog -->
  <el-dialog
    v-model="permVisible"
    :title="`表权限 & 列权限 — ${props.roleName}`"
    width="640px"
    :close-on-click-modal="false"
  >
    <el-form label-width="80px">
      <el-form-item label="数据源">
        <el-select
          v-model="selectedDs"
          :disabled="savingPerm"
          placeholder="选择数据源"
          clearable
          style="width: 100%"
        >
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
            隐藏列（勾选即不可见，新增列默认可见）：
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
          style="margin-top: 4px; margin-left: 24px; font-size: 12px; color: var(--app-text-muted)"
        >
          该表无列信息
        </div>
      </div>
      <div style="margin-top: 16px">
        <el-button type="primary" :loading="savingPerm" :disabled="permLoading" @click="savePerm">
          保存权限
        </el-button>
      </div>
    </div>
    <div v-else style="color: var(--app-text-muted); padding: 16px 0">请先选择一个数据源</div>
  </el-dialog>
</template>
