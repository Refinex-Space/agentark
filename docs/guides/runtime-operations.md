---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: docs/README.md
---

# Runtime 运维 Runbook

## 适用范围

本文处理 Runtime Server 的接单、Worker、Lease、SSE、HITL 和恢复。MySQL 是 Session、Run、Work、Event、Approval、State、Checkpoint、Usage、Idempotency 与 Outbox 的权威存储；Redis 只负责快速互斥，Local/S3-compatible Object Store 保存大 Event 或状态载荷。禁止通过清表、改 Fencing Token、重写 Event 或关闭 Flyway处理故障。

## 启动前检查

Runtime 默认端口为 `8082`，默认不启动 Worker。非测试环境至少确认：

```bash
test -n "$AGENTARK_RUNTIME_DB_URL"
test -n "$AGENTARK_RUNTIME_DB_USERNAME"
test -n "$AGENTARK_RUNTIME_DB_PASSWORD"
test -n "$AGENTARK_CONTROL_BASE_URL"
test -n "$AGENTARK_RUNTIME_INSTANCE_KEY"

./mvnw -pl agentark-services/agentark-runtime-server -am verify
curl -fsS http://127.0.0.1:8082/actuator/health
```

不要打印上述变量。生产启用 Public API 时还必须配置 Security Starter 的 HTTPS Issuer/JWK 和 Audience；启用 Worker 前必须提供真实 AgentScope Model Factory、Component Factory 和 Secret Resolver。缺失能力时应保持 Worker 关闭或让启动失败，不能替换为 Fake/Unavailable Engine。

## 关键配置

| 配置 | 默认值 | 说明 |
|---|---:|---|
| `AGENTARK_RUNTIME_WORKER_ENABLED` | `false` | 是否启动持久队列 Worker |
| `AGENTARK_RUNTIME_INSTANCE_KEY` | `runtime-local-1` | 多副本必须使用唯一且稳定的 Pod/实例标识 |
| `AGENTARK_RUNTIME_LEASE_TTL` | `30s` | Redis 与 MySQL 执行 Lease TTL |
| `AGENTARK_RUNTIME_WORKER_POLL_DELAY` | `250ms` | 空队列轮询间隔 |
| `AGENTARK_RUNTIME_HEARTBEAT_DELAY` | `10s` | Runtime Instance 心跳间隔 |
| `AGENTARK_CONTROL_BASE_URL` | `http://localhost:8081` | Control Internal API 基础地址；生产必须使用受控网络和 TLS |
| `AGENTARK_RUNTIME_INTERNAL_TOKEN` | 无 | 调用 Control Internal API 的短期服务 Token，不得写入文件或日志 |
| `AGENTARK_REDIS_HOST` / `AGENTARK_REDIS_PORT` | `localhost` / `6379` | Redis 协调地址 |
| `AGENTARK_RUNTIME_OBJECT_ROOT` | `.agentark/data/runtime-objects` | Local Profile 大载荷目录；生产应替换为受支持对象存储 |

## 接单与状态诊断

HTTP `202` 表示 Turn、Run、Work Item、幂等记录、`run.accepted` Event 和 Outbox 已在 Runtime MySQL 提交，不表示 Provider 已完成 Snapshot 加载或执行。发生故障时先保留 `runId`，依次检查：

1. `GET /api/v1/runtime/runs/{runId}` 的 Run 状态和稳定错误码；
2. `GET /api/v1/runtime/runs/{runId}/events?after=0` 是否包含 `run.accepted`、`run.started`、`run.failed` 或 `run.abandoned`；
3. Work Item 是否 READY、CLAIMED、COMPLETED、FAILED 或 CANCELLED；
4. Runtime Instance 是否 ACTIVE，心跳是否晚于 Lease TTL；
5. Control Snapshot Internal API 是否可用，当前 ETag Cache 是否已有该不可变 Revision；
6. ObjectRef 指向的 Authority、Hash、大小和媒体类型是否与 Runtime 记录一致。

