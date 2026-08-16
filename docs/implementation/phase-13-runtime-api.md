---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: docs/README.md
---

# Phase 13 Runtime API 执行报告

## 结论

Phase 13 将 Phase 11 的中立 Runtime 领域和 Phase 12 的 AgentScope Provider 组装为 WebFlux Runtime 服务。Public API 已覆盖 Session、Turn、Run、Event、SSE、取消和 HITL Approval；Worker 使用 Runtime MySQL 持久队列、Redis 快速互斥和 MySQL 单调 Fencing，不读取 Control 数据库，也不把 Redis 当成权威状态。

本阶段复用 Phase 11 的 Runtime V2 表和触发器，没有新增 Flyway。`runtime_work_item`、`runtime_instance`、`runtime_agent_state`、Checkpoint、Event、Usage、Idempotency 和 Outbox 仍由 Runtime 独占；Provider 没有自动建表权限。

## 接单与固定 Snapshot

创建 Session 时，Runtime 通过 Control Internal API 解析启用的 Deployment，提交 Provider、Schema 与 Capability，并用 ETag 缓存不可变 Snapshot。Session 事务只保存已核对的 Deployment、Revision、Snapshot 和 Content Hash，创建后不得漂移。

创建 Turn 时不加载或编译 Snapshot。单一 Runtime MySQL 事务提交 Turn、首个 Run Attempt、持久 Work Item、幂等结果、`run.accepted` Event 和 Outbox；事务提交后 Controller 才返回 `202 Accepted` 和稳定 `runId`。相同幂等键和相同 Request Hash 返回原资源，相同键但不同 Hash 返回稳定冲突。Worker 后续加载或编译失败也会把既有 Run 写成可查询的 FAILED，不会丢失接单结果。

## Worker、Lease 与恢复

Worker 每次只 Claim 一个到期可见的 Work Item。MySQL Claim 在事务内递增 Fencing Token 并同步到 Run/Turn；Redis Lease 只减少跨实例竞争。执行前、终态提交前以及 Event、State、Checkpoint 写入时都校验当前 Owner、未过期 Lease 和 Fencing Token。Redis 或 MySQL 续租失败会立即使本地 Execution Lease 失效，并调用 Provider Cancel 阻止后续外部调用；数据库触发器和条件更新继续拒绝陈旧 Worker 的 Event、State、Checkpoint 与终态写入。

有可恢复 Checkpoint 的孤儿 Run 由新 Owner 和新 Fencing Token 接管同一 Attempt；没有完整 Checkpoint 的 Run 转为 ABANDONED，并为同一 Turn 创建新 Attempt。暂停结果必须同时存在待决 Approval 和已提交 Checkpoint，否则降级为明确失败。Runtime Instance 启动后注册并周期心跳；进程关闭先把本地状态切为 DRAINING，Worker 不再领取新任务，再写入 DRAINED。

## Event、SSE 与载荷

所有 Event 先写 Runtime MySQL，再在事务提交后发送可丢失的进程内提示。SSE `id` 是已提交的 `sessionSequence`，`Last-Event-ID` 使用同一序号；订阅先按游标回放，再由本地通知和一秒持久轮询追平跨实例事件。通知丢失或 Redis 全量丢失不影响正确性。

SSE 使用 256 个元素的有界缓冲，慢消费者溢出时只关闭该订阅，不阻塞 Worker；每十五秒发送不持久化的 Heartbeat。关闭 SSE 不调用 Cancel。超过内联阈值的 Provider Event Payload 由 Storage Starter 写入带 Hash、大小和媒体类型的 `ObjectRef`，API Envelope 与 `contracts/schemas/runtime-event/v1.json` 对齐，不序列化 AgentScope Event，也不暴露隐藏推理链。

## HITL、取消与用量

`approval.requested` Signal 与对应 Event 同事务转换为 Approval，只保存 Tool 名、Tool Call ID、参数摘要 Hash、策略引用和到期时间，不保存原始参数。列表和决策均校验 JWT 中精确 Organization/Project 租户选择及 `runtime:approve` 权限。决策使用 Expected Version 和 Idempotency Key；重复相同请求返回同一结果，重复键不同 Hash 冲突。Approval 可进入 APPROVED、REJECTED、EXPIRED 或 CANCELLED；全部决策完成后重新入队，并由新 Fencing Token Resume。

取消先持久化 `run.cancelled`、Run/Turn/Work Item 终态和 Outbox，再通知 Provider。Approval 的 APPROVED、REJECTED、EXPIRED、CANCELLED 状态变化同步追加不含 Tool 参数的 Runtime Event 与 Outbox；到期决策在可恢复 Checkpoint 保护下以拒绝结果重新入队，避免 Run 永久停留在 PAUSED。Timeout 复用稳定取消命令语义并形成明确终态。Provider 只读取 `ModelHttpException`/`HttpTransportException` 的结构化 HTTP 状态码识别 429，并把超时单独分类；响应体、异常消息和凭证不会写入稳定错误详情。`model.call.completed` 中经过 Provider 防腐层过滤的 Token 与 Duration 被追加到 `usage_record`，价格版本和最终成本留给后续治理阶段，不在 Runtime 伪造。

