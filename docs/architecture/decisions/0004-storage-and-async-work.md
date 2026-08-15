---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# ADR-0004：MySQL 所有权、AgentState 与异步工作流

## 状态

Accepted

## 决策

开发环境可共用一个 MySQL 8.4 实例，但使用 `agentark_control`、`agentark_runtime`、`agentark_scheduler` 三个 Schema 和三个最小权限账号。生产可迁移到独立实例而不改变领域代码。

Runtime 的 v1 默认可恢复状态保存在 Runtime MySQL：

- Session/Turn/Run/Event/Approval/Checkpoint 是 AgentArk 权威状态；
- `runtime_work_item` 是 Runtime 自有持久队列；
- AgentScope `AgentStateStore` 通过 Provider Adapter 写入 AgentArk 管理的版本化 `runtime_agent_state`；
- Redis 只承担 Lease、通知、短期缓存和协调，不单独承载不可重建状态；
- 大型 State/Checkpoint/Payload 外置 Object Storage，数据库保存 `ObjectRef` 和 Hash。

Provider 先写不可见的新 AgentState Version，再由 Runtime 本地事务提交 Checkpoint 引用、Event 和 Run 状态；未被 Checkpoint 引用的版本可回收。恢复只读取已提交 Checkpoint 指向的 State Version。

禁止启用 AgentScope MySQL Adapter 的自动建库建表。所有表由 AgentArk Flyway 管理，显式携带 Organization、Project、Session、Agent 和版本维度，不采用上游将 `(userId, sessionId)` 打包到单一字符串的表模型。

## AgentScope 源码证据

固定 Commit `0c61e7494197ded54eefdeaf9bdeb51807beb752` 中：

- [`AgentStateStore`](https://github.com/Refinex-Space/agentscope-java/blob/0c61e7494197ded54eefdeaf9bdeb51807beb752/agentscope-core/src/main/java/io/agentscope/core/state/AgentStateStore.java) 定义 Agent 状态的 save/load/delete/list Port；
- [`MysqlAgentStateStore`](https://github.com/Refinex-Space/agentscope-java/blob/0c61e7494197ded54eefdeaf9bdeb51807beb752/agentscope-extensions/agentscope-extensions-mysql/src/main/java/io/agentscope/extensions/mysql/state/MysqlAgentStateStore.java) 默认使用 `agentscope.agentscope_sessions`，主键为 `(session_id, state_key, item_index)`，状态保存在 `LONGTEXT`，并可选择自动创建库表；
- 该实现把 `(userId, sessionId)` 组合到一个 `session_id` 值，只解决 AgentStateStore 持久化，不定义 IAM、资产、Revision、Deployment、Runtime Work Queue、Event、Approval、Scheduler Job 或平台治理模型。

因此 AgentArk 复用其 `AgentStateStore` 语义和兼容测试，不复用其平台数据库边界或自动 DDL。

## Knowledge 摄取

Control 创建 KnowledgeRevision；Scheduler 只拥有 Job 和 Attempt。Scheduler 完成 Parser/Chunk/Embedding/Qdrant 验证后，通过幂等 Internal Command 或 Durable Event 提交 `attemptId/count/checksum/artifactRefs`。Control 校验后在本地事务中将 Revision 标记为 READY 并写 Outbox。Scheduler 永不直连 Control Schema。
