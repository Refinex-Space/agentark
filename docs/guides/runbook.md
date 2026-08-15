---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# 当前仓库 Runbook

## 控制面检查

```bash
python3 tools/harness/knowledge_gate.py
python3 tools/harness/verify_upstreams.py
git diff HEAD --check
git status --short
```

`verify_upstreams.py` 默认验证两个来源仓库包含固定 Commit；Phase 00 建立 detached Worktree 后使用 `--require-worktrees` 同时校验工作视图 HEAD。

## 当前限制

仓库尚无 `pom.xml`、`mvnw`、`agentark-web/package.json`、Compose 或 Helm，因此当前不能运行后端、前端、容器或部署验证。对应能力必须由 `PLAN.md` 的拥有 Phase 创建并在本页增加真实命令。

## 回滚

- 文档/控制面变更：使用 Git Diff 精确反向修改，不覆盖用户已有改动。
- 固定上游 Worktree：先运行校验，再用来源仓库的 `git worktree remove <exact-path>`；禁止直接递归删除。
- 已发布 Flyway、Contract、Revision/Event 不允许原地回滚；按所属规范使用 Forward Fix 或兼容迁移。

## Loop 就绪性

当前 Loop 为 `DISABLED`。只有同时满足以下条件才可通过独立变更启用：

1. 目标 Work Package 边界、停止条件和失败升级规则明确；
2. 最小构建/测试命令已真实存在并稳定运行；
3. 无需生产密钥、外部付费调用或不可逆操作；
4. 日志、阶段报告和 Diff 可复查；
5. 最大迭代次数/时间、失败阈值和人工 Checkpoint 已定义。
