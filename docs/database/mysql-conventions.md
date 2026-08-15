---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# MySQL 与 Flyway 规范

## 实例与会话基线

- 目标版本是 MySQL `8.4.11`，存储引擎固定为 InnoDB；开发、迁移测试和部署不得使用 `latest` 或 H2 MySQL Mode 代替最终验收。
- Schema、表和字符串列默认使用 `utf8mb4` / `utf8mb4_0900_ai_ci`。Identifier、Hash、Idempotency Key 等需要区分大小写或逐字节比较的字段，必须在列上显式使用 `ascii_bin`、`VARBINARY` 或经评审的大小写敏感排序规则。
- MySQL Server、JDBC 会话和应用时间线统一为 UTC。连接初始化必须设置 `time_zone='+00:00'`，并启用 `STRICT_TRANS_TABLES`、`ONLY_FULL_GROUP_BY`、`ERROR_FOR_DIVISION_BY_ZERO`、`NO_ZERO_DATE`、`NO_ZERO_IN_DATE`、`NO_ENGINE_SUBSTITUTION`。
- 业务时刻使用 `TIMESTAMP(6)`，Java 使用 `Instant`；禁止依赖 JVM、操作系统或数据库默认时区。纯日期、当地营业时间和时区标识分别使用 `DATE`、`TIME(6)` 与 IANA Zone ID 建模。
- 金额使用定点数和 ISO 4217 Currency；禁止浮点累计成本。布尔值使用 `BOOLEAN`/`TINYINT(1)` 并在应用层验证合法值。

## 命名与 Owner

- Schema、表、列、索引和约束统一使用小写 `snake_case`，不使用保留字、引号标识符、按环境变化的前缀或超过 MySQL 64 字符上限的名称。
- 主键、唯一约束、普通索引、外键和检查约束分别使用 `pk_<table>`、`uk_<table>_<purpose>`、`idx_<table>_<purpose>`、`fk_<table>_<target>`、`ck_<table>_<purpose>`。单列 `id` 主键可使用 InnoDB 隐式主键名；其余名称必须稳定且可审计。
- `agentark_control`、`agentark_runtime`、`agentark_scheduler` 分别只由 Control、Runtime、Scheduler 写入。Gateway 不连接业务数据库；跨平面 ID 是逻辑引用，不使用跨 Schema SQL、Foreign Key、Mapper、事务或共享账号。
- 每个 Server 只装配一个所属 DataSource 和一个 Flyway Location。部署系统预建 Schema 与最小权限账号，应用账号不具备创建其他 Schema、用户或授权的权限。

| Owner | Schema | Flyway Location | 首个业务表 Phase |
|---|---|---|---|
| Control | `agentark_control` | `classpath:db/migration/control` | Phase 07 |
| Runtime | `agentark_runtime` | `classpath:db/migration/runtime` | Phase 11 |
| Scheduler | `agentark_scheduler` | `classpath:db/migration/scheduler` | Phase 15 |

## 类型与数据边界

- 主键和稳定引用使用 RFC 9562 UUIDv7，对外表示为标准 UUID 字符串，MySQL 保存为 `BINARY(16)`。`UuidV7BinaryTypeHandler` 按 Most Significant Bits → Least Significant Bits 的网络字节序写入，不采用 MySQL `UUID_TO_BIN(..., 1)` 的重排格式；非 UUIDv7 值必须拒绝。
- `TIMESTAMP(6)` 只承诺微秒精度；输入纳秒在进入持久化边界前必须明确截断或拒绝，不能在回读时静默改变幂等 Hash。
- 状态使用长度受控、大小写固定的稳定字符串代码，不使用 Java ordinal 或数据库 `ENUM`。状态转换由领域模型和带前置状态的条件更新共同约束。
- JSON 只保存 Snapshot、版本化扩展、低频 Payload 或外部 Schema 明确的文档。授权、租户、状态、时间、版本、幂等、唯一性、Join 和高频过滤字段必须规范化；查询 JSON Path 前先评估生成列/函数索引和演进兼容性。
- Blob、大文本和大 State 进入 Object Storage；表中只保存 `ObjectRef`、SHA-256、Size、Media Type 和加密元数据。ObjectRef 不是授权凭据。
- Secret、Token、Credential 和敏感 Tool Argument 明文不得进入表、JSON、Migration、日志或测试 Fixture；只保存 `SecretRef`、摘要或受控元数据。

## 共有字段

业务表按语义选用：`id BINARY(16)`、`organization_id BINARY(16)`、`project_id BINARY(16)`、`created_at TIMESTAMP(6)`、`created_by`、`updated_at TIMESTAMP(6)`、`updated_by`、`version BIGINT`。可更新聚合的 `version` 从 `0` 开始且不得为负；不可变表没有业务更新接口，但仍记录创建信息。

所有唯一约束必须包含实际租户边界。跨 Schema ID 只作逻辑引用，不建外键。单 Schema 内仅在聚合生命周期一致且不会阻碍归档时使用外键。

