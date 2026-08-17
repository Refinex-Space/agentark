---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: docs/README.md#上游迁移审计
---

# Phase 01 上游源码清单

## 1. 审计边界

本清单只描述两个固定 detached Worktree 的事实，不把移动分支、在线文档或本地来源仓库当前 HEAD 当作证据：

| 来源 | Commit | 固定视图 | 阶段前状态 |
|---|---|---|---|
| AgentScope Java | `0c61e7494197ded54eefdeaf9bdeb51807beb752` | `.agentark/upstreams/agentscope-java-2.0.2` | detached、clean |
| DeepSeek Harness | `47f943859bef60e4160492346772ded9b24f765a` | `.agentark/upstreams/deepseek-harness` | detached、clean |

本阶段未迁移实现、未运行会在固定视图中生成文件的构建或格式化命令。逐路径取用结论见 [迁移清单](migration-manifest.md)，关键行为见 [行为基线](behavior-baseline.md)。

## 2. AgentScope Service 总览

`agentscope-service/pom.xml` 是四个 Java 模块的聚合 POM；同级另有 Go 控制面 `aistio/` 和 React 控制台 `frontend/`。

| 区域 | 主文件数 | 测试文件数 | 启动入口 | 主要依赖/职责 |
|---|---:|---:|---|---|
| `service-common` | 107 | 7 | 无 | 共享 DTO、错误、安全、配置、协调、JPA Entity/Repository、资源服务、Agent State |
| `service-gateway` | 2 | 2 | `io.agentscope.builder.GatewayApp` | Spring Cloud Gateway、静态 Route、Header 清洗、SSE 长超时 |
| `service-dataplane` | 39 | 7 | `io.agentscope.builder.DataApp` | Session Turn、Event/SSE、HITL、Lease、Work Queue、Harness 组装 |
| `service-scheduler` | 16 | 3 | `io.agentscope.builder.SchedulerApp` | Cron、Channel Runtime、Outbound、Self-hosted Hands Worker |
| `aistio` | 311 个版本化文件 | 44 个 Go 测试文件 | `cmd/aistiod` | K8s Control、ASDP、Runtime Store、产品控制面、Console 静态资源 |
| `frontend` | 110 个版本化文件 | 0 | `src/main.tsx` | Vite/React 控制台、功能语义与 API Client |

Java Service 父 POM 使用 Spring Boot `4.0.4`、Spring Cloud `2025.1.2`。`service-common`、Dataplane 和 Scheduler 都直接接触同一组 JPA 实体或 Repository；这只是上游事实，不符合 AgentArk 的 Control/Runtime/Scheduler 数据所有权。

### 2.1 配置与本地拓扑

| 配置来源 | 关键配置 | 审计结论 |
|---|---|---|
| `service-gateway/src/main/resources/application.yml` | 端口 `8080`；Control/Data/Scheduler URL；Gateway Route；5 秒连接超时、1 小时响应超时；Actuator | Route 与 Header 清洗可 `ADAPT`；长超时只能用于 SSE Route，不能成为全局默认 |
| `service-dataplane/src/main/resources/application.yml` | 端口 `8082`；PostgreSQL `dp` Schema；JPA；JWT/Internal Token/Vault Key；Control URL；Lease；Workspace；E2B；DashScope | 包含开发 Secret fallback、`ddl-auto=update` 和共享 JWT；均不得进入 AgentArk 生产配置 |
| `service-scheduler/src/main/resources/application.yml` | 端口 `8083`；与 Dataplane 共用 `dp` 数据源/JPA；Control/Data URL；Channel reconcile 退避；Reply timeout/poll interval | 共享表和阻塞轮询不保留；配置归 Scheduler Owner，并进入版本化配置参考 |
| `docker-compose.yml`、`docker/postgres-init.sql` | Gateway/Control/Data/Scheduler 为 `8080`–`8083`；PostgreSQL 17 创建 `cp/rt/dp`；默认开发凭据；模型 Key 透传 | 仅用于上游本地复现；默认凭据、固定数据库密码和直接暴露数据库端口均为 `REJECT` |
| `docker/Dockerfile.plane` | 运行预构建 Java Plane JAR | Compose 仍引用 `2.0.1-SNAPSHOT` JAR，而固定源码为 2.0.2；Phase 02 复现前必须显式修正或记录该机械基线偏差 |
| `scripts/dev-up.sh`、`dev-down.sh`、`smoke.sh` | 构建/启动/清理本地栈，执行 API Smoke | 会创建容器、进程和 `.dev-stack/`，只能在隔离可写工作区运行；Smoke 还依赖模型凭据 |

`service-common` 没有独立 `application.yml`；它通过配置类、`@ConfigurationProperties`、共享 Entity/Repository 和消费模块的资源文件完成装配。这也是其不能整体迁入的原因之一。

## 3. `service-common` 职责清单与拆分目标

