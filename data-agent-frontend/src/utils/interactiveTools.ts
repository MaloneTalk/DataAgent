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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * limitations under the License.
 */

/**
 * 交互工具定义 —— 对应后端一个 throw ToolSuspendException 的工具方法。
 * 新增交互表单只需在此注册表加一条配置，无需修改 TracePanel / useAgentChat。
 */
export interface InteractiveToolDef {
  /** tool_call 步骤的标签，如 "向用户提问" */
  label: string;
  /** 从 toolCall.input 中取展示文本的字段名 */
  questionField: string;
  /** tool_result 步骤的标签，如 "用户回答" */
  resultLabel: string;
}

/** 所有交互工具的注册表，key 为工具名（与后端 @Tool.name 一致） */
export const INTERACTIVE_TOOLS: Record<string, InteractiveToolDef> = {
  ask_user: {
    label: '向用户提问',
    questionField: 'question',
    resultLabel: '用户回答',
  },
};

/** 判断工具名是否为已注册的交互工具 */
export function isInteractiveTool(toolName: string | null | undefined): boolean {
  return toolName != null && toolName in INTERACTIVE_TOOLS;
}
