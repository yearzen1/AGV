# AGENTS.md

本文件是本仓库对 AI Agent 的强制约定。任务与这里的规则冲突时，先向用户确认，不要跳过。

## 必须使用项目 Skill

`.agents/skills/` 下的 skill 是本仓库工作流的一部分。任务匹配任一 skill 的 `description` 时，**必须先用 skill 工具加载并遵守**，不得凭记忆直接实现。

| Skill | 何时必须使用 |
| --- | --- |
| `document-driven-develop` | 任何代码变更：新增功能、修 bug、行为调整 |
| `diataxis` | 写、修改或重组文档、教程、README |
| `conventional-commit` | 写提交信息、拆分提交 |
| `tdd` | 需要测试先行的实现任务 |

## 文档驱动开发门禁

1. 改代码前先定位受影响文档，完成对应文档修改并自检。
2. 文档 diff 必须早于代码 diff。
3. `docs:` 提交必须排在对应的 `feat:` / `fix:` 提交之前。
4. 实现中发现文档不可行：先改文档并说明原因，再改代码。
5. 不允许「代码是对的，文档回头再补」。

豁免：仅文档修订、注释、格式化、拼写修正等无行为变化的改动。

## 文档位置

- 教程：`docs/tutorials/`
- 操作指南：`docs/how-to/`
- 参考：`docs/reference/`
- 解释：`docs/explanation/`
- 项目入口与接口：`README.md`

## 领域术语

- 领域术语以 `docs/reference/领域模型与术语.md` 为准。
- 修改实体、字段、接口或页面文案中的领域名词前，先更新该文档。
- 跨层重命名按 `docs/how-to/统一领域命名.md` 分阶段执行，不允许一次性全改。

## 提交

遵循 Conventional Commits：

```text
<type>(<scope>): <subject>

中文正文说明为什么改，而不是改了什么
```

- `subject` 用英文祈使句、小写开头、不加句号、不超过 72 字符。
- 一个提交只表达一个意图，能单独 revert。
