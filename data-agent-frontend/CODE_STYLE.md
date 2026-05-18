# 代码规范和检查工具配置说明

## 已配置的工具

### 1. 代码格式化 — Prettier

自动格式化代码，确保代码风格一致。

| 配置项   | 值       |
| -------- | -------- |
| 引号     | 单引号   |
| 分号     | 启用     |
| 尾随逗号 | 启用     |
| 最大行宽 | 100 字符 |
| 缩进     | 2 空格   |
| 换行符   | LF       |

- 配置文件: `.prettierrc`
- 忽略文件: `.prettierignore`

### 2. 代码规范检查 — ESLint

检查代码质量和规范，基于 ESLint 10 flat config。

**规则继承链:**

- `eslint:recommended` — JavaScript 推荐规则
- `typescript-eslint/recommended` — TypeScript 推荐规则
- `plugin:vue/vue3-essential` — Vue 3 核心规则
- `eslint-config-prettier` — 关闭与 Prettier 冲突的规则

**关键自定义规则:**

- `prefer-const` / `no-var` — 强制使用 const/let
- `object-shorthand` — 强制对象属性简写
- `quote-props` — 仅在必要时使用引号
- `@typescript-eslint/no-explicit-any` — 不建议使用 any（warn）
- `@typescript-eslint/no-unused-vars` — 未使用变量警告（忽略 `_` 前缀）
- `no-console` — 生产环境 warn
- `no-debugger` — 生产环境 error

- 配置文件: `eslint.config.js`

### 3. TypeScript 类型检查 — vue-tsc

Vue + TypeScript 类型检查，编译器配置了 strict 模式。

- `strict: true` — 所有严格检查项启用
- `noUnusedLocals: true` — 未使用的局部变量报错
- `noUnusedParameters: true` — 未使用的参数报错

- 配置文件: `tsconfig.json`

---

## 使用命令

### 代码格式化

```bash
# 格式化所有代码
npm run format

# 检查代码格式（不修改，适用于 CI）
npm run format:check
```

### 代码规范检查

```bash
# 检查并自动修复（部分规则）
npm run lint

# 仅检查不修复（适用于 CI）
npm run lint:check
```

### TypeScript 类型检查

类型检查已集成在构建过程中，`npm run build` 会自动先执行 `vue-tsc -b`。

如需单独运行:

```bash
npx vue-tsc --noEmit
```

### 一键全量检查

```bash
npm run lint:check && npm run format:check
```

---

## 推荐的开发流程

1. **编辑器配置**: 安装 ESLint 和 Prettier 插件，启用保存时自动格式化
2. **提交前检查**:
   ```bash
   npm run lint && npm run format
   ```
3. **CI 自动检查**: 每次推送和 PR 时自动运行 lint、format:check 和 build
