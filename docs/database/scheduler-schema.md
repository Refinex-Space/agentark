---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: AGENTS.md#knowledge-map
---

# Scheduler Schema 逻辑模型

Schema：`agentark_scheduler`。唯一写入者是 Scheduler Server。它不保存 Control Catalog、Knowledge Metadata 或 Runtime Session/Run 事实。

Phase 15 已通过 `V2__phase_15_scheduler.sql` 创建下列表、Repository 和多实例/幂等测试；Phase 14 的 Ingestion Worker 继续只定义中立处理与结果契约，不拥有 Scheduler 数据。

Trigger 只允许通过 Audience 受限的 `/internal/v1/scheduler/triggers` 登记。Cron 登记在同一事务中创建首个 Cursor 和 `trigger.created` Outbox；同租户同 Key 的完全相同定义幂等复用，不同定义返回冲突。`config_json` 是目标 Job Payload 的非敏感字符串字段，点火时补充平台保留的 `_triggerId`、`_triggerScheduledAt`、`_triggerContract`；疑似 Secret、Token、Password、Credential 或 API Key 的配置键被应用层拒绝，Webhook 密钥只保存合法 `SecretRef`。

## Flyway 归属

| Phase | 迁移范围 | 边界 |
|---|---|---|
| 06 | `V1__phase_06_schema_baseline.sql` | 只建立 Scheduler 独立 Migration History 起点，不创建业务表 |
| 14 | 无 Scheduler 业务表 | 只定义 Knowledge Ingestion Handler 与结果契约 |
| 15 | Trigger、Cursor、Job、Attempt、Lease、Delivery、Dead Letter、Idempotency、Outbox | 完整调度事实、重试与多实例一致性 |
| 16 | 只允许经本模型评审后的 Channel 兼容增量 | Channel 不能拥有 Runtime Session 或 Control Catalog |

调度处理器不得以实现便利提前创建表；任何 Owner 或状态模型变化必须先更新本文档。

| 表 | 关键内容 | 关键约束与索引 |
|---|---|---|
| `trigger_definition` | id, organization_id, project_id, type, schedule/config, target contract, status, version | Scope 内 key 唯一；配置不含 Secret 明文 |
| `trigger_cursor` | trigger_id, next_fire_at, last_fire_at, last_token, version | trigger 一对一；next_fire 索引 |
| `job` | id, organization_id, project_id, type, business_key, payload/ref/hash, status, priority, available_at, retry_policy | `(type, business_key)` 幂等唯一；Claim 索引 |
| `job_attempt` | id, job_id, attempt_number, owner, fencing_token, status, started/ended, error_code, result_ref | `(job_id, attempt_number)` 唯一；只追加 |
| `job_lease` | job_id, owner, fencing_token, lease_until, version | job 一对一；Token 单调 |
| `delivery` | id, job_id, channel/endpoint identity, provider_idempotency_key, status, response summary/ref | Provider key 唯一；不保存 Credential |
| `dead_letter` | id, job_id, final_attempt_id, reason, redrive_count, status, created_at | 状态/time 索引；Redrive 授权审计 |
| `scheduler_idempotency_record` | scope type/id, idempotency_key, request_hash, result_ref, status, expires_at | Scope 内 key 唯一 |
| `scheduler_outbox` | event_id, aggregate type/id, type, payload/ref, status, available_at, attempts | event_id 唯一；Claim 索引 |

## 状态和副作用

- Job 至少一次执行；Handler 必须声明幂等能力。
- 无 Provider Idempotency Key 的外部写操作默认不自动重试。
- Claim、续租、完成、失败和 Redrive 都校验 Fencing Token。
- Retry Budget 耗尽后进入 Dead Letter；Redrive 生成新 Attempt 并记录操作者和审计关联。
- Cron 计算只推进 `trigger_cursor` 并创建幂等 Job，不直接运行 Agent。
- `job_attempt` 每次 Claim 追加一行，只允许 `RUNNING` 转换到一个终态；重试和 Lease 接管均创建新 Attempt。
- Trigger、Cursor、Job 与对应接单 Outbox 使用 Scheduler 本地事务，不把内存扫描结果当作事实。

## 跨平面命令

- 创建 Agent Turn：调用 Runtime Internal API，使用稳定 Idempotency Key。
- 读取任务所需配置：调用 Control Internal API 或使用版本化 Descriptor。
- 完成 Knowledge 摄取：向 Control 提交 Ingestion Result；Control 校验并转换 KnowledgeRevision 状态。
- Scheduler 不通过共享 Mapper、DataSource 或数据库账号访问其他 Schema。