`service-common/src/main/java/io/agentscope/builder` 不是普通工具包，而是多个平面的实现混合物。

| 上游包/类群 | 实际职责 | AgentArk 拆分目标 |
|---|---|---|
| `web.api.error` | `ApiException`、错误分类、异常处理 | `agentark-starter-web` 的 Problem Details；稳定错误码进入 `agentark-kernel` |
| `web.auth` | JWT、内部 Token、Environment Key Filter、启动校验 | `agentark-starter-security`；用户身份归 `agentark-control`，服务身份和环境凭据使用独立机制 |
| `runtime.config`、`web.config` | Agent/Binding/Channel 配置与 JPA 装配 | 领域配置分别进入 Control、Scheduling；基础装配进入相应 Starter/Server |
| `web.catalog`、`catalog.spec` | Agent 定义、Spec 编解码、Definition Store | Draft/Revision/Snapshot 归 `agentark-control`；AgentScope 转换归 Provider |
| `web.coord` | Lease、Interrupt、Cron Fire、HITL、Work Queue、Worker Heartbeat | Runtime Lease/HITL/Queue 归 `agentark-runtime`；Cron/Job 归 `agentark-scheduling` |
| `web.managed` | Session/Agent/Environment/Memory/Vault DTO 和事件类型 | 按公共/Internal 契约与各领域 Adapter 重建，禁止形成共享 DTO 包 |
| `web.managed.service` | ACL、Event Log、Memory/Vault、Session 协调服务 | 分别进入 Control、Runtime；不允许跨平面 Service 共享 Repository |
| `web.persistence.jpa` | 20 个实体、Repository、JPA AgentStateStore | 使用目标 MyBatis-Plus/Flyway 模型按 Schema Owner 重建；不保留 JPA Entity |
| `web.share` | 资源共享 ACL | `agentark-control` 授权域 |
| `web.workspace` | 共享 Workspace 路径 | Control 的 Workspace 资产与 Runtime Mount Port 分离 |

对应上游测试为 `ApiExceptionTest`、`AgentAclServiceTest`、`AgentVersionSnapshotTest`、`JpaAgentStateStoreTransactionTest`、`SessionEventLogCrossProcessTest`、`TurnInterruptCoordinationTest`。`BuilderCommonTestApp` 只是测试装配。

## 4. Java 数据模型

上游 `service-common` 有 20 个 JPA 表映射：

| Entity | Table | 上游混合用途 | AgentArk Owner |
|---|---|---|---|
| `UserEntity` | `builder_user` | 用户 | Control |
| `AgentEntity` | `builder_agent` | Agent 草稿 | Control |
| `AgentVersionEntity` | `builder_agent_version` | 版本快照 | Control |
| `AgentShareEntity` | `builder_agent_share` | Agent 分享 | Control |
| `ResourceShareEntity` | `builder_resource_share` | 通用分享 | Control |
| `EnvironmentEntity` | `builder_environment` | 运行环境 | Control |
| `DeploymentEntity` | `builder_deployment` | Deployment/Cron 配置 | Control；Trigger 投影到 Scheduler |
| `ManagedSessionEntity` | `builder_session` | Session 生命周期 | Runtime；Control 只保留发布/部署引用 |
| `SessionEventEntity` | `builder_session_event` | Event Log/SSE | Runtime |
| `AgentStateEntity` | `builder_agent_state` | Harness State | Runtime |
| `CoordLeaseEntity` | `builder_coord_lease` | Turn/Cron Lease | 按 Runtime/Scheduler 分表并带 fencing |
| `CoordHitlTicketEntity` | `builder_coord_hitl` | HITL Ticket | Runtime |
| `CoordWorkItemEntity` | `builder_coord_work` | Hands Work Queue | Runtime |
| `CoordWorkerHeartbeatEntity` | `builder_coord_worker` | Worker Presence | Runtime |
| `MemoryStoreEntity` | `builder_memory_store` | Memory Store | Control 资产 |
| `MemoryEntity` | `builder_memory` | Memory Document | Control/Runtime 端口，禁止共享表 |
| `MemoryVersionEntity` | `builder_memory_version` | Memory Version | Control |
| `VaultEntity` | `builder_vault` | Vault 元数据 | Control |
| `VaultCredentialEntity` | `builder_vault_credential` | 加密凭据 | Control Secret 域 |
| `UserMarketplaceEntity` | `builder_user_marketplace` | Skill Marketplace | Control |

这些表是迁移语义来源，不是 AgentArk DDL。AgentArk 的最终逻辑模型以 `docs/database/` 为准。

## 5. Dataplane

### 5.1 包与关键类

