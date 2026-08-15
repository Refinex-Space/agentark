---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: docs/README.md#上游迁移审计
---

# Aistio → AgentArk Java Control Plane 绞杀计划（初稿）

## 1. 目标与非目标

目标是在不让 Runtime 停摆、不跨库、不双写同一聚合的前提下，把 Aistio 产品控制面逐 Cohort 替换为 `agentark-control`/`agentark-control-server`，同时把 Aistio Runtime Store 语义迁入中立 `agentark-runtime`。

非目标：

- 一次性翻译 311 个 Go 文件；
- 复制 Go DDL 为 MySQL DDL；
- 让 Java Control 直接读 Aistio `cp/rt` 表；
- 用前端切换掩盖后端 Owner 不清；
- 在 v1 把 Kubernetes CRD 变成 AgentArk 产品资产权威。

## 2. 当前职责分解

```text
aistiod
├── Product Control (internal/product)
│   ├── User/Auth/Admin
│   ├── Agent/Version/Workspace/Marketplace/File
│   ├── Environment/Memory/Vault
│   ├── Session lifecycle/resolve
│   └── Deployment/Webhook/Channel
├── Hosted Runtime Control (internal/httpapi/sessionops/store)
│   ├── Session/Turn/Event/Command
│   ├── Data Plane Registry
│   └── Team/Task/Message
└── Kubernetes Control (api/controller/discovery/asdp)
    ├── Agent/AgentTeam/ModelConfig/MCPServer/SandboxClaim CRD
    └── BYO Adapter/ASDP
```

目标 Owner：

| 语义 | 最终 Owner | 数据 Owner |
|---|---|---|
| IAM、Agent 资产、Revision/Snapshot、Environment、Deployment、Secret Metadata | `agentark-control` | Control Schema |
| Session、Turn、Run、Event、Approval、Checkpoint、Lease、Work Queue | `agentark-runtime` | Runtime Schema |
| Trigger、Job、Attempt、Delivery、Channel Runtime | `agentark-scheduling` | Scheduler Schema |
| BYO/A2A/AG-UI/Team Compatibility | 对应 Adapter/Contract | 不获得跨域写表权 |

## 3. 绞杀边界

Gateway 是唯一流量切换点，Internal Contract 是唯一跨语言边界：

```text
Browser/SDK
  -> AgentArk Gateway
     -> Aistio Cohort (尚未迁移)
     -> Java Control Cohort (已迁移)
     -> Java Runtime/Scheduler (独立 Owner)
```

每个 Cohort 必须满足：

1. 先冻结语言中立 OpenAPI/JSON Schema；
2. Java 端实现、契约测试、数据迁移/回填和 Read Shadow；
3. Gateway 按 Route Cohort 切读写，不按单个请求随机拆分；
4. 切换后 Aistio 对该聚合只读或禁用 Route；
5. 观察窗口通过后删除旧写路径；
6. 保留按 Cohort 回切 Route 的回滚开关。

禁止 Java/Aistio 同时双写同一业务表。确需跨 Owner 传播时使用 Outbox/Event，消费者幂等落自己的表。

## 4. Cohort 顺序

| Cohort | Aistio 来源 | Java 目标 | 前置 Phase | 切换 Gate |
|---:|---|---|---|---|
| C0 | Health/Version/Capabilities | Foundation/Servers | P02–05 | 四服务启动、契约版本、可观测性 |
| C1 | Auth/User/Admin | Control IAM | P07 | OIDC/JWK、本地 Profile、RBAC、审计、密码/Token 不泄露 |
| C2 | Workspace/Marketplace/File | Control Assets | P08–09 | Path/ACL/Version/Object Store、导入导出 |
| C3 | Agent/Version/Share | Control Catalog/Release | P08–10 | Draft→Revision→Snapshot、内容 Hash、ACL、回滚 |
| C4 | Environment/Memory/Vault | Control Profiles/Secret | P08–10 | SecretRef、加密、挂载 Snapshot、审计 |
| C5 | Deployment | Control Release | P10 | Deployment 状态机、Internal Snapshot resolve |
| C6 | Session lifecycle/resolve | Runtime + Control Internal Contract | P10–13 | Session Owner、Snapshot Pin、幂等创建、状态机、恢复 |
| C7 | Event/Turn/Command/Data Plane Registry | Runtime | P11–13 | Lease/Fencing、SSE Resume、HITL、Checkpoint、跨副本 |
| C8 | Webhook/Channel/Cron fire | Scheduling | P15 | Trigger/Job/Attempt/Delivery/Dead Letter、Outbox |
| C9 | Team/Task/Message | Collaboration Compatibility | P21 | ACL、Task/Plan、Wake、Lifecycle、恢复 Contract |
| C10 | CRD/ASDP/BYO | Deployment/Compatibility | P21–22 | 明确产品需求、K8s/BYO Contract、HA/升级策略 |

C6–C8 不允许为赶进度合并成一次切换；Session Owner、Runtime Event 和 Scheduler Job 的失败模式不同。

## 5. Route 归属

