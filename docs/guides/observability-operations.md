---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: docs/README.md
---

# Observability 与 Governance 运维

## 边界

Phase 19 的本地观测栈只用于开发和验收：OpenTelemetry Collector 接收四服务 OTLP Trace，Tempo 保存短期 Trace，Prometheus 拉取低基数 Metric，Grafana加载仓库托管 Dashboard。它不保存 Runtime Event、Audit Event、Prompt、文档或 Secret，也不是 Phase 22 的生产部署清单。

三类事实必须分开排查：

| 事实 | Owner | 故障时是否影响业务 |
|---|---|---|
| Trace/Metric/Structured Log | OTel/Prometheus/日志后端 | 不应；Exporter 使用有界队列并允许丢弃 Telemetry |
| Runtime Event/Usage 原始记录 | Runtime MySQL/Object Storage | 必须持久；不能因 Collector 不可用丢失 |
| Audit/Usage/Quota/Evaluation | Control MySQL | 必须持久；严格租户授权后查询 |

## 本地启动

复制示例并生成本地强密码，文件不得提交：

```bash
cp deploy/observability/.env.example deploy/observability/.env
openssl rand -hex 32
```

把生成值写入 `.env` 的 `AGENTARK_GRAFANA_ADMIN_PASSWORD`，然后先验证配置：

```bash
docker compose \
  --env-file deploy/observability/.env \
  -f deploy/observability/docker-compose.yml \
  config
```

启动观测栈：

```bash
docker compose \
  --env-file deploy/observability/.env \
  -f deploy/observability/docker-compose.yml \
  up -d
```

服务端显式开启 Trace Export，并把 Collector 地址指向本机 `4318`。四服务仍从各自 `application.yml` 使用 W3C Trace Context；不要配置 B3 与 W3C 双写。

```text
AGENTARK_OTEL_EXPORT_ENABLED=true
AGENTARK_OTEL_TRACES_ENDPOINT=http://localhost:4318/v1/traces
AGENTARK_OTEL_SAMPLING_PROBABILITY=0.1
```

本地入口：Grafana `http://127.0.0.1:3001`、Prometheus `http://127.0.0.1:9090`、Tempo API `http://127.0.0.1:3200`。这些端口只绑定回环地址。

## 验证

```bash
curl -fsS http://127.0.0.1:8080/actuator/prometheus >/dev/null
curl -fsS http://127.0.0.1:8081/actuator/prometheus >/dev/null
curl -fsS http://127.0.0.1:8082/actuator/prometheus >/dev/null
curl -fsS http://127.0.0.1:8083/actuator/prometheus >/dev/null
curl -fsS http://127.0.0.1:9090/-/ready
curl -fsS http://127.0.0.1:3200/ready
```

Prometheus Target 必须显示四服务和 Collector；Metric Label 不得出现 `sessionId`、`userId`、`projectId`、`runId` 等无界值。Trace 中检查 Gateway、Control/Runtime 和 Model/Tool/RAG 子 Span 使用同一 Trace ID；Runtime Event 在有效执行 Span 内保存同一 Trace ID。

治理 Web 入口为 `/observe`。它只调用 Control Public API，展示 Audit、Usage/Cost、Quota、Evaluation 和 Trace Link；浏览器不访问 Tempo Internal API，也不缓存完整敏感事件。

## 故障处理

- Collector/Tempo 不可用：业务应继续；检查四服务 Exporter 丢弃计数、Collector 内存限制和 `AGENTARK_OTEL_TRACES_ENDPOINT`。禁止把 Export 改成同步阻塞业务。
- Prometheus 抓取失败：先检查管理端口网络策略和 `/actuator/prometheus`，不要临时暴露 `env`、`beans` 或完整 Health Detail。
- Runtime Usage 长期 `PENDING`：确认 `AGENTARK_RUNTIME_USAGE_GOVERNANCE_ENABLED=true`、Control Internal Service Identity 有效，以及 Runtime 账号仍只访问 Runtime Schema。
- Hard Quota 拒绝异常：检查 Control `quota_policy/quota_reservation` 与 Runtime `run.quota_reservation_ref`。终态释放失败由 Reservation TTL 回收，不能手工删除正在 `HELD` 的有效记录。
- Scheduler/Runtime Audit 汇聚失败：本地 Event/Outbox 仍是重放证据；先恢复 Control Internal API，再依据 `sourceEventId` 幂等补投，不能改写既有 `audit_event`。
- Grafana 登录失败：只轮换本地 `.env` 与对应本地 Grafana 数据卷；不要把密码写入 Compose、README 或命令历史。

## 停止与回滚

```bash
docker compose \
  --env-file deploy/observability/.env \
  -f deploy/observability/docker-compose.yml \
  down
```

保留卷时 Trace、Metric 与 Dashboard 本地状态可恢复；执行 `down -v` 会删除本地观测数据，必须由使用者明确授权。应用侧回滚只需关闭 `AGENTARK_OTEL_EXPORT_ENABLED` 和 Runtime Usage Worker；不得回滚或删除已经执行的 Flyway V7/V3，应以前向迁移修正。
