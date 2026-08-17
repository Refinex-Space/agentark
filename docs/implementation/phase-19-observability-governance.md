---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: PLAN.md#phase-19--opentelemetryauditusagecostquota-与-evaluation-基线
---

# Phase 19：OpenTelemetry、Audit、Usage/Cost、Quota 与 Evaluation 基线

## 结论

Phase 19 建立了可运行的治理事实闭环，而不是只添加 Dashboard：四服务使用 Spring Boot OpenTelemetry/Micrometer 和 W3C Trace Context；Control V7 统一拥有 append-only Audit、Usage/Cost、Quota、Evaluation 与 Release Gate；Runtime V3 保留原始 Usage、治理投递状态和 Turn 级并发 Reservation 引用；Web `/observe` 只消费真实 Public API。

## 所有权与事务边界

| 能力 | 权威 Owner | 边界 |
|---|---|---|
| Trace/Metric | 各服务产生，外部 Backend 保存 | Backend 不可用不回滚业务；正文与 Secret 默认不采集 |
| Audit Ledger | Control `audit_event` | 只追加；Control 本地审计加入业务事务，跨平面以 `sourceEventId` 幂等汇聚 |
| Runtime Usage | Runtime `usage_record` | 先保留 Provider 原始/估算事实，再异步幂等汇聚到 Control |
| Usage/Cost 查询 | Control `usage_ledger/usage_aggregate` | 成本固定 `price_table_version_id`、币种和 `estimated` |
| Hard Quota | Control `quota_policy/quota_reservation` | Policy 行锁防并发超卖；Runtime 接单事务绑定 Reservation，终态提交后释放 |
| Evaluation | Control Dataset/Evaluator/Run/Score/Release Gate | Revision、Snapshot、Dataset Version、Evaluator Version 全部固定 |

没有新增第五个后端部署单元，没有跨 Schema SQL/Mapper/DataSource，也没有让 Runtime 读取 Control 表。

## Telemetry

稳定 Span 已覆盖：

```text
control.agent.publish
control.deployment.promote
runtime.turn.execute
runtime.agent.compile
agent.run
model.call
tool.call
mcp.call
knowledge.retrieve
sandbox.execute
scheduler.job.execute
```

`AgentArkTelemetry` 同时记录对应低基数 Duration Timer；Tag 白名单排除 Session、User、Project、Run 等无界标识。Spring Boot 注入的 `WebClient.Builder`/`RestClient.Builder` 负责 HTTP Observation 和 W3C 传播，避免静态 Builder 绕过 Trace Filter。Provider 信号持久化 Runtime Event 时优先使用当前有效 Trace ID。

四服务只暴露 `health,info,prometheus`，OTLP Export 默认关闭、队列和超时有界。Collector 再次删除 Authorization、Prompt、Document、Tool Arguments 与 Secret 属性，形成应用白名单后的第二层保护。

## Control Governance

Control V7 新增：

- `audit_event` 与 UPDATE/DELETE 拒绝 Trigger；
- 版本化 `price_table/price_table_version`；
- `usage_ledger/usage_aggregate`；
- `quota_policy/quota_reservation`；
- `evaluation_dataset/evaluation_dataset_version/evaluation_test_case`；
- `evaluator/evaluator_version/evaluation_run/evaluation_score`；
- `release_gate`。

Public API 严格执行 IAM 权限：`audit:read`、`usage:read`、`quota:read/manage`、`evaluation:read/manage`、`price:manage`。Internal API 只接受面向 Control 的 Service Identity，用于 Audit/Usage 汇聚和 Quota Reservation；明文 Secret、Prompt、文档和 Tool 参数不进入 Wire Schema。

确定性 Evaluator 对每个固定 Test Case 的期望 Hash 做 exact-match，按不可变权重计算总分，可与固定 Baseline Run 比较。HARD Release Gate 在 Deployment 创建/Promote 前拒绝缺少合格 Evaluation 的 Revision；Rollback 仍只移动已知 Revision 指针，不被 Gate 阻断。

## Runtime 与 Scheduler

Runtime V3 扩展 Usage 的 Organization/Project/Revision/Deployment/Session/Turn 关联、Model/Embedding/Tool/Sandbox 计量、估算标识、价格版本/币种/成本和治理投递状态。Worker 只有显式启用后才批量 Claim 并向 Control 汇聚，失败有界重试，Runtime MySQL 始终保留权威原始记录。

