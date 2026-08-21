---
owner: refinex
updated: 2026-08-21
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

| ID         | 候选源路径                                   | 分类      | 目标模块/Phase                                                                  | Disposition 与行为门禁                                                                                                                                                                                                   |
| ---------- | -------------------------------------------- | --------- | ------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| SVC-COM-01 | `service-common/web/api/error`               | ADAPT     | `agentark-kernel` + `agentark-starter-web` / P03–04                             | P04 已按 RFC 9457 重写 HTTP 映射并保留稳定错误码；未知异常不回显原消息；覆盖 [ERR-01](behavior-baseline.md#err-01-错误映射)                                                                                              |
| SVC-COM-02 | `service-common/web/auth`                    | ADAPT     | `agentark-starter-security` + `agentark-control` / P04、P07                     | P04 已替换为 HTTPS Issuer/JWK、Audience、服务身份和严格 JWT Principal 转换；P07 已实现 Issuer/Subject 映射、Membership、Role/Permission/Binding、项目服务账号和摘要 API Key；拒绝共享 Secret 与客户端 Tenant Header 授权 |
| SVC-COM-03 | `service-common/runtime/config`              | ADAPT     | `agentark-control`、`agentark-scheduling` / P08、P15                            | 按资产 Owner 拆分，不保留共享配置对象                                                                                                                                                                                    |
| SVC-COM-04 | `service-common/web/catalog*`                | ADAPT     | `agentark-control` + Provider / P08、P10、P12                                   | Spec 语义进入不可变 Snapshot；Codec 留在防腐层                                                                                                                                                                           |
| SVC-COM-05 | `service-common/web/coord`                   | ADAPT     | Redis Starter + `agentark-runtime` + `agentark-scheduling` / P04、P11、P13、P15 | P04 提供缓存侧 Lease/Fencing/Idempotency 基础；P11 已建立 Runtime Approval、持久 Work Queue、MySQL Fencing 和幂等权威事实；API/Worker 装配归 P13，Cron 归 P15，Redis 不作权威状态                                        |
| SVC-COM-06 | `service-common/web/managed*`                | ADAPT     | Control/Runtime Contract Adapter / P09–13                                       | DTO 按契约重建，禁止 shared DTO 包                                                                                                                                                                                       |
| SVC-COM-07 | `service-common/web/persistence/jpa`         | REJECT    | 无                                                                              | 不迁入 JPA Entity、Repository、`ddl-auto=update` 或共享表                                                                                                                                                                |
| SVC-COM-08 | `service-common/web/share`                   | ADAPT     | `agentark-control` / P07–10                                                     | P07 已以 Organization/Project/Environment Owner 链和 Scope-aware Role Binding 建立资源级授权基线；P08–10 资源继续复用该检查，不能退化为 Owner 字符串                                                                     |
| SVC-COM-09 | `service-common/web/workspace`               | ADAPT     | Control + Runtime Mount Port / P08、P12                                         | 分离资产路径和执行挂载                                                                                                                                                                                                   |
| SVC-COM-10 | 整个 `service-common`                        | REJECT    | 无                                                                              | 禁止整体复制和创建 `agentark-common`                                                                                                                                                                                     |
| SVC-GW-01  | `service-gateway/application.yml` Route 表   | ADAPT     | `agentark-gateway-server` / P16                                                 | 保留四平面路由与 Internal 拒绝；覆盖 GW-01/GW-02                                                                                                                                                                         |
| SVC-GW-02  | Gateway Header 清洗                          | ADAPT     | `agentark-gateway-server` / P16                                                 | 外部不能注入服务身份；扩展 OIDC/CORS/Rate Limit                                                                                                                                                                          |
| SVC-GW-03  | Gateway App 启动类                           | REJECT    | 无                                                                              | 薄启动类不值得复制                                                                                                                                                                                                       |
| SVC-DP-01  | `DataSessionApiController`                   | ADAPT     | `agentark-runtime` Adapter / P13                                                | 保留 Event/SSE/HITL 语义，改为版本化契约、SSE id/重连                                                                                                                                                                    |
| SVC-DP-02  | `DataSessionService`                         | ADAPT     | `agentark-runtime` / P11、P13                                                   | P11 已重建 Session Owner、固定 Snapshot、Turn/Run 状态机与持久化；P13 只装配 API/Worker，禁止跨平面查询表                                                                                                                |
| SVC-DP-03  | `SessionTurnRunner`                          | ADAPT     | Runtime + Provider / P11–13                                                     | P11 已实现持久 Admission、Claim、Fencing、幂等、取消/恢复命令和 Fake Engine；P12 接 Provider，P13 装配 Worker 与外部命令                                                                                                 |
| SVC-DP-04  | `HarnessAgentBuildService`                   | ADAPT     | `agentark-runtime-provider-agentscope` / P12                                    | 只消费 `AgentRevisionSnapshot`，缓存键包含 Snapshot Hash                                                                                                                                                                 |
| SVC-DP-05  | `EnvironmentSpecFactory`                     | ADAPT     | Provider / P12                                                                  | local/remote/sandbox/self-hosted 语义；禁止 remote 静默降级                                                                                                                                                              |
| SVC-DP-06  | `SessionEventMapper`                         | REUSE     | Provider / P12–13                                                               | 仅纯映射候选；许可补齐、源头测试和 AgentArk Event 契约通过后才能迁入                                                                                                                                                     |
| SVC-DP-07  | `SessionEventLog`                            | ADAPT     | `agentark-runtime` / P11、P13                                                   | P11 已建立 MySQL 追加式 Event Store、Session/Run 双 Sequence、ObjectRef 和并发测试；P13 只实现 SSE `id`/续传与通知                                                                                                       |
| SVC-DP-08  | `TurnLeaseService` / `JdbcCoordinationStore` | ADAPT     | `agentark-runtime` / P11、P13                                                   | P11 已建立 Work Claim Fencing、Owner、过期回收和数据库拒绝旧 Token；P13 补 Runtime Instance Heartbeat/Drain，不复制共享 Repo                                                                                             |
| SVC-DP-09  | Tool Notification/Confirmation               | ADAPT     | Provider + Runtime Approval / P11–13                                            | P11 已建立中立 Approval 聚合、状态机、参数 Hash 与恢复命令；AgentScope Middleware 转换归 P12，决策 API 归 P13                                                                                                            |
| SVC-DP-10  | Hands Work Queue/Controller                  | ADAPT     | `agentark-runtime` / P11、P13                                                   | P11 已建立 Durable Work、Claim、Complete、Release、Attempt 和过期回收；P13 装配 poll/heartbeat/stop 与运维行为                                                                                                           |
| SVC-DP-11  | 默认 Secret、CORS、Auto-DDL                  | REJECT    | 无                                                                              | 不安全开发默认不得进入任何环境配置                                                                                                                                                                                       |
| SVC-SCH-01 | `CronDeploymentScheduler`                    | REFERENCE | `agentark-scheduling` / P15                                                     | 只借鉴 due/fire lease；目标必须有 Trigger/Job/Attempt/Retry                                                                                                                                                              |
| SVC-SCH-02 | `SchedulerChannelRuntime`                    | ADAPT     | `agentark-scheduling` / P15                                                     | Channel 配置 reconcile、启动状态与退避                                                                                                                                                                                   |
| SVC-SCH-03 | `ManagedSessionChannelBridge`                | ADAPT     | `agentark-scheduling` / P15                                                     | 改为 Runtime Client + 事件驱动 Reply，拒绝阻塞轮询                                                                                                                                                                       |
| SVC-SCH-04 | `ChannelExternalKeys`                        | REUSE     | `agentark-scheduling` / P15                                                     | 纯键规范候选；需许可与 collision/round-trip 测试                                                                                                                                                                         |
| SVC-SCH-05 | `OutboundService`                            | ADAPT     | `agentark-scheduling` / P15                                                     | 加 Delivery/Attempt/Idempotency/Audit                                                                                                                                                                                    |
| SVC-SCH-06 | `HandsWorkerMain` / `LocalHandsToolExecutor` | DEFER     | Worker 交付 / P21–22                                                            | 自托管 Worker 协议稳定、安全审计和 Sandbox Policy 后再评估                                                                                                                                                               |

## 3. Aistio 绞杀矩阵

| ID     | 候选源路径/资源                                          | 分类      | 目标模块/Phase                      | Disposition                                                                                                                        |
| ------ | -------------------------------------------------------- | --------- | ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| AIO-01 | `internal/product/handlers_auth.go`、`handlers_admin.go` | REFERENCE | `agentark-control` / P07            | P07 已参考身份传递与管理 API 语义重建 OIDC/JWT/API Key IAM；拒绝 7 天 HS256、本地用户名密码库、明文 Seed 用户和共享 Internal Token |
| AIO-02 | Agent/Workspace/Marketplace/File Handlers                | ADAPT     | `agentark-control` / P08、P10       | P08 已独立建立 Agent 稳定身份、Workspace Profile 与 Skill ObjectRef；Draft/Revision/Snapshot 由 P10 重建                           |
| AIO-03 | Product Session Handlers/Internal resolve                | ADAPT     | Control Contract + Runtime / P10–13 | 拆 Session 生命周期 Owner，版本化 Internal Contract                                                                                |
| AIO-04 | Environment/Vault/Memory Handlers                        | ADAPT     | Control / P08–10                    | P08 已独立建立 Secret Metadata/Binding、Memory Profile、Resolver SPI 和审计；拒绝明文 Vault 请求                                   |
| AIO-05 | Deployment/Webhook/Channel Handlers                      | ADAPT     | Control + Scheduling / P10、P15     | Control 保存意图，Scheduler 拥有执行记录                                                                                           |
| AIO-06 | Team REST/Store/Controller                               | DEFER     | Control/Runtime Collaboration / P21 | Team 契约、权限、恢复 Gate 后迁移                                                                                                  |
| AIO-07 | Runtime Store Session/Turn/Event/Command                 | REFERENCE | Runtime / P11–13                    | P11 已按中立 Domain 与 MySQL V2 独立实现状态、Event、Command 和恢复语义；不复制 Go Store/PostgreSQL SQL，P13 再装配 API            |
| AIO-08 | `internal/product/migrate.go`                            | REJECT    | 无                                  | 拒绝启动时大段幂等 DDL                                                                                                             |
| AIO-09 | `internal/store/postgres/migrations`                     | REFERENCE | MySQL Runtime Schema / P06、P11     | 只参考实体和索引语义，不翻译 PostgreSQL DDL                                                                                        |
| AIO-10 | CRD/Controller/Helm                                      | DEFER     | Deployment / P22                    | v1 不把 Kubernetes CRD 作为产品域权威                                                                                              |
| AIO-11 | ASDP/SDK/Sidecar Adapter                                 | DEFER     | Compatibility / P21                 | BYO Agent 契约确定后再评估                                                                                                         |
| AIO-12 | `seed.go` 明文默认密码日志                               | REJECT    | 无                                  | P07 Dev Bootstrap 只在 `local` Profile 且显式开启时创建无凭据资源；禁止记录或生成默认口令、Token、API Key                          |
| AIO-13 | Go Aistio 一次性重写                                     | REJECT    | 无                                  | 必须按 [绞杀计划](aistio-strangler.md) 分 Cohort                                                                                   |

## 4. AgentScope Framework 依赖矩阵

| ID     | 上游模块/包                                          | 分类      | AgentArk 使用位置                  | Disposition                                                                                                                        |
| ------ | ---------------------------------------------------- | --------- | ---------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| ASF-01 | `agentscope-dependencies-bom`                        | REFERENCE | `agentark-bom` / P02               | 版本证据参考；AgentArk 自己治理依赖版本                                                                                            |
| ASF-02 | `agentscope-core` Agent/RuntimeContext/Message/Event | REFERENCE | Provider / P12                     | `DEPENDENCY`；只在防腐层转换，禁止类型外泄                                                                                         |
| ASF-03 | `agentscope-core` Permission/Middleware/State/MCP    | REFERENCE | Provider / P12–13                  | `DEPENDENCY`；平台 Policy/Approval 仍为 AgentArk 领域                                                                              |
| ASF-04 | `agentscope-core` RAG/Knowledge                      | REFERENCE | `agentark-knowledge` Adapter / P14 | `DEPENDENCY`；仅受控 Adapter 包允许导入                                                                                            |
| ASF-05 | `agentscope-harness` HarnessAgent/Builder/Middleware | REFERENCE | Provider / P12                     | `DEPENDENCY`，禁止复制框架核心                                                                                                     |
| ASF-06 | Harness Workspace/Memory/Skill/Subagent/Team         | REFERENCE | Provider / P12                     | `DEPENDENCY`，由 Snapshot Compiler 配置                                                                                            |
| ASF-07 | Harness Sandbox/Filesystem/DistributedStore          | REFERENCE | Provider + Runtime Port / P12–13   | `DEPENDENCY`；存储实现必须服从 Runtime Owner                                                                                       |
| ASF-08 | Harness Channel/Gateway                              | REFERENCE | Scheduling Adapter / P15           | 仅使用 Channel 抽象，不让 Scheduler 执行推理循环                                                                                   |
| ASF-09 | Model Provider Extensions                            | DEFER     | Provider / P12                     | 按产品支持清单逐个引入，禁止全量闭包                                                                                               |
| ASF-10 | MySQL/PostgreSQL/Redis/Object Store Extensions       | DEFER     | Foundation/Provider / P04、P12     | P04 已按 AgentArk 契约独立实现 MySQL/Redis/Local Object Store 基础，没有复制或依赖这些 Extension；Provider 级复用继续 DEFER 到 P12 |
| ASF-11 | Sandbox Extensions                                   | DEFER     | Provider / P12、P20                | E2B/Daytona/K8s/AgentRun 分别做安全与许可评审                                                                                      |
| ASF-12 | RAG Extensions                                       | REFERENCE | Knowledge / P14                    | 已逐项审计 Qdrant/Reader/Embedding/Knowledge/Tool；不复制 Store 或旧 Knowledge 类型                                                |
| ASF-13 | A2A/AG-UI/Agent Protocol                             | DEFER     | API Compatibility / P21            | 公共契约、认证和恢复语义确定后引入                                                                                                 |
| ASF-14 | AgentScope Framework 核心源码复制                    | REJECT    | 无                                 | 依赖优先，禁止维护私有 Fork                                                                                                        |

## 5. Frontend 双参考矩阵

| ID       | 来源                                                                                                                               | 分类      | AgentArk 目标/Phase               | 取用边界                                                                                                                                                |
| -------- | ---------------------------------------------------------------------------------------------------------------------------------- | --------- | --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| FE-AS-01 | AgentScope `frontend/src/api`                                                                                                      | REFERENCE | `agentark-web` API layer / P17–18 | Agent/Session/Team/HITL 语义；按 AgentArk OpenAPI 重建 Client                                                                                           |
| FE-AS-02 | AgentScope Pages/Features                                                                                                          | REFERENCE | Web IA / P17–18                   | 能力覆盖清单，不复用视觉或页面代码                                                                                                                      |
| FE-AS-03 | AgentScope `streamEvents` Parser                                                                                                   | REJECT    | 无                                | 无重连、无 SSE id、坏帧静默丢弃                                                                                                                         |
| FE-AS-04 | AgentScope Radix/Tailwind 依赖选择                                                                                                 | REFERENCE | Web Foundation / P17              | 不能绕过 AgentArk 独立技术选型和锁定                                                                                                                    |
| FE-DS-01 | DeepSeek `apps/web` + `packages/client/web*`                                                                                       | REFERENCE | Web Shell / P17                   | 薄入口和 Shell 分层参考，不迁入 Cordis 装配                                                                                                             |
| FE-DS-02 | `ui-theme/styles`                                                                                                                  | REFERENCE | Design Token / P17                | 视觉层级、暗色和字体度量参考；Token 重新命名实现                                                                                                        |
| FE-DS-03 | `ui-layout`、`ui-sidebar`                                                                                                          | REFERENCE | Layout / P17–18                   | 密度、响应式、面板交互参考                                                                                                                              |
| FE-DS-04 | `ui-conversation`、`ui-tool`、`ui-trajectory`                                                                                      | REFERENCE | Runtime UX / P18                  | Stream、Approval、Tool、Timeline/虚拟化交互参考                                                                                                         |
| FE-DS-05 | `ui-primitives`、Markdown/Terminal/JsonTree                                                                                        | REFERENCE | Component Library / P17–18        | 行为和安全测试参考；不复制品牌图标                                                                                                                      |
| FE-DS-06 | `ui-workspace`                                                                                                                     | REFERENCE | Workspace / P18                   | 文件浏览参考；不能声称上游提供通用代码编辑器                                                                                                            |
| FE-DS-07 | Client Runtime/Connection                                                                                                          | REFERENCE | Web Session Store / P17–18        | 生命周期和投影参考；协议必须换成 AgentArk Event 模型                                                                                                    |
| FE-DS-08 | Cordis/Slots/Plugin Inventory/Plugin Settings                                                                                      | REJECT    | 无                                | DeepSeek Plugin Architecture 不作为 AgentArk 应用内核                                                                                                   |
| FE-DS-09 | Fish Logo、BrandWordmark、DeepSeek 名称/品牌色                                                                                     | REJECT    | 无                                | 品牌和商标不复制                                                                                                                                        |
| FE-DS-10 | `assets/community-*.png`                                                                                                           | REJECT    | 无                                | 社区二维码/品牌资产与产品无关                                                                                                                           |
| FE-DS-11 | 设计近似 Glyph/Icon                                                                                                                | REJECT    | 无                                | 上游说明部分为手绘近似，AgentArk 使用自有/有明确许可图标                                                                                                |
| FE-DS-12 | DeepSeek Web 测试场景                                                                                                              | REFERENCE | Web E2E / P17–18                  | 复建 Approval/Scroll/Timeline/Terminal/Workspace 场景，不复制 Snapshot                                                                                  |
| FE-SH-01 | shadcn/ui `new-york-v4/login-05.json`；`shadcn@4.18.0`，SHA-256 `2679ef5ce4e3967084fb18981ce39659c79b495ac873d2307905b8645d3d9a23` | ADAPT     | React `/sign-in` + 首次改密 / P23 | 迁入居中单列、品牌区、Field Group/Separator、账号字段和全宽主操作；拒绝 Acme、Apple/Google 和条款占位链接；密码只提交同源 Gateway，不新增聚合 `radix-ui` |

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

| 范围          | 实际结果                                                                                                                    | 仍未进入本阶段                                     |
| ------------- | --------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------- |
| Error/Web     | 独立实现 `ProblemDetail`、Request/Trace/Tenant Context、Jackson 强类型 ID 和 MVC/WebFlux 条件化配置                         | 业务 Endpoint、业务 DTO、统一 `Result<T>`          |
| Security      | 独立实现 OIDC/JWK/Audience Decoder、`AgentArkPrincipal`、Service Identity、Tenant Selection、API Key SPI 和 Method Security | User、Role、Membership、API Key 生命周期和资源授权 |
| Persistence   | 独立实现 MyBatis-Plus Boot 4 插件、UUIDv7/Instant/JSON TypeHandler、审计字段接口并复用 Boot Hikari/Flyway 基础              | 业务 Mapper/DO、DDL、Migration 和数据库账号        |
| Redis         | 独立实现类型化缓存、Key/TTL 规范及 Lua 原子 Lease/Fencing/Idempotency/Rate Limit                                            | Durable Work、Approval、Job 或其他 MySQL 事实      |
| Storage       | 独立实现 Object Store SPI、服务端生成路径的 Local 实现与 S3-compatible Factory SPI                                          | S3/OSS/COS SDK、生产凭据和部署配置                 |
| Observability | 独立实现 Micrometer/OTel 适配、W3C Trace、结构化日志、Span 约定、Tag 白名单和内容脱敏                                       | Exporter、Collector、告警和生产 Dashboard          |

以上代码均为 AgentArk 独立实现，只依据固定上游提炼行为语义；没有迁入上游实现文件、文件头、资源或第三方资产。

## 8. Phase 08 实际处置

| 范围                                                 | 分类                   | 实际结果                                                                              | 未进入本阶段                                                     |
| ---------------------------------------------------- | ---------------------- | ------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| AgentScope Model Extensions                          | `DEPENDENCY/REFERENCE` | Control 使用平台中立 Provider Descriptor、Model Profile、能力与参数 JSON、`SecretRef` | 厂商 SDK、模型调用、重试和流式执行                               |
| AgentScope MCP                                       | `DEPENDENCY/REFERENCE` | 独立实现 Server/Version、Transport、Endpoint、SSRF 信息模型与 Tool Descriptor 快照    | MCP Client、健康探测、DNS 在线解析和工具执行                     |
| AgentScope Skill/Workspace/Memory/Sandbox/Permission | `DEPENDENCY/REFERENCE` | 独立实现稳定身份、不可变 Profile/Policy Version 和 Skill ObjectRef                    | Skill 解包/执行、Workspace 挂载、Memory Backend、Sandbox Runtime |
| Aistio Agent/Model/MCP/Vault                         | `ADAPT`                | 重建为 Project Owner、只追加版本、Secret Metadata/Binding、Public Contract 和审计     | Go Handler/Store、PostgreSQL DDL、明文 Vault 请求                |
| AgentScope Frontend 字段                             | `REFERENCE`            | 仅用于核对功能字段和版本语义                                                          | React 组件、状态管理和视觉代码                                   |
| AgentScope/Provider 实现源码                         | `REJECT`               | `agentark-control` 无 `io.agentscope` 或厂商 SDK 类型                                 | 任何框架核心复制或私有 Fork                                      |

Phase 08 新增代码均为 AgentArk 独立实现，没有迁入固定上游源码、文件头、品牌资源或第三方资产。运行时转换继续由 Phase 12 的 Provider 防腐层负责。

## 9. Phase 09 实际处置

| 来源范围                                       | 分类              | 实际结果                                                                                               | 明确延后或拒绝                                                                                                                   |
| ---------------------------------------------- | ----------------- | ------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------- |
| Core deprecated `rag/Knowledge`、Document 模型 | `REFERENCE`       | 只提炼检索能力边界；AgentArk 独立建立 KnowledgeBase、Document/Revision、ACL 与不可变 KnowledgeRevision | 不复制旧类型，不让其进入 Domain/API                                                                                              |
| RAG Simple `SimpleKnowledge`                   | `REFERENCE`       | 独立拆出 Parser、Chunk、Embedding、VectorIndex、Retriever、Reranker Ports 与 Fake Adapter              | 不直接组合 Provider 实现，不同步执行大文档摄取                                                                                   |
| Reader/Chunker/Embedding                       | `REFERENCE/DEFER` | Phase 09 只建立中立 Port 和版本化 Profile                                                              | PDF/Word/Tika、真实 Embedding 与模型 SDK 延后 P14                                                                                |
| Qdrant Store                                   | `ADAPT`           | `adapter.out.vector.qdrant.QdrantKnowledgeVectorStore`                                                 | 使用中立 Port/JDK REST；强制 Organization/Project/Revision/Document Filter、Payload Index、Count/Checksum 和删除；不复制上游实现 |
| Elasticsearch/Milvus/PgVector Store            | `DEFER`           | 仅保留中立 `KnowledgeVectorStore` Port                                                                 | 没有实际工作负载和安全 E2E，不引入客户端依赖                                                                                     |
| Bailian/Dify/Haystack/RAGFlow                  | `DEFER/REJECT`    | 仅记录托管检索和 Rerank 语义                                                                           | P14 未逐个做安全、许可和产品决策前不得进入运行闭包                                                                               |
| AgentScope Service/Aistio/Frontend Knowledge   | `REFERENCE`       | 全量索引确认没有独立 Knowledge/Document 管理 API/UI 可迁移                                             | 不虚构上游 Service 功能或复制无关页面                                                                                            |

Phase 09 实现源码均为 AgentArk 独立实现。Phase 14 保持 `agentark-knowledge` Domain/Application 无 `io.agentscope`、Qdrant、Elasticsearch、Milvus 或 PgVector 类型；AgentScope 适配只进入 `adapter.out.vector.agentscope`，Qdrant 只进入 `adapter.out.vector.qdrant`。

## 10. Phase 10 实际处置

| 来源范围                                           | 分类                   | 实际结果                                                                                 | 明确延后或拒绝                                                        |
| -------------------------------------------------- | ---------------------- | ---------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| Aistio Agent Version/Managed Agent                 | `ADAPT/REFERENCE`      | 独立建立 Agent Draft、Validation、不可变 Revision 和 Canonical Snapshot                  | 不复制 Go DTO/Store/PostgreSQL DDL，不保留可变 Agent 回退             |
| Dataplane Control Resolve Client                   | `ADAPT`                | 建立 Snapshot/Deployment Internal OpenAPI、ETag、Provider/Schema/Capability 校验         | 拒绝 Runtime 读 Control DB、Catalog 或 Draft；拒绝共享 Internal Token |
| `HarnessAgentBuildService` 与 Harness Builder      | `DEPENDENCY/REFERENCE` | Snapshot v1 固定 Phase 12 组装所需 Model/Prompt/MCP/Skill/Knowledge/Profile/Policy/Limit | Phase 10 不引入 `io.agentscope`，实际 Builder 转换延后 Phase 12       |
| Aistio Deployment/Environment                      | `REFERENCE/ADAPT`      | 独立建立 Environment 内稳定 Deployment、Revision 指针、Promote/Rollback/Enable/Disable   | 上游 Cron/Webhook Deployment 不是发布指针模型，不迁入其 Handler/Store |
| AgentScope Frontend Agent/Environment/Session 流程 | `REFERENCE`            | 固定 Draft → Publish → Deployment → Runtime 的产品语义                                   | 不复制 React 页面、API Client、状态管理或视觉实现                     |
| AgentScope/Aistio Snapshot 类型                    | `REJECT`               | 使用 AgentArk Kernel 与 JSON Schema v1，Canonical Hash 排除顶层 `contentHash`            | 不允许上游 Message/Event/DTO/Entity 进入 Contract 或持久化模型        |

Phase 10 新增源码、SQL、Schema 与 OpenAPI 均为 AgentArk 独立实现。发布 Outbox 的 Diff Summary 只列上一 Revision 和变化区段，不包含资产正文；Canary 仅保留模型并由应用拒绝执行，真实分流属于后续 Runtime/Routing 阶段。

## 11. Phase 11 实际处置

| 来源范围                                | 分类                | 实际结果                                                                                        | 明确延后或拒绝                                                              |
| --------------------------------------- | ------------------- | ----------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Dataplane Session/Turn/Run 与 Admission | `ADAPT`             | 独立建立固定 Snapshot Session、Turn/Run Attempt、持久 Work Item、幂等和中立 Application Service | 不复制 JPA Entity/DTO；Public/Internal Runtime API 和 Worker 装配延后 P13   |
| `SessionEventLog` 与 Aistio Event Store | `ADAPT/REFERENCE`   | 建立 MySQL 追加式 Event Store、Session/Run 双 Sequence、ObjectRef Payload 与 Outbox             | 不复制 JPA/Go SQL；SSE、Preview 和 `Last-Event-ID` 延后 P13                 |
| Lease、Interrupt 与 Work Queue          | `ADAPT`             | 建立 Claim Fencing、过期回收、取消/恢复命令、数据库旧 Token 防御和并发测试                      | 不保留进程内 Active Turn Registry；Instance Heartbeat/Drain 延后 P13        |
| HITL Ticket 与 Tool Confirmation        | `ADAPT`             | 建立中立 Approval 聚合、参数 Hash、状态机和 Repository Port                                     | AgentScope Middleware 映射归 P12，决策 Endpoint 和跨副本通知归 P13          |
| Agent State/Checkpoint                  | `REFERENCE`         | Runtime MySQL 保存版本、Hash、ObjectRef、Commit 可见性、Checkpoint 与 Fencing                   | 拒绝 Provider Auto-DDL 和 `agentscope_sessions` 私表；Provider Codec 归 P12 |
| AgentScope Message/Event/Runtime 类型   | `REJECT/DEPENDENCY` | Domain/Application/Contract 不含 `io.agentscope`，Fake Engine 独立验证执行闭环                  | 只有 P12 Provider 防腐层可依赖并转换 AgentScope 类型                        |

Phase 11 新增源码、SQL 与 Runtime Event Schema 均为 AgentArk 独立实现。Redis 只可加速 Lease/通知，MySQL Event、Work、State、Checkpoint 与 Object Storage Ref 始终是恢复权威；不能把缓存存活误写为 Runtime 可恢复性。

## 12. Phase 12 实际处置

| 来源范围                                                  | 分类                   | 实际结果                                                                                                                          | 明确延后或拒绝                                                                    |
| --------------------------------------------------------- | ---------------------- | --------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `HarnessAgent` Builder、RuntimeContext、Message           | `DEPENDENCY/REFERENCE` | 独立 Provider 将 Snapshot 编译为单 Run Handle，显式注入 Project/Session Context 与 AgentArk State Adapter                         | 不复制 Core/Harness 源码，不使用无 Context 的已废弃入口                           |
| Typed Event、HITL、Cancel/Resume                          | `ADAPT/DEPENDENCY`     | 逐类映射稳定 Signal，过滤 Thinking，Tool 参数流只保留长度、审批参数只暴露 Hash，按 RuntimeContext 与 Fencing Token 定向取消和恢复 | AgentScope Event/Msg 不进入 API、DB、Domain 或 Application                        |
| Agent State/Distributed Backend                           | `ADAPT/DEPENDENCY`     | 经典 AgentStateStore API 适配到 `runtime_agent_state`/Checkpoint Port，保留追加版本、Commit 与 Fencing                            | 拒绝 `agentscope_sessions`、Provider Auto-DDL、本地 JSON State 和跨 Schema Mapper |
| Model、MCP、Skill、Workspace、Memory、Sandbox、Permission | `DEPENDENCY/REFERENCE` | 编译为无 Secret/Session 状态 Binding，经受控组件工厂贡献 Builder；Skill 增加 Hash/Size/Media Type 校验                            | 厂商 SDK、真实 MCP Client、Skill 执行和生产 Sandbox 不伪造，按 P13/P20 装配与审计 |
| Knowledge/RAG                                             | `REFERENCE/DEFER`      | 固定 Knowledge Revision 与 Retrieval Profile 进入编译计划，自定义检索事件映射为稳定 RAG Signal                                    | AgentScope/Qdrant Retriever 实现延后 P14，禁止 Provider 回读 Control Catalog      |
| 固定源码与 Maven 2.0.2 二进制                             | `REFERENCE/DEPENDENCY` | 建立 Compatibility Test 与矩阵，运行实现以发布 JAR 为准                                                                           | 不因同版本号假定 API 一致；CAS State 与 `disableTranscript` 差异必须显式处理      |

Phase 12 代码均为 AgentArk 独立防腐实现，只通过 Maven 依赖调用 AgentScope 发布 API；没有迁入上游源码、测试、资源或文件头。Snapshot Profile 内容闭包已补齐，Runtime 无需回读 Control Catalog 即可完成编译。

## 13. Phase 13 实际处置

| 来源范围                                      | 分类    | 实际结果                                                                                                     | 明确延后或拒绝                                                                |
| --------------------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------- |
| Dataplane Session/Turn API                    | `ADAPT` | 建立版本化 WebFlux Public API；Turn 接单事务提交 Run、Work、幂等、Event 与 Outbox 后返回 202                 | 不复制 JPA Controller/DTO，不允许 API 直接调用 Mapper 或在接单前编译 Snapshot |
| `DataSessionService` / Control Resolve Client | `ADAPT` | 通过 Internal API 固定 Deployment/Revision/Snapshot，使用 ETag 缓存与 Provider Capability 协商               | 拒绝 Runtime 读 Control Schema、Catalog、Draft 或可变 Agent 配置              |
| `SessionEventLog` / SSE                       | `ADAPT` | 持久 Event 先提交；SSE 使用 Session Sequence、`Last-Event-ID`、Heartbeat、有界背压和 MySQL 轮询追平          | 不使用进程内 Preview 作为事实，不因 SSE 断开取消 Run；Gateway 代理归 P16      |
| Turn Lease / Work Queue / Runtime Instance    | `ADAPT` | Runtime MySQL Claim 递增 Fencing；Redis 快速互斥；续租失败触发 Provider Cancel；Instance 心跳和 Drain 已装配 | 不复制共享 Coordination Repository，不让 Redis 保存权威 Run/Owner 状态        |
| Tool Confirmation / HITL                      | `ADAPT` | 参数 Hash Approval、租户授权、乐观锁、幂等决策、到期、取消、Checkpoint 与新 Token Resume 已装配              | 不保存原始 Tool 参数，不暴露 AgentScope `ConfirmResult` 或 Middleware 类型    |
| Recovery / Usage / Provider Error             | `ADAPT` | Checkpoint 孤儿接管、不可恢复新 Attempt、接单后准备失败可查询、原始 Token/Duration Usage 与 429/Timeout 分类 | 价格结算、跨区域容灾、Dead Letter UI 和生产演练延后 P19/P21/P22               |

Phase 13 没有迁入上游实现代码或新增 Provider 私表。Runtime V2、ObjectRef 和 Outbox 继续是权威恢复链；AgentScope 只存在于独立 Provider 模块。

## 14. Phase 14 实际处置

| 候选能力                             | 分类              | AgentArk 落点                                              | 明确边界                                                                                       |
| ------------------------------------ | ----------------- | ---------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `SimpleKnowledge`、Reader/Chunk 组合 | `REFERENCE/ADAPT` | 安全扫描、进程 Parser、Profile Chunk、异步 Worker          | 不复制单体 Knowledge；状态机和租户授权仍由 AgentArk 拥有                                       |
| `VDBStoreBase`、`QdrantStore`        | `REFERENCE/ADAPT` | 中立 `KnowledgeVectorStore` 与 Qdrant REST Adapter         | 不导出 Qdrant 类型；Collection 不是授权事实                                                    |
| AgentScope Knowledge/Hook/Tool 绑定  | `ADAPT`           | `adapter.out.vector.agentscope.AgentScopeKnowledgeAdapter` | 使用正式 Toolkit Tool 扩展点；固定 Revision/ACL，拒绝旧 Knowledge 类型和 Provider 直读 Catalog |
| AgentScope Embedding Provider        | `REFERENCE/DEFER` | `EmbeddingProvider`、`QueryEmbeddingProvider` Port         | Phase 14 测试使用 Fake；生产 Provider 和 Secret 装配按后续工作包交付                           |
| Scheduler 异步任务模式               | `REFERENCE/DEFER` | `KnowledgeIngestionWorker` 可复用管线                      | 持久 Job/Attempt、Retry/Dead Letter 和 Handler 装配归 Phase 15                                 |

Phase 14 未复制上游源码或第三方资产。新增 `agentscope-core` 依赖只用于正式 `Toolkit`/`@Tool` API，许可证继续由既有 SBOM/NOTICE 门禁跟踪；Qdrant 通过标准 REST 调用，没有引入 Qdrant SDK 依赖。

## 15. Phase 15 实际处置

| 候选能力                                        | 分类               | AgentArk 落点                                                                                | 明确边界                                                                                       |
| ----------------------------------------------- | ------------------ | -------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `CronDeploymentScheduler` 分钟扫描与 Fire Lease | `ADAPT`            | `TriggerDefinitionService`、`CronCalculator`、`CronTriggerService`、`trigger_cursor`         | 计算、Cursor 推进和 Handler 执行分离；拒绝失败只写日志和进程时间窗去重                         |
| Scheduler Channel Runtime                       | `ADAPT/REJECT`     | 中立 `ChannelGateway`、`ChannelMessageJobHandler` 与 `adapter.out.channel.agentscope` Bridge | 不阻塞轮询 Runtime Event，不导入 Harness，不在 Scheduler 执行推理循环                          |
| Outbound/Hands Worker                           | `ADAPT`            | Durable Job/Attempt/Lease、Delivery、Retry Budget、Dead Letter、类型隔离 Worker Pool         | 拒绝无 Attempt/Fencing 的进程内重试；无幂等声明的写操作默认不自动重试                          |
| `service-common` Coordination/Entity            | `REFERENCE/REJECT` | Scheduler 独占 MyBatis Mapper、V2 Flyway、Owner + Fencing Token                              | 不复制 JPA Entity、共享 Repository、跨 Schema SQL 或 Auto-DDL                                  |
| AgentScope Channel 能力                         | `REFERENCE`        | 独立 Bridge SPI 只映射中立消息                                                               | 固定上游没有可直接迁入的 Durable Job 语义；具体 Provider 和许可在组合层单独评审                |
| Knowledge Ingestion 调度                        | `ADAPT`            | `KnowledgeIngestionJobHandler` 调用 Phase 14 Worker，结果经 Control Internal Client 幂等提交 | Worker 不写 Control DB；缺少真实扫描/Parser/Embedding/Object/Qdrant Provider 时 Handler 不注册 |
| Agent Turn 调度                                 | `ADAPT`            | `RuntimeTurnJobHandler` 调用 `/internal/v1/runtime/turns`                                    | Scheduler POM 不依赖 Runtime 或 AgentScope Provider；Runtime 事务提交后才返回稳定 Run ID       |

Phase 15 新增源码、SQL 和 Contract 均为 AgentArk 独立实现，没有复制上游文件或第三方资产。Scheduler 只依赖 AgentArk 中立 Knowledge 契约；AgentScope Channel 适配包当前是版本 Bridge SPI，不把框架类型引入 Scheduler 制品。

## 16. Phase 16 实际处置

| 候选能力                            | 分类               | AgentArk 落点                                                     | 明确边界                                                                 |
| ----------------------------------- | ------------------ | ----------------------------------------------------------------- | ------------------------------------------------------------------------ |
| Gateway 静态 Route 与 Internal 拒绝 | `ADAPT`            | `GatewayRouteConfiguration` 固定优先级路由和 `/internal/**` 404   | 不复制上游 YAML；目标 URL 来自受控配置，不代理内部 API                   |
| 上游 Header 清洗                    | `ADAPT`            | `GatewayHeaderSanitizationFilter` 删除派生身份与客户端证书 Header | 保留原始签名凭据供下游独立验证；租户 Header 只表示选择意图               |
| 上游全局一小时响应超时              | `REFERENCE/REJECT` | 仅 SSE Route 禁用普通响应超时并关闭代理缓冲                       | 普通 API 保持 30 秒默认超时，禁止为流式请求放宽所有路由                  |
| 上游缺失 OIDC/CORS/限流             | `ADAPT`            | Foundation JWT/JWK、精确 CORS、Redis 固定窗口限流                 | 拒绝共享 HMAC Secret、固定内部 Token、生产通配 CORS 和限流失败静默放行   |
| API Key 前置认证                    | `ADAPT`            | Control 内部摘要自省、Gateway SHA-256 键短 TTL 正缓存             | 明文只在当前请求；无效结果与错误不缓存；Runtime/Scheduler 不接受 API Key |
| Gateway 业务 DTO/Repository         | `REJECT`           | 无落点                                                            | Gateway 不连接业务数据库，不依赖 Control/Runtime/Scheduler 实现模块      |

Phase 16 没有复制上游源码或资产。Gateway 只新增边缘 Adapter、配置与测试；Control 仅新增版本化 API Key 自省响应，不改变 API Key 表、摘要算法或 IAM Owner。

## 17. Phase 17 实际处置

| 候选能力                                             | 分类        | AgentArk 落点                                              | 明确边界                                                                                |
| ---------------------------------------------------- | ----------- | ---------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| AgentScope Agent/Environment/Session/Event/HITL/Team | `REFERENCE` | Web IA、Feature 语义、Runtime Timeline/Approval 需求       | 不复制页面、DTO、Client、状态管理、样式或品牌；Phase 18 已在该边界内独立实现业务流程    |
| AgentScope TanStack/Router/Radix/Tailwind 工程经验   | `REFERENCE` | AgentArk 独立依赖选择、Provider/Router 和 Design System    | 不把上游目录或生成模型作为 UI Domain，不引入 Service 私有协议                           |
| DeepSeek Web Entry/Shell/Layout                      | `REFERENCE` | Sidebar/Header/Command/Panel 和 Lazy Shell 的交互层级      | 不迁入 Cordis/Plugin 装配，不复制 React/CSS 源码或测试 Snapshot                         |
| DeepSeek Theme/Terminal/Timeline/Inspector           | `REFERENCE` | AgentArk 自有 Token、主题、Split Pane、Timeline、Inspector | 不复制 `--dsw-*` 数值、Logo、favicon、图片、Glyph、文案或像素资产                       |
| DeepSeek Native/Python/Plugin Runtime                | `REJECT`    | 无落点                                                     | 不进入 Web Package、Lockfile、应用内核或构建流程                                        |
| AgentArk Public OpenAPI/Runtime Event v1             | `ADAPT`     | Orval Fetch Client、Feature Query 封装、Fetch SSE Client   | 只允许仓库内 Schema 引用；Provider Event、Control Entity 和明文 Secret 不进入浏览器模型 |

Phase 17 新增源码、SVG、文案和 Token 均为 AgentArk 独立实现。上游固定 Worktree 保持只读，Web Lockfile 不含 AgentScope Service、DeepSeek Harness、Cordis 或 Plugin Runtime Package；完整证据见 [Web 上游参考边界](../frontend/source-reference.md)。

## 18. Phase 18 实际处置

| 候选能力                                           | 分类        | AgentArk 落点                                                                | 明确边界                                                                              |
| -------------------------------------------------- | ----------- | ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| AgentScope Agent/Environment/Session/HITL 页面语义 | `REFERENCE` | Govern、Build、Release、Runtime、Approval Feature 的状态与操作范围           | 不复制 React 页面、DTO、Client 或 Dataplane 私有协议；只调用 AgentArk Public Contract |
| AgentScope Event/Team/Task 交互                    | `REFERENCE` | 持久 Event Timeline、消息流、调用树、Inspector 和 Approval 关联              | 不序列化 Provider Event，不展示隐藏推理链，不把聊天气泡当唯一事件视图                 |
| DeepSeek Workbench/Split/Command/Dense UI          | `REFERENCE` | AgentArk 自有 App Shell、工作台布局、Command Palette、表格和窄屏导航         | 不复制 Token 数值、CSS/React 源码、品牌、图片、Cordis 或 Plugin Runtime               |
| Control/Runtime/Scheduler Public Contract          | `ADAPT`     | Agent/Deployment 列表、Snapshot/Diff、Runtime 状态/Payload、Job/Trigger 列表 | 只补齐 Web 必需的 Owner API；不共享 Mapper、数据库或 Internal API                     |
| Phase 18 真实产品 E2E                              | `ADAPT`     | 临时 MySQL/Redis、RS256 身份、四服务 Test Classpath 和 Playwright            | 使用确定性测试 Engine；不伪造生产 OIDC、真实模型或外部 Provider 验收                  |

Phase 18 没有迁入上游源码、资产、品牌或新运行时依赖。新增页面、Token、文案、E2E Harness 和截图均为 AgentArk 独立实现；本地截图属于忽略的测试产物。

## 19. Phase 19 实际处置

| 候选能力                                        | 分类               | AgentArk 落点                                              | 明确边界                                                                            |
| ----------------------------------------------- | ------------------ | ---------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| AgentScope Event/Middleware/Model/Tool 指标语义 | `REFERENCE/ADAPT`  | AgentArk 稳定 Span、低基数 Timer、Runtime Event Trace 关联 | 不暴露或序列化 AgentScope Telemetry 类型，不采集隐藏推理链、Prompt 或 Tool 参数     |
| Dataplane Event/Usage                           | `ADAPT`            | Runtime V3 原始 Usage、Control Usage Ledger/聚合与异步汇聚 | 不复制 JPA Entity，不让 Control 读 Runtime Schema，不把 Provider 估算伪装成账单真值 |
| Aistio Dashboard/Audit/Metric                   | `REFERENCE`        | Control append-only Audit、治理 Public API、Web `/observe` | 不复制 Go Migration/API/UI，不继承默认用户或共享 Secret                             |
| AgentScope Frontend Dashboard/Inspect           | `REFERENCE`        | Trace Link、Audit/Usage/Quota/Evaluation 操作视图          | 不复制页面、Client、品牌或本地 Mock 治理事实                                        |
| OpenTelemetry/Micrometer                        | `DEPENDENCY/ADAPT` | Foundation Observability Starter 与四服务配置              | OTel Backend 不成为业务依赖，Metric 禁止无界租户/会话 Label                         |
| Tempo/Prometheus/Grafana                        | `DEPENDENCY`       | `deploy/observability/` 本地开发栈                         | 不声明为生产 HA/安全/保留方案，生产部署归 Phase 22                                  |

Phase 19 没有迁入上游源码或视觉资产。治理 Domain、Flyway、Contracts、Web 页面、Dashboard 和告警均为 AgentArk 独立实现；AgentScope 仍只通过既有 Provider 防腐层提供执行语义。

## 20. Phase 20 实际处置

| 候选能力                                | 分类               | AgentArk 落点                                                   | 明确边界                                                                                  |
| --------------------------------------- | ------------------ | --------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| AgentScope Permission/HITL              | `REFERENCE/ADAPT`  | AgentArk Permission Policy、Approval 参数 Hash、Fencing Resume  | 不复制 Permission 类型；生产不启用 BYPASS，文档/Tool 输出不能提升权限                     |
| AgentScope MCP Transport                | `REFERENCE/ADAPT`  | `McpEndpointGuard` 与 `ConnectionPermit`                        | 不复制 Transport；生产 Component Factory 必须消费固定地址 Permit，不能自行重解析绕过 SSRF |
| AgentScope Sandbox Extensions           | `REFERENCE/DEFER`  | AgentArk Sandbox 安全合同与 Kubernetes 基线                     | 不引入 Docker Socket、本地 Shell 或未审查 E2B/Daytona SDK；真实 Adapter 逐个评审          |
| AgentScope Skill Security Scanner       | `REFERENCE`        | Artifact Hash、Ed25519、CycloneDX、扫描证明和许可证门禁         | 上游启发式扫描不能替代签名、SBOM 或 Sandbox，不复制实现                                   |
| AgentScope State Backend                | `REFERENCE/REJECT` | Runtime `runtime_agent_state`、Checkpoint、ObjectRef 与 Fencing | 禁止 Auto-DDL 和上游 Store 直接连接 Runtime DataSource                                    |
| DeepSeek `THIRD_PARTY_NOTICES` 生成实践 | `REFERENCE`        | AgentArk 自身 Maven/Trivy SBOM、THIRD_PARTY_NOTICES 与 CI       | 不复制 DeepSeek 品牌、插件闭包、特殊许可 Payload 或上游 Notice 内容                       |
| Trivy/Cosign/GitHub Attestation         | `DEPENDENCY`       | 固定 Digest/Commit 的 Security 与 Supply Chain Workflow         | 不使用可变 Scanner/Action 标签；生产镜像必须固定 Digest                                   |

Phase 20 没有迁入 AgentScope 或 DeepSeek 源码、测试 Fixture、品牌和资产。新增安全实现、清单、测试和文档均为 AgentArk 独立代码；上游只提供固定 Commit 的行为与限制证据。

## 21. Phase 21 实际处置

| 候选能力                                         | 分类              | AgentArk 落点                                                                      | 明确边界                                                                                  |
| ------------------------------------------------ | ----------------- | ---------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| Aistio User/Environment/Agent/Version/Deployment | `MIGRATE/ADAPT`   | 显式 Principal 映射、Environment、Agent Draft/Publish、不可变 Snapshot、Deployment | 不迁本地密码/HMAC、API Key Hash 或 Go DTO；Agent 使用 `(owner_id, agent_id)` 复合来源 Key |
| Aistio Vault Credential                          | `ADAPT`           | Control Secret Metadata 与受控 `secretMappings`                                    | 不导出 `ciphertext`，不伪造外部路径；未预先迁到真实 Secret Provider 时阻断                |
| Aistio Cron/Webhook Deployment                   | `ADAPT`           | Control Deployment + Scheduler Trigger Public Contract                             | Webhook 必须配置新 SecretRef；不迁旧 Token，不让 Control 拥有 Job                         |
| Product/Runtime Session 与 Command               | `REFERENCE/ADAPT` | 活动 Owner Pin、终态归档、Runtime Instance 重注册、Command Audit                   | 不重放历史副作用，不把旧 Event 伪造成 Java Run，不切换活动 Session Owner                  |
| Compatibility Proxy/Shadow                       | `ADAPT`           | `tools/migration/aistio_shadow.py`                                                 | 仅 Loopback/GET；显式字段投影、Secret/安全 Gate；不是默认服务或长期 Route                 |
| Team/Task/Message、CRD、ASDP/BYO                 | `DEFER`           | ADR-0006 Backlog                                                                   | 需要独立 Collaboration/Deployment Adapter Contract，不阻塞核心切换                        |
| Hosted `dp_*` Store、Go 本地认证、Aistio UI      | `REJECT`          | 无长期落点                                                                         | AgentArk 已有中立 Runtime Ports、OIDC/JWK 和独立 Web，不翻译表或复制静态资产              |

Phase 21 新增迁移工具、Schema、Fixture、测试和文档均为 AgentArk 独立实现，没有把任何候选提升为文件级 `REUSE`。默认 Flags 为 `JAVA_ONLY`，Go Writes/Fallback 为 `DISABLED`；真实外部 Aistio Cohort 仍须按 Runbook 产生独立批准证据。

## 22. 变更协议

后续 Phase 改变任何分类时，必须同时更新：本清单、对应阶段报告、行为测试引用和 [许可清单](license-and-notice.md)。从 `REFERENCE/DEFER/REJECT` 提升到 `REUSE` 属于显著风险变化，必须给出文件级来源、目标路径、许可证和回滚证据。
