# Skill 使用指南

Skill 是 Data Agent 的可复用流程模块——告诉 Agent 按什么步骤、用哪些工具完成任务。每个 Skill 是 `skills/<skill-name>/SKILL.md` 目录下的一个 Markdown 文件。

## SKILL.md 格式

```markdown
---
name: my-custom-skill
description: 一句话描述这个 Skill 做什么。
---

# My Custom Skill

你是一个 XXX 助手。当用户提出 XXX 相关问题时，按以下步骤操作：

1. 使用 `get_datasources` 确认目标数据源
2. 使用 `get_domains`、`get_tables` 选择数据域和表或普通视图
3. 使用 `get_table_schema` 获取字段，必要时用 `get_metric_caliber` 核对指标
4. 多数据源时在上述语义工具及 `execute_sql` 中显式传入 `datasource_id`
5. 执行 SELECT 并汇总；需要图表时，用返回的 Result ID 调用 `generate_chart`

## 注意事项

- 约束 A
- 约束 B
```

- **frontmatter**（必填）：`name`（Skill 标识）、`description`（用途说明）。
- **正文**：写清流程步骤和可用工具，由 AgentScope 的 `SkillBox` 注册给 Agent 使用。可引用的工具列表见 [architecture.md](architecture.md#7-agent-内置工具)。Skill 不授予权限，工具仍按当前用户权限和数据范围执行。

## 添加自定义 Skill

直接把 Skill 目录放入项目根目录的 `skills/` 下即可：

```bash
mkdir -p skills/my-skill
# 编写 skills/my-skill/SKILL.md
```

默认文件系统路径为 `./skills`，相对于后端进程工作目录解析。从 `data-agent-backend/` 启动时，仓库根目录的 Skill 应通过 `../skills` 加载：

```bash
cd data-agent-backend
mvn spring-boot:run "-Dspring-boot.run.arguments=--io.github.malonetalk.skill.filesystem[0].path=../skills"
```

启动日志会显示解析后的绝对路径及 `Registered skill` 记录，可据此确认是否加载成功。`AgentService.init()` 在启动时创建工具箱与 SkillBox；新增或修改本地 Skill 后需重启后端。

Docker 镜像将仓库 `skills/` 复制到 `/app/skills`，默认路径可直接使用；修改后需重新构建后端镜像。更多来源与 Git、Nacos 配置见 [configuration.md](configuration.md#5-skill-加载源)。

## 内置 Skill

| Skill | 说明 |
| --- | --- |
| `data-query` | 根据表结构生成 SQL 并执行查询，汇总结果 |

## 后端 Skill 与外部 Agent Skill

| 目录 | 加载方 | 用途 |
| --- | --- | --- |
| `skills/` | 后端 AgentScope `SkillBox` | 指导内置聊天 Agent 使用服务端工具；由 `skill.properties` 配置来源，更新后重启后端 |
| `data-agent-skill/` | 外部 Agent | 指导调用 `data-agent-cli`；复制到调用方的 Skill 目录，不通过后端加载 |

两者不共用安装位置。外部 Agent 只能调用 REST 暴露的只读工具，不能因复制 Skill 获得 Python、图表或导出能力；安装与权限见 [external-agent.md](external-agent.md)。内置聊天的绘图流程及字段限制见 [charts.md](charts.md)。