并发 Run Quota 在接单前向 Control 申请；允许结果与首次 Run 在同一 Runtime 事务绑定。成功、失败、超时或取消终态提交后释放，暂停/恢复和新 Attempt 沿用同一 Turn 级 Reservation。Control 行锁测试证明上限为一时两个并发申请只有一个成功。

Runtime HITL 决策与 Run Cancel、Scheduler Job Cancel 与 Dead Letter Redrive 均保留本地 Event/Outbox，并在提交后向 Control 幂等汇聚安全 Audit 投影。Control 暂时不可用不覆盖已提交本地终态。

## Web 与部署

`/observe` 提供治理概览、Trace/Audit、Usage/Cost、Price Table、Quota、Dataset、Evaluator、Evaluation Run 和 Release Gate 视图。所有数据来自生成式 Control Client；页面不使用生产 Mock，不回显 Secret，也不把 Generated Client 当 UI Domain。

`deploy/observability/` 固定本地 OTel Collector、Tempo、Prometheus 和 Grafana，含数据源、Dashboard 与告警规则。端口只绑定本机回环；Grafana 密码无默认值；部署方式和故障处理见 [Observability 运维](../guides/observability-operations.md)。

## 验证证据

阶段收官实际执行以下门禁：

```bash
./mvnw -T 1C clean verify
pnpm --dir agentark-web api:check
pnpm --dir agentark-web lint
pnpm --dir agentark-web typecheck
pnpm --dir agentark-web test
pnpm --dir agentark-web build
pnpm --dir agentark-web test:e2e
pnpm --dir agentark-web test:e2e:real
docker compose -f deploy/observability/docker-compose.yml config
python3 tools/harness/knowledge_gate.py
git diff HEAD --check
```

| 验证 | 实际结果 |
|---|---|
| `./mvnw -T 1C clean verify` | 20 个 Reactor 模块全部通过；总耗时 2 分 52 秒 |
| Control/Runtime/Scheduler MySQL Testcontainers | V7/V3/V2 空库、升级、中文 COMMENT、跨 Schema 权限全部通过 |
| Audit/Quota/Evaluation 定向测试 | Audit UPDATE/DELETE Trigger、额度为一的并发 Hard Quota、固定版本确定性评分、Runtime Reservation 终态释放全部通过 |
| `api:check/lint/typecheck/test/build` | 生成 Client 无漂移；7 个 Vitest 文件、12 个用例和生产构建通过 |
| `test:e2e` | Chromium 设计系统、键盘、可访问性与窄屏两项通过 |
| `test:e2e:real` | 临时 MySQL/Redis 与四服务真实主链路一项通过，耗时约 1.4 分钟 |
| Playwright CLI | `/design-system` 独立快照与截图通过，产物位于已忽略的 `output/playwright/` |
| Observability 配置 | Compose `config --quiet`、OTel Collector `validate`、Prometheus `promtool check config` 均通过，5 条告警规则有效 |
| Harness/Git | 56 份 Active 文档知识门禁、两个固定上游 Worktree、`git diff HEAD --check` 全部通过 |

真实 E2E 在收官过程中先后暴露并修复了事务/方法安全 Bean 的 `final` 代理冲突、最小组合根 HTTP Builder 缺失、Governance Mapper Tenant 插件误介入、注解 SQL XML 转义、Usage 幂等 SQL 歧义以及治理表单异步重置/窄屏溢出。最终通过结果来自修复后的完整重跑，不是忽略失败。

## 安全与剩余边界

- 本地 Observability Compose 不是生产清单；生产 HA、TLS、认证、存储保留和备份归 Phase 22。
- Trace 采样不能作为 Audit 或 Usage 证据；Audit/Usage 保持不采样的 MySQL 事实。
- Scheduler/Runtime 跨平面 Audit 先保留本地 Outbox，再做提交后汇聚；Control 故障期间需按 `sourceEventId` 运维重放。
- 云成本目录、真实 Provider 账单核对、LLM-as-judge 和在线预算中断只保留版本化扩展边界，不伪造成已接入能力。
- 浏览器验收使用确定性 E2E Provider；真实外部模型、生产 Collector 和告警通知渠道仍需部署环境验收。
