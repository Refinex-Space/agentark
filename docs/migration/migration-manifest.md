---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: docs/README.md#上游迁移审计
---

# Phase 01 迁移清单

## 1. 分类规则

本清单的分类是后续 Phase 的入口门禁，不等于已授权复制：

- `REUSE`：可候选机械迁入的纯实现；仍须许可、来源头、行为测试和目标包审查；
- `ADAPT`：保留行为，按 AgentArk 架构和契约重写；
- `REFERENCE`：只学习能力/API/交互或以依赖使用，不复制实现；
- `REJECT`：明确禁止进入目标实现；
- `DEFER`：有价值但不进入当前阶段，必须满足触发条件再评估。

AgentScope Framework 的“直接依赖”在分类列记为 `REFERENCE`，Disposition 明确写 `DEPENDENCY`。任何 `REUSE` 都受 [许可门禁](license-and-notice.md) 约束；固定 AgentScope Commit 缺根 LICENSE/NOTICE，未补齐前不得复制源码。

## 2. Service 迁移矩阵

| ID | 候选源路径 | 分类 | 目标模块/Phase | Disposition 与行为门禁 |
|---|---|---|---|---|
| SVC-COM-01 | `service-common/web/api/error` | ADAPT | `agentark-kernel` + `agentark-starter-web` / P03–04 | P04 已按 RFC 9457 重写 HTTP 映射并保留稳定错误码；未知异常不回显原消息；覆盖 [ERR-01](behavior-baseline.md#err-01-错误映射) |
| SVC-COM-02 | `service-common/web/auth` | ADAPT | `agentark-starter-security` + `agentark-control` / P04、P07 | P04 已替换为 HTTPS Issuer/JWK、Audience、服务身份和严格 JWT Principal 转换；User/Role/Membership 生命周期仍归 P07 |
| SVC-COM-03 | `service-common/runtime/config` | ADAPT | `agentark-control`、`agentark-scheduling` / P08、P15 | 按资产 Owner 拆分，不保留共享配置对象 |
| SVC-COM-04 | `service-common/web/catalog*` | ADAPT | `agentark-control` + Provider / P08、P10、P12 | Spec 语义进入不可变 Snapshot；Codec 留在防腐层 |
| SVC-COM-05 | `service-common/web/coord` | ADAPT | Redis Starter + `agentark-runtime` + `agentark-scheduling` / P04、P11、P13、P15 | P04 已提供原子 Lease/Fencing/Idempotency/Rate Limit 基础；HITL、Queue、Cron 和持久事实仍按 Owner 留给后续阶段 |
| SVC-COM-06 | `service-common/web/managed*` | ADAPT | Control/Runtime Contract Adapter / P09–13 | DTO 按契约重建，禁止 shared DTO 包 |
| SVC-COM-07 | `service-common/web/persistence/jpa` | REJECT | 无 | 不迁入 JPA Entity、Repository、`ddl-auto=update` 或共享表 |
| SVC-COM-08 | `service-common/web/share` | ADAPT | `agentark-control` / P07–10 | 资源级 ACL，不能只校验 Owner 字符串 |
| SVC-COM-09 | `service-common/web/workspace` | ADAPT | Control + Runtime Mount Port / P08、P12 | 分离资产路径和执行挂载 |
| SVC-COM-10 | 整个 `service-common` | REJECT | 无 | 禁止整体复制和创建 `agentark-common` |
| SVC-GW-01 | `service-gateway/application.yml` Route 表 | ADAPT | `agentark-gateway-server` / P16 | 保留四平面路由与 Internal 拒绝；覆盖 GW-01/GW-02 |
| SVC-GW-02 | Gateway Header 清洗 | ADAPT | `agentark-gateway-server` / P16 | 外部不能注入服务身份；扩展 OIDC/CORS/Rate Limit |
| SVC-GW-03 | Gateway App 启动类 | REJECT | 无 | 薄启动类不值得复制 |
| SVC-DP-01 | `DataSessionApiController` | ADAPT | `agentark-runtime` Adapter / P13 | 保留 Event/SSE/HITL 语义，改为版本化契约、SSE id/重连 |
| SVC-DP-02 | `DataSessionService` | ADAPT | `agentark-runtime` / P11、P13 | Session Owner 与 Runtime 状态机重建；禁止跨平面查询表 |
| SVC-DP-03 | `SessionTurnRunner` | ADAPT | Runtime + Provider / P13 | 先 Lease 后 Admission、取消/释放/恢复；增加 fencing 和幂等 |
| SVC-DP-04 | `HarnessAgentBuildService` | ADAPT | `agentark-runtime-provider-agentscope` / P12 | 只消费 `AgentRevisionSnapshot`，缓存键包含 Snapshot Hash |
| SVC-DP-05 | `EnvironmentSpecFactory` | ADAPT | Provider / P12 | local/remote/sandbox/self-hosted 语义；禁止 remote 静默降级 |
| SVC-DP-06 | `SessionEventMapper` | REUSE | Provider / P12–13 | 仅纯映射候选；许可补齐、源头测试和 AgentArk Event 契约通过后才能迁入 |
| SVC-DP-07 | `SessionEventLog` | ADAPT | `agentark-runtime` / P11、P13 | 保留 per-session seq/cursor；重建 MySQL 事务与 SSE 续传 |
| SVC-DP-08 | `TurnLeaseService` / `JdbcCoordinationStore` | ADAPT | `agentark-runtime` / P11、P13 | 增加 fencing token、Owner、过期/抢占测试；不复制共享 Repo |
| SVC-DP-09 | Tool Notification/Confirmation | ADAPT | Provider + Runtime Approval / P12–13 | Middleware 依赖 AgentScope，Ticket/审批归中立 Runtime |
| SVC-DP-10 | Hands Work Queue/Controller | ADAPT | `agentark-runtime` / P11、P13 | Durable Work、claim/ack/heartbeat/stop；增加 attempt/dead-letter 语义 |
| SVC-DP-11 | 默认 Secret、CORS、Auto-DDL | REJECT | 无 | 不安全开发默认不得进入任何环境配置 |
| SVC-SCH-01 | `CronDeploymentScheduler` | REFERENCE | `agentark-scheduling` / P15 | 只借鉴 due/fire lease；目标必须有 Trigger/Job/Attempt/Retry |
| SVC-SCH-02 | `SchedulerChannelRuntime` | ADAPT | `agentark-scheduling` / P15 | Channel 配置 reconcile、启动状态与退避 |
| SVC-SCH-03 | `ManagedSessionChannelBridge` | ADAPT | `agentark-scheduling` / P15 | 改为 Runtime Client + 事件驱动 Reply，拒绝阻塞轮询 |
| SVC-SCH-04 | `ChannelExternalKeys` | REUSE | `agentark-scheduling` / P15 | 纯键规范候选；需许可与 collision/round-trip 测试 |
| SVC-SCH-05 | `OutboundService` | ADAPT | `agentark-scheduling` / P15 | 加 Delivery/Attempt/Idempotency/Audit |
| SVC-SCH-06 | `HandsWorkerMain` / `LocalHandsToolExecutor` | DEFER | Worker 交付 / P21–22 | 自托管 Worker 协议稳定、安全审计和 Sandbox Policy 后再评估 |

## 3. Aistio 绞杀矩阵

| ID | 候选源路径/资源 | 分类 | 目标模块/Phase | Disposition |
|---|---|---|---|---|
| AIO-01 | `internal/product/handlers_auth.go`、`handlers_admin.go` | REFERENCE | `agentark-control` / P07 | 资源/API 语义参考；拒绝 7 天 HS256、自带用户库作为生产认证 |
| AIO-02 | Agent/Workspace/Marketplace/File Handlers | ADAPT | `agentark-control` / P08–10 | 以 AgentArk Draft/Revision/Snapshot 重建 |
| AIO-03 | Product Session Handlers/Internal resolve | ADAPT | Control Contract + Runtime / P10–13 | 拆 Session 生命周期 Owner，版本化 Internal Contract |
| AIO-04 | Environment/Vault/Memory Handlers | ADAPT | Control / P08–10 | SecretRef、ACL、审计和不可变挂载 |
| AIO-05 | Deployment/Webhook/Channel Handlers | ADAPT | Control + Scheduling / P10、P15 | Control 保存意图，Scheduler 拥有执行记录 |
| AIO-06 | Team REST/Store/Controller | DEFER | Control/Runtime Collaboration / P21 | Team 契约、权限、恢复 Gate 后迁移 |
| AIO-07 | Runtime Store Session/Turn/Event/Command | REFERENCE | Runtime / P11–13 | 状态语义和测试参考，不复制 Go Store/SQL |
| AIO-08 | `internal/product/migrate.go` | REJECT | 无 | 拒绝启动时大段幂等 DDL |
| AIO-09 | `internal/store/postgres/migrations` | REFERENCE | MySQL Runtime Schema / P06、P11 | 只参考实体和索引语义，不翻译 PostgreSQL DDL |
| AIO-10 | CRD/Controller/Helm | DEFER | Deployment / P22 | v1 不把 Kubernetes CRD 作为产品域权威 |
| AIO-11 | ASDP/SDK/Sidecar Adapter | DEFER | Compatibility / P21 | BYO Agent 契约确定后再评估 |
| AIO-12 | `seed.go` 明文默认密码日志 | REJECT | 无 | 禁止记录或生成可见默认凭据 |
| AIO-13 | Go Aistio 一次性重写 | REJECT | 无 | 必须按 [绞杀计划](aistio-strangler.md) 分 Cohort |

## 4. AgentScope Framework 依赖矩阵

| ID | 上游模块/包 | 分类 | AgentArk 使用位置 | Disposition |
|---|---|---|---|---|
| ASF-01 | `agentscope-dependencies-bom` | REFERENCE | `agentark-bom` / P02 | 版本证据参考；AgentArk 自己治理依赖版本 |
| ASF-02 | `agentscope-core` Agent/RuntimeContext/Message/Event | REFERENCE | Provider / P12 | `DEPENDENCY`；只在防腐层转换，禁止类型外泄 |
| ASF-03 | `agentscope-core` Permission/Middleware/State/MCP | REFERENCE | Provider / P12–13 | `DEPENDENCY`；平台 Policy/Approval 仍为 AgentArk 领域 |
| ASF-04 | `agentscope-core` RAG/Knowledge | REFERENCE | `agentark-knowledge` Adapter / P14 | `DEPENDENCY`；仅受控 Adapter 包允许导入 |
| ASF-05 | `agentscope-harness` HarnessAgent/Builder/Middleware | REFERENCE | Provider / P12 | `DEPENDENCY`，禁止复制框架核心 |
| ASF-06 | Harness Workspace/Memory/Skill/Subagent/Team | REFERENCE | Provider / P12 | `DEPENDENCY`，由 Snapshot Compiler 配置 |
| ASF-07 | Harness Sandbox/Filesystem/DistributedStore | REFERENCE | Provider + Runtime Port / P12–13 | `DEPENDENCY`；存储实现必须服从 Runtime Owner |
| ASF-08 | Harness Channel/Gateway | REFERENCE | Scheduling Adapter / P15 | 仅使用 Channel 抽象，不让 Scheduler 执行推理循环 |
| ASF-09 | Model Provider Extensions | DEFER | Provider / P12 | 按产品支持清单逐个引入，禁止全量闭包 |
| ASF-10 | MySQL/PostgreSQL/Redis/Object Store Extensions | DEFER | Foundation/Provider / P04、P12 | P04 已按 AgentArk 契约独立实现 MySQL/Redis/Local Object Store 基础，没有复制或依赖这些 Extension；Provider 级复用继续 DEFER 到 P12 |
| ASF-11 | Sandbox Extensions | DEFER | Provider / P12、P20 | E2B/Daytona/K8s/AgentRun 分别做安全与许可评审 |
| ASF-12 | RAG Extensions | DEFER | Knowledge / P14 | 根据 Qdrant/Embedding 目标逐个评估 |
| ASF-13 | A2A/AG-UI/Agent Protocol | DEFER | API Compatibility / P21 | 公共契约、认证和恢复语义确定后引入 |
| ASF-14 | AgentScope Framework 核心源码复制 | REJECT | 无 | 依赖优先，禁止维护私有 Fork |

## 5. Frontend 双参考矩阵

| ID | 来源 | 分类 | AgentArk 目标/Phase | 取用边界 |
|---|---|---|---|---|
| FE-AS-01 | AgentScope `frontend/src/api` | REFERENCE | `agentark-web` API layer / P17–18 | Agent/Session/Team/HITL 语义；按 AgentArk OpenAPI 重建 Client |
| FE-AS-02 | AgentScope Pages/Features | REFERENCE | Web IA / P17–18 | 能力覆盖清单，不复用视觉或页面代码 |
| FE-AS-03 | AgentScope `streamEvents` Parser | REJECT | 无 | 无重连、无 SSE id、坏帧静默丢弃 |
| FE-AS-04 | AgentScope Radix/Tailwind 依赖选择 | REFERENCE | Web Foundation / P17 | 不能绕过 AgentArk 独立技术选型和锁定 |
| FE-DS-01 | DeepSeek `apps/web` + `packages/client/web*` | REFERENCE | Web Shell / P17 | 薄入口和 Shell 分层参考，不迁入 Cordis 装配 |
| FE-DS-02 | `ui-theme/styles` | REFERENCE | Design Token / P17 | 视觉层级、暗色和字体度量参考；Token 重新命名实现 |
| FE-DS-03 | `ui-layout`、`ui-sidebar` | REFERENCE | Layout / P17–18 | 密度、响应式、面板交互参考 |
| FE-DS-04 | `ui-conversation`、`ui-tool`、`ui-trajectory` | REFERENCE | Runtime UX / P18 | Stream、Approval、Tool、Timeline/虚拟化交互参考 |
| FE-DS-05 | `ui-primitives`、Markdown/Terminal/JsonTree | REFERENCE | Component Library / P17–18 | 行为和安全测试参考；不复制品牌图标 |
| FE-DS-06 | `ui-workspace` | REFERENCE | Workspace / P18 | 文件浏览参考；不能声称上游提供通用代码编辑器 |
| FE-DS-07 | Client Runtime/Connection | REFERENCE | Web Session Store / P17–18 | 生命周期和投影参考；协议必须换成 AgentArk Event 模型 |
| FE-DS-08 | Cordis/Slots/Plugin Inventory/Plugin Settings | REJECT | 无 | DeepSeek Plugin Architecture 不作为 AgentArk 应用内核 |
| FE-DS-09 | Fish Logo、BrandWordmark、DeepSeek 名称/品牌色 | REJECT | 无 | 品牌和商标不复制 |
| FE-DS-10 | `assets/community-*.png` | REJECT | 无 | 社区二维码/品牌资产与产品无关 |
| FE-DS-11 | 设计近似 Glyph/Icon | REJECT | 无 | 上游说明部分为手绘近似，AgentArk 使用自有/有明确许可图标 |
| FE-DS-12 | DeepSeek Web 测试场景 | REFERENCE | Web E2E / P17–18 | 复建 Approval/Scroll/Timeline/Terminal/Workspace 场景，不复制 Snapshot |

## 6. 明确拒绝项

以下项目不因后续实现方便而重新解释：

1. 整体复制 `service-common` 或创建 giant common；
2. AgentScope Core/Harness 源码 Fork；
3. JPA Entity/Repository、PostgreSQL DDL、`ddl-auto=update` 直接迁入；
4. Shared JWT Secret、长期 Internal Token、默认密码/Secret、明文凭据日志；
5. 无 fencing 的 Lease、无 Attempt/Dead Letter 的 Scheduler、阻塞轮询 Channel Reply；
6. 缺 `id`/`Last-Event-ID` 和自动重连的 SSE 实现；
7. Aistio Big Bang 重写或 Go/Java 双写同一业务表；
8. DeepSeek Cordis/Plugin 内核、品牌、Logo、社区图片和官方 Claude 平台负载；
9. 让 AgentScope Runtime 类型进入 Provider 外部或公共 API/持久化模型；
10. 在 AgentScope 根 LICENSE/NOTICE 证据缺失时复制其源码。

## 7. Phase 04 实际处置

| 范围 | 实际结果 | 仍未进入本阶段 |
|---|---|---|
| Error/Web | 独立实现 `ProblemDetail`、Request/Trace/Tenant Context、Jackson 强类型 ID 和 MVC/WebFlux 条件化配置 | 业务 Endpoint、业务 DTO、统一 `Result<T>` |
| Security | 独立实现 OIDC/JWK/Audience Decoder、`AgentArkPrincipal`、Service Identity、Tenant Selection、API Key SPI 和 Method Security | User、Role、Membership、API Key 生命周期和资源授权 |
| Persistence | 独立实现 MyBatis-Plus Boot 4 插件、UUIDv7/Instant/JSON TypeHandler、审计字段接口并复用 Boot Hikari/Flyway 基础 | 业务 Mapper/DO、DDL、Migration 和数据库账号 |
| Redis | 独立实现类型化缓存、Key/TTL 规范及 Lua 原子 Lease/Fencing/Idempotency/Rate Limit | Durable Work、Approval、Job 或其他 MySQL 事实 |
| Storage | 独立实现 Object Store SPI、服务端生成路径的 Local 实现与 S3-compatible Factory SPI | S3/OSS/COS SDK、生产凭据和部署配置 |
| Observability | 独立实现 Micrometer/OTel 适配、W3C Trace、结构化日志、Span 约定、Tag 白名单和内容脱敏 | Exporter、Collector、告警和生产 Dashboard |

以上代码均为 AgentArk 独立实现，只依据固定上游提炼行为语义；没有迁入上游实现文件、文件头、资源或第三方资产。

## 8. 变更协议

后续 Phase 改变任何分类时，必须同时更新：本清单、对应阶段报告、行为测试引用和 [许可清单](license-and-notice.md)。从 `REFERENCE/DEFER/REJECT` 提升到 `REUSE` 属于显著风险变化，必须给出文件级来源、目标路径、许可证和回滚证据。
