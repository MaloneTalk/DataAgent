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
  import type { AnswerProvenance } from '@/api/agent';

  defineProps<{ provenance: AnswerProvenance }>();

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      // Clipboard API may fail in insecure contexts; ignore.
    }
  }
</script>

<template>
  <details class="provenance-panel">
    <summary>
      数据来源 · {{ provenance.sqlExecutions.filter(s => s.status === 'SUCCESS').length }} 次查询
      · {{ provenance.status === 'COMPLETE' ? '完整记录' : '部分记录' }}
    </summary>
    <div class="provenance-panel__body">
      <p v-if="provenance.traceIds.length" class="provenance-panel__muted">
        Trace：{{ provenance.traceIds.join('、') }}
      </p>
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
            <button type="button" class="provenance-panel__copy" @click="copy(sql.sql)">
              复制 SQL
            </button>
          </div>
          <pre>{{ sql.sql }}</pre>
          <div
            v-if="sql.executedSql && sql.executedSql !== sql.sql"
            class="provenance-panel__muted"
          >
            实际执行：
            <button
              type="button"
              class="provenance-panel__copy"
              @click="copy(sql.executedSql || '')"
            >
              复制 SQL
            </button>
            <pre>{{ sql.executedSql }}</pre>
          </div>
        </div>
      </section>

      <section>
        <h4>4. 引用的指标口径</h4>
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
            <button type="button" class="provenance-panel__copy" @click="copy(python.code)">
              复制 Python
            </button>
          </div>
          <div v-if="python.sourceSqlCallIds.length" class="provenance-panel__muted">
            声明的输入 SQL：{{ python.sourceSqlCallIds.join('、') }}
          </div>
          <pre>{{ python.code }}</pre>
          <div v-if="python.output" class="provenance-panel__muted">输出：{{ python.output }}</div>
        </div>
      </section>

      <section>
        <h4>6. 表的逻辑链路</h4>
        <p v-if="!provenance.relations.length">本次 SQL 未识别到表连接</p>
        <p v-for="(relation, index) in provenance.relations" :key="index">
          {{ relation.sourceTable }}.{{ relation.sourceColumn }} → {{ relation.targetTable }}.{{
            relation.targetColumn
          }}
          <span class="provenance-panel__muted">
            · {{ relation.relationType || 'SQL 实际连接，未匹配已配置关系' }}
            {{ relation.description || '' }}
          </span>
        </p>
      </section>

      <p v-for="warning in provenance.warnings" :key="warning" class="provenance-panel__warning">
        {{ warning }}
      </p>
    </div>
  </details>
</template>

<style scoped>
  .provenance-panel {
    border: 1px solid var(--app-border);
    border-radius: 8px;
    margin-bottom: 12px;
    font-size: 12px;
  }
  summary {
    cursor: pointer;
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
    background: var(--app-bg-page);
    padding: 8px;
    border-radius: 4px;
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
    cursor: pointer;
  }
</style>