## API、安全与错误

Public OpenAPI 固定九个 Phase 13 路径，并使用 Bearer JWT、`Idempotency-Key`、标准 Problem Detail 和 Runtime Event Schema。权限分为 `runtime:execute`、`runtime:read`、`runtime:cancel`、`runtime:approve`；资源与 Token 租户不一致时返回不可枚举资源的 404。安全未启用时只开放脱敏健康/信息端点，全部 Runtime API 拒绝；启用时必须配置受信 Issuer/JWK/Audience。401/403 使用稳定错误码，响应不回显 Token、异常消息或租户细节。

真实 Worker 默认为关闭。只有显式设置 `agentark.runtime.worker-enabled=true`，且 Model Factory、Component Factory 和 Secret Resolver 均有生产 Bean 时才启动；缺少任一能力会启动失败，不静默使用不可执行 Engine。配置和故障处置见 [Runtime 运维 Runbook](../guides/runtime-operations.md)。

## 测试与验收范围

Phase 13 定向测试覆盖：双实例单 Owner 与陈旧 Token 拒绝、Redis Lease 丢失回调、SSE 回放/通知/断开不取消、Approval 幂等/到期/新 Token Resume、Checkpoint 孤儿恢复、不可恢复新 Attempt，以及接单后 Snapshot 准备失败仍保留 Run 和失败 Event。

2026-08-16 收官验收结果：三模块 Reactor `clean verify` 通过，其中 Runtime 20 个单元测试与 9 个 MySQL 集成测试、AgentScope Provider 32 个测试、Runtime Server 1 个启动测试均为零失败；Phase 13 聚焦测试 8 个、Runtime API E2E 2 个、Kernel 契约与中文文档门禁 7 个均通过。带 `-Xlint:deprecation` 的 Runtime 主代码与测试重新编译无过时 API 告警。

验收命令：

```bash
./mvnw -pl agentark-runtime,agentark-runtime-provider-agentscope,agentark-services/agentark-runtime-server -am clean verify

./mvnw -pl agentark-runtime \
  -Dtest='*Lease*,*Fencing*,*Sse*,*Recovery*,*Approval*' test

python3 tools/harness/knowledge_gate.py
python3 tools/harness/verify_upstreams.py --require-worktrees
git diff HEAD --check
```

Testcontainers 使用真实 MySQL 8.4 验证 V2 Migration、追加 Event、并发 Sequence、Fencing Trigger、State/Checkpoint 恢复和 MyBatis 事务。Redis 续租失败由原子 Port 行为测试覆盖；本阶段没有执行 Kubernetes Pod、真实 OIDC、真实厂商模型、网关断线或跨主机网络故障演练，这些边界不能从单元和容器测试外推为生产验收。

## 风险与后续边界

- Control Snapshot Cache 只允许使用已成功校验的不可变 ETag 条目；首次加载失败没有可降级内容，Run 会进入明确失败或后续重试流程。
- 进程内 Event 通知可丢失，跨实例延迟上限由一秒 MySQL 轮询决定；后续可替换为 Redis Pub/Sub，但不得改变持久 Event 真相源。
- Lease 丢失 Cancel 是尽力中断；外部 Provider 已接收的不可撤销副作用仍需依赖 Tool 幂等、风险策略和 Fencing 后置拒绝，不能宣称完全撤销。
- Runtime Server 已提供生产装配边界，但仓库仍没有真实 Model/MCP/Sandbox Provider Bean；默认 Worker 关闭是刻意的安全状态。
- Phase 13 没有实现 Gateway SSE 代理、跨区域容灾、价格结算、Dead Letter 运维 UI 或 Kubernetes PDB；分别属于 Phase 16、19、21、22。

## 回滚

- 本阶段没有新增数据库版本或数据迁移；源码、契约和文档可按当前 Git Diff 精确反向修改，不覆盖 Phase 11/12 既有变更。
- 回滚 Runtime API 时必须同时回滚 OpenAPI、AsyncAPI 和 Server Bean 装配，不能保留对外声明但移除实现。
- 若只关闭 Worker，设置 `AGENTARK_RUNTIME_WORKER_ENABLED=false` 并重启；已接单 Work Item 保留在 MySQL，可由恢复后的兼容 Worker 继续处理。
- 不得通过删除 Runtime Event、Checkpoint、Work Item、Outbox 或 Flyway History 回滚运行事实。
