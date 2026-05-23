# 代码规范

## 提交代码前

```bash
# npm
npm run lint && npm run format

# pnpm
pnpm run lint && pnpm run format
```

这一条命令会自动修复代码规范问题并格式化代码。执行通过即可提交。

## 其他命令

| 场景                  | npm                                          | pnpm                                           |
| --------------------- | -------------------------------------------- | ---------------------------------------------- |
| 仅检查不修改（CI 用） | `npm run lint:check && npm run format:check` | `pnpm run lint:check && pnpm run format:check` |
| 单独类型检查          | `npx vue-tsc --noEmit`                       | `pnpm exec vue-tsc --noEmit`                   |

## 配置文件

| 工具     | 用途                                                  | 配置文件           |
| -------- | ----------------------------------------------------- | ------------------ |
| Prettier | 代码格式化（单引号、分号、2 空格缩进、100 字符行宽）  | `.prettierrc`      |
| ESLint   | 代码规范检查（Vue 3 + TypeScript + Prettier 兼容）    | `eslint.config.js` |
| vue-tsc  | TypeScript 类型检查（strict 模式，已集成在 build 中） | `tsconfig.json`    |

## 建议

安装 ESLint 和 Prettier 编辑器插件，启用保存时自动格式化，可以减少手动运行命令的频率。
