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
 -->
<script setup lang="ts">
  import { CopyDocument } from '@element-plus/icons-vue';
  import type { AnswerProvenance } from '@/api/agent';

  defineProps<{ provenance: AnswerProvenance }>();

  function copy(text: string) {
    navigator.clipboard.writeText(text).catch(() => undefined);
  }
</script>

<template>
  <div class="provenance-panel">
    <div class="provenance-panel__summary">
      数据证据 · {{ provenance.sqlExecutions.filter(s => s.status === 'SUCCESS').length }} 次查询 ·
      {{ provenance.status === 'COMPLETE' ? '完整记录' : '部分记录' }}
    </div>
    <div class="provenance-panel__body">
      <section>
        <h4>1. 数据源</h4>
        <p v-if="!provenance.datasources.length">本次回答未执行数据查询</p>
        <p v-for="source in provenance.datasources" :key="source.id">
          {{ source.name || source.id }} · {{ source.databaseName || '未命名数据库' }}
        </p>
      </section>

      <section>
        <h4>2. 领域、表、字段</h4>
        <p v-if="!provenance.tables.length">无已确认的来源表</p>
        <div v-for="table in provenance.tables" :key="table.name" class="provenance-panel__item">
          <strong>{{ table.domain || '未分配领域' }} / {{ table.name }}</strong>
          <span v-if="table.description">· {{ table.description }}</span>
          <div class="provenance-panel__muted">
            字段：{{
              table.columns
                .map(c => (c.description ? `${c.name}（${c.description}）` : c.name))
                .join('、') || '未能精确识别'
            }}
          </div>
        </div>
      </section>

      <section>
        <h4>3. 执行的 SQL</h4>
        <p v-if="!provenance.sqlExecutions.length">无</p>
        <div
          v-for="sql in provenance.sqlExecutions"
          :key="sql.callId"
          class="provenance-panel__item"
        >
          <div>
            {{ sql.status === 'SUCCESS' ? '成功' : '未确认成功' }}
            <span class="provenance-panel__muted">· {{ sql.callId }}</span>
            <span v-if="sql.returnedRows !== null">· 返回 {{ sql.returnedRows }} 行</span>
            <span v-if="sql.truncated">· 结果已截断</span>
            <button
              type="button"
              class="provenance-panel__copy"
              title="复制 SQL"
              aria-label="复制 SQL"
              @click="copy(sql.sql)"
            >
              <el-icon><CopyDocument /></el-icon>
            </button>
          </div>
          <pre class="provenance-panel__sql">{{ sql.sql }}</pre>
          <details
            v-if="sql.executedSql && sql.executedSql !== sql.sql"
            class="provenance-panel__executed"
          >
            <summary>实际执行 SQL</summary>
            <div class="provenance-panel__executed-body">
              <button
                type="button"
                class="provenance-panel__copy"
                title="复制 SQL"
                aria-label="复制 SQL"
                @click="copy(sql.executedSql || '')"
              >
                <el-icon><CopyDocument /></el-icon>
              </button>
              <pre>{{ sql.executedSql }}</pre>
            </div>
          </details>
        </div>
      </section>

      <section>
        <h4>4. 查询到的指标口径</h4>
        <p v-if="!provenance.metricReferences.length">无</p>
        <p v-for="metric in provenance.metricReferences" :key="metric.key">
          {{ metric.name }}（{{ metric.key }}）
          <span v-if="metric.measureExpr">· {{ metric.measureExpr }}</span>
          <span v-if="metric.filters">· 过滤：{{ metric.filters }}</span>
        </p>
      </section>

      <section>
        <h4>5. Python 计算</h4>
        <p v-if="!provenance.pythonExecutions.length">无</p>
        <div
          v-for="python in provenance.pythonExecutions"
          :key="python.callId"
          class="provenance-panel__item"
        >
          <div>
            {{ python.status === 'SUCCESS' ? '成功' : '未成功' }}
            <button
              type="button"
              class="provenance-panel__copy"
              title="复制 Python"
              aria-label="复制 Python"
              @click="copy(python.code)"
            >
              <el-icon><CopyDocument /></el-icon>
            </button>
          </div>
          <pre>{{ python.code }}</pre>
        </div>
      </section>

      <p v-for="warning in provenance.warnings" :key="warning" class="provenance-panel__warning">
        {{ warning }}
      </p>
    </div>
  </div>
</template>

<style scoped>
  .provenance-panel {
    border: 1px solid var(--app-border);
    border-radius: 8px;
    margin-bottom: 12px;
    font-size: 12px;
  }
  .provenance-panel__summary {
    padding: 10px 16px;
    color: var(--app-text-secondary);
    font-size: 13px;
  }
  .provenance-panel__body {
    padding: 0 16px 12px;
  }
  section {
    border-top: 1px solid var(--app-border);
    padding: 10px 0;
  }
  h4 {
    margin: 0 0 8px;
    font-size: 12px;
  }
  p {
    margin: 5px 0;
  }
  pre {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    padding: 8px;
    color: var(--app-text-primary);
    background: var(--app-bg-page);
    border-left: 3px solid var(--app-border);
    border-radius: 4px;
  }
  .provenance-panel__sql {
    background: color-mix(in srgb, var(--app-link) 8%, var(--app-bg-page));
    border-left-color: var(--app-link);
  }
  .provenance-panel__executed {
    margin-top: 8px;
    border: 1px solid color-mix(in srgb, var(--el-color-success) 45%, var(--app-border));
    border-radius: 6px;
    background: color-mix(in srgb, var(--el-color-success) 6%, var(--app-bg-card));
  }
  .provenance-panel__executed summary {
    padding: 8px 10px;
    color: var(--el-color-success);
    font-weight: 600;
    cursor: pointer;
  }
  .provenance-panel__executed-body {
    padding: 0 10px 10px;
  }
  .provenance-panel__executed-body .provenance-panel__copy {
    display: block;
    margin: 0 0 2px auto;
    color: var(--el-color-success);
  }
  .provenance-panel__executed-body pre {
    margin: 0;
    background: color-mix(in srgb, var(--el-color-success) 10%, var(--app-bg-page));
    border-left-color: var(--el-color-success);
  }
  .provenance-panel__item {
    margin: 8px 0;
  }
  .provenance-panel__muted {
    color: var(--app-text-muted);
  }
  .provenance-panel__warning {
    color: #b45309;
  }
  .provenance-panel__copy {
    margin-left: 8px;
    padding: 2px;
    border: 0;
    background: none;
    color: var(--app-accent);
    cursor: pointer;
    vertical-align: middle;
  }
</style>
