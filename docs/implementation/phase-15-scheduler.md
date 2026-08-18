---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: docs/README.md
---

# Phase 15：Scheduler、持久 Job、Cron、Webhook、Channel 与重试

## 结论

Phase 15 将 Scheduler 从空服务壳升级为独立的持久调度平面：MySQL 是 Trigger、Job、Attempt、Lease、Delivery、Dead Letter、幂等和 Outbox 的唯一事实源；Worker 按 Job Type 隔离、至少一次派发，并用 Owner + 单调 Fencing Token 拒绝陈旧写入。Scheduler 不依赖 `agentark-runtime` 或 AgentScope Harness，不连接 Control/Runtime Schema；Agent Turn 和 Knowledge 结果只经版本化 Internal Client 协作。

当前实现不伪造生产 Provider。`KnowledgeIngestionWorker`、`OutboundEndpointResolver` 或 `ChannelGateway` 未装配时，对应 Handler 不注册，Worker 不 Claim 该类型任务。生产恶意文件扫描、Embedding、Object Store、Qdrant、Endpoint SSRF Resolver 和 AgentScope Channel Bridge 仍需由部署配置提供真实 Bean；默认 Worker 关闭。

## 固定上游审计

审计基于 `.agentark/upstreams/agentscope-java-2.0.2` 固定 Worktree：

| 上游区域 | 观察到的行为 | AgentArk 处置 |
|---|---|---|
| `service-scheduler` Cron | 分钟扫描、短期 Fire Lease、失败主要记录日志 | `ADAPT`：Cron 只推进版本 Cursor 并同事务创建 Durable Job/Outbox；执行由 Worker Claim 完成 |
| `service-scheduler` Channel | 拉取配置、阻塞轮询回复、进程内重试 | `ADAPT/REJECT`：保留中立消息与 Provider Bridge；拒绝阻塞轮询和 Scheduler 内推理循环 |
| `service-scheduler` Outbound/Hands | 调用 Runtime/外部渠道但缺少统一 Attempt、Dead Letter 和 Fencing | `ADAPT`：版本化 Internal Client、Delivery、Retry Budget、超时、Dead Letter 和审计 |
| `service-common` Lease/实体 | 共享模块混合 JPA 实体和协调逻辑 | `REFERENCE/REJECT`：只提炼行为；Scheduler 独占 MyBatis Mapper 和 MySQL Schema |
| AgentScope Channel | Provider 类型与运行能力可复用 | `REFERENCE`：仅在 `adapter.out.channel.agentscope` 映射中立 DTO；具体版本 Bridge 不进入调度核心 |

没有复制上游业务源码、JPA Entity、Harness 推理循环或数据库迁移。

## 领域与事务

`SchedulerModels` 定义四类 Job：`KNOWLEDGE_INGESTION`、`RUNTIME_TURN`、`OUTBOUND_WEBHOOK`、`CHANNEL_MESSAGE`。Job 状态为 `READY → CLAIMED → SUCCEEDED/RETRY_WAIT/DEAD_LETTERED/CANCELLED`；超时 Attempt 使用 `TIMED_OUT`，Lease 接管把旧 Attempt 记为 `ABANDONED`。`SchedulerStateMachine` 禁止终态重新 Claim，并要求 `DEAD_LETTERED → READY` 只能走授权 Redrive。Audience 受限的 Internal Trigger Contract 支持幂等登记 CRON/WEBHOOK；Cron 在同一事务创建首个 Cursor 和 Outbox，疑似敏感配置键被拒绝。

关键本地事务包括：

- Internal Enqueue：Job + `job.accepted` Outbox；同 Type/Business Key 只允许相同租户和 Payload Hash；
- Cron 点火：乐观锁推进 Cursor + Job + Outbox；
- Webhook 接入：Nonce/Request Hash + Job + Outbox；
- Claim：锁定一个到期或过期 Job + 递增 Fencing Token + Lease + 新 Attempt；
- 成功/失败：Attempt 终态 + Job 状态 + Lease 删除 + 可选 Dead Letter + Outbox；
- Cancel/Redrive：Job/Dead Letter + Outbox + 不可吞掉的审计事实。

