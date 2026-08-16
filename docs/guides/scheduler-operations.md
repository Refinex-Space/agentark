---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: docs/README.md
---

# Scheduler 运维手册

## 适用范围

本文覆盖 `agentark-scheduler-server` 的 Durable Job、Cron、Webhook、Channel、Knowledge Ingestion、Runtime Turn、Retry 和 Dead Letter。Scheduler 只写 `agentark_scheduler`，不连接 Control/Runtime Schema，也不执行 Agent 推理循环。

## 启用前检查

默认 `agentark.scheduler.worker-enabled=false`。启用前必须同时满足：

1. Scheduler MySQL Flyway 已到 V2，九张业务表和 `flyway_schema_history` 均在 `agentark_scheduler`；
2. `AGENTARK_SECURITY_ENABLED=true`，Issuer/JWK/Audience 已配置；
3. `AGENTARK_SCHEDULER_INTERNAL_TOKEN` 是短期服务身份 Token，调用 Runtime 时 Audience 包含 `agentark-runtime`，调用 Control 时使用对方要求的 Audience；
4. 每个启用的 Job Type 都有真实 Handler Provider；缺少 Handler 的类型不会被当前进程 Claim；
5. Knowledge 摄取必须具备恶意文件扫描、受限 Parser、Object Store、Embedding 和 Qdrant Provider；任何一项缺失都不得装配 `KnowledgeIngestionWorker`；
6. Outbound Webhook 必须装配拒绝私网、回环、链路本地、UserInfo 和非 HTTPS 的 `OutboundEndpointResolver`；
7. 多副本使用互不相同且稳定的 `AGENTARK_SCHEDULER_INSTANCE_KEY`，时钟通过宿主 NTP 保持同步。

本地只验证结构或管理 API 时保持 Worker 关闭，不能用 Fake Provider 冒充生产就绪。

## Trigger 登记

Trigger 只通过带 `agentark-scheduler` Audience 的服务身份调用 `/internal/v1/scheduler/triggers` 登记。CRON 必须提供 Spring 六段表达式和 IANA 时区且不提供 SecretRef；WEBHOOK 必须提供 `secret://<scope>/<name>` 且不提供 Cron 字段。同一租户和 Key 的相同定义幂等复用，不同定义返回冲突。

`config` 是目标 Job Payload 的非敏感字符串字段，最多 32 项，单值最多 16 KiB。禁止把 Secret、Token、Password、Credential、API Key 或签名 URL 放入配置；Cron 点火只会补充 `_triggerId`、`_triggerScheduledAt` 和 `_triggerContract`。登记前必须用目标 Handler 的版本化 Payload Contract 校验配置字段，否则 Job 会被接收但在 Handler 解析阶段失败并进入 Retry/Dead Letter。

## 关键配置

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `AGENTARK_CONTROL_BASE_URL` | `http://localhost:8081` | 仅调用版本化 Control Internal API；生产使用受控网络和 TLS |
| `AGENTARK_RUNTIME_BASE_URL` | `http://localhost:8082` | 仅调用 Runtime Internal API；禁止连接 Runtime DB |
| `AGENTARK_SCHEDULER_INTERNAL_TOKEN` | 空 | 空值不会回退到共享 Secret；调用内部 API 会明确失败 |
| `AGENTARK_SCHEDULER_INSTANCE_KEY` | `agentark-scheduler-local` | 多副本 Owner 标识，必须唯一且稳定 |
| `AGENTARK_SCHEDULER_LEASE_TTL` | `30s` | Job Lease；每约三分之一周期续租 |
| `AGENTARK_SCHEDULER_WORKER_ENABLED` | `false` | 显式启用常驻 Worker 与 Cron 扫描 |
| `AGENTARK_SCHEDULER_WORKER_POLL_DELAY` | `1s` | 无任务时轮询间隔 |
| `AGENTARK_SCHEDULER_CRON_SCAN_DELAY` | `30s` | 到期 Cursor 扫描间隔 |
| `AGENTARK_SCHEDULER_WORKER_POOL_SIZE` | `2` | 每个 Job Type 的隔离 Worker 数，范围 1–64 |

## 状态诊断

先检查服务和数据库归属：

```bash
curl -fsS http://localhost:8083/actuator/health
./tools/verify-core.sh
```

管理 API 只返回状态，不返回 Job Payload、Provider 正文或 Credential。查询 Job 和 Dead Letter 时必须使用具有精确 Organization/Project 选择的 JWT：

```bash
curl --fail --silent --show-error \
  -H "Authorization: Bearer ${AGENTARK_OPERATOR_TOKEN}" \
  "http://localhost:8083/api/v1/scheduler/jobs/${JOB_ID}?organizationId=${ORGANIZATION_ID}&projectId=${PROJECT_ID}"
```

