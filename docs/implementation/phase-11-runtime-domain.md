---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: docs/README.md
---

# Phase 11 Runtime 中立领域执行报告

## 结论

Phase 11 建立了不依赖 AgentScope 的 Runtime 权威模型：Session 创建时固定 Deployment、Revision、Snapshot 与 Snapshot Hash；Turn 表达用户请求，Run 以递增 Attempt 表达每次执行；Event、Approval、Agent State、Checkpoint、Usage、Idempotency、Outbox、持久 Work Item 和 Fencing 统一由 Runtime Owner 管理。

本阶段没有接入 AgentScope、真实 Control HTTP Client、Runtime API、SSE 或常驻 Worker。`AgentExecutionEngine` 与 `SnapshotLoader` 只是中立端口，Fake Engine 用于证明状态机；AgentScope 防腐层属于 Phase 12，WebFlux API、SSE、HITL 决策端点和 Worker 装配属于 Phase 13。

## 固定上游取用边界

只读复核继续使用 AgentScope 固定 Commit `0c61e7494197ded54eefdeaf9bdeb51807beb752`。Dataplane 的 Session/Turn、数据库 Event Log、HITL Ticket、Turn Lease、Hands Work Queue、取消与恢复语义被归类为 `ADAPT/REFERENCE`；JPA Entity、共享 Repository、进程内 Active Turn Registry、200 ms HITL 轮询、AgentScope Event 类型和 Provider 自动建表均未迁入。

Aistio PostgreSQL Runtime Store 只用于核对 Session、Turn、Event、Command、分页和恢复含义。Phase 11 的 MySQL 表、事务、双层 Event Sequence、Fencing Trigger 与 Port 均为 AgentArk 独立实现，不是 PostgreSQL DDL 的逐句翻译。

## 中立领域与状态机

`RuntimeModels` 定义 Session、Turn、Run、Approval、RuntimeEvent、RuntimeInstance、RuntimeWorkItem、AgentStateVersion、Checkpoint、UsageRecord、IdempotencyRecord、RuntimeOutboxEvent、SnapshotDescriptor 和执行结果。所有标识复用 Kernel UUIDv7 强类型 ID，时间统一使用 `Instant`，Payload 必须在 Inline JSON 与 `ObjectRef` 之间二选一。

`RuntimeStateMachine` 显式约束 Turn、Run 和 Approval 的合法迁移。首次接受 Turn 原子建立 Turn、Attempt 1 Run、Work Item、`run.accepted` Event、Outbox 和幂等记录；重试追加新 Run Attempt，不覆盖历史 Run。Fake Engine 覆盖成功、失败、暂停、恢复和取消，非法迁移返回稳定 Runtime 冲突错误。

Session 一经创建不能改变 Deployment、Revision、Snapshot 或 Snapshot Hash。执行只通过 `SnapshotLoader` 获取已固定 Snapshot；Runtime Domain/Application 不读取 Control 表，也不持有 Control Entity 或可编辑 Catalog 类型。

## Event、幂等与 Fencing

每个持久 Event 同时具有全局 UUIDv7 `eventId`、Session 内 `sessionSequence` 和 Run 内 `sequence`。Session/Run 行锁分别分配序号，数据库唯一约束防止重复；Event Trigger 校验当前 Run Fencing Token，Update/Delete Trigger 保证追加式事实不可原地修改。

Event 在状态更新前写入。终态使用明确的 `run.succeeded`、`run.failed` 或 `run.cancelled` 事实；SSE 以后只消费 Event Store，不能成为事实源。大 Payload 保存不可授权的 `ObjectRef`、Checksum、Size 和 Content-Type，Schema 通过 `oneOf` 保证 Inline Payload 与 Payload Ref 恰有一种。

幂等记录在 Scope 内永久绑定 Request Hash。相同 Key 和相同 Hash 返回首次资源；相同 Key 不同 Hash 返回冲突。Work Claim 每次递增 Fencing Token；Event、Agent State 与 Checkpoint 的关键写入由 MySQL Trigger 拒绝旧 Token，避免过期 Owner 恢复后提交结果。

## V2 与持久化所有权

