---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: docs/README.md
---

# JPA 到 MyBatis-Plus 语义迁移

## 范围与结论

AgentScope Service 固定源码 `0c61e7494197ded54eefdeaf9bdeb51807beb752` 中的 JPA Entity、Repository 和自动建表行为只作 `REFERENCE`。AgentArk 不复制 Annotation、继承关系、派生查询、Lazy/Cascade 或 PostgreSQL 方言；迁移单位是可观察的仓储与事务语义，由所属平面重新定义领域端口、DO、Mapper、SQL 和 Contract Test。

Phase 06 只建立公共语义底座和测试表，不迁移任何业务表。Control、Runtime、Scheduler 的业务 Mapper 分别由 Phase 07–10、11、15 创建。

## 上游行为证据

| 上游证据 | 需要保留的行为 | AgentArk 处理 |
|---|---|---|
| `service-common` 的 Session/Event Repository 与跨进程测试 | Scope 查询、Event Sequence 单调、稳定顺序、并发可见 | Runtime 显式 Mapper 查询、唯一约束、事务与真实 MySQL 测试 |
| `ManagedSessionEntity` 的 `@Version` 语义 | 并发更新只能有一个成功 | `version BIGINT` + MyBatis-Plus 乐观锁；影响行数为 `0` 转冲突 |
| `CoordLeaseEntity` 与 Turn/Cron 协调测试 | Claim、续租、条件释放不能误删新 Owner | Runtime/Scheduler 分别建模 Lease + Fencing Token，不共享表 |
| `JpaAgentStateStoreTransactionTest` | State 读取/写入需要明确事务和可恢复可见性 | Runtime State/Checkpoint 本地事务；拒绝 PostgreSQL Large Object 依赖 |
| Spring Data 派生查询与分页 | 条件、排序、页边界可重复 | 显式 QueryWrapper/Mapper SQL，排序末尾必须有唯一 Tie-breaker |

机械基线的 H2 MySQL Mode 测试只用于说明上游行为，不是 AgentArk MySQL 兼容证据；AgentArk Contract Test 固定运行 `mysql:8.4.11`。

## 逐项语义映射

| JPA/Spring Data 语义 | MyBatis-Plus 实现约束 | 必测结果 |
|---|---|---|
| `save` 新增/更新合并 | 分离 `insert` 与带版本的 `update`，调用方明确意图 | 不存在记录不能被更新路径静默插入 |
| `findById` | Mapper 按主键查询并在 Adapter 映射领域对象 | 不存在返回显式 Optional/Not Found，不返回半初始化 Proxy |
| 派生条件查询 | 显式 LambdaQueryWrapper 或 XML/Annotation SQL | Scope、状态、时间和排序全部可见且受评审 |
| `Pageable` | 显式 Page 上限、排序列白名单和唯一 Tie-breaker | 总数、页边界、同值排序稳定；超限拒绝或裁剪按契约执行 |
| `@Version` | `@Version` 字段 + Optimistic Locker，更新后检查影响行数 | 旧副本更新返回 `0`，不可覆盖新值 |
| `@Transactional` | Application Service 上的 Spring 事务；Mapper 不自行开启事务 | 中途异常回滚全部本地写入；跨服务调用不纳入事务 |
| `@Column(unique=true)` / 唯一索引 | Flyway 命名唯一约束，包含真实租户 Scope | 冲突暴露为 `DuplicateKeyException` 并转换稳定领域错误 |
| `Instant` / `timestamptz` | `TIMESTAMP(6)`、UTC 会话、`UtcInstantTypeHandler` | 微秒值往返；不受机器默认时区影响 |
| UUID | RFC 9562 UUIDv7 + `BINARY(16)` + `UuidV7BinaryTypeHandler` | 原值往返；非 v7、错误长度拒绝 |
| JSON/JSONB | MySQL JSON + `JsonNodeTypeHandler`，稳定字段外提 | 合法 JSON 往返；无类型 Map 不能成为契约 |
| Cascade / Orphan Removal | Application Service 显式编排，按聚合顺序写入/删除 | 事务失败完全回滚；不得意外跨聚合删除 |
| Lazy Loading | `REJECT`；查询端口明确需要的对象与投影 | 无 Session 关闭后懒加载异常或隐式 N+1 |
| Soft Delete 全局过滤 | `REJECT` 全局默认；仅聚合语义明确时实现 | 唯一性、恢复、清除和默认查询均有专门测试 |

## 代码归属与命名

- 领域端口：`<Aggregate>Repository`，放在所属领域/应用模块，不依赖 MyBatis。
- 数据对象：`<Aggregate>DO`，只属于 `adapter.out.persistence`；不能作为 API DTO 或领域实体。
- Mapper：`<Aggregate>Mapper`，只访问当前 Owner Schema。允许按聚合直接扩展 MyBatis-Plus `BaseMapper<DO>`，禁止创建共享业务 `BaseMapper`。
- Adapter：`Mybatis<Aggregate>Repository`，负责 DO/领域映射、影响行数检查、数据库异常分类和显式 Scope。
- 查询对象：高复杂度查询使用用途明确的 Criteria/Projection，不创建万能 Map、万能 Wrapper 或字符串字段排序入口。

## Tenant 防御

`agentark-starter-persistence` 只有在所属平面提供 `TenantLineHandler` 且 `tenant-defense-enabled=true` 时才加入 `TenantLineInnerInterceptor`。Phase 06 没有 IAM Claim→Tenant Expression 的可信来源，因此不提供猜测性默认 Handler。

后续 Owner 必须同时满足：入口认证得到可信 Scope、应用命令验证资源归属、Repository 显式带 Scope、Tenant Interceptor 作为 SQL 防御、数据库账号限制 Schema。只通过 Tenant Interceptor 不能视为完成授权。

## 事务与异常规则

- 本地事务只能写一个 Schema；跨平面一致性使用 Outbox、幂等命令和补偿，不使用分布式数据库事务。
- 乐观锁影响行数 `0`、唯一约束、死锁、锁等待超时和连接不可用必须分类；禁止统一吞为“保存失败”。
- Job/Work Item Claim 使用单条条件更新或 `SELECT ... FOR UPDATE SKIP LOCKED` 的受控事务，并始终携带 Fencing Token。
- 外部 Model、Tool、MCP、Object Storage 或服务调用不放在长事务内；先提交 durable work，再由 Worker 执行。

## Phase 06 Contract Test

测试专用 `persistence_contract_record` 不属于任何逻辑模型，只验证：UUIDv7/Instant/JSON 往返、Scope 内稳定分页排序、旧版本更新失败、唯一约束异常和 Spring 事务回滚。Fixture 随 Maven 测试生命周期存在，不能进入生产 Flyway。