| Route Family | 过渡期 Owner | 最终 Owner |
|---|---|---|
| `/api/auth/**`、`/api/user/**`、`/api/admin/**` | Aistio → C1 | Java Control |
| `/api/agents/**`、`/api/workspaces/**`、`/api/marketplaces/**`、`/api/files/**` | Aistio → C2/C3 | Java Control |
| `/api/environments/**`、`/api/memory-stores/**`、`/api/vaults/**` | Aistio → C4 | Java Control |
| `/api/deployments/**` | Aistio → C5；Fire 分离到 Scheduler | Java Control + Scheduler |
| `/api/sessions` 生命周期 | Aistio → C6 | Java Runtime Public API；Control 只提供 Snapshot Resolve |
| `/api/sessions/*/events`、SSE/HITL | Java Dataplane baseline → C7 | Java Runtime |
| `/api/channels/**` | Aistio 配置 + Java Scheduler Runtime → C8 | Control 配置 + Scheduler 执行 |
| `/api/internal/**` | 版本化 Cohort Contract | 对应 Owner 的 Internal API |
| `/api/v1/teams/**`、CRD/BYO | Aistio → C9/C10 | Compatibility Adapter |

## 6. 数据迁移原则

### 6.1 映射而非表翻译

Aistio Product DDL、Runtime PostgreSQL Migration 和 Java JPA `builder_*` 表只是三套来源模型。AgentArk 迁移按领域映射到 `control-schema.md`、`runtime-schema.md`、`scheduler-schema.md`，不得做表名一一翻译。

### 6.2 数据迁移步骤

每个 Cohort 采用相同序列：

1. 生成 Source Count、主键范围、空值/重复/外键异常报告；
2. 以可重跑 Batch 写入 AgentArk Schema，记录 source id、source version、checksum；
3. 执行 Count/Hash/抽样语义对账；
4. Read Shadow 比较 Aistio 与 Java DTO，差异可观测但不影响用户；
5. 短写冻结或 Outbox Drain，执行增量回填；
6. Gateway 切 Cohort，Aistio Route 只读/关闭；
7. 观察期结束后归档旧表，不立即删除。

涉及 Vault Credential 时不导出明文。应在受控进程内解密后立即用 AgentArk KMS/Envelope Key 重加密，记录数量和失败，不记录值。

## 7. Internal Contract

第一版必须至少覆盖：

- `ResolveAgentRevision(snapshotId/revisionId)`；
- `Create/ResolveSession` 的 tenant、deployment、snapshot、environment 和 idempotency；
- Runtime 状态更新的 expected version/fencing；
- Scheduler `FireTrigger` 到 Runtime `StartRun` 的 idempotency；
- Channel config revision 与 runtime status report；
- Vault/Memory/Knowledge Mount 只返回受限引用或短期凭据，不返回产品表结构。

契约必须版本化、兼容测试、明确错误码、超时、重试和幂等；不得暴露 AgentScope、JPA、Go Store 或数据库列类型。

## 8. Read Shadow 与切换指标

每个 Cohort 至少观察：

- Request 成功率、p50/p95/p99、错误类型差异；
- DTO 归一化后的字段差异率；
- 权限决定差异率（任何放宽都是阻断）；
- Session/Run/Event Count 与 terminal 状态差异；
- Duplicate Job/Delivery、Lease takeover、SSE reconnect gap；
- 审计事件完整率与 Secret Redaction。

Go/No-Go 条件必须是量化阈值并写进对应阶段报告；本初稿不虚构尚未压测的数值。

## 9. 回滚

回滚单位是 Cohort：

1. Gateway Route 回切 Aistio；
2. 停止 Java Cohort 的新写入，不回滚其他已稳定 Cohort；
3. 保留 AgentArk 已写数据和 Outbox，标记切换窗口；
4. 对增量数据执行预先验证过的逆向同步或人工补偿；
5. Runtime/Session Cohort 只允许在没有双 Owner 的前提下回切，进行中的 Run 必须 Drain 或固定在原 Owner 完成。

不使用数据库 Schema 回滚作为首选；Flyway Migration 向前修复，旧列/表在观察期保留。

## 10. 明确风险

- Aistio Product 与 Runtime Store 都有 `sessions`，Java JPA 还有 `builder_session`；必须先定义唯一 Session Owner；
- Aistio Team 状态通过 Hook 与 Product Session 状态同步，切 C6 时可能影响 Team Member Activity；
- Vault Key 可回退到 JWT Secret 派生，迁移必须区分真正 Master Key 与回退数据；
- Cron `lastRunAt`、Fire Lease 和新 Scheduler Job 之间可能重复点火，C8 需业务幂等键；
- BYO/CRD/ASDP 与 Hosted Product 共享一个进程但不是同一领域，不能因进程边界一起迁；
- 固定 Aistio 使用 Go `1.26.0`，本机 Phase 00 未把 Go 列为目标工具链，后续实际运行测试前需补 Toolchain Evidence。

## 11. 本初稿完成度

本文件已固定职责、Cohort、Route、数据迁移、契约、切换和回滚原则，足以指导 P07–P22 的详细计划。实际表字段映射、迁移 Batch、阈值和生产窗口应在对应 Cohort Phase 以真实实现和测试补充，不在 Phase 01 预造。
