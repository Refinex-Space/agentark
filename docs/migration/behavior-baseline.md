---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: docs/README.md#上游迁移审计
---

# Phase 01 上游行为基线

## 1. 使用方法

本文件把固定上游的关键行为转换为可复现迁移契约。`上游行为` 是固定 Commit 的事实，`AgentArk 门禁` 是后续实现必须保留或主动纠正的目标。源码路径均相对于 AgentScope 固定 Worktree；DeepSeek 路径单独标记。

Phase 01 只做只读取证；Phase 02 已在同一固定 SHA 的隔离可写完整 Worktree 中执行 Java、Go 和 AgentScope Service Frontend 命令。固定 detached 证据视图仍保持只读，完整结果见 [机械迁入报告](mechanical-import-report.md)。

## ERR-01 错误映射

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `service-common/.../api/error/ApiErrorType.java`、`ApiExceptionTest` | 将输入、认证、Not Found、Conflict、429、内部错误映射为 HTTP 状态和错误类型 | 稳定错误码与 HTTP Problem Details 分离；错误体含 trace/correlation，不泄露内部异常；覆盖所有公共 API |

注意：存在 `RATE_LIMIT` 错误枚举不代表 Gateway 已实现限流。

## GW-01 公共路由与 Internal 隔离

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `service-gateway/src/main/resources/application.yml`、`GatewayRouteTableTest` | `/api/internal/**` 在公共 Gateway 返回 404；外部输入的 Internal Token/User 被移除；Session Turn 与 Work 路由到 Dataplane，其余产品 API 和 SPA 到 Control | 保留 Internal 拒绝与 Header 清洗；Route 目标必须来自受控配置；Internal 身份改用服务身份，禁止只信任调用方 Header |

## GW-02 SSE 代理

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| Gateway `application.yml` | Connect timeout 5 秒；全局响应超时放宽到 1 小时，以免切断 Session SSE | SSE Route 单独设置超时/Buffer/Compression；验证断连、慢消费者、Gateway 重启与重连；不得把所有 API 统一放宽到长超时 |

## GW-03 CORS、认证与限流缺口

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| Gateway 源码/YAML全量搜索；Dataplane `DataSecurityConfig` | Gateway 未发现认证 Filter、自定义 CORS 或 RequestRateLimiter；Dataplane 自己配置 CORS | Gateway 必须成为公共 OIDC/JWK、CORS、租户/主体级 Rate Limit 的责任点；内部服务只接收 Gateway/服务身份，不能依赖宽松 CORS |

## RT-01 Turn Admission 原子边界

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `SessionTurnRunner`、`DataSessionApiController`、`SessionTurnAdmissionTest` | 先抢 Turn Lease，成功后才在 `onAdmitted` 回调持久化 `user.message`；Rejected Wake 不记录消息 | `Session + clientRequestId` 幂等；Lease/Fencing 成功、Message/Event 写入、Turn 创建形成可恢复边界；Rejected 不留下幽灵消息 |

## RT-02 Event 序号与跨副本可见性

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `SessionEventLog`、`SessionEventLogCrossProcessTest` | 数据库唯一约束保证同 Session `seq` 唯一；冲突重试；订阅按游标轮询数据库，能看到其他进程写入；Purge 后拒绝迟到 Append；Transcript Clear 后仍可继续写 | 单 Session 单调序号、append 幂等键、Purge Tombstone、跨副本订阅必须有并发测试；不得只用进程内 Sink 作为权威 |

## RT-03 SSE Cursor 与 Preview

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `DataSessionApiController.streamEvents/toSse`、Frontend `streamEvents` | 持久事件与 stream-only preview 合并；Preview `seq=-1`；SSE 只写 `event`/`data`，续传使用 `after` Query，没有 `id`/`Last-Event-ID`，前端无自动重连 | 持久事件必须输出 SSE `id=seq`；支持 `Last-Event-ID` 和显式 cursor；Preview 使用独立 ephemeral 类型/标识；重连不得重放或遗漏 committed Event |

## RT-04 AgentScope Event 转换

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `SessionEventMapperTest`、`ToolNotificationMiddleware` | Message/Thinking preview 使用 start/delta/end；多轮输出分组；Tool input/output 按 tool-use id 累积 | AgentScope Event 只能在 Provider 内转换；AgentArk Event Schema 明确 committed/preview、Message/Thinking/Tool/Usage/Error；未知事件保留类型和安全载荷，不静默丢失 |

## RT-05 Harness 编译与缓存

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `HarnessAgentBuildService`、`HarnessAgentBuildServiceCacheKeyTest` | 从 Control resolve 的 Snapshot 构建 Agent；普通 Session 复用 owner/agent/spec，Team Session 追加 session id 以隔离 TeamContext | 只接受不可变 `AgentRevisionSnapshot`；缓存键至少含 tenant、revision/snapshot hash、provider config；Team/Session scope 不得共享角色和可变 Context；Cache Evict 有生命周期测试 |

