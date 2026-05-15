# Data Agent 前端

## 项目介绍

Data Agent 管理系统前端，基于 Vue 3 + TypeScript + Vite 开发。

## 技术栈

| 技术 | 说明 |
|------|------|
| Vue 3 | 响应式前端框架 |
| TypeScript | 类型安全的 JavaScript 超集 |
| Vite | 构建工具 |
| Vue Router | 路由管理 |
| Pinia | 状态管理 |
| Axios | HTTP 请求 |
| Element Plus | UI 组件库 |

## 项目结构

```
src/
├── api/              # API 接口封装
│   └── request.ts   # Axios 实例配置
├── components/       # 公共组件
│   └── layout/      # 布局组件（Header、Sidebar）
├── router/           # 路由配置
│   └── index.ts
├── stores/           # 状态管理（Pinia）
│   └── user.ts      # 用户状态
├── styles/           # 全局样式
│   └── main.css
├── views/            # 页面视图（对应路由）
│   ├── data-source/ # 数据源管理
├── App.vue          # 根组件
├── main.ts         # 入口文件
└── vite-env.d.ts   # 类型定义
```

## 快速开始

### 环境要求

- Node.js >= 18
- pnpm >= 8

### 安装依赖

```bash
cd data-agent-frontend
pnpm install
```

### 启动开发服务器

```bash
pnpm dev
```

访问 http://localhost:3000

### 构建生产版本

```bash
pnpm build
```

## 开发规范

### 1. 新增页面

在 `src/views/` 下对应模块目录创建 `.vue` 文件，然后在 `src/router/index.ts` 中配置路由：

```typescript
{
  path: '/xxx',
  name: 'Xxx',
  component: () => import('@/views/xxx/Xxx.vue'),
  meta: { title: '页面标题' }
}
```

### 2. 新增 API

在 `src/api/` 目录下创建对应的接口文件：

```typescript
// src/api/xxx.ts
import request from './request'

export function getXxxList(params: any) {
  return request.get('/xxx/list', { params })
}

export function createXxx(data: any) {
  return request.post('/xxx/create', data)
}
```

### 3. 状态管理

使用 Pinia 管理全局状态，在 `src/stores/` 下创建 store：

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useXxxStore = defineStore('xxx', () => {
  const list = ref([])

  const fetchList = async () => {
    // ...
  }

  return { list, fetchList }
})
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
|------|----------|
| `@/` | `src/` |

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

| 命令 | 说明 |
|------|------|
| `pnpm dev` | 启动开发服务器 |
| `pnpm build` | 构建生产版本 |
| `pnpm preview` | 预览构建结果 |