禁止手工把 FAILED 改回 RUNNING。需要重试时使用正式恢复/重试命令，使系统生成新 Fencing Token；无完整 Checkpoint 时必须形成新 Run Attempt。

## Lease 与孤儿恢复

同一 Run 只有当前 MySQL Fencing Token 可以写 Event、State、Checkpoint 或终态。Redis Lease 丢失会触发 Provider Cancel，但外部调用可能已经发生；排障时不得据此假定外部副作用已撤销。

识别孤儿的原则是：Work Item 的 `claim_until` 已过期、原 Runtime Instance 心跳过期且没有新 Owner。新实例 Claim 后：

- 有已提交、可恢复 Checkpoint：同一 Run 以新 Token RECOVER；
- 无完整 Checkpoint：旧 Run 进入 ABANDONED，同一 Turn 创建新 Attempt；
- 旧 Worker 后续返回：数据库条件更新或 Trigger 拒绝其陈旧 Token。

Redis 全量丢失不需要修改 MySQL。恢复 Redis 后让 Worker 正常竞争即可；不得从 Redis 反写 Runtime 状态。

## SSE 与慢消费者

SSE 客户端必须保存最后一个已收到的数字 `id`，重连时原样发送 `Last-Event-ID`。该值是 Session Sequence，不是 Run Sequence、Event UUID 或进程内计数。Heartbeat 只是保活注释，不写 Event Store，也不能推进游标。

慢消费者超过 256 个未消费 Event 时订阅会以背压错误关闭；客户端应从最后持久 `id` 重连。SSE 连接关闭不会取消 Run，取消必须显式调用 `POST /api/v1/runtime/runs/{runId}:cancel`。跨实例通知丢失时一秒 MySQL 轮询会追平；若持续延迟，优先检查数据库连接池、慢查询指标和 Event Sequence 锁竞争。

## HITL

审批人需要 `runtime:approve`，且 JWT 必须选择与 Approval 完全一致的 Organization/Project。决策请求必须携带 `Idempotency-Key` 和当前 `expectedVersion`：

- 同 Key/同 Hash 重放返回原结果；
- 同 Key/不同 Hash 返回冲突；
- 到期决策会先持久化 EXPIRED；
- 全部 Approval 已决且 Checkpoint 完整时，Work Item 重新入队并以新 Token Resume；
- 取消 Run 会取消仍待决的 Approval。

参数正文不会出现在 Approval；审核依据是 Tool 名、动作、参数摘要和 Hash。需要原始敏感参数的工作流必须另行设计受控查看能力，不能扩展 Event/日志泄露正文。

## 排空与停止

正常关闭使用进程编排器发送终止信号。Runtime 先在进程内切换 DRAINING，停止领取新 Work Item，再更新数据库 DRAINING/DRAINED。SSE 客户端可重连其他副本；正在执行的 Run 由 Lease、Checkpoint 和孤儿恢复接管。

不要直接 Kill 多个副本后立即清理 Work Item。紧急停止后至少等待原 Lease TTL，再由健康实例执行 Reconciliation。若外部 Tool 不具备幂等性，恢复前必须人工核对其副作用。

## 验证与回滚

```bash
./mvnw -pl agentark-runtime \
  -Dtest='*Lease*,*Fencing*,*Sse*,*Recovery*,*Approval*' test

./mvnw \
  -pl agentark-runtime,agentark-runtime-provider-agentscope,agentark-services/agentark-runtime-server \
  -am clean verify

git diff HEAD --check
```

本阶段无新 Flyway，回滚不删除数据。快速停止领取新工作只需将 `AGENTARK_RUNTIME_WORKER_ENABLED=false` 后滚动重启；已持久 Work Item 保留。代码回滚必须保持 Runtime Event Schema、OpenAPI、Server Endpoint 和 Provider 接口版本一致。若回滚版本不支持已发布 Snapshot Schema 或 Compiler Version，禁止启动 Worker。