## RT-06 Session 配置固定性

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `DataSessionService.mergeAgentOverrides`、`HarnessAgentBuildService` | Control resolve 提供 Agent Snapshot；运行时 `agentOverrides` 合并明确不持久化，只记录本地事件/警告 | AgentArk 禁止未持久化 Override 改变可恢复执行；所有 Override 在 Session/Run 创建前固化进 Snapshot 或 Run Input，并具有 hash/audit |

## RT-07 HITL 暂停与恢复

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `ToolConfirmationMiddleware`、`ToolConfirmationCoordinator`、`TurnInterruptCoordinationTest` | Policy 为 allow/ask/deny；ask 创建持久 Ticket、发 `session.requires_action`、更新状态，通过轮询跨副本恢复或超时 | Approval 是 Runtime 聚合：唯一 request id、Policy 决策证据、允许/拒绝/过期状态、幂等答复、跨副本恢复；不得用 daemon 200ms 轮询作为最终设计 |

## RT-08 Lease、Interrupt 与 Fencing

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `TurnLeaseService`、`JdbcCoordinationStore`、`TurnInterruptCoordinationTest` | Lease 通过唯一插入竞争；跨副本 Interrupt Ticket 有 TTL；Active Turn 仍有进程内 Map；未发现单调 fencing token | 所有 Runtime 写入携带 fencing token；旧 Owner 即使恢复也不能提交事件/状态；Heartbeat、过期、抢占、GC Pause、重复 Interrupt 有确定性测试 |

## RT-09 Self-hosted Hands Work Queue

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `EnvironmentWorkQueue`、`HandsLeaseService`、Worker Controllers、`SelfHostedHandsDataPlaneTest` | Environment Worker poll/claim、heartbeat、update、ack/stop，工具结果恢复 Session；旧路径保留进程内 Registry | Durable Work Item 有 attempt、visibility timeout、claim token/fencing、幂等结果、取消、死信；Environment Key 不明文存储；Worker 权限绑定 tenant/environment |

## SCH-01 Cron Fire

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `CronDeploymentScheduler` | 每分钟扫描 due Deployment；分钟窗口 + 2 分钟 Fire Lease 防重复；失败只写日志 | Trigger 计算与 Job 创建事务化；保存 scheduledAt、Job、Attempt、Backoff、Misfire、Dead Letter；多副本 exactly-once-effect 由幂等键和 fencing 保证 |

## SCH-02 Channel Runtime 与 Reply

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `SchedulerChannelRuntime`、`ManagedSessionChannelBridge`、`ChannelExternalKeysTest` | 拉取配置并按指纹 reconcile；入站 find-or-create Session，投递消息后阻塞轮询 Event 等回复 | 配置 Revision 化；Channel 入站生成 durable Delivery/Job；Reply 通过 Event/Outbox 驱动，超时/重试/幂等/审计明确；不使用 Thread sleep 轮询 |

## SCH-03 Outbound Delivery

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `OutboundService`、`OutboundController` | 校验 Channel/Agent 路由后通过 `ChannelManager` 发送 | Outbound 必须创建 Delivery + Attempt；Provider Message ID 与幂等键持久化；Retry 分类区分 4xx/429/5xx/timeout |

## AIO-01 产品认证

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `internal/product/auth.go`、`middleware.go`、`seed.go` | bcrypt 密码；HS256 JWT 7 天；Internal Shared Token；开发 Seed 日志打印用户名/明文密码 | 使用 OIDC/JWK 或明确受控的本地身份 Profile；刷新/吊销/审计；服务身份短期凭据或 mTLS；绝不记录明文密码/Token |

## AIO-02 产品与 Runtime Schema

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `internal/product/migrate.go`、`internal/store/postgres/migrations/0001..0009` | 产品表由启动时幂等 DDL 建立；Runtime 表用版本迁移和 advisory lock；语义与 Java JPA 表重叠 | 所有 Schema 只由 Flyway 版本迁移；Control/Runtime/Scheduler 物理 Owner 分离；启动服务无 Auto-DDL 权限；跨平面只走契约 |

## AIO-03 Runtime Session/Command

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `internal/httpapi`、`internal/sessionops`、Store tests | Session/Turn/Event/Command、Data Plane Registry、队列路由和分页游标分层；有 memory/postgres Store | 参考状态机和分页契约；AgentArk 以中立 Runtime Domain 重建，不能让 Go Store 或 AgentScope 类型成为公共模型 |