| 包/类 | 行为 |
|---|---|
| `web.api.DataSessionApiController` | 事件写入/查询/清理、SSE、Hands Stats；认证后再访问 Session |
| `web.managed.DataSessionService` | 通过 `ControlPlaneClient` 解析 Session，管理 Runtime 状态和本地 Event Log |
| `web.managed.SessionTurnRunner` | 先获 Turn Lease、再持久化 admitted 用户消息；构建/缓存 Agent；异步执行、中断、释放 |
| `web.catalog.HarnessAgentBuildService` | 从 Control resolve 的 Agent Snapshot 组装 `HarnessAgent.Builder` |
| `web.managed.EnvironmentSpecFactory` | `local/sandbox/remote/self_hosted` 到 Filesystem/Sandbox Spec 的映射 |
| `web.managed.service.SessionEventLog` | 数据库分配 Session 序号、游标查询、跨进程轮询订阅、Transcript 清理 |
| `web.coord.TurnLeaseService`、`JdbcCoordinationStore` | Turn/Cron Lease、Interrupt、HITL、Work Queue 和 Heartbeat |
| `web.toolbus.ToolNotificationMiddleware` | 将 Tool Call/Result 转为流事件 |
| `web.toolbus.ToolConfirmationMiddleware` | `always_allow/always_ask/deny` Policy |
| `web.toolbus.ToolConfirmationCoordinator` | 持久化 Ticket，发 `session.requires_action`，轮询恢复 |
| `web.managed.HandsLeaseService`、`web.api.WorkerEnvironmentController` | Self-hosted Hands Work claim/ack/heartbeat/stop/result |
| `web.api.SelfHostedWorkerController` | Environment Key 认证的 pending tools、skills、tool results |

### 5.2 Endpoint

| Method/Path | 语义 |
|---|---|
| `POST /api/sessions/{id}/events` | 接收 `user.message`、interrupt、tool confirmation/result、outcome、system message |
| `GET /api/sessions/{id}/events` | 按 `after` 与 `types` 读取持久事件 |
| `DELETE /api/sessions/{id}/events` | 用户清 Transcript；内部调用可 Purge/Release |
| `GET /api/sessions/{id}/events/stream` | SSE，合并持久事件与 preview delta |
| `GET /api/sessions/{id}/hands-stats` | Hands 统计 |
| `GET /api/environments/{id}/work` | Worker claim/poll Work Item |
| `GET /api/environments/{id}/work/{workId}` | 读取 Work Item |
| `POST /api/environments/{id}/work/{workId}/ack` | 完成 Work |
| `POST /api/environments/{id}/work/{workId}/heartbeat` | 续约/心跳 |
| `POST /api/environments/{id}/work/{workId}/stop` | 停止 Work |
| `POST /api/environments/{id}/work/{workId}/update` | 上报进度 |
| `GET /api/environments/{id}/sessions/{sessionId}/pending-tools` | Self-hosted 待执行工具 |
| `POST /api/environments/{id}/sessions/{sessionId}/tool-results` | 回传工具结果并恢复 Turn |
| `GET /api/environments/{id}/sessions/{sessionId}/skills` | Worker 获取 Skill |
| `GET /agentscope/*` | BYO Agent info/health/session/context/messages/terminate/compress |

SSE Frame 只设置 `event=<type>` 和 JSON `data`，没有 SSE `id`，也没有处理 `Last-Event-ID`；续传依赖 `after` Query。Preview 使用 `seq=-1` 且不持久化。`agentOverrides` 的 Runtime 合并会明确记录“不持久化”的警告。这两点必须在 AgentArk 中修正。

### 5.3 Harness 组装

`HarnessAgentBuildService` 从 Control Plane resolve payload 获取版本固定的 Snapshot、Workspace 和 Definition Files，设置：

- stable `agentId`、显示名、System Prompt、Model、`maxIters`；
- Workspace、`AgentStateStore`、Tools/MCP、Vault 解析后的 `ToolsConfig`；
- Definition Store 和 Skill Repository；
- `ToolNotificationMiddleware`、`ToolConfirmationMiddleware`；
- Environment Filesystem/Sandbox、Memory/Resource Mount、Team Middleware。

普通 Session 缓存键为 `owner/agent/spec.cacheSuffix()`；Team Session 追加 Session ID，避免不同 Team 继承错误角色。该编译过程应迁入 `agentark-runtime-provider-agentscope`，缓存生命周期和 Snapshot Hash 由 AgentArk Runtime 约束。

## 6. Scheduler

| 类 | 行为与限制 |
|---|---|
| `CronDeploymentScheduler` | 每分钟扫描启用的 `DeploymentEntity`，按分钟窗口抢 2 分钟 Fire Lease，再调用 Control `/api/internal/deployments/{id}/fire` |
| `SchedulerChannelRuntime` | 从 Control 拉 Channel 配置、指纹对比、启动/停止 DingTalk/Feishu/WeCom/GitHub/GitLab Channel，失败后退避重试 |
| `ManagedSessionChannelBridge` | find-or-create Session，向 Dataplane 投递用户消息，轮询 Event 直到终态后回复 Channel |
| `SchedulerGateway` | 将 Harness Channel 入站映射为租户、Agent 和稳定 External Key |
| `OutboundController` / `OutboundService` | `POST /api/outbound/send`，通过 `ChannelManager` 与 `ChannelRouter` 投递 |
| `HandsWorkerMain` | 独立 Self-hosted Worker：poll、执行本地工具、heartbeat、ack/update |
| `LocalHandsToolExecutor` | 本地文件/命令工具实现，生产需要 Sandbox/Policy 重新审计 |

