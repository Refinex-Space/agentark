---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# 领域术语路由

领域定义的规范来源是 [系统架构第 7 章与术语表](../architecture/overview.md)。本页只补充易混淆边界：

| 术语 | 边界 |
|---|---|
| AgentDraft | 可编辑工作副本，不可直接作为生产 Runtime 输入 |
| AgentRevision | 一次不可变发布，关联唯一 Snapshot |
| AgentRevisionSnapshot | 运行所需版本闭包的规范化、带 Hash 的不可变表示，只含 SecretRef |
| Deployment | Environment 中指向确定 Revision 的期望状态；Rollback 改指针，不改 Revision |
| Session | 连续上下文，创建时固定 Deployment/Revision/Snapshot |
| Turn | 一次业务输入处理；可有多个 Run Attempt |
| Run | Turn 的一次执行尝试，拥有 Event、Approval、Checkpoint 和 Usage |
| RuntimeWorkItem | Runtime MySQL 中已持久接单的执行工作，不等同 Redis 通知 |
| AgentStateVersion | Provider 状态的版本化存储；只有已提交 Checkpoint 指向的版本可恢复 |
| KnowledgeRevision | 完整、不可变、验证通过的索引版本；只有 READY 可进入 Snapshot |
| Job | Scheduler 拥有的异步执行事实，不拥有 Harness 推理循环 |
| Fencing Token | 单调写令牌，用数据库条件拒绝过期 Worker |
