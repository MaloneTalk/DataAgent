# Data Agent 前端

## 项目介绍

Data Agent 管理系统前端，基于 Vue 3 + TypeScript + Vite 开发。

## 技术栈

| 技术         | 说明                       |
| ------------ | -------------------------- |
| Vue 3        | 响应式前端框架             |
| TypeScript   | 类型安全的 JavaScript 超集 |
| Vite         | 构建工具                   |
| Vue Router   | 路由管理                   |
| Pinia        | 状态管理                   |
| Axios        | HTTP 请求                  |
| Element Plus | UI 组件库                  |
| ECharts      | 图表展示                   |
| marked       | Markdown 渲染              |

## 项目结构

```
src/
├── api/              # API 与类型；request.ts 统一处理认证、权限及字段错误
├── components/       # 公共组件、布局、数据证据等
├── composables/      # useAgentChat、useDatasource、useFieldErrors
├── directives/       # v-permission 按钮权限
├── router/           # 路由与权限守卫
├── stores/           # 用户权限、主题状态（Pinia）
├── styles/           # main.css、theme.css
├── utils/            # 下载、日期、报告模板与交互工具
├── views/
│   ├── chat/         # 流式对话、会话历史、数据源选择
│   ├── data-source/  # 数据源管理
│   ├── semantic/     # 领域、表列语义、逻辑关系与画布
│   ├── metric/       # 指标口径
│   ├── report/       # 报告资产
│   ├── table-export/ # CSV 导出资产
│   ├── evidence/     # 数据证据
│   ├── sys-user/     # 用户及资料、密码、角色对话框
│   ├── sys-role/     # 角色、功能权限与数据范围
│   └── login/        # 登录
├── App.vue           # 根组件
├── main.ts           # 入口文件
└── vite-env.d.ts     # 类型定义
```

## 快速开始

### 环境要求

- Node.js 22（≥ 22.13），与 CI 对齐；现有 ESLint 10 不支持 Node 18。
- npm 随 Node.js 安装，CI 使用 `package-lock.json`。
- 可选 pnpm：使用 Docker 构建所用的 `pnpm@11.24.0`；工作区配置使用了 `allowBuilds`。

### 安装依赖

```bash
cd data-agent-frontend
npm ci
```

### 启动开发服务器

```bash
npm run dev
```

访问 http://localhost:3000

### 构建生产版本

```bash
npm run build
```

`build` 先执行 `vue-tsc -b` 类型检查，再执行 Vite 构建。如使用 pnpm，安装命令为 `pnpm install --frozen-lockfile`，脚本用 `pnpm run <脚本名>` 执行。

后端启动、管理员初始化和 Skill 路径见[快速开始](../docs/getting-started.md)，多人授权见[权限说明](../docs/permissions.md)。

## 页面与会话行为

| 入口        | 路由                                                                            |
| ----------- | ------------------------------------------------------------------------------- |
| AI 智能分析 | `/chat`、`/chat/:sessionId`                                                     |
| 数据源管理  | `/data-source`                                                                  |
| 语义管理    | `/semantic/domain`、`/semantic/table`、`/semantic/metric`、`/semantic/relation` |
| 资产管理    | `/asset/report`、`/asset/table-export`、`/asset/evidence`                       |
| 系统管理    | `/system/user`、`/system/role`                                                  |

`stores/user.ts` 从 `/api/auth/me` 获取当前权限，路由 `meta.permission` 与 `v-permission` 控制入口和按钮。路由权限数组满足任一项即可；例如只有 `analysis:session:read` 也能查看聊天历史，输入与新建仍要求 `analysis:chat:use`。后端负责最终授权。

`useAgentChat` 先通过 `POST /api/agent/session` 获取服务端 ID，再用原生 `fetch` 消费 SSE；不在浏览器生成会话 ID。`ChatDatasourceSelect.vue` 在发出消息后锁定数据源选择。流式事件或历史回放中的 `ask_user` 由前端收集回复，再通过 `toolResults` 继续请求。

## 开发规范

### 1. 新增页面

在 `src/views/` 下对应模块目录创建 `.vue` 文件，然后在 `src/router/index.ts` 中配置路由：

```typescript
{
  path: '/xxx',
  name: 'Xxx',
  component: () => import('@/views/xxx/Xxx.vue'),
  meta: { title: '页面标题', permission: '已有的权限点编码' },
}
```

### 2. 新增 API

在 `src/api/` 目录下创建对应的接口文件：

```typescript
import request, { type ApiResponse } from './request';
import type { DatasourceOption } from './datasource';

// 普通 JSON 接口：Axios 响应 → 统一响应外层 → 业务数据
export function loadDatasourceOptions() {
  return request
    .get<ApiResponse<DatasourceOption[]>>('/datasource/options')
    .then(res => res.data.data);
}
```

API 类型与后端 DTO / VO 对齐，不使用 `any`。`request.ts` 保留 Axios 响应，调用方提取 `res.data.data`；数据源写接口的业务值位于 `data.value`。`ApiError` 保存 `code`、`errorCode`、`details`；表单通过 `useFieldErrors` / `getFieldErrorMap` 展示字段错误。401 清理登录并跳转，403 触发 `permissions-changed`，由应用刷新权限和重新执行路由守卫。SSE 复用同一错误转换入口。

### 3. 状态管理

使用 Pinia 管理全局状态，在 `src/stores/` 下创建 store：

```typescript
import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useXxxStore = defineStore('xxx', () => {
  const list = ref([]);

  const fetchList = async () => {
    // ...
  };

  return { list, fetchList };
});
```

### 4. 组件规范

- 公共组件放在 `src/components/` 目录下
- 页面级组件放在对应 `views/xxx/components/` 目录下
- 组件命名使用大驼峰（PascalCase），如 `DataTable.vue`

### 5. 样式规范

- 全局样式写在 `src/styles/main.css`
- 组件样式使用 `<style scoped>` 局部样式

### 6. 路径别名

| 别名 | 实际路径 |
| ---- | -------- |
| `@/` | `src/`   |

使用示例：`import Xxx from '@/components/Xxx.vue'`

## 代理配置

开发环境通过 Vite 代理解决跨域问题，配置在 `vite.config.ts`：

```typescript
proxy: {
  '/api': {
    target: 'http://localhost:8080',  // 后端地址
    changeOrigin: true
  }
}
```

## 命令说明

| 命令              | 说明                   |
| ----------------- | ---------------------- |
| `npm run dev`     | 启动开发服务器         |
| `npm run build`   | 类型检查并构建生产版本 |
| `npm run preview` | 预览构建结果           |

CI 使用 npm；在前端目录执行与其一致的检查，并补跑数据范围回归测试：

```bash
npm test
npm run lint:check
npm run format:check
npm run build
```

`npm test` 当前执行 `tests/roleDataScope.test.mjs`。自动修复命令为 `npm run lint`，格式化命令为 `npm run format`；不要把自动修复命令当作 CI 的只读检查。更多约定见 [CODE_STYLE.md](CODE_STYLE.md) 和[贡献指南](../docs/contributing.md)。
