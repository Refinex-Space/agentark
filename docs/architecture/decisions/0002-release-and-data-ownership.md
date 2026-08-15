---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# ADR-0002：不可变发布与数据所有权

## 状态

Accepted

## 决策

可编辑 `AgentDraft` 发布后生成不可变 `AgentRevision` 和完整 `AgentRevisionSnapshot`。Snapshot 包含运行所需的固定版本依赖、Schema Version、Runtime Provider 和 Content Hash，只保存 `SecretRef`。

Session 创建时固定 Deployment、Revision 和 Snapshot。后续资产更新或 Deployment Promote 只影响新 Session；Rollback 只改变 Deployment 指针，不修改旧 Revision。

Control、Runtime、Scheduler 分别拥有自己的 MySQL Schema。Runtime 不读取 Control Catalog 表，Scheduler 不直接更新 Control/Runtime 表。跨平面 ID 是逻辑引用，不建立跨 Schema 外键。

## 一致性

- 单聚合修改使用本地事务。
- 业务变化与 Outbox 在同一事务提交。
- Consumer 按 Event ID 和业务幂等键去重。
- Redis、索引和实时通知均不是 Revision、Run、Approval、Job 或 Knowledge Metadata 的唯一事实源。

