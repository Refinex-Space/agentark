---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# Upgrade and Rollback Runbook

## 兼容窗口

- Snapshot 与 Runtime Event 使用版本化 Schema；升级必须支持当前 N 和已存在时的 N-1。
- Internal/Public OpenAPI 变化先通过 Contract Baseline 和 Consumer Test；破坏性变化必须新建版本路径。
- 数据库使用 Expand → Migrate → Contract；Contract 只能在所有旧实例退出且回退窗口关闭后执行。

## 滚动升级

1. 冻结新发布物的 Commit、Digest、SBOM、签名、Provenance 与 Compatibility Matrix。
2. 完成备份和 Restore 抽样，运行 Expand/Migrate Flyway。
3. 先升级 Control，再对 Scheduler/Runtime 执行 Drain 和滚动升级，最后升级 Gateway/Web。
4. 观察错误率、P95、Event Lag、Lease Conflict、Queue Age、Outbox Lag、Quota 和 Audit。
5. 验证旧 Session 仍固定旧 Revision/Snapshot，新 Session 使用当前 Deployment。
6. 观察窗口结束后才允许 Contract Migration 和关闭旧 Digest 回退入口。

## 回滚判定

出现 Contract 不兼容、数据校验失败、Critical/High 安全回归、事件丢失、陈旧 Fencing 写入、持续错误率或恢复失败时回滚应用路由/Digest。已执行 Migration 不降级；数据库不兼容时停止写入并使用前向修复或从只读备份重建。

## AgentScope Provider 升级

任何 AgentScope 版本变化先使 Compatibility Test 和 Matrix 显式变化，只允许在专用 Provider 内适配。不得在同一次紧急回滚中同时改变 AgentScope、Snapshot Schema、数据库和公共 API。