上游没有独立的持久化 Job/Attempt/Dead Letter 聚合；Cron 失败只记录日志，下个周期是否重试取决于 `lastRunAt` 和 Lease 行为；Channel Reply 使用阻塞轮询。这些只能作为行为参考，不能满足 `scheduler-schema.md`。

## 7. Gateway

Gateway 仅有启动类、YAML Route 和两项测试。

| Route 组 | 目标 | 边缘行为 |
|---|---|---|
| `/api/internal/**` | 本地 404 | 阻止公共入口访问 Internal API |
| Session Event/Hands、Environment Work | Dataplane | 1 小时响应超时用于 SSE |
| Scheduler Outbound/Callback | Scheduler | 静态路由 |
| Auth/User/Admin/Agent/Workspace/Memory/Vault/Deployment/Channel/File、Session Lifecycle | Control | 移除 Environment Key |
| 其他路径 | Control | Console SPA fallback |

Gateway 会移除外部请求中的 `X-Builder-Internal-Token`、`X-Builder-Internal-User`，但没有自定义认证 Filter、CORS Policy 或 Rate Limiter；Dataplane 自己配置宽松 CORS。AgentArk Gateway 必须补齐 OIDC/JWK、租户级限流、标准 CORS 和 SSE Backpressure，不能把“存在错误枚举 429”误报为已有限流。

## 8. Go Aistio

### 8.1 三套职责

| 区域 | 职责 |
|---|---|
| `api/v1alpha1`、`internal/controller`、`config/crd`、`helm` | Agent、AgentTeam、ModelConfig、MCPServer、SandboxClaim 的 Kubernetes 控制器/CRD |
| `internal/httpapi`、`internal/sessionops`、`internal/store`、`internal/dataplane` | Runtime Session、Turn、Event、Command、Team Task/Message、Data Plane Registry |
| `internal/product` | Console 产品 API、用户/JWT、Agent/Workspace/Session/Memory/Vault/Deployment/Channel/File |

`cmd/aistiod` 将它们装配到一个进程。`AISTIO_ENABLE_KUBERNETES=false` 时可只运行产品和 Hosted Runtime 路径。

### 8.2 产品 API 与认证

产品 API 以 Gin 注册，主要资源组为：

- `/api/auth`、`/api/user`、`/api/admin/users`；
- `/api/agents`、versions、clone、shares、bindings、presences、skills、tools；
- `/api/workspaces`、files、skills、subagents、tools、marketplaces；
- `/api/environments`、`/api/sessions`、`/api/memory-stores`、`/api/vaults`；
- `/api/deployments` 的 CRUD、archive、run、pause、unpause、webhook；
- `/api/channels`、`/api/files`；
- `/api/internal/sessions/*/resolve|runtime|overrides`、Environment Key、Agent Version、Vault、Memory Mount、Deployment Fire、Channel Runtime。

Console 身份为 7 天 HS256 JWT；密码用 bcrypt。Internal API 依赖共享 `X-Builder-Internal-Token`，可通过 `X-Builder-Internal-User` 注入 acting user。Webhook 和登录公开。上游 `seed.go` 会把默认用户名/明文密码写入日志，只能拒绝。

### 8.3 数据库迁移

产品面 `internal/product/migrate.go` 在启动时直接执行一段幂等 DDL，创建 `users`、`agents`、`agent_versions`、`environments`、`sessions`、`memory_stores`、`memories`、`memory_versions`、`vaults`、`vault_credentials`、`deployments`、`resource_shares`、`channels`、`agent_bindings`、`files`、`workspaces`、`workspace_files`、`marketplaces`。它不是有序版本化迁移。

Runtime Store 使用 PostgreSQL Advisory Lock 和 `0001`–`0009` 嵌入式 up/down SQL，表包括：

- `sessions`、`session_turns`、`session_events`、`session_commands`、`session_snapshots`、`session_transcript_index`；
- `context_snapshots`、`token_usage_metrics`、`agent_metrics`；
- `data_planes`、`dp_kv`、`dp_locks`、`dp_snapshots`、`dp_bus_entries`、`dp_async_tools`、`dp_tasks`；
- `teams`、`team_members`、`team_messages`、`team_tasks`、`team_task_history`。

两套 Schema 和 Java `dp` JPA 表存在语义重叠。AgentArk 只参考资源与状态语义，使用已有 MySQL/Flyway 三 Schema 设计，不迁入 Go DDL。

## 9. AgentScope Service Frontend

### 9.1 工程与状态

