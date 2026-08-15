---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# 上游源码基线

| 上游 | 固定证据 | 用途 | 许可/边界 |
|---|---|---|---|
| AgentScope Java | Maven `2.0.1`；Commit `35f52181fb37eed97cf0adacf2d1c13a63bbfb7d` | Runtime、Service、测试和迁移行为证据 | Apache-2.0；按 SHA 读取，保留来源与 Notice，不修改上游 |
| DeepSeek Harness | Commit `47f943859bef60e4160492346772ded9b24f765a` | 前端视觉、交互和工程实践参考 | 按仓库许可证审计；不复制品牌、Logo 或完整插件内核 |

本地来源路径可由 `PLAN.md` 顶部环境变量覆盖，不是项目事实。Phase 00 必须创建或验证 `.agentark/upstreams/` 下的 detached Worktree，并将实际工具版本、来源仓库状态和验证命令写入 Phase 00 报告后，才可将该 Phase 标记为 `DONE`。

读取源码前运行：

```bash
python3 tools/harness/verify_upstreams.py --require-worktrees
```

移动 `main`、在线滚动文档或本地来源仓库当前 HEAD 都不能替代固定 Commit 证据。