软删除不是全局默认。只有法律保留、用户可恢复或聚合历史语义明确要求时才增加 `deleted_at/deleted_by`；唯一约束、默认查询、恢复、再次创建、归档和数据清除必须同时定义。Event、Snapshot、Revision、Attempt 等不可变历史使用追加和状态语义，不伪装成软删除。

## 索引规则

- 租户查询索引以 `organization_id, project_id` 或最小实际 Scope 开头。
- 状态队列使用 `status, available_at/next_attempt_at, id` 的稳定扫描索引。
- Event 使用 `(run_id, sequence)` 唯一约束和 `(session_id, event_id)` 恢复索引。
- Outbox 使用 `(status, available_at, id)` Claim 索引和全局唯一 `event_id`。
- 幂等记录使用 `(scope_type, scope_id, idempotency_key)` 唯一约束，并保存 `request_hash`。
- 所有索引必须对应已记录查询；禁止为每列机械建索引。

## 并发与安全

- 乐观锁更新必须使用 `WHERE id = ? AND version = ?` 并原子执行 `version = version + 1`；影响行数为 `0` 表示冲突，禁止静默视为成功。Published Revision 等不可变资源同时由 Application、Repository 和数据库约束策略保护。
- Lease/Fencing 的关键写必须包含 `WHERE fencing_token = :expected` 或拒绝更小 Token 的等价原子条件。
- MyBatis-Plus `TenantLineInnerInterceptor` 只在所属平面提供 `TenantLineHandler` 后启用；它只作 SQL 纵深防御，不能替代 API 授权、Principal Scope 校验、Repository 显式 Scope、对象存储前缀或跨服务契约验证。
- MyBatis SQL Logger 固定为 `NoLoggingImpl`。慢查询日志只允许 Mapper Statement ID、受控 Operation、Outcome 和 Duration；禁止输出 SQL 正文、参数、连接凭据、租户数据或结果集。指标标签只能使用低基数白名单。

## Repository 与事务

- 领域端口命名为 `<Aggregate>Repository`，由所属模块定义；MyBatis 适配器命名为 `Mybatis<Aggregate>Repository`，数据库对象使用 `<Aggregate>DO`，Mapper 使用 `<Aggregate>Mapper`。DTO、DO、领域对象不得互相复用。
- 业务 Mapper 可以按聚合扩展 MyBatis-Plus `BaseMapper<DO>`，但禁止创建平台共享业务 `BaseMapper`、万能 Repository、跨聚合 Cascade 或隐式 Lazy Loading。
- 分页必须有显式排序和唯一 Tie-breaker；Cursor 条件必须与排序完全一致。禁止依赖数据库自然顺序，禁止无界 `SELECT`，禁止把 Offset Page 用于高频无限事件流。
- 事务边界位于所属 Application Service；外部网络调用、长时间模型执行和跨平面调用不进入本地数据库事务。唯一约束冲突、乐观锁失败和死锁必须转换为稳定领域错误，不能吞异常。
- MyBatis TypeHandler 只负责类型往返，不承担授权、业务默认值或 Schema 演进。UUIDv7、UTC `Instant` 和 JSON 必须在真实 MySQL Contract Test 中覆盖。

## Flyway

- 三个 Schema 使用独立账号、Location 和各自 Schema 内的 `flyway_schema_history`；`create-schemas=false`、`clean-disabled=true`、`validate-migration-naming=true` 是生产强制值。
- 版本迁移命名为 `V<整数>__<lower_snake_case_description>.sql`。同一 Owner 内版本单调递增且全局唯一；发布后不可改写、重命名或删除。
- Phase 06 的 `V1__phase_06_schema_baseline.sql` 只建立独立历史起点，不创建业务表或额外元数据表。后续业务 DDL 必须由逻辑模型标注的 Owner Phase 增量加入。
- 兼容迁移使用 Expand → Migrate/Backfill → Contract。MySQL DDL 可能隐式提交，生产回滚默认采用 Forward Fix；需要数据回填时必须拆分、幂等、可观测并设置批量上限。
- 禁止 Hibernate、MyBatis-Plus、AgentScope Adapter 或应用启动逻辑自动建表。
- 每次变更必须验证空库迁移、上一版本升级、重复启动、N/N-1 滚动期和 Forward Fix。测试可以对临时 Schema 启用 `clean`，生产配置永远不得启用。
- DDL 进入代码前，必须与所属逻辑模型中的表、约束、Owner 和查询一致。

## 测试与门禁

- 迁移和持久化契约使用 `mysql:8.4.11` Testcontainers；随机凭据只存在于测试进程和临时容器，不写入源码、资源、日志或报告。
- 每个 Owner 的测试至少证明：V1 空库成功、上一版本基线可升级、固定字符集/排序规则/UTC/严格模式、所属账号可访问自身 Schema 且不能读取另一个 Schema。
- 共享持久化 Contract Test 至少证明：UUIDv7/Instant/JSON 往返、稳定分页排序、乐观锁冲突、Spring 事务回滚和唯一约束异常语义。
- `tools/harness/knowledge_gate.py` 扫描三个 Owner 的生产源码与 Server 配置，拒绝跨 Schema 引用、限定 Schema SQL、错误 Flyway Location 或带默认值的生产数据库占位符。