`frontend/package.json` 的真实名称是 `agentscope-service-frontend`，React 18 + React Router 6 + TanStack Query 5 + Radix + Tailwind 4，使用 Vite。它同时存在 `package-lock.json`，没有 pnpm Lockfile，也没有测试脚本或测试文件。

状态分两层：Server State 主要由 TanStack Query 管理，页面交互和 Chat Stream 使用本地 React State/Ref；未使用 Redux/Zustand。`src/api/*` 与 `src/lib/apiClient.ts` 是 API Client。

### 9.2 页面/能力

| 工作区 | 页面/组件 |
|---|---|
| Build | Agents、Agent Settings/Workspace/Skills/Tools/Subagents/Channels、Workspaces、Environments、Memory Stores、Vaults、Deployments |
| Run | Sessions、Session Detail、`ChatPanel`、Transcript、Event Timeline、Tool Call、HITL Confirmation |
| Operate | Fleet、Agents、Sessions、Governance |
| Teams | Overview/List/Create/Detail/Templates |
| Admin | Login/Profile/User Admin |

`api/managedSessions.ts` 定义 `ManagedSession`、`SessionEvent`、Inbound Event、SSE 和 HITL API。SSE 为带 Bearer Header 的 `fetch` 流解析，不是 `EventSource`；通过空行切 Frame，只提取 `data:`，坏 JSON 被静默忽略，也没有自动重连。AgentArk 应复用功能语义，不复用该解析器或视觉实现。

## 10. AgentScope Core、Harness 与 Extensions

Core 有 304 个主文件、204 个测试文件；Harness 有 262 个主文件、119 个测试文件。AgentArk 应通过 Maven 依赖使用它们，不复制框架核心。

| 能力 | 固定源码位置 | 代表测试/示例 | 决策 |
|---|---|---|---|
| Agent/Builder | `agentscope-harness/.../HarnessAgent.java`、`HarnessAgentBuilderSupport.java` | `HarnessAgentTest`、`HarnessAgentIntegrationExampleTest` | 依赖 |
| RuntimeContext/Event | `agentscope-core/.../agent/RuntimeContext.java`、`Event.java`、`EventType.java` | `RuntimeContextTest`、`EventTest`、`AgentEventStreamTest` | 依赖并在 Provider 转换 |
| Message/ContentBlock | `agentscope-core/.../message/` | Core Message/Tool Result tests | 依赖并映射为中立 Event |
| Permission/HITL | `agentscope-core/.../permission/`、Tool Suspend/Interrupt 机制 | `PermissionEngineTest`、`AgentSpawnToolPermissionTest`、Model E2E `HITLBasicE2ETest` | 依赖；平台审批另建领域模型 |
| Middleware | `agentscope-core/.../middleware/`、`agentscope-harness/.../middleware/` | `HarnessMiddlewareOrderTest`、各 Middleware Test | 依赖 |
| Workspace/Filesystem | `agentscope-harness/.../workspace/`、`filesystem/` | `WorkspaceManagerPathSafetyTest`、`WorkspaceResolutionTest` | 依赖；路径策略外加平台约束 |
| Memory/Compaction | `agentscope-core/.../memory/`、`agentscope-harness/.../memory/` | `MemoryFlushManagerTest`、Compaction/State tests | 依赖 |
| Skill | `agentscope-core/.../skill/`、`agentscope-harness/.../skill/` | `SkillRuntimeTest`、`SkillSecurityScannerTest` | 依赖；供应链治理由平台负责 |
| Sub-Agent/Team | `agentscope-core/.../tool/subagent/`、`agentscope-harness/.../subagent|team/` | `SubagentIsolationIntegrationTest`、`TeamsMiddlewareTest` | 依赖 |
| State/Distributed | `agentscope-core/.../state/AgentStateStore.java`、Harness `DistributedStore`/`BaseStore` | `AgentStateStoreVersioningContractTest`、`InMemoryStoreCASTest` | Port 适配 |
| Sandbox | Harness `sandbox/`、`filesystem/sandbox/` | `SandboxManagerIsolationTest`、`WorkspaceMountSupportTest` | 依赖，Provider 组装 |
| MCP | Core `tool/McpClientManager.java`、`tool/mcp/`，Harness `tools/McpServerRegistrar.java` | `McpClientManagerTest`、`McpToolTest` | 依赖 |
| RAG/Knowledge | Core `rag/` | `KnowledgeTest`；Extensions 各 Provider tests | `agentark-knowledge` 受控 Adapter |
| Channel | Harness `gateway/ChannelManager`、`gateway/channel/` | `ChannelRouterOutboundAddressTest` | 依赖；Scheduler Adapter |
| A2A | Extensions Protocol `agentscope-extensions-a2a-client/server` | `A2aAgentTest`、Server Converter tests | 延后到对应协议 Phase |
| AG-UI | `agentscope-extensions-agui`、AGUI Spring Boot Starter | `AguiAgentAdapterV2Test`、`AguiResumeCoordinatorTest` | 延后并契约适配 |
| Model Provider | Core `ModelProvider` SPI；Extensions `openai/dashscope/anthropic/gemini/ollama` | 每个 Provider Test 与 Model E2E | 按需依赖，不复制 SDK 封装 |