`job_attempt` 按 Attempt 追加行；单行只允许从 `RUNNING` 转为一个终态。重试永远创建新 Attempt，不覆盖历史行。

## Retry、Lease 与多实例

Retry Policy 固定最大 Attempt、初始/最大退避、倍率、Jitter 和单 Attempt Timeout。Worker 只对 `INHERENT` 或 `PROVIDER_KEY` 副作用自动重试；`NONE` 即使出现暂态错误也直接 Dead Letter。达到预算、Handler 不可用或幂等声明不匹配均不静默循环。

MySQL `FOR UPDATE SKIP LOCKED` 负责多实例 Claim，`job_lease` 和 `job.current_fencing_token` 保存当前 Owner。续租、Attempt 终态、Job 终态和 Delivery 状态都绑定当前 Owner/Token；Lease 过期后新 Owner 递增 Token，旧 Worker 无法提交结果。队列深度和最老等待年龄仅按固定 Job Type 标签暴露，避免高基数指标。

## Cron、Webhook 与 Channel

Cron 使用 Spring 六段表达式和 IANA 时区，计算器不持久化、不执行 Handler。Cursor 的 `next_fire_at` 和 `(trigger, scheduledAt)` 业务键防止重复扫描扩大副作用；DST 缺口已有固定时区测试。Trigger 的非敏感 `config` 形成目标 Job Payload，并只补充 `_triggerId`、`_triggerScheduledAt` 和 `_triggerContract` 三个平台字段，避免生成目标 Handler 无法解析的空壳任务。

入站 Webhook 使用 HMAC-SHA256、五分钟双向时钟窗口、Nonce、防重放和 1 MiB 流式读取上限。Secret 由 `SecretRef` 按需解析，字符/字节缓冲在验证后清零。外发 Webhook 禁止重定向和非 HTTPS，Header 白名单不允许 Forwarded/Credential；响应以流方式最多读取 4097 字节并立即关闭，持久摘要只含状态与有界字节数，不保存 Provider 正文。

Channel Domain 只携带组织、项目、Channel、Conversation、Recipient、文本、非敏感属性和 Provider 幂等键。`AgentScopeChannelAdapter` 位于指定 Adapter 包，只映射到独立组合层的 Bridge，不导入 Harness 类型；因此升级和具体 Provider 依赖不会污染 Scheduler Domain/Application。

## Knowledge 与 Runtime 协作

`KnowledgeIngestionJobHandler` 把固定 request/revision/job/attempt 映射到 Phase 14 `KnowledgeIngestionWorker`。Worker 只从 Control Internal API 读取计划并提交幂等 `IngestionResult`；KnowledgeRevision 的 READY/FAILED 转换和 Control Outbox 仍由 Control 本地事务完成。

`RuntimeTurnJobHandler` 只通过 `RuntimeInternalClient` 调用 `POST /internal/v1/runtime/turns`。Runtime 新增对应 Controller：验证输入 Hash、Session 租户和 `agentark-runtime` Audience 后，原子创建 Turn/Run/WorkItem/Event/Outbox 并返回稳定 `runId`。Scheduler POM 不依赖 Runtime 模块。

## API、安全与配置

`public-scheduler-v1.yaml` 覆盖 Job 查询、取消、Dead Letter 列表/Redrive 和 HMAC Webhook；`internal-scheduler-v1.yaml` 覆盖 Trigger 登记和 Durable Job 接单；`internal-runtime-v1.yaml` 覆盖 Scheduler 实际调用的 Turn 接单。`scheduler-job/v1.json` 固定 Job Type、状态、Retry Policy、幂等能力和 Hash 形态。

