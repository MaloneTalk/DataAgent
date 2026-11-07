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
  import { computed, reactive, ref, watch } from 'vue';
  import { ElMessage } from 'element-plus';
  import { useUserStore } from '@/stores/user';
  import { confirmCsv, previewCsv, repreviewCsv, type CsvImport } from '@/api/csvImport';

  const props = defineProps<{ modelValue: boolean; initial?: CsvImport }>();
  const emit = defineEmits<{
    (e: 'update:modelValue', value: boolean): void;
    (e: 'imported'): void;
  }>();
  const user = useUserStore();
  const visible = computed({
    get: () => props.modelValue,
    set: value => emit('update:modelValue', value),
  });
  const options = reactive({ encoding: 'UTF-8', delimiter: ',', header: true, emptyAsNull: true });
  const file = ref<File>();
  const task = ref<CsvImport>();
  const busy = ref(false);
  const previewOptions = ref('');
  const changed = computed(() => previewOptions.value !== JSON.stringify(options));
  const canConfirm = computed(
    () => user.hasPermission('data:csv:import') && user.hasPermission('semantic:table:sync'),
  );
  const types = ['VARCHAR', 'TEXT', 'BIGINT', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME'];

  watch(
    () => props.modelValue,
    async open => {
      if (!open) return;
      file.value = undefined;
      task.value = undefined;
      Object.assign(
        options,
        props.initial?.options ?? {
          encoding: 'UTF-8',
          delimiter: ',',
          header: true,
          emptyAsNull: true,
        },
      );
      if (props.initial) {
        busy.value = true;
        try {
          task.value = await repreviewCsv(props.initial.id, options);
          previewOptions.value = JSON.stringify(options);
        } catch {
          // The request interceptor displays the error.
        } finally {
          busy.value = false;
        }
      }
    },
  );

  function chooseFile(event: Event) {
    file.value = (event.target as unknown as { files?: File[] }).files?.[0];
    task.value = undefined;
  }

  async function preview() {
    if (!task.value && !file.value) {
      ElMessage.warning('请先选择 CSV 文件');
      return;
    }
    if (file.value && file.value.size > 50 * 1024 * 1024) {
      ElMessage.warning('文件不能超过 50 MiB');
      return;
    }
    busy.value = true;
    try {
      task.value = task.value
        ? await repreviewCsv(task.value.id, options)
        : await previewCsv(file.value!, options);
      previewOptions.value = JSON.stringify(options);
    } catch {
      // The request interceptor displays the error.
    } finally {
      busy.value = false;
    }
  }

  async function confirm() {
    if (!task.value || changed.value) return;
    if (!task.value.displayName.trim() || task.value.columns.some(column => !column.name.trim())) {
      ElMessage.warning('请填写表名称及所有字段名称');
      return;
    }
    busy.value = true;
    try {
      await confirmCsv(task.value);
      ElMessage.success('已提交导入，可在记录中查看进度');
      emit('imported');
      visible.value = false;
    } catch {
      // The request interceptor displays the error.
    } finally {
      busy.value = false;
    }
  }
</script>

<template>
  <el-dialog
    v-model="visible"
    title="导入 CSV"
    width="min(1100px, 95vw)"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-alert
      title="单文件最多 50 MiB、10 万条记录、100 列。请确认字段类型，转换失败会停止整次导入。"
      type="info"
      :closable="false"
    />
    <el-form label-width="95px" class="csv-form" :disabled="busy">
      <el-form-item label="CSV 文件">
        <input type="file" accept=".csv,text/csv" aria-label="选择 CSV 文件" @change="chooseFile" />
        <span v-if="props.initial && !file">{{ props.initial.originalFilename }}</span>
      </el-form-item>
      <div class="parse-options">
        <el-form-item label="编码">
          <el-select v-model="options.encoding">
            <el-option label="UTF-8" value="UTF-8" />
            <el-option label="GB18030" value="GB18030" />
          </el-select>
        </el-form-item>
        <el-form-item label="分隔符">
          <el-select v-model="options.delimiter">
            <el-option label="逗号" value="," />
            <el-option label="分号" value=";" />
            <el-option label="Tab" :value="'\t'" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="解析选项">
        <el-checkbox v-model="options.header">第一条记录是表头</el-checkbox>
        <el-checkbox v-model="options.emptyAsNull">空单元格转为 NULL</el-checkbox>
        <el-button :loading="busy" @click="preview">
          {{ task ? '重新预览' : '上传并预览' }}
        </el-button>
      </el-form-item>
      <template v-if="task">
        <el-alert
          v-if="changed"
          title="解析选项已变化，请重新预览后提交。重新预览会重置字段类型。"
          type="warning"
          :closable="false"
        />
        <el-form-item label="表名称">
          <el-input v-model="task.displayName" :maxlength="200" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="task.description" type="textarea" :maxlength="400" />
        </el-form-item>
        <p>根据前 1,000 条数据推断类型。编号有前导零时请使用文本；金额请使用 DECIMAL。</p>
        <el-table :data="task.columns" max-height="300">
          <el-table-column label="字段名称" min-width="150">
            <template #default="{ row }"><el-input v-model="row.name" :maxlength="200" /></template>
          </el-table-column>
          <el-table-column label="类型" width="150">
            <template #default="{ row }">
              <el-select v-model="row.type">
                <el-option v-for="type in types" :key="type" :value="type" :label="type" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="长度 / 精度" min-width="210">
            <template #default="{ row }">
              <el-input-number
                v-if="row.type === 'VARCHAR'"
                v-model="row.length"
                :min="1"
                :max="4000"
              />
              <template v-if="row.type === 'DECIMAL'">
                <el-input-number
                  v-model="row.precision"
                  aria-label="总位数"
                  :min="1"
                  :max="65"
                  controls-position="right"
                  class="decimal-input"
                />
                <el-input-number
                  v-model="row.scale"
                  aria-label="小数位数"
                  :min="0"
                  :max="Math.min(30, row.precision)"
                  controls-position="right"
                  class="decimal-input"
                />
              </template>
            </template>
          </el-table-column>
        </el-table>
        <p>数据预览（最多 20 条）</p>
        <el-table :data="task.preview" max-height="240">
          <el-table-column
            v-for="column in task.columns"
            :key="column.index"
            :label="column.name"
            :prop="String(column.index)"
            min-width="140"
            show-overflow-tooltip
          />
        </el-table>
      </template>
    </el-form>
    <template #footer>
      <el-button :disabled="busy" @click="visible = false">关闭</el-button>
      <el-button
        type="primary"
        :disabled="!task || changed || !canConfirm"
        :loading="busy"
        @click="confirm"
      >
        确认导入
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
  .csv-form {
    margin-top: 20px;
  }
  .parse-options {
    display: flex;
    gap: 24px;
  }
  .parse-options .el-select {
    width: 150px;
  }
  .decimal-input {
    width: 95px;
    margin-right: 8px;
  }
</style>