### 10.1 Phase 09 RAG/Knowledge 定位结果

固定 AgentScope `0c61e7494197ded54eefdeaf9bdeb51807beb752` 的 RAG 能力分为旧 Core 抽象、Simple 实现和外部托管集成三层：

| 能力 | 固定源码 | 代表测试 | Phase 09 结论 |
|---|---|---|---|
| 旧 Knowledge 契约 | `agentscope-core/src/main/java/io/agentscope/core/rag/Knowledge.java`、`model/Document.java`、`DocumentMetadata.java` | `agentscope-core/src/test/java/io/agentscope/core/legacy/rag/KnowledgeTest.java` | 2.0.0 起已标记 deprecated；只参考检索输入输出，不作为平台模型 |
| Simple Knowledge | `agentscope-extensions-rag-simple/.../knowledge/SimpleKnowledge.java` | `SimpleKnowledgeTest`、`RAGInMemoryE2ETest` | 直接组合 Embedding 与 VDB Store，缺少租户、不可变版本、ACL 和摄取状态；只 `REFERENCE` |
| Reader/Parser/Chunk | `reader/Reader.java`、`AbstractChunkingReader.java`、`TextChunker.java`、PDF/Word/Tika/Image Reader | `TextChunkerTest`、`PDFReaderTest`、`WordReaderTest`、`TikaReaderTest` | 提炼为 `DocumentParser` 与 `ChunkingStrategy` Port；实现延后到 P14 |
| Embedding | `embedding/EmbeddingModel.java` 及 OpenAI/DashScope/Ollama 实现 | 各 Embedding 单元与 E2E 测试 | 提炼为 `EmbeddingProvider` Port；凭据只通过 Phase 08 `SecretRef` |
| Vector Store | `store/VDBStoreBase.java`、`QdrantStore.java`、`ElasticsearchStore.java`、`MilvusStore.java`、`PgVectorStore.java` | `VDBStoreBaseTest` 与各 Store Test | 提炼为 `VectorIndex` Port；Collection 名绝不作为租户授权 |
| Retriever/Reranker | `SimpleKnowledge.retrieve(...)`、Bailian/Dify/Haystack/RAGFlow 配置与转换 | 各集成 `KnowledgeTest`、Rerank Config Test | 拆成 `Retriever`、`Reranker` 和不可变 Retrieval Profile；托管产品 API 延后评估 |

AgentScope Service 的 Aistio Product Handler、Java Service Controller 和 Frontend 全量索引中没有独立 Knowledge Base、Document、Ingestion 管理 API 或页面。Phase 09 因此不能声称“迁移了现成 Service 功能”，而是依据 Framework RAG 行为和 AgentArk Control Owner 独立建立平台模型。

Extension 后端还包括 Redis/MySQL/PostgreSQL/OSS/COS Distributed Store，E2B/Daytona/Kubernetes/AgentRun Sandbox，Simple/Bailian/Dify/Haystack/RAGFlow RAG，以及 Channel、Scheduler 和 Spring Boot Starters。是否引入必须由对应 Phase 按目标基础设施和许可单独决策，不能把整个 `agentscope-extensions` 加入运行闭包。

## 11. DeepSeek Harness 前端真实入口

### 11.1 Web Shell

仓库根包为 `@deepseek-ai/dsh-root@0.1.0-rc.5`，要求 Node `^22.19.0 || >=24`、pnpm `11.7.0`。真正 Web 应用是：

```text
apps/web
└── @deepseek-ai/dsh-web-frontend
    └── src/main.ts
        └── new AppWebEntry(root).run()
            └── @deepseek-ai/dsh-client-web
```

`apps/web` 只是 Vite 薄入口；Shell、模块种子、启动 Gate 和 React 装配位于 `packages/client/web`。运行链分层为：

```text
packages/client/connection
  -> packages/client/runtime (SessionManager / Session / projection)
  -> packages/client/web-react (React binding / scoped slots)
  -> packages/client/web (AppWebEntry / AppRoot / assembly)
  -> packages/client/ui-* (presentation)
```

### 11.2 可借鉴 UI 区域

