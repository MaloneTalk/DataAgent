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
  import { ref, computed } from 'vue';
  import type { TraceStep, ChatMessage } from '@/composables/useAgentChat';
  import type { ChatStreamEventType } from '@/api/agent';

  const props = defineProps<{
    message: ChatMessage;
  }>();

  const isExpanded = ref(true);

  function toggleExpand() {
    isExpanded.value = !isExpanded.value;
  }

  const stepCounts = computed(() => {
    const counts: Record<string, number> = {};
    for (const step of props.message.traceSteps) {
      counts[step.type] = (counts[step.type] || 0) + 1;
    }
    return counts;
  });

  const toolCallCount = computed(() => stepCounts.value.tool_call || 0);
  const toolResultCount = computed(() => stepCounts.value.tool_result || 0);

  const summaryLabel = computed(() => {
    const parts: string[] = [];
    const total = toolCallCount.value + toolResultCount.value;
    if (total > 0) {
      parts.push(`${total} Tools Called`);
    }
    const thinkCount = stepCounts.value.thinking || 0;
    if (thinkCount > 0) {
      parts.push(`${thinkCount} Thoughts`);
    }
    return parts.join(' · ') || `${props.message.traceSteps.length} Steps`;
  });

  interface StepRenderer {
    label: string;
  }

  const stepRenderers: Record<string, StepRenderer> = {
    thinking: { label: 'Thought' },
    tool_call: { label: 'Action' },
    tool_result: { label: 'Observation' },
    question: { label: 'Question' },
  };

  function getStepRenderer(type: ChatStreamEventType): StepRenderer {
    return stepRenderers[type] ?? { label: type };
  }

  function stepLabel(step: TraceStep): string {
    if (step.type === 'tool_call' && step.toolCall?.name === 'ask_user') {
      return '[向用户提问]';
    }
    return `[${getStepRenderer(step.type).label}]`;
  }

  function stepContent(step: TraceStep): string {
    if (step.type === 'tool_call' && step.toolCall) {
      if (step.toolCall.name === 'ask_user') {
        return (step.toolCall.input.question as string) ?? '等待用户输入...';
      }
      return `调用工具: ${step.toolCall.name}`;
    }
    if (step.type === 'tool_result' && step.toolResult) {
      return `工具 ${step.toolResult.name} 返回结果`;
    }
    if (step.type === 'question') {
      return `等待用户回答: ${step.content ?? ''}`;
    }
    return step.content ?? '';
  }

  function formatJson(obj: unknown): string {
    try {
      return JSON.stringify(obj, null, 2);
    } catch {
      return String(obj);
    }
  }

  function displayMultiline(text: string | null): string {
    if (!text) return '';
    return text.replace(/\\n/g, '\n');
  }
</script>

<template>
  <div v-if="message.traceSteps.length > 0" class="trace-panel" :class="{ expanded: isExpanded }">
    <div class="trace-panel__header" @click="toggleExpand">
      <span class="trace-panel__arrow">▶</span>
      <span class="trace-panel__dot">●</span>
      <span class="trace-panel__title">Agent 思考与执行链路</span>
      <span class="trace-panel__summary">{{ summaryLabel }}</span>
    </div>
    <div v-show="isExpanded" class="trace-panel__body">
      <div
        v-for="(step, idx) in message.traceSteps"
        :key="idx"
        class="trace-step"
        :class="`trace-step--${step.type}`"
      >
        <span class="trace-step__label">{{ stepLabel(step) }}</span>
        <span class="trace-step__content">{{ displayMultiline(stepContent(step)) }}</span>
        <div v-if="step.type === 'tool_call' && step.toolCall" class="trace-step__code">
          <div class="code-label">Input:</div>
          <pre class="code-block">{{ displayMultiline(formatJson(step.toolCall.input)) }}</pre>
        </div>
        <div v-if="step.type === 'tool_result' && step.toolResult" class="trace-step__code">
          <div class="code-label">Output:</div>
          <pre class="code-block">{{ displayMultiline(step.toolResult.output) }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
  .trace-panel {
    background: var(--app-bg-page);
    border: 1px solid var(--app-border);
    border-radius: 8px;
    margin-bottom: 16px;
    overflow: hidden;
    transition:
      background-color 0.2s,
      border-color 0.2s;
  }

  .trace-panel__header {
    padding: 10px 16px;
    cursor: pointer;
    font-size: 13px;
    font-weight: 500;
    color: var(--app-text-secondary);
    display: flex;
    align-items: center;
    gap: 8px;
    user-select: none;
  }

  .trace-panel__header:hover {
    background: var(--app-bg-hover);
  }

  .trace-panel__arrow {
    font-size: 10px;
    color: var(--app-text-muted);
    transition: transform 0.2s;
  }

  .trace-panel.expanded .trace-panel__arrow {
    transform: rotate(90deg);
  }

  .trace-panel__dot {
    color: #16a34a;
    font-size: 10px;
  }

  .trace-panel__title {
    white-space: nowrap;
  }

  .trace-panel__summary {
    color: var(--app-text-muted);
    font-weight: normal;
    margin-left: auto;
  }

  .trace-panel__body {
    padding: 0 16px 16px 16px;
    border-top: 1px solid var(--app-border);
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
  }

  .trace-step {
    margin-top: 12px;
    padding-left: 12px;
    border-left: 2px solid var(--app-border);
    position: relative;
    line-height: 1.6;
  }

  .trace-step::before {
    content: '';
    position: absolute;
    left: -5px;
    top: 6px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--app-text-muted);
  }

  .trace-step__label {
    font-weight: 600;
    margin-right: 6px;
  }

  .trace-step__content {
    color: var(--app-text-secondary);
    white-space: pre-wrap;
  }

  .trace-step--thinking {
    color: var(--app-text-muted);
  }
  .trace-step--thinking .trace-step__label {
    color: var(--app-text-muted);
  }

  .trace-step--tool_call {
    color: var(--app-link);
  }
  .trace-step--tool_call .trace-step__label {
    color: var(--app-link);
  }

  .trace-step--tool_result {
    color: #16a34a;
  }
  .trace-step--tool_result .trace-step__label {
    color: #16a34a;
  }

  .trace-step--question {
    color: #d97706;
  }
  .trace-step--question .trace-step__label {
    color: #d97706;
  }

  .trace-step--summary {
    color: #7c3aed;
  }
  .trace-step--summary .trace-step__label {
    color: #7c3aed;
  }

  .trace-step__code {
    margin-top: 8px;
  }

  .code-label {
    color: var(--app-text-muted);
    font-size: 11px;
    margin-bottom: 4px;
  }

  .code-block {
    background: #1e293b;
    color: #e2e8f0;
    padding: 10px;
    border-radius: 6px;
    overflow-x: auto;
    white-space: pre-wrap;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
    margin: 0;
  }
</style>
