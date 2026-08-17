---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: docs/README.md#安全运维
---

# Aistio Cutover Rollback Runbook

## 原则

回滚单位是 Cohort，不是数据库或整个平台。回滚不能改写 AgentArk Revision/Snapshot/Event，不能恢复 Java/Go 双写，不能让活动 Session 中途换 Owner。Flyway 不做 Down；错误使用前向修复或补偿 API。

## 触发条件

- Java 权限比 Go 更宽；
- Canonical Match、5xx、p95 或 Count/Hash/Reference 低于批准阈值；
- Snapshot/Revision 映射错误；
- 新 Session 错误固定到错误 Deployment/Revision；
- Audit/Secret Redaction 不完整；
- Java Primary 出现不可接受的持续故障。

## 立即动作

1. 停止受影响 Cohort 的新 Java 写，不停止其他已稳定 Cohort。
2. 保留 Gateway/Proxy/Checkpoint/Report/Trace/Audit，不删除失败记录。
3. 新 Session 创建故障时先 Disable Java Deployment；已有 Java Run 按 Runtime Cancel/Drain 流程处理。
4. 已在 Go Owner 的活动 Session 保持 Go，不做迁移或重建。
5. 若满足只读 Fallback 条件，可临时设置 `GO_FALLBACK`，到期时间必须在未来 24 小时内；写请求仍被代理拒绝。

## Route 回切

- 只读 Cohort：Gateway GET 回切 Go，Java 继续 Shadow；记录切换 UTC 和 Route Hash。
- 写 Cohort：只有确认 Java 未产生需要逆同步的新事实，或已经完成审批补偿后，才允许恢复 Go 写。
- Agent/Deployment：Java 已发布 Revision 不删除；回切只改变外部 Route，不改变 Snapshot。
- Session/Runtime：按 Session Owner 路由；不能把 Java Session ID 发给 Go，也不能把 Go Session 绑定到 Java Catalog。
- Scheduler：Job 已入队时禁止在 Go 再次 Fire；先按 Idempotency Key/Trigger Cursor 对账。

## 数据补偿

Checkpoint 是唯一来源映射证据。对每个受影响资源输出：Source Key/Hash、Target ID/Hash、首次/最后操作、失败代码和批准动作。补偿只能调用 Owner API：

- 错误 Deployment 指针：使用 Rollback API；
- 错误启用状态：Disable；
- Catalog/Agent 重复稳定 Key：先 Reconcile，禁止直接删表；
- Secret Metadata 错误：Disable/Revoke，不导出值；
- 大对象不一致：按 Checksum 重传到新 ObjectRef，旧对象进入删除流程；
- Runtime/Scheduler 副作用：使用 Cancel/Dead Letter/Redrive 正式命令，不手改表。

不提供自动 Java→Go 逆向双写工具。若业务事实必须回填 Go，需单独数据变更审批、字段级映射和可恢复脚本。

## 恢复 Java Primary

根因修复后重新执行：固定 Contract Test、Migration Unit、Dry Run、受影响资源 Apply/Resume、Count/Hash/Reference、Shadow Gate。新的 Export 或 Config 会改变 Plan Hash，必须重新审批，不能复用旧 Checkpoint。

恢复顺序仍为 `SHADOW → JAVA_PRIMARY → JAVA_ONLY`。Go Fallback 到期后必须关闭并删除 Route，不能把回滚临时状态变成长期架构。

## 归档

归档 Incident ID、Cohort、时间窗、Gateway Diff、Plan/Checkpoint/Report Hash、受影响资源列表和最终决策。更新 Threat Model、ADR 和 Phase 报告；不得把响应正文、Secret、Token 或 PostgreSQL DSN 放入普通 Issue/日志。