| 能力 | 真正位置 | AgentArk 取用边界 |
|---|---|---|
| Design Token/Theme | `packages/client/ui-theme/src/styles/*.css`（主入口为 `design-platform.css`、`base.css`），共 514 个 `--dsw-*` 声明 | 视觉度量参考；重建设计 Token，不复制品牌变量集合 |
| Layout | `ui-layout/AppFrame.*` | 多列 Shell、可折叠面板参考 |
| Sidebar | `ui-sidebar/SidebarRoot.*` | 会话/导航密度与响应式参考 |
| Conversation | `ui-conversation/chat`、`skeleton`、`input`、`queue` | 流式消息、Composer、Approval、Queue 交互参考 |
| Terminal/Tool | `ui-primitives/TerminalBlock.*`、`ui-tool/tool` | 命令、读写、搜索、Diff、结果折叠参考 |
| Timeline | `ui-trajectory/TrajectoryTimeline.*`、`TrajectoryTable.*` | Turn/Tool Timeline 与虚拟化参考 |
| Workspace | `ui-workspace/WorkspaceBrowser.*` | 文件树与选择参考；上游没有通用 Monaco/CodeMirror 编辑器 |
| Markdown/Inspector | `ui-primitives/markdown`、`JsonTree.*` | 不可信内容渲染、增量 Markdown、安全链接参考 |
| Primitives | `ui-primitives` | Button/Menu/Modal/Toast/RiskConfirmation 等交互参考 |

这些包围绕 Cordis Slot/Plugin 架构组装。AgentArk 不迁入 Cordis、`ui-slots`、插件清单/配置页或 Everything-is-a-plugin 内核；视觉和交互需按 AgentArk 独立信息架构实现。

### 11.3 测试位置

- `apps/web/tests/*.e2e.ts` 覆盖 Approval、Plan、Queue、Long Chat、Scroll、Trajectory Virtualization、Terminal、Workspace、Subagent、Settings 等浏览器行为；
- `packages/client/runtime/tests/manager.client.spec.ts` 覆盖 `SessionManager` 生命周期；
- `packages/client/{connection,web,web-react,ui-*}/tests` 覆盖运行时和组件行为；
- 根命令 `pnpm run test:gui` 运行 Client/Host 测试，`DSH_SNAPSHOT=replay pnpm run test:web:built` 运行已构建 Web 基线。

## 12. 原始构建与启动方式

| 区域 | 上游命令 | 本阶段执行情况 |
|---|---|---|
| Java Monorepo | `mvn install -DskipTests`；测试可用 `mvn test` 或限定 `-pl ... -am test` | 未执行；会写固定 Worktree `target/` |
| Service 本地栈 | `cd agentscope-service && scripts/dev-down.sh && BUILDER_REBUILD=1 scripts/dev-up.sh` | 未执行；会建容器、进程和 `.dev-stack/` |
| Service Smoke | `cd agentscope-service && scripts/smoke.sh` | 未执行；依赖已启动服务和模型凭据 |
| Aistio | `cd agentscope-service/aistio && go test ./...`；Integration 用 `make test-integration` | Phase 21 已在固定 detached Worktree 执行 `go test ./...` 并通过；未执行依赖外部 PostgreSQL/Kubernetes 的 Integration |
| Service Frontend | `cd agentscope-service/frontend && npm install && npm run build` | 未执行；安装依赖且构建会改写 `aistio/ui` |
| DeepSeek GUI | `pnpm run test:gui` | 未执行；当前本机 pnpm 10.33.0 与上游 11.7.0 不匹配 |
| DeepSeek Web | `pnpm run build`，再 `DSH_SNAPSHOT=replay pnpm run test:web:built` | 未执行；会产生构建输出 |

命令是否通过不能由源码审计推断。Phase 02 的隔离机械基线必须在可写临时 Worktree 中实际运行相应构建与测试。

## 13. Phase 10 发布与 Runtime 配置获取复核

Phase 10 继续使用 AgentScope 固定 Commit `0c61e7494197ded54eefdeaf9bdeb51807beb752`，只读复核 Aistio、Dataplane、Frontend 与 Harness 的发布链路，没有迁入上游实现文件。

| 上游证据 | 实际行为 | AgentArk 结论 |
|---|---|---|
| Aistio `AgentVersionService`、Agent Handler/Store | Agent 发生变化时追加版本记录，但仍保留从可变 Agent 回退组装配置的兼容路径 | `ADAPT` 只追加版本意图；`REJECT` 可变回退和 Runtime 直接读目录 |
| Dataplane `ControlPlaneClient` | 注释明确 Dataplane 通过 Control resolve API 获取配置而不是查询 Control 表；认证依赖共享 `X-Builder-Internal-Token` | `ADAPT` 服务间 API 边界；`REJECT` 长期共享 Token，改用受 Audience 约束的 Service Identity |
| Dataplane `HarnessAgentBuildService` | 从 Control resolve payload 组装名称、System Prompt、Model、Tools/MCP、Skills、Workspace、Memory、Sandbox 和 Harness Builder | `REFERENCE/DEPENDENCY`；Phase 12 Provider 只消费完整 `AgentRevisionSnapshot`，不接触 Draft/Control Entity |
| Aistio Deployment、Environment 与 Runtime Command | Deployment 更接近 Cron/Webhook/Channel 运行触发配置，没有 Environment 内 `desiredRevisionId`、Promote/Rollback 指针模型 | `REFERENCE` 触发语义；AgentArk Deployment/Revision Pointer 为独立新设计 |
| Frontend Build/Operate Agent、Environment、Session 页面 | 创建流程直接选择可变 Agent、默认 Environment/Workspace，Session 再选择 Agent 与 Environment | `REFERENCE` 功能流程；AgentArk UI 必须展示 Draft → Validate → Publish → Deployment → Session 的明确边界 |
| Harness `HarnessAgent` Builder | Builder 需要模型、Prompt、Tool/MCP、Skill、Workspace、Memory、Sandbox、Permission 等运行时能力 | `DEPENDENCY/REFERENCE`；Snapshot v1 使用语言中立字段，Phase 12 防腐层转换，禁止复制 Framework 类型 |

