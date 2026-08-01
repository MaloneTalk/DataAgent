# 贡献指南

感谢你考虑为 Data Agent 做贡献！本文说明如何在本地搭建开发环境、遵循的规范，以及提交流程。

## 1. 开发环境

### 后端

- JDK 17+（推荐 21），Maven 3.9+
- 导入 `data-agent-backend`（标准 Maven 项目，Spring Boot）
- 配置元数据库与 LLM（见 [configuration.md](configuration.md)），本地起 `mvn spring-boot:run`
- 代码规范总纲见仓库根目录 `AGENTS.md`（**必读**）

### 前端

- Node 18+，pnpm 8+
- `cd data-agent-frontend && pnpm install`
- 常用命令：

| 命令 | 说明 |
| --- | --- |
| `pnpm dev` | 启动开发服务器（默认 3000） |
| `pnpm build` | 类型检查 + 构建 |
| `pnpm lint` | ESLint 自动修复 |
| `pnpm format` | Prettier 格式化 |

- 前端规范见 `data-agent-frontend/CODE_STYLE.md` 与 `eslint.config.js` / `.prettierrc`。

## 2. 代码规范（要点）

后端以 `AGENTS.md` 为准，核心原则：

- **保持简单**：单文件 ~400 行、单函数 ~80 行为上限，超出则拆分。
- **YAGNI**：当前不需要的接口/参数/抽象先不做。
- **优先复用**：JDK → Spring 原生 → 已引入依赖 → 才自写。
- **不写样板**：用 Lombok（`@Data`/`@Builder`）、MapStruct 生成机械代码；多字段用 `@Builder` 而非长构造器。
- **为下一个读者写**：命名揭示意图，复杂逻辑拆小步。
- 已启用 **Checkstyle**（含方法长度检查），CI 会拦截不合规代码。

### AI 辅助编码（推荐 ponytail）

我们**不反对**用 AI 辅助编写代码——欢迎用 AI 提升效率。但为了让 AI 产出的代码保持简洁、不堆砌多余抽象与样板，请在提交前用 **ponytail** skill 过一遍：

- **写代码时**：加载 [ponytail](https://github.com/DietrichGebert/ponytail)，让它约束 AI 只写当下需要的最小实现，避免违背 YAGNI、过度封装与冗余样板。
- **提交前审查**：用 ponytail 对自己的改动做一遍 review，删掉冗余代码、未使用的抽象与样板，再提 PR。

这与本仓库 `AGENTS.md` 的"保持简单 / YAGNI / 优先复用"原则一致。AI 写的代码同样需要满足 Checkstyle 与 CI 要求。

前端：ESLint + Prettier 统一风格，组件用 PascalCase，公共组件放 `components/`、页面组件放 `views/xxx/components/`。

## 3. 提交信息约定

采用 **Conventional Commits** 风格，便于生成变更日志与阅读历史：

```
<type>(<scope>): <subject>
```

- `type`：`feat`（新功能）/`fix`（修复）/`refactor`（重构）/`style`（格式）/`docs`（文档）/`test`（测试）/`chore`（构建/杂项）
- `scope`：可选，模块名，如 `agent`、`semantic`、`frontend`
- 示例：
  - `feat(agent): add ask_user tool for clarification`
  - `fix(semantic): correct join path for order table`
  - `docs: split README into docs/`

> 仓库历史中曾混用 `feature:` 与 `feat:`，新提交请统一使用 `feat:`。

## 4. 分支与 PR 流程

1. Fork 或基于 `main` 拉出特性分支（如 `feat/xxx`、`fix/yyy`）。
2. 本地开发并通过后端/前端构建与 lint。
3. 提交信息遵循上述约定。
4. 发起 PR，描述**动机、改动、验证方式**；关联相关 Issue。
5. 等待 CI（见下）通过与维护者评审；评审意见修改后追加提交或 rebase。

## 5. 持续集成（CI）

仓库 `.github/workflows/` 下已有两条流水线，PR 会触发：

- `backend.yml`：后端构建（Maven）
- `frontend.yml`：前端构建（pnpm build / lint）

请确保本地跑通对应流水线的等价步骤后再提交。

## 6. 行为准则

- 提交代码即表示你同意以 [AGPL-3.0](../../LICENSE) 许可证发布你的贡献。
- 不要提交密钥、凭证或敏感信息（参见 [configuration.md](configuration.md#安全提示)）。
- 重大设计变更建议先开 Issue 讨论。
