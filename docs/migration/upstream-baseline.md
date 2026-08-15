---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# 上游源码基线

## 固定来源

| 上游 | 固定证据 | Detached Worktree | 用途 | 许可/边界 |
|---|---|---|---|---|
| AgentScope Java | Maven `2.0.2`；Commit `0c61e7494197ded54eefdeaf9bdeb51807beb752` | `.agentark/upstreams/agentscope-java-2.0.2` | Runtime、Service、测试和迁移行为证据 | Apache-2.0；按 SHA 读取，保留来源与 Notice，不修改上游 |
| DeepSeek Harness | Commit `47f943859bef60e4160492346772ded9b24f765a` | `.agentark/upstreams/deepseek-harness` | 前端视觉、交互和工程实践参考 | 按仓库许可证审计；不复制品牌、Logo 或完整插件内核 |

AgentScope 固定 SHA 选择的是来源仓库远端 `main` 可达的 `release/2.0.2` 合并提交。它同时包含 `agentscope-core/`、`agentscope-harness/`、`agentscope-extensions/` 和 `agentscope-service/`。原 2.0.1 候选 SHA 不包含 `agentscope-service/`，不能满足 Phase 01–02 的输入要求；完整纠错证据见 [Phase 00 执行基线](../implementation/phase-00-execution-baseline.md)。

## 固定视图规则

本地来源路径可由 `PLAN.md` 顶部环境变量覆盖，不是项目事实。固定 Worktree 必须满足：

- HEAD 与上表 SHA 完全相同，处于 detached 状态且工作区干净；
- `.agentark/` 由 AgentArk 根 `.gitignore` 排除，不进入根仓库版本、构建产物或格式化输入；
- 每个 Phase 在读取前后都检查两个 Worktree 的 HEAD 和状态；
- 不在固定视图中执行会写源码或生成未跟踪文件的构建、格式化、安装和代码生成命令；
- 需要更新基线时先修改 ADR、PLAN 和校验脚本，再创建新路径，禁止在旧路径静默切换版本。

Detached Worktree 是受治理的只读证据视图，不是文件系统级写保护。状态门禁用于发现误写，不能替代操作者遵守只读边界。

## 取用分类

Phase 00 不迁移任何上游文件。AgentScope 的逐文件 `REUSE` / `ADAPT` / `REFERENCE` / `REJECT` / `DEFER` 分类由 Phase 01 完成；DeepSeek Harness 在平台层固定为 `REFERENCE`，不能成为 AgentArk 后端、领域模型或插件内核来源。

## 验证与移除

读取源码前运行：

```bash
python3 tools/harness/verify_upstreams.py --require-worktrees
```

移除固定视图时只使用来源仓库的 Worktree 命令和精确路径：

```bash
git -C "$AGENTSCOPE_REPO" worktree remove "$AGENTSCOPE_ROOT"
git -C "$DEEPSEEK_HARNESS_REPO" worktree remove "$DEEPSEEK_HARNESS_ROOT"
```

移动 `main`、在线滚动文档或本地来源仓库当前 HEAD 都不能替代固定 Commit 证据。