固定上游没有提供 AgentArk 所需的 Canonical Snapshot Schema、SHA-256 内容哈希、跨资产发布事务、Outbox、Environment Revision 指针或 ETag Internal Contract。上述能力属于 AgentArk 新设计，不能描述为上游已有实现。

## 14. Phase 11 Runtime 行为消费复核

Phase 11 继续使用同一固定 Commit，只读取 Dataplane、Service Common、Core 与 Harness 已登记源码，没有迁入实现文件。行为消费结果如下：

| 上游证据 | 消费的行为语义 | AgentArk 落点与差异 |
|---|---|---|
| `DataSessionService`、`SessionTurnRunner`、Admission 测试 | Session、Turn 接受、执行与取消生命周期 | 独立拆分 Session、Turn、Run Attempt 与持久 Work Item；Session 固定 Phase 10 Snapshot，禁止 Runtime 直读 Catalog |
| `SessionEventLog` 与跨进程测试 | 数据库 Event Log、Session Sequence 和游标回放 | MySQL Event Store 增加 Run Sequence、全局 Event ID、Fencing、ObjectRef 和不可变 Trigger；SSE 留给 P13 |
| `TurnLeaseService`、`JdbcCoordinationStore`、Interrupt 测试 | Owner 竞争、过期、跨副本取消/恢复 | Claim 时递增 Fencing Token，数据库拒绝旧 Owner 写 Event/State/Checkpoint；不保留进程内权威 Registry |
| HITL Ticket、Tool Confirmation Middleware | PENDING/Decision、参数确认和恢复 | 中立 Approval 保存 Argument Hash 与 Policy Version；AgentScope Middleware 只允许在 P12 Provider 转换 |
| Hands Work Queue 与 Self-hosted 测试 | Poll、Claim、Heartbeat、Ack/Stop 和恢复 | P11 只建立 Durable Queue/Claim/Release/Complete；Controller、Worker Heartbeat、Stop 与权限归 P13/P21 |
| AgentState Store、Checkpoint 相关测试 | 版本化 State 与恢复点 | Runtime MySQL 保存 State Version、Hash、ObjectRef、Commit 可见性和 Checkpoint；拒绝 AgentScope Auto-DDL |

上游只有 Session 级 Event 序号且缺少单调 Fencing Token；AgentArk 的双 Sequence、旧 Token 数据库 Trigger、幂等记录、Runtime Outbox 和 ObjectRef Payload 是为满足既定架构约束新增的中立能力，不能归因于上游已有实现。

## 15. Phase 21 Aistio 全量审计补充

Phase 21 对固定 Commit 下 311 个版本化 Aistio 文件完成二次审计：215 个 Go 文件、44 个 Go 测试、18 个 Runtime Migration SQL、34 个 YAML/Helm/CRD 文件。实际注册 Route 为 Product 149 条、Runtime/Kubernetes 82 条；Product `cp` 18 张表，Runtime 迁移后 22 张表。完整 Package、Route、Auth、表、Session/Team/Command、Registration/ASDP/CRD、UI、配置和部署清单见 [Aistio 绞杀与迁移规范](aistio-strangler.md)。

列级复核确认 Product 时间字段是 Epoch 毫秒，Runtime 时间字段是 `TIMESTAMPTZ`；Agent 稳定主键是 `(owner_id, agent_id)` 而非单独 `agent_id`。Phase 21 导出因此统一转换 UTC，并使用 `owner_id/agent_id` 复合来源 Key。Environment `config_json`、`api_key_hash`、Vault `ciphertext`、Deployment `webhook_token`、用户 `password_hash` 均不进入导出；目标 Principal、SecretRef、Webhook SecretRef 和 Profile/Model/MCP/Skill 版本必须由受控配置显式映射。

本阶段没有复制 Go 源码、SQL Migration、Proto、CRD、测试或 UI。新增 Python/SQL 工具是依据已记录字段语义独立实现；默认 AgentArk Compose 仍只包含四个 Java Server。真实外部 Aistio 数据、生产流量和 Kubernetes 集成未提供，故只完成可执行工具/Fixture/Contract Gate，不把生产迁移描述为已发生。