## AIO-04 Team 协调

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `internal/team`、Team Store/HTTP tests | Team/Member/Message/Task、Plan、Wake、删除后写入保护、生命周期清理 | 进入 P21 前先锁定 Team/Task Owner、ACL、幂等、恢复和清理策略；不能用 Session 结束隐式删除 Team 状态 |

## FE-01 AgentScope Console Event/HITL

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| `frontend/src/api/managedSessions.ts`、`ChatPanel.tsx` | `fetch` 读取 SSE；按 Event 构造 Message/Tool/HITL UI；Malformed Frame 静默忽略；无测试 | Web Client 必须有标准 SSE Parser、重连退避、cursor、去重、坏帧可观测；Approval 操作显示风险、状态和幂等结果；用组件/E2E 覆盖 |

## ASF-01 AgentScope 依赖兼容

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| Core/Harness 代表测试：`RuntimeContextTest`、`EventTest`、`PermissionEngineTest`、`HarnessAgentTest`、`HarnessMiddlewareOrderTest`、`WorkspaceManagerPathSafetyTest`、`SandboxManagerIsolationTest`、`McpToolTest` | Core/Harness 已提供 Agent 循环、事件、权限、中间件、Workspace、Sandbox、MCP 等能力 | 通过依赖使用；Provider Compatibility Suite 固定 2.0.2 行为；升级 AgentScope 时先跑 Contract Test，再改 Adapter；禁止复制框架核心 |

## DSH-01 Web 视觉与交互参考

| 证据 | 上游行为 | AgentArk 门禁 |
|---|---|---|
| DeepSeek `apps/web`、`packages/client/ui-*`、`apps/web/tests` | 薄 Web 入口，Client Runtime/React/Presentation 分层；Token、Shell、Conversation、Approval、Terminal、Timeline、Workspace 有密集测试 | 只复建视觉和交互意图；AgentArk 使用独立路由、状态、组件和 API；Cordis/Slot/Plugin、品牌、Logo、Snapshot 不进入产品 |

## 2. 负向基线

以下“上游不存在或不足”也是必须保留的审计结论，后续不能被表述为已经拥有：

| 缺口 | 事实 | 后续 Owner |
|---|---|---|
| Gateway Rate Limit | 未发现实现 | P16 |
| 标准 SSE Resume | 无 `id`/`Last-Event-ID` 和客户端自动重连 | P13、P17 |
| Lease Fencing | 未发现单调 fencing token | P11、P13 |
| Durable Scheduler Job | Java Scheduler 无 Job/Attempt/Dead Letter 聚合 | P15 |
| Frontend Tests | AgentScope Service Frontend 无测试脚本/文件 | P17–18 |
| Agent Override Recovery | Runtime Override 不持久化 | P10–13 |
| Production Secret Defaults | Compose/Dev 脚本含默认密码与 Secret；仅声明开发用途 | P20、P22 |
| AgentScope License Bundle | 固定 Commit 缺根 LICENSE/NOTICE | P02 迁入许可 Gate |
| AgentScope Service 独立构建 | Service Parent 和 `agentscope-extensions-aistio` 依赖只能由完整 Monorepo Reactor 解析 | P02 机械基线只作证据；最终 AgentArk 通过发布依赖使用 Framework |
| AgentScope Frontend Build | lint 未声明 ESLint；源码缺 `src/features/build/`，固定 Commit 无法完成 lint/build | P17–18 只参考功能语义并独立实现 |
| 开发默认凭据日志 | Dataplane/Scheduler 测试启动会打印默认管理员明文密码 `admin` | P20 明确拒绝并增加 Secret/Log Gate |

## 3. 数据一致性检查清单

后续实现至少覆盖这些场景：

1. 两个 Runtime 副本同时接收同一 `clientRequestId`；
2. Lease Owner GC Pause 后过期，旧 Owner 恢复并尝试写 Event；
3. SSE 在 committed event N 后断开，携 `Last-Event-ID=N` 重连；
4. Preview 已发送但 Turn 失败，Committed Transcript 不出现伪完成消息；
5. HITL 回答重复、过期、跨副本、Session 终止后到达；
6. Hands Worker heartbeat 丢失、重复 ack、旧 claim 回传结果；
7. Cron 多副本同一分钟触发、误点火、执行超时、重试耗尽；
8. Channel Provider 返回 429/5xx/重复 Provider Message ID；
9. Snapshot 发布后 Draft 改动，现有 Session 仍可复现；
10. Control/Runtime 任一侧暂时不可用时，不通过跨库查询降级。

## 4. 代表性上游测试索引

