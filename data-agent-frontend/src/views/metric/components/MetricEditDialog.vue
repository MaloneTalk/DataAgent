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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 -->

<script setup lang="ts">
  import { computed, nextTick, reactive, ref, watch } from 'vue';
  import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
  import {
    createMetric,
    updateMetric,
    metricCalculationTypeLabels,
    metricValueTypeLabels,
    type MetricCalculationType,
    type MetricInfo,
    type MetricValueType,
  } from '@/api/metric';

  const visible = defineModel<boolean>({ required: true });
  const props = defineProps<{ metric: MetricInfo | null; datasourceId: number | undefined }>();
  const emit = defineEmits<{ saved: [] }>();

  const formRef = ref<FormInstance>();
  const submitting = ref(false);
  const form = reactive({
    metricKey: '',
    name: '',
    aliases: '',
    measureExpr: '',
    filters: '',
    timeField: '',
    calculationType: 'EXPRESSION' as MetricCalculationType,
    valueType: 'NUMBER' as MetricValueType | undefined,
    unit: '',
    resultMultiplier: '1',
    currencyCode: '',
    description: '',
  });

  const rules = computed<FormRules<typeof form>>(() => ({
    metricKey: [
      { required: true, whitespace: true, message: '指标 key 不能为空', trigger: 'blur' },
    ],
    name: [{ required: true, whitespace: true, message: '指标名称不能为空', trigger: 'blur' }],
    calculationType: [{ required: true, message: '请选择计算方式', trigger: 'change' }],
    unit: [
      {
        required: !!form.valueType,
        whitespace: !!form.valueType,
        message: '请填写标准结果单位',
        trigger: 'blur',
      },
    ],
    resultMultiplier: [
      {
        validator: (_rule, value: string) => {
          const valid =
            /^(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(value.trim()) &&
            Number.isFinite(Number(value)) &&
            Number(value) > 0;
          if (!valid) {
            return new Error('结果倍率必须为正数');
          }
          return form.valueType === 'PERCENT' && Number(value) !== 1
            ? new Error('百分比倍率必须为1，展示时再乘以100')
            : true;
        },
        trigger: 'blur',
      },
    ],
    currencyCode: [
      {
        required: form.valueType === 'MONEY',
        pattern: /^[A-Za-z]{3}$/,
        transform: (value: string) => value.trim(),
        message: '请填写3位币种代码，如 CNY',
        trigger: 'blur',
      },
    ],
  }));

  watch(visible, async open => {
    if (!open) return;
    const metric = props.metric;
    Object.assign(form, {
      metricKey: metric?.metricKey ?? '',
      name: metric?.name ?? '',
      aliases: metric?.aliases ?? '',
      measureExpr: metric?.measureExpr ?? '',
      filters: metric?.filters ?? '',
      timeField: metric?.timeField ?? '',
      calculationType: metric?.calculationType ?? 'EXPRESSION',
      valueType: metric ? (metric.valueType ?? undefined) : 'NUMBER',
      unit: metric?.unit ?? '',
      resultMultiplier: metric?.resultMultiplier ?? '1',
      currencyCode: metric?.currencyCode ?? '',
      description: metric?.description ?? '',
    });
    await nextTick();
    formRef.value?.clearValidate();
  });

  const handleValueTypeChange = () => {
    if (form.valueType === 'PERCENT') {
      form.unit = '1';
      form.resultMultiplier = '1';
    } else if (form.unit === '1') {
      form.unit = '';
    }
    if (form.valueType !== 'MONEY') form.currencyCode = '';
    formRef.value?.clearValidate(['unit', 'resultMultiplier', 'currencyCode']);
  };

  const handleSubmit = async () => {
    if (!formRef.value || !(await formRef.value.validate().catch(() => false))) return;
    if (!props.metric && props.datasourceId === undefined) {
      ElMessage.warning('请先选择数据源');
      return;
    }
    submitting.value = true;
    try {
      const fields = {
        name: form.name.trim(),
        aliases: form.aliases.trim(),
        measureExpr: form.measureExpr.trim(),
        filters: form.filters.trim(),
        timeField: form.timeField.trim(),
        calculationType: form.calculationType,
        valueType: form.valueType,
        unit: form.unit.trim(),
        resultMultiplier: form.resultMultiplier.trim(),
        currencyCode: form.currencyCode.trim(),
        description: form.description.trim(),
      };
      if (props.metric) {
        await updateMetric(props.metric.id, fields);
        ElMessage.success('指标口径已更新');
      } else if (props.datasourceId !== undefined) {
        await createMetric({
          datasourceId: props.datasourceId,
          metricKey: form.metricKey.trim(),
          ...fields,
        });
        ElMessage.success('指标口径已创建');
      }
      visible.value = false;
      emit('saved');
    } finally {
      submitting.value = false;
    }
  };
</script>

<template>
  <el-dialog v-model="visible" :title="metric ? '编辑指标口径' : '新增指标口径'" width="680px">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :validate-on-rule-change="false"
      label-width="110px"
    >
      <el-form-item label="指标 Key" prop="metricKey">
        <el-input
          v-model="form.metricKey"
          :disabled="!!metric"
          placeholder="稳定标识,如 sales,不可重复"
        />
      </el-form-item>
      <el-form-item label="指标名称" prop="name">
        <el-input v-model="form.name" placeholder="官方中文名,如 销售额" />
      </el-form-item>
      <el-form-item label="同义词">
        <el-input v-model="form.aliases" placeholder="逗号分隔,如 GMV,营收,流水" />
      </el-form-item>
      <el-form-item label="计算方式" prop="calculationType">
        <el-select v-model="form.calculationType">
          <el-option
            v-for="(label, value) in metricCalculationTypeLabels"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
        <div class="form-hint">用于说明计算方式，仍须填写完整度量表达式及业务规则。</div>
      </el-form-item>
      <el-form-item label="度量">
        <el-input v-model="form.measureExpr" placeholder="如 SUM(paid_amount)" />
      </el-form-item>
      <el-form-item label="结果类型" prop="valueType">
        <el-select
          v-model="form.valueType"
          placeholder="未配置，请补充"
          @change="handleValueTypeChange"
        >
          <el-option
            v-for="(label, value) in metricValueTypeLabels"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="标准结果单位" prop="unit">
        <el-input
          v-model="form.unit"
          :disabled="form.valueType === 'PERCENT'"
          maxlength="32"
          placeholder="如 元、人、次"
        />
        <div v-if="form.valueType === 'PERCENT'" class="form-hint">
          1 表示无量纲比值，0.1234 展示为 12.34%。
        </div>
      </el-form-item>
      <el-form-item label="结果倍率" prop="resultMultiplier">
        <el-input
          v-model="form.resultMultiplier"
          :disabled="form.valueType === 'PERCENT'"
          placeholder="如 1 或 0.01"
        />
        <div class="form-hint">
          标准结果 = 度量表达式原始结果 × 倍率。分转元填
          0.01；表达式已除以100则填1。最多12位整数和12位小数。
        </div>
      </el-form-item>
      <el-form-item v-if="form.valueType === 'MONEY'" label="币种" prop="currencyCode">
        <el-input v-model="form.currencyCode" maxlength="3" placeholder="如 CNY、USD" />
        <div class="form-hint">指定结果币种；混合币种须在表达式或过滤条件中处理。</div>
      </el-form-item>
      <el-form-item label="过滤条件">
        <el-input v-model="form.filters" placeholder="如 status='paid' AND is_test=0" />
      </el-form-item>
      <el-form-item label="时间字段">
        <el-input v-model="form.timeField" placeholder="如 settle_time" />
      </el-form-item>
      <el-form-item label="说明">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="业务口径说明 / 规则"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
  .form-hint {
    width: 100%;
    margin-top: 4px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.5;
  }
</style>
