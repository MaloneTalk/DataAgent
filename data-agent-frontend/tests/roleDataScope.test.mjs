/*
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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import { setImmediate } from 'node:timers/promises';
import { URL } from 'node:url';
import vm from 'node:vm';
import ts from 'typescript';
import { effectScope, nextTick, reactive, ref } from 'vue';

const require = createRequire(import.meta.url);
const source = readFileSync(
  new URL('../src/views/sys-role/RoleDataScopeDialog.vue', import.meta.url),
  'utf8',
);
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const code = ts.transpileModule(
  `${script}\nmodule.exports = { permVisible, selectedDs, checkedTables, canSave, savePerm };`,
  { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } },
).outputText;

async function settle() {
  await nextTick();
  await setImmediate();
  await nextTick();
}

function dialog(t, overrides = {}) {
  const saved = [];
  const props = reactive({ roleId: 2, roleName: 'Analyst' });
  const api = {
    getPermissions: async () => [{ datasourceId: 1, tableNames: ['orders'] }],
    getColumnPermissions: async () => [{ tableName: 'orders', columnNames: ['secret'] }],
    getAllTableColumns: async () => ({ orders: ['id', 'secret'] }),
    saveDataScope: async (roleId, request) => saved.push({ roleId, request }),
    ...overrides,
  };
  const modules = {
    vue: require('vue'),
    'element-plus': { ElMessage: { success() {} } },
    '@/api/sysRole': api,
    '@/api/datasource': {
      getDatasourceList: async () => [{ id: 1, name: 'Source' }],
      getDatasourceTables: async () => ['orders'],
    },
  };
  const module = { exports: {} };
  const scope = effectScope();
  t.after(() => scope.stop());
  scope.run(() =>
    vm.runInNewContext(code, {
      module,
      exports: module.exports,
      require: name => modules[name],
      defineModel: () => ref(false),
      defineProps: () => props,
    }),
  );
  return { ...module.exports, saved, props };
}

async function open(state) {
  state.permVisible.value = true;
  await settle();
  state.selectedDs.value = 1;
  await settle();
}

for (const method of ['getPermissions', 'getColumnPermissions', 'getAllTableColumns']) {
  test(`blocks saves when ${method} fails`, async t => {
    const state = dialog(t, {
      [method]: async () => {
        throw new Error('Load failed');
      },
    });
    await open(state);
    assert.equal(state.canSave.value, false);
    await state.savePerm();
    assert.equal(state.saved.length, 0);
  });
}

test('allows an explicit empty grant only after successful loading', async t => {
  const state = dialog(t);
  await open(state);
  assert.equal(state.canSave.value, true);
  state.checkedTables.value = [];
  await state.savePerm();
  assert.equal(state.saved.length, 1);
  assert.equal(state.saved[0].roleId, 2);
  assert.equal(
    JSON.stringify(state.saved[0].request),
    JSON.stringify({ datasourceId: 1, tableNames: [], hiddenColumns: {} }),
  );
});

test('blocks stale grants immediately after the role or datasource changes', async t => {
  const state = dialog(t);
  await open(state);
  state.props.roleId = 3;
  assert.equal(state.canSave.value, false);
  await state.savePerm();
  assert.equal(state.saved.length, 0);
  await settle();
  assert.equal(state.canSave.value, true);
  state.selectedDs.value = 2;
  assert.equal(state.canSave.value, false);
  await state.savePerm();
  assert.equal(state.saved.length, 0);
});

test('ignores a late response after a failed switch to another datasource', async t => {
  let finish;
  let calls = 0;
  const state = dialog(t, {
    getPermissions: () => {
      if (calls++ === 0)
        return new Promise(resolve => {
          finish = resolve;
        });
      return Promise.reject(new Error('Load failed'));
    },
  });
  await open(state);
  state.selectedDs.value = 2;
  await settle();
  finish([{ datasourceId: 1, tableNames: ['orders'] }]);
  await settle();
  assert.equal(state.canSave.value, false);
  await state.savePerm();
  assert.equal(state.saved.length, 0);
});