生产排障优先观察以下低基数指标：

- `agentark.scheduler.queue.depth{job.type=...}`：已到期的 READY/RETRY_WAIT 数；
- `agentark.scheduler.queue.oldest.age.seconds{job.type=...}`：最老到期任务等待秒数；
- MySQL 中 `CLAIMED` 且 `claim_until < UTC_TIMESTAMP(6)`：可由新 Owner 接管的过期任务；
- `dead_letter.status=OPEN`：预算耗尽且需要人工判断的任务。

不得通过手工更新 `job`、`job_attempt`、`job_lease` 或 `dead_letter` 修复状态；这会绕过 Fencing、审计与 Outbox。

## Retry、超时与 Dead Letter

Retry Policy 在 Job 创建时固定。退避为指数增长并施加有界 Jitter，达到 `maxBackoff` 后不再增长。只有 `INHERENT` 或 `PROVIDER_KEY` Handler 可以自动重试；`NONE` 写操作即使返回暂态错误也直接进入 Dead Letter。

单次 Handler 超时形成 `TIMED_OUT` Attempt；幂等能力和剩余预算允许时进入 `RETRY_WAIT`，否则形成 `OPEN` Dead Letter。Lease 过期被接管时，旧 Attempt 标记为 `ABANDONED`，新 Attempt 获取更大的 Fencing Token；旧 Worker 的终态或 Delivery 更新会被数据库条件拒绝。

## Redrive

Redrive 是高风险写操作，要求 `scheduler:redrive`、精确租户选择和非空人工原因。它保留原 Job/Attempt 历史，只把原 Job 从 `DEAD_LETTERED` 恢复为 `READY`，并将当前 Dead Letter 标记为 `REDRIVEN`、递增计数、写审计和 Outbox。

```bash
curl --fail --silent --show-error -X POST \
  -H "Authorization: Bearer ${AGENTARK_OPERATOR_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"reason":"已修复下游配置并确认请求可幂等重放"}' \
  "http://localhost:8083/api/v1/scheduler/jobs/${JOB_ID}:redrive?organizationId=${ORGANIZATION_ID}&projectId=${PROJECT_ID}"
```

未确认下游副作用幂等性、凭据轮换状态或原始 Payload 合法性时禁止 Redrive。

## Cron 与 DST

Cron 使用 Spring 六段表达式和显式 IANA 时区。计算与执行分离：推进 `trigger_cursor`、插入 Job 和接单 Outbox 在同一 Scheduler 事务内完成，Handler 由后续 Claim 执行。业务键包含 Trigger 和计划时间，同一计划点重复扫描不会产生不同副作用。

DST 缺口中的不存在本地时间由时区规则跳到下一合法匹配点；DST 重叠时必须通过固定版本测试确认实际点火序列。修改表达式或时区前先评估 Misfire 和重复业务键，不直接改 Cursor。

## Webhook 与外发投递

入站签名格式为 `v1=HMAC_SHA256(timestamp + "." + nonce + "." + body)`。允许时钟偏差五分钟，Nonce 为 16–128 位安全字符，正文最多 1 MiB。Nonce、请求 Hash、Job 和 Outbox 同事务落库；重复 Nonce 返回冲突，不再创建 Job。Secret 只通过 `SecretRef` 临时解析并立即清零。

外发 Webhook 禁止重定向、非 HTTPS、UserInfo 和任意 Credential Header；响应采用流式有界读取，只保存 HTTP 状态和最多 4097 字节的计数摘要，不保存正文。DNS/IP 复核由生产 `OutboundEndpointResolver` 负责，部署时仍需出口网络策略防止 DNS Rebinding。

## 故障与恢复

- Scheduler 进程中断：等待 Lease 到期，新实例 Claim 后把旧 Attempt 标记为 `ABANDONED`；不要复用旧 Token。
- Control 暂时不可用：Knowledge 结果或配置读取按 Handler 分类进入 Retry/Dead Letter；Scheduler 不写 Control DB。
- Runtime 暂时不可用：仅 429、5xx 和网络暂态错误可在幂等预算内重试；4xx 不自动重试。
- MySQL 不可用：停止接单和新外部调用，不允许把 Redis 或内存队列当事实源。
- 时钟漂移：停止 Cron/Webhook Worker，修复 NTP 后核对 Cursor 和签名窗口，再恢复服务。

## 回滚

代码回滚先把 `AGENTARK_SCHEDULER_WORKER_ENABLED=false`，等待当前 Lease 到期并确认没有 `CLAIMED` Job，再回退应用制品。Flyway V2 已应用后禁止删除表、修改历史或执行 `clean`；旧版本应用不认识 V2 时只能保持停止，并通过新的前向迁移修正问题。Dead Letter、Attempt、Delivery 和 Outbox 是审计事实，不随应用回滚删除。
