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

1. 使用 `get_domains` 获取可用数据域
2. 使用 `get_table_schema` 获取表结构
3. ...
4. 汇总结果返回给用户

## 注意事项

- 约束 A
- 约束 B
```

- **frontmatter**（必填）：`name`（Skill 标识）、`description`（用途说明）。
- **正文**：写清流程步骤和可用工具，会被拼入 LLM 的 system prompt。可引用的工具列表见 [architecture.md](architecture.md#4-api-概览)。

## 添加自定义 Skill

直接把 Skill 目录放入项目根目录的 `skills/` 下即可：

```bash
mkdir -p skills/my-skill
# 编写 skills/my-skill/SKILL.md
```

默认配置已覆盖 `skills/` 目录，放进去就能用。更多加载方式见 [configuration.md](configuration.md#5-skill-加载源)。

## 内置 Skill

| Skill | 说明 |
| --- | --- |
| `data-query` | 根据表结构生成 SQL 并执行查询，汇总结果 |