`V2__phase_11_runtime_domain.sql` 创建 13 张 Runtime 业务表：`session`、`turn`、`run`、`runtime_work_item`、`runtime_instance`、`runtime_event`、`runtime_event_payload_ref`、`approval`、`runtime_agent_state`、`runtime_checkpoint`、`usage_record`、`runtime_idempotency_record` 和 `runtime_outbox`。全部表和字段使用 MySQL 原生中文 `COMMENT`，可穷举字段同时声明完整合法值与 `CHECK` 约束。

MyBatis Adapter 使用显式、无 Schema 前缀的 SQL，只连接 Runtime DataSource。持久 Work Claim 使用 MySQL `FOR UPDATE SKIP LOCKED`；序号、状态迁移、幂等、Event、State、Checkpoint、Usage 和 Outbox 均落入 Runtime 自己的 Flyway 历史。没有 JPA、Hibernate、AgentScope Store 或 Application Auto-DDL。

`runtime_agent_state` 保存版本、Hash、Inline JSON 或 ObjectRef、Commit 可见性和 Fencing Token；Checkpoint 只能引用同一 Run 中已经提交的 State Version。Redis 不是权威状态，即使缓存全量丢失，MySQL 仍可恢复 Session/Run/Event/State/Checkpoint 元数据，大对象内容继续由 Object Storage 的 `ObjectRef` 定位。

## 测试与验收范围

单元测试覆盖三套状态机、固定 Snapshot、成功/失败/取消/暂停恢复、Retry 新 Attempt、重复命令、Request Hash 冲突、并发双 Sequence、Lease 过期回收和旧 Fencing Token。MySQL 8.4 Testcontainers 覆盖空库 V1 → V2 Migration、13 张表、中文注释、Snapshot 不可变、Event 追加性、20 并发连接分配 Session/Run Sequence、旧 Token 拒绝、ObjectRef State/Checkpoint 恢复和真实 MyBatis 执行闭环。

最终 `clean verify` 的 5 个 Reactor 模块全部成功，共执行 14 个测试套件、112 项测试，失败 0、错误 0、跳过 0。知识门禁检查 39 份 Active 文档；AgentScope Java 与 DeepSeek Harness 固定 Worktree、Runtime 依赖/导入/Schema 边界和 `git diff HEAD --check` 均通过。

## 风险与后续边界

- `traceId` 当前由 Runtime 生成稳定关联值，不代表 Phase 13 的真实 W3C 入站 Trace Context；API/Worker 装配时必须传入和传播实际 Trace。
- Outbox 只有持久记录，没有发布、重试或 Dead Letter Worker；`PENDING` 不能描述为已投递。
- `cancel` 会调用 Engine Port；真实远端 Provider 需要在 Phase 12–13 明确事务提交与外部副作用顺序，不能依赖 Fake Engine 的同步行为。
- Work Item 已有持久 Claim、过期回收和 Fencing，但 Runtime Instance Heartbeat、持续轮询、Drain 与 API 仍属于 Phase 13。
- Object Store 本阶段只持久化和恢复 `ObjectRef`；实际大对象上传、读取和权限验证继续复用 Storage Starter，并需在 Phase 13 的端到端路径验证。
- MySQL Trigger 依赖 `log_bin_trust_function_creators=ON`；共享环境未启用该实例参数时 V2 会明确失败，不得临时授予应用账号 `SUPER`。

## 回滚

- 未发布源码、契约和文档按本阶段 Git Diff 精确反向修改，不覆盖 Phase 10 或用户其他未提交改动。
- V2 测试数据库随 Testcontainers 销毁；不得在共享数据库手工删除表或 Trigger。
- V2 一旦进入共享环境不得删除、重命名或改写，只能追加更高版本 Flyway Forward Fix。应用回滚前必须确认旧 Runtime 能忽略 V2 新表。

```bash
./mvnw -pl agentark-runtime -am clean verify

rg -n "io\.agentscope" \
  agentark-runtime/src/main/java \
  -g '**/domain/**' \
  -g '**/application/**' && exit 1 || true

rg -n "agentark_control\." agentark-runtime && exit 1 || true

python3 tools/harness/knowledge_gate.py
python3 tools/harness/verify_upstreams.py --require-worktrees
git diff HEAD --check
```
