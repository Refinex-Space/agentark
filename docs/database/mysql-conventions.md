---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# MySQL 与 Flyway 规范

## 基线

- MySQL 8.4 LTS，`utf8mb4`；排序规则在 Phase 05 的目标镜像上验证后固定，禁止依赖未记录的实例默认值。
- 服务和数据库会话使用 UTC；业务时间使用 `TIMESTAMP(6)`，需要表达纯日期或本地时间时单独建模。
- 主键使用 UUIDv7，对外标准 UUID，MySQL 保存为 `BINARY(16)`；字节序由统一 TypeHandler 固定并做往返测试。
- 金额使用定点数和 ISO 4217 Currency；禁止浮点累计成本。
- 状态使用稳定字符串代码，不使用 Java ordinal 或数据库 ENUM。
- JSON 只保存 Snapshot、版本化扩展和低频 Payload；授权、状态、租户、时间、幂等和高频过滤字段必须规范化。
- Blob、大文本和大 State 进入 Object Storage；表中保存 ObjectRef、SHA-256、Size、Media Type 和加密元数据。

## 共有字段

业务表按语义选用：`id`、`organization_id`、`project_id`、`created_at`、`created_by`、`updated_at`、`updated_by`、`version`。不可变表没有业务更新接口，但仍记录创建信息。软删除不是全局默认。

所有唯一约束必须包含实际租户边界。跨 Schema ID 只作逻辑引用，不建外键。单 Schema 内仅在聚合生命周期一致且不会阻碍归档时使用外键。

## 索引规则

- 租户查询索引以 `organization_id, project_id` 或最小实际 Scope 开头。
- 状态队列使用 `status, available_at/next_attempt_at, id` 的稳定扫描索引。
- Event 使用 `(run_id, sequence)` 唯一约束和 `(session_id, event_id)` 恢复索引。
- Outbox 使用 `(status, available_at, id)` Claim 索引和全局唯一 `event_id`。
- 幂等记录使用 `(scope_type, scope_id, idempotency_key)` 唯一约束，并保存 `request_hash`。
- 所有索引必须对应已记录查询；禁止为每列机械建索引。

## 并发与安全

- 乐观锁使用 `version BIGINT NOT NULL`；Published Revision 等不可变资源同时由 Application、Repository 和数据库权限/触发保护策略约束。
- Lease/Fencing 的关键写必须包含 `WHERE fencing_token = :expected` 或拒绝更小 Token 的等价原子条件。
- Secret 明文、Provider Credential、完整敏感 Tool Argument 不进入数据库、Migration、日志或测试 Fixture。
- MyBatis-Plus Tenant Interceptor 只作纵深防御，Repository 查询仍显式带 Scope。

## Flyway

- `agentark_control`、`agentark_runtime`、`agentark_scheduler` 使用独立账号、Location 和 History。
- Migration 已发布后不可改写；使用 Expand → Migrate/Backfill → Contract。
- 禁止 Hibernate、MyBatis-Plus、AgentScope Adapter 或应用启动逻辑自动建表。
- 每次变更必须验证空库迁移、上一版本升级、重复启动、N/N-1 滚动期和回滚/Forward Fix。
- DDL 进入代码前，必须与所属逻辑模型中的表、约束、Owner 和查询一致。