| 区域 | 固定测试 |
|---|---|
| Service Common | `SessionEventLogCrossProcessTest`、`TurnInterruptCoordinationTest`、`JpaAgentStateStoreTransactionTest`、`AgentAclServiceTest` |
| Dataplane | `SessionTurnAdmissionTest`、`SessionEventMapperTest`、`HarnessAgentBuildServiceCacheKeyTest`、`SelfHostedHandsDataPlaneTest`、`EnvironmentSpecFactoryE2bTest` |
| Gateway | `GatewayRouteTableTest`、`GatewayAppContextLoadTest` |
| Scheduler | `ChannelExternalKeysTest`、`SelfHostedHandsUnitTest`、`SchedulerAppContextLoadTest` |
| Aistio | `event_paging_test.go`、`queue_test.go`、`matrix_test.go`、`team_auth_test.go`、`team_deleted_writes_test.go`、`lifecycle_cleanup_test.go`、`postgres_test.go` |
| Core/Harness | `RuntimeContextTest`、`EventTest`、`PermissionEngineTest`、`AgentStateStoreVersioningContractTest`、`HarnessAgentTest`、`HarnessMiddlewareOrderTest`、`SubagentIsolationIntegrationTest`、`SandboxManagerIsolationTest` |
| Protocol/Provider | `AguiAgentAdapterV2Test`、`AguiResumeCoordinatorTest`、`A2aAgentTest`、各 Model Provider Test |
| DeepSeek Web | `approval-composer.e2e.ts`、`chat-scroll-contract.e2e.ts`、`trajectory-virtualization.e2e.ts`、`pwsh-terminal.e2e.ts`、`workspace-management.e2e.ts`、`runtime/tests/manager.client.spec.ts` |

## 5. AgentArk 验收映射

| 后续 Phase | 必须消费的基线 |
|---|---|
| P02 | ASF-01、许可 Gate、上游原始构建命令 |
| P03–04 | ERR-01、AIO-02、AgentScope 类型隔离 |
| P06 | AIO-02 与三 Schema Owner |
| P07–10 | AIO-01、RT-06、资源 ACL/Snapshot |
| P11–13 | RT-01–09、GW-02 |
| P14 | Core RAG/Knowledge 依赖边界 |
| P15 | SCH-01–03 |
| P16 | GW-01–03 |
| P17–18 | FE-01、DSH-01 |
| P20 | AIO-01、Hands/Sandbox/Skill/MCP 安全 |
| P21 | AIO-03–04、A2A/AG-UI/BYO Compatibility |

## 6. 可复现命令

以下命令已在固定 SHA 的隔离可写 Worktree 中运行；当前 detached 证据视图保持只读。

```bash
# Java Service + 所需上游模块
mvn -pl \
  agentscope-service/service-common,\
agentscope-service/service-gateway,\
agentscope-service/service-dataplane,\
agentscope-service/service-scheduler \
  -am test

# Core/Harness 兼容基线
mvn -pl agentscope-core,agentscope-harness -am test

# Go Control/Runtime Store
cd agentscope-service/aistio
go test ./... -count=1

# 需要本地 envtest 资产的 Controller Integration
make test-integration

# AgentScope Service Frontend（只有编译/lint，无上游测试）
cd agentscope-service/frontend
npm ci
npm run lint
npm run build

# DeepSeek Client/Host 和已构建 Web
pnpm install --frozen-lockfile
pnpm run test:gui
pnpm run build
DSH_SNAPSHOT=replay pnpm run test:web:built
```

模型 Provider E2E、Service Smoke、Docker Compose 和 Kubernetes envtest 需要额外凭据或基础设施，必须在相应阶段报告中区分“未运行”“跳过”和“通过”。

## 7. Phase 02 实际结果

| 命令 | 结果 | 边界 |
|---|---|---|
| Java Service 四模块 `-am test` | PASS；24 Reactor Project，3660 tests，0 failures，0 errors，14 skipped | 包含 Core/Harness/Extensions 和 Service 依赖模块；未运行模型/真实服务 E2E |
| `go test ./... -count=1` | PASS；全部列出 Package 通过，源码树有 44 个 Go Test 文件 | 未运行 Kubernetes envtest、真实 PostgreSQL 或部署测试 |
| `npm ci` | PASS；142 Packages；Audit 为 3 moderate + 3 high | 只证明 Lockfile 可安装 |
| `npm run lint` | FAIL；`eslint: command not found` | 上游 Package 未声明 ESLint |
| `npm run build` | FAIL；两个 `features/build` 页面 Module 缺失 | 固定 Commit 源码缺目录，不在机械基线修复 |

测试日志还观察到 Scheduler Context 对本机 Control 重试 12 次、开发默认管理员密码被打印以及 Netty macOS native resolver 警告。后续 AgentArk 兼容测试必须把这些行为区分为“需要保留的契约”与“明确拒绝的上游缺陷”。