安全默认下只开放脱敏 Actuator 和自身 HMAC 验签的 Webhook；管理/Internal API 全部拒绝。显式启用 Security 后，管理 API 要求 JWT 权限和精确租户选择，Internal API 还要求 `SERVICE` Principal 的 Audience 包含 `agentark-scheduler`。Redrive 使用单独 `scheduler:redrive` 权限、人工原因、审计和 Outbox。

配置、启用前置和故障恢复见 [Scheduler 运维手册](../guides/scheduler-operations.md)。

## 数据库

Scheduler Flyway V2 创建九张业务表，所有表和字段具有 MySQL 原生中文 `COMMENT`；状态/类型字段注释列出全部合法值并与 `CHECK` 和 Java Enum 一致。Phase 22 真实 Trigger 演练发现应用写入的 `aggregate_type='trigger'` 未包含在 V2 Outbox CHECK 中，已新增前向 V3 迁移把合法集合修正为 `trigger/job/dead_letter/audit`，并保留 V2 不可变。迁移从 V1 空基线升级，不使用 JPA/Hibernate、共享 BaseMapper、跨 Schema SQL 或应用 Auto-DDL。

完整字段、索引和 Owner 见 [Scheduler Schema](../database/scheduler-schema.md)。

## 验证证据

2026-08-16 实际执行结果：

- `./mvnw -pl agentark-scheduling,agentark-services/agentark-scheduler-server -am clean verify`：14 个 Reactor 模块全部成功；Scheduler 28 个单元测试、8 个 MySQL 8.4 集成测试和 Scheduler Server 1 个上下文测试均为零失败；
- MySQL 集成测试覆盖空库和 V1→V2、九张表及全部字段中文 COMMENT、双连接 `FOR UPDATE SKIP LOCKED`、陈旧 Fencing Token 拒绝、Control/Runtime Schema 连接与写入拒绝；
- `./mvnw -pl agentark-runtime,agentark-services/agentark-runtime-server -am clean verify`：13 个 Reactor 模块全部成功，证明新增 Internal Turn 接单没有破坏 Runtime；
- `./mvnw -pl agentark-scheduling -am -Dtest=SchedulingArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test`：2 个架构规则通过；
- `./mvnw -pl agentark-kernel -Dtest='ContractSchemaTest,ContractDocumentLintTest' test`：17 个 OpenAPI/JSON Schema/文档 Lint 测试通过；
- `./mvnw -pl agentark-scheduling dependency:tree`：未出现 `agentark-runtime` 或 `agentark-runtime-provider-agentscope`；禁用 Harness/Runtime 实现扫描无命中；
- `python3 tools/harness/knowledge_gate.py`：47 份 active 文档通过；`python3 tools/harness/verify_upstreams.py --require-worktrees`：两个固定 Commit 与 detached Worktree 验证通过；
- 两个固定上游 Worktree `git status --short` 均为空，`git diff HEAD --check` 通过。

曾使用不带 `-am` 的单模块定向测试命令，因本地 Maven 仓库中的 Knowledge 依赖不是本次 Reactor 产物而在测试编译阶段失败；改用上述带 `-am` 的正确命令后通过。该失败不作为验收成功记录，也未通过安装快照掩盖。

## 已知边界

- 默认 Worker 关闭，生产 Provider 不在本阶段伪造；缺少对应 Bean 的 Job Type 不会被 Claim。
- Outbound Endpoint 的 DNS/IP 审核由生产 Resolver 执行；仍需出口网络策略防止 DNS Rebinding。
- AgentScope Channel Bridge 只定义防腐接口，具体 Channel Provider 组合和真实外部服务 E2E 需部署方提供。
- Gateway 尚未路由 Scheduler Public/Callback API；该统一入口属于 Phase 16。

## 回滚

先关闭 Worker、等待 Lease 到期并确认没有有效 `CLAIMED` Job，再回滚应用代码和契约。Flyway V2 一旦应用不得修改或删除；需要修正时新增前向迁移。Job、Attempt、Delivery、Dead Letter、幂等和 Outbox 均为审计事实，不随应用回滚清理。
