---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: AGENTS.md#knowledge-map
---

# Runtime Schema 逻辑模型

Schema：`agentark_runtime`。唯一写入者是 Runtime Server。所有表显式包含可验证的 Organization/Project Scope；来自 Control 的 Deployment/Revision/Snapshot ID 只作逻辑引用。

Phase 11 创建逻辑模型对应的 Flyway、Repository 和事务测试；Phase 12 只实现 Provider Adapter，Phase 13 装配 API/Worker，不得另建 Provider 私有表。

当前实现由 `V2__phase_11_runtime_domain.sql` 落地，并由 MySQL 8.4 Testcontainers 验证空库升级、约束、Trigger、并发 Sequence、Fencing 和 MyBatis 往返。本文仍是字段与 Owner 的规范来源；后续变更必须先修改逻辑模型，再追加 Flyway。

## Flyway 归属

| Phase | 迁移范围 | 边界 |
|---|---|---|
| 06 | `V1__phase_06_schema_baseline.sql` | 只建立 Runtime 独立 Migration History 起点，不创建业务表 |
| 11 | Session、Turn、Run、Work Item、Instance、Event、Approval、Agent State、Checkpoint、Usage、Idempotency、Outbox | 完整 Runtime 持久模型与仓储事务 |
| 12 | 无新增 Provider 私有表 | AgentScope Adapter 通过 Runtime 端口读写权威模型 |
| 13 | 只允许经本模型评审后的兼容增量 | API、SSE、Worker 装配不得绕过 Runtime Owner |

Provider 适配便利不能改变表 Owner 或引入 AgentScope 自动建表；任何逻辑模型变化必须先更新本文档。

| 表 | 关键内容 | 关键约束与索引 |
|---|---|---|
| `session` | id, organization_id, project_id, deployment_id, revision_id, snapshot_id/hash, participant/channel metadata, status, event_sequence, version | 创建后固定 Revision/Snapshot；幂等创建唯一键；Session Event 序列由行锁原子递增 |
| `turn` | id, session_id, sequence, input/ref/hash, status, current_run_id, fencing_token, version | `(session_id, sequence)` 唯一；状态/更新时间索引 |
| `run` | id, turn_id, attempt_number, provider/compiler versions, status, event_sequence, started/ended, fencing_token, error_code | `(turn_id, attempt_number)` 唯一；运行状态索引；Run Event 序列由行锁原子递增 |
| `runtime_work_item` | id, run_id, status, priority, available_at, claimed_by, claim_until, fencing_token, attempt_count | `run_id` 唯一；`(status, available_at, priority, id)` Claim 索引 |
| `runtime_instance` | id, instance_key, started_at, heartbeat_at, capabilities, drain_status | `instance_key` 唯一；heartbeat 索引 |
| `runtime_event` | id/event_id, organization_id, project_id, session_id, turn_id, run_id, session_sequence, run_sequence, type, schema_version, payload/ref, occurred_at, fencing_token | `event_id` 全局唯一；`(session_id, session_sequence)` 与 `(run_id, run_sequence)` 分别唯一且单调；Session 回放索引；只追加 |
| `runtime_event_payload_ref` | event_id, object_ref, hash, size, media_type, encryption_metadata | event 一对一；ObjectRef 不作授权凭据 |
| `approval` | id, run_id, action/tool identity, argument_hash, policy_version, status, expected_version, expires_at, decision metadata | PENDING 查询索引；同一决策幂等；参数不可替换 |
| `runtime_agent_state` | id, session_id, run_id, agent_key, state_key, item_index, state_version, state_json/object_ref, content_hash, committed, fencing_token, created_at | `(session_id, agent_key, state_key, item_index, state_version)` 唯一；过期 Fencing Token 拒绝写入；未引用版本可回收 |
| `runtime_checkpoint` | id, run_id, sequence, agent_state_id/version/ref, event_sequence, content_hash, recoverable, fencing_token, created_at | `(run_id, sequence)` 唯一；只引用已提交 State Version；过期 Fencing Token 拒绝写入 |
| `usage_record` | id, run_id, event_id, provider/model/tool, usage dimensions, estimate, price_version, occurred_at | Provider request ID 去重；run/time 查询 |
| `runtime_idempotency_record` | scope type/id, idempotency_key, request_hash, result_ref, status, expires_at | Scope 内 key 唯一；同 key 不同 hash 冲突 |
| `runtime_outbox` | event_id, aggregate type/id, type, payload/ref, status, available_at, attempts | event_id 唯一；Claim 索引 |

## 接收与执行事务

创建 Turn 的请求事务只完成：鉴权结果确认、幂等建档、Turn/Run/WorkItem、`run.accepted` Event 和 Outbox；提交后返回 `202`。Worker 随后 Claim WorkItem、获得递增 Fencing Token、加载固定 Snapshot 并编译执行。

所有 Event、Checkpoint、Approval Resume 和终态写入必须校验当前 Fencing Token。过期 Worker 即使外部调用成功，也不能提交结果。

## AgentState 与 Checkpoint

`runtime_agent_state` 是 AgentScope Provider 的持久适配表，不采用上游自动创建的 `agentscope_sessions`。Provider 写入新版本后，Runtime 事务提交指向该版本的 Checkpoint 和 Event；恢复只能选择已提交 Checkpoint。AgentState 不直接替代 Session/Run/Event 权威状态。

大 State 使用 Object Storage；数据库仍保存 Scope、Version、Hash、Commit 可见性和 ObjectRef。Redis 中的缓存或副本可以全部丢失并从 MySQL/Object 重建。
