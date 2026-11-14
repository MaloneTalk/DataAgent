# 贡献指南

感谢你考虑为 Data Agent 做贡献！本文说明如何在本地搭建开发环境、遵循的规范，以及提交流程。

## 1. 开发环境

### 后端

- JDK 17+（推荐 21），Maven 3.9+
- 导入 `data-agent-backend`（标准 Maven 项目，Spring Boot）
- 配置元数据库、LLM 与首次管理员密码（见 [configuration.md](configuration.md)）
- 从 `data-agent-backend/` 启动并加载仓库 Skill：`mvn spring-boot:run "-Dspring-boot.run.arguments=--io.github.malonetalk.skill.filesystem[0].path=../skills"`
- Python 分析工具另需运行环境提供 `python3`、pandas、numpy、scipy
- 代码规范总纲见仓库根目录 `AGENTS.md`（**必读**）

### 前端

- Node.js 22（≥ 22.13），与 CI 的 Node 22 对齐；当前 ESLint 10 不支持 Node 18
- 进入 `data-agent-frontend/` 后执行 `npm ci`，按仓库 `package-lock.json` 安装
- 如使用 pnpm，采用 Docker 构建所用的 `pnpm@11.24.0`，执行 `pnpm install --frozen-lockfile`；`pnpm-workspace.yaml` 使用了 `allowBuilds`
- 常用命令：

| 命令 | 说明 |
| --- | --- |
| `npm run dev` | 启动开发服务器（默认 3000） |
| `npm run build` | 类型检查 + 构建 |
| `npm test` | 角色数据范围回归测试 |
| `npm run lint:check` / `npm run format:check` | ESLint / Prettier 只检查，不修改文件 |
| `npm run lint` / `npm run format` | ESLint 自动修复 / Prettier 格式化 |

- 前端规范见 `data-agent-frontend/CODE_STYLE.md` 与 `eslint.config.js` / `.prettierrc`。

## 2. 代码规范（要点）

后端以 `AGENTS.md` 为准，核心原则：

- **保持简单**：文件超过约 400 行、函数超过约 80 行时考虑拆分，保持职责清晰。
- **YAGNI**：当前不需要的接口/参数/抽象先不做。
- **优先复用**：JDK → Spring 原生 → 已引入依赖 → 才自写。
- **不写样板**：用 Lombok（`@Data`/`@Builder`）、MapStruct 生成机械代码；多字段用 `@Builder` 而非长构造器。
- **为下一个读者写**：命名揭示意图，复杂逻辑拆小步。
- **新模型使用 `model.*`**：PO / DTO / VO / BO 放入对应子包；PO 继承 `BasePo`，VO 实现 `BaseVo`。存量 `entity` / `dto` 包仍在迁移，不再新增类型。
- **复用持久层与响应机制**：新 Mapper 继承 `AuditableMapper<T>` 并优先使用 MyBatis-Plus，复杂 SQL 才新增 XML；Controller 直接返回 `BaseVo` 实现，由 `BaseVoResponseAdvice` 统一包装，不再使用旧 `Result` 包装新接口。
- **内部可空值显式化**：Java 内部层间使用 `Optional` 表达可空值；JDK / 框架类优先 import，存在项目同名类时按 `AGENTS.md` 使用全限定名。
- **SQL 兼容 MySQL 5.7**：元数据库 SQL 不使用 CTE、窗口函数或其他 MySQL 8 专有语法。普通分析用户的 SQL 还须符合当前授权解析器支持范围。
- **新增入口必须声明权限**：业务接口声明 `@RequirePermission`，本人基础接口使用 `@LoginRequired`；权限码统一定义在 `PermissionEnum`，敏感 Agent 工具使用可信用户上下文检查权限。
- **许可证合规**：新增/升级任何依赖前，先确认其许可证与本项目 AGPLv3 兼容。
- 已启用 **Checkstyle**（含方法长度检查）与 **Spotless**，两者均在 Maven `verify` 阶段检查。需要修复格式时执行 `mvn spotless:apply`。

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
2. 本地开发并通过对应模块的构建、格式与回归检查（见下）。
3. 提交信息遵循上述约定。
4. 发起 PR，描述**动机、改动、验证方式**；关联相关 Issue。
5. 等待 CI（见下）通过与维护者评审；评审意见修改后追加提交或 rebase。

## 5. 持续集成（CI）

仓库 `.github/workflows/` 下已有两条流水线，PR 会触发：

- `backend.yml`：JDK 17，执行 `mvn verify --file data-agent-backend/pom.xml`，包含测试、Checkstyle 与 Spotless。
- `frontend.yml`：Node 22，执行 `npm ci`，分别检查 `npm run lint:check`、`npm run format:check` 与 `npm run build`。

从仓库根目录检查后端：

```bash
mvn verify --file data-agent-backend/pom.xml
```

从前端目录检查前端；`npm test` 是现有数据范围回归命令，当前 CI 未自动执行，相关改动应本地补跑：

```bash
cd data-agent-frontend
npm ci
npm test
npm run lint:check
npm run format:check
npm run build
```

## 6. 行为准则

- 提交代码即表示你同意以 [AGPL-3.0](../LICENSE) 许可证发布你的贡献，以及 [AGPLv3-NOTICE.md](AGPLv3-NOTICE.md) 中的要求。
- 不要提交密钥、凭证或敏感信息（参见 [configuration.md](configuration.md#8-安全提示)）。
- 重大设计变更建议先开 Issue 讨论。
