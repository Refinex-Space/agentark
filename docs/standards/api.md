---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: AGENTS.md#knowledge-map
---

# API 与 Event 契约标准

- Public API 使用 `/api/v1/**`，Internal API 使用 `/internal/v1/**`；身份、Audience 和权限模型不同。
- OpenAPI 3.1、AsyncAPI 和 JSON Schema 是语言中立契约；Java DTO 由契约生成或留在所属 Adapter。
- 每个写操作必须明确可否自动重试；发布、触发、决策和任何声明为可重试的创建操作必须定义 Idempotency Key、Request Hash 和冲突语义。
- 未建立持久 Idempotency Record 的同步资源创建默认不可自动重试，必须使用 Scope 内业务唯一键和稳定 `409` 避免静默重复；API Key 创建因明文只展示一次，明确属于不可重放操作。
- 异步请求只在持久工作与幂等结果提交后返回 `202`，并返回稳定 Operation/Run ID。
- 错误使用稳定错误码与 Problem Detail；不得暴露 Secret、内部栈或 Provider 原始对象。
- Event 有全局 `eventId`、Aggregate 内单调 Sequence、Schema Version 和明确终态；先持久化再通知。
- SSE 使用持久 Event ID 和 `Last-Event-ID` 恢复；连接生命周期不代表 Run 生命周期。
- Breaking Change 需要新版本或兼容迁移，支持当前与前一契约版本的滚动窗口。
- 跨平面只传不可变 Snapshot、Descriptor、Command 或 Event，不共享数据库模型。

0.1.0 首次发布契约基线额外遵循：

- `contracts/baselines/0.1.0.sha256` 冻结全部 OpenAPI、AsyncAPI、JSON Schema、示例和迁移 Contract；`tools/release/verify-contract-baseline.sh` 对任何增删改失败。
- 首个 Snapshot/Event 版本只有 N=v1，不存在历史 N-1。引入 v2 时必须保留并同时验证 v1，建立真实 N/N-1 滚动窗口后才能废弃 v1。
- 0.1.0 内需要兼容修正的契约必须保持向后兼容并显式更新摘要；破坏性变化只能进入新的 API/Schema 版本和新的发布基线，不能静默覆盖 v1。

Phase 03 已建立以下契约基线：

- `contracts/openapi/`：Public Control、Public Runtime、Public Scheduler 和三个 Internal API 的 OpenAPI 3.1 文档；业务 Endpoint 只能由所属阶段在实现存在后补充，尚无实现的文档或区域必须保持空路径；
- `contracts/asyncapi/runtime-events-v1.yaml`：Runtime Event 的 AsyncAPI 3.0 消息骨架；
- `contracts/schemas/agent-revision-snapshot/v1.json`：不可变 Revision Snapshot 的 Draft 2020-12 Schema；
- `contracts/schemas/runtime-event/v1.json`：稳定 Runtime Event Envelope；
- `contracts/schemas/problem-detail/v1.json`：公共 Problem Detail 错误模型。

Phase 07 增加以下 IAM 契约约束：

- `public-control-v1.yaml` 只声明实现中存在的 Organization、Project、Environment、Membership、Role、Role Binding、Service Account、Permission 与 API Key 路径；契约测试对完整 Path 集合做精确比较；
- Public Control 全局安全方案只允许 Bearer JWT 或 `Authorization: ApiKey <credential>`；健康与 Info 端点不属于该业务 OpenAPI；
- Organization/Project/Environment 路径参数使用 UUIDv7。客户端 Tenant Header 不是授权输入，Owner Scope 必须由已认证 Principal、路径资源归属和数据库有效权限共同确定；
- IAM 无权、未认证、未找到、冲突和输入错误分别使用稳定 `ARK-IAM-*` Problem Detail 代码，不回显 SQL、约束名或内部异常；
- `contracts/schemas/iam-public/v1.json` 是 IAM Public DTO 的唯一 Schema。`ApiKeyView` 不得包含摘要或明文；`CreatedApiKeyResponse.plaintext` 是只在创建响应出现一次的 `readOnly` 字段；
- API Key 创建不允许调用方自动重试。创建响应丢失时先按非秘密元数据定位并吊销，再显式创建新 Key；不得缓存、记录或通过列表接口恢复明文。

Phase 08 增加以下资产目录契约约束：

- Public Control 增加 Catalog、不可变版本、版本 Diff、Skill Artifact、Secret Metadata 和 Environment Binding 的七组真实路径；契约测试继续精确比较完整 Path 集合；
- `assetKind` 只能取契约固定分类，不能作为任意数据库表名；所有路径资源先通过 Project Owner 和 IAM 权限校验；
- 资产与 Secret 列表使用不透明 Cursor，`limit` 范围为 1–100；Cursor 只编码已授权查询的稳定排序值，不携带租户选择或授权事实；
- 行为资产版本只追加；稳定身份归档使用乐观锁且不物理删除版本。版本 Diff 只返回发生变化的 JSON Pointer，不回显可能敏感的旧值和新值；
- Skill 上传返回不含签名参数的持久 `ObjectRef`，版本提交再次复核 Hash、大小和媒体类型；上传接口不执行 Artifact；
- Secret API 只接收和返回 Provider、External Path/Version、Scope、状态和 Binding，不存在接收或读取 Secret 值的 Endpoint；
- `contracts/schemas/catalog-public/v1.json` 是 Catalog、ObjectRef 与 Secret Public DTO 的版本化 Schema，Golden File 由 Kernel 契约测试验证。

Phase 09 增加以下 Knowledge 契约约束：

- Public Control 增加 Knowledge Base、Data Source、Document/Revision、四类 Profile、Knowledge Revision、摄取描述和删除状态转换的八组真实路径；契约测试继续精确比较完整 Path 集合；
- 所有列表使用项目 Scope 内 UUIDv7 稳定排序的不透明 Cursor，`limit` 范围为 1–100。游标只携带上一页末资源标识，不携带 Project、ACL 或授权事实；
- `contracts/schemas/knowledge-public/v1.json` 是 Knowledge Public DTO 的版本化 Schema。持久化 JSON 在 Adapter 中还原为对象，不向调用方暴露双重编码字符串、MyBatis 行或 AgentScope/Qdrant 类型；
- 原文件上传使用服务端生成对象路径并返回无授权参数的 `ObjectRef`；客户端可提供 SHA-256 做完整性校验，但不能指定授权路径；
- 摄取接口只持久化 `DESCRIBED` 意图并返回 `202`，不声称已经创建 Scheduler Job 或完成 Embedding。重复幂等键只能指向同一 Knowledge Revision；
- Knowledge Revision 只有 `READY` 可供 Agent Revision Resolver 引用；状态转换、不可变内容绑定、失败代码和清理状态必须由领域状态机约束。

Phase 10 增加以下 Release 契约约束：

- `contracts/schemas/release-public/v1.json` 定义 Agent、Draft、Validation Report、Revision、Deployment 和对应写请求；Public Control 只声明实际实现的 Agent/Draft/Publish/Revision 与 Deployment 路径；
- Publish 请求的 `idempotencyKey` 在 Project + Agent Scope 内永久绑定 `expectedDraftVersion`。相同键同版本重放返回首次 Revision；相同键不同版本返回稳定冲突，不能再次解析资产或追加 Outbox；
- `contracts/schemas/agent-revision-snapshot/v1.json` 是 Runtime 消费的完整语言中立 Snapshot。Canonical Hash 使用排除顶层 `contentHash` 的规范 JSON 计算，Snapshot 只允许 `SecretRef`，不得包含 Credential 值；
- Phase 10 首次建立 `internal-control-v1.yaml` 时只声明 Snapshot 与 Deployment Descriptor；调用方必须是含 `agentark-control` Audience 的 Service Identity，并声明 Runtime Provider、支持的 Snapshot Schema Versions 和 Capabilities；后续阶段只在真实实现存在后增量追加路径；
- Snapshot 响应的 `ETag` 基于 `contentHash`，`If-None-Match` 精确命中返回 `304`。Deployment Descriptor 不暴露 Control Entity、DO、Mapper、Draft 或 Catalog Payload；
- Publish Outbox 的 Diff Summary 只允许上一 Revision ID 与变化的顶层区段名，禁止包含 Prompt、文档、Tool 参数、Secret 或资产旧值/新值。

Phase 11 增加以下 Runtime Event 契约约束：

- `contracts/schemas/runtime-event/v1.json` 中 `sessionSequence` 是 Session 回放游标，兼容保留的 `sequence` 是 Run 内游标；两者均从 1 开始、分别单调且不可复用；
- Event 必须包含全局唯一 `eventId`、`sessionId`、`turnId`、`runId`、`traceId`、`fencingToken`、`schemaVersion` 和 `occurredAt`。Event Store 先持久化事实，SSE、AsyncAPI 和 Outbox 都只是消费或投递机制；
- Payload 必须在 Inline JSON 与 `ObjectRef` 之间二选一。`ObjectRef` 只描述对象标识、Hash、大小和媒体类型，不携带签名 URL、Secret 或授权事实；
- Event 不得包含隐藏 Chain-of-Thought。Provider 原始事件必须在 Phase 12 防腐层转换为公开 Message、Tool、Usage、Approval、Error 或安全的未知事件载荷；
- 终态必须有明确持久 Event；数据库拒绝 Event Update/Delete 和旧 Fencing Token 写入。Phase 13 的 SSE `id` 与 `Last-Event-ID` 必须基于已提交的 `sessionSequence`，不能使用进程内 Preview 序号替代。

Phase 12 增加以下 Snapshot 编译契约约束：

- `contracts/schemas/agent-revision-snapshot/v1.json` 中 Memory、Workspace、Sandbox Profile 的 `configuration` 必须冻结为只含 JSON 值的不可变对象；禁止空属性名、非有限数字以及 Java、Provider 或 SDK 私有类型进入契约；
- Canonical Snapshot 对对象属性递归按名称排序、保留数组顺序，并在排除顶层 `contentHash` 后计算 SHA-256；Control Golden File、Runtime Provider 编译校验和缓存键必须使用同一规则；
- Runtime Provider 必须在解析 Secret 或创建运行组件前验证 `schemaVersion`、`runtimeProvider`、`contentHash`、租户标识和资产能力；编译错误转换为稳定 AgentArk 错误，不能透出 AgentScope 原始对象；
- AgentScope Typed Event 必须映射到 `runtime-event/v1.json` 语义。未知 Event 只保留安全类型标识，Tool 参数、Approval 参数和隐藏推理内容不得进入 Event、日志或缓存；
- 编译缓存键固定为 Provider、Snapshot Schema、Snapshot Hash 与 Compiler Version，且只缓存不含 Secret 和 Session 可变状态的不可变编译计划；缓存丢失必须能够由 Snapshot 重建。

Phase 13 增加以下托管 Runtime 契约约束：

- `public-runtime-v1.yaml` 只声明已经实现的 Session 创建/查询、Turn 接单、Run 查询/取消、Event 列表/SSE、Approval 列表/决策九条路径；`runtime-events-v1.yaml` 只声明已提交 Runtime Event 的消费通道；
- Session 创建必须在校验 Deployment、Revision、Snapshot、Provider、Schema 和 Capability 后固定不可漂移的执行身份，返回 `201 Created`；重复的 Session 幂等请求只返回首次资源，同键异参返回稳定冲突；
- Turn 接单必须在单一 Runtime MySQL 事务提交 Turn、首个 Run、Work Item、幂等结果、`run.accepted` Event 和 Outbox 后返回 `202 Accepted` 与稳定 `runId`。Snapshot 加载、编译、Lease 和外部调用只能由提交后的 Worker 执行，准备失败不得使 `runId` 消失；
- Session、Turn 和 Approval 决策使用 `Idempotency-Key`。重复同键同 Request Hash 返回首次结果；同键不同 Hash 返回 `409`，不得重复执行 Tool、Model 或审批副作用；
- Runtime Public API 只接受 Bearer JWT，并按 `runtime:execute`、`runtime:read`、`runtime:cancel`、`runtime:approve` 分权。Organization/Project 必须同时匹配已认证 Principal 和资源归属；跨租户直接对象访问按不可枚举资源返回 `404`；
- Event 列表的 `after` 和 SSE 的 `Last-Event-ID` 均表示已提交 `sessionSequence`。SSE 必须先回放再切实时追平，Heartbeat 不持久化、不推进游标；慢消费者只能关闭自身订阅，SSE 断开不得取消 Run；
- Approval Public DTO 只暴露 Tool 名、Tool Call ID、参数摘要 Hash、策略引用、状态与版本，不暴露原始 Tool 参数。决策必须校验当前版本、权限和幂等结果，并在新 Lease/Fencing Token 下恢复执行；
- Runtime 错误统一使用 Problem Detail 和稳定 `ARK-RUNTIME-*` 代码。401/403 不回显 Token、内部异常或租户细节；Provider 429、超时和不可恢复错误必须先转换为 AgentArk 分类后再进入 API/Event。

Phase 14 增加以下 Knowledge 摄取与检索契约约束：

- `knowledge-ingestion-internal/v1.json` 只表达 Control 解析的固定摄取计划和 Worker 幂等结果。线路上的标识、`Checksum`、`ObjectRef`、Profile 和 `SecretRef` 均为语言中立值；禁止直接序列化 Domain、MyBatis、Qdrant 或 AgentScope 对象；
- `GET /internal/v1/knowledge/ingestions/{requestId}/plan` 与 `POST /internal/v1/knowledge/ingestions/{requestId}:complete` 只允许包含 `agentark-control` Audience 的 Service Identity。Worker 只能通过这两个端点读取计划和提交结果，不得持有 Control DataSource；
- 摄取结果的 `idempotencyKey` 在 Project Scope 内永久绑定同一请求、Revision、Job、Attempt、数量、摘要、制品和终态；相同键异参必须返回稳定冲突，不能重复追加 Outbox；
- `knowledge-retrieval/v1.json` 固定结果正文、Document/DocumentRevision/Chunk Citation、`UNTRUSTED_EXTERNAL` 信任标记和不采集查询或文档正文的 Trace/Usage；
- Runtime 只能按不可变 Snapshot 固定的 `READY` Knowledge Revision 检索。调用方先把 Document ACL 解析为文档白名单；客户端或模型不能提交原始 Qdrant Filter、替换租户、Revision、Profile 或上下文预算；
- Qdrant/Embedding/Parser 不可用必须形成显式失败结果或 Provider 错误，不能伪装成空检索或 `READY`。原文、Chunk Artifact、Vector Payload 和 Control Metadata 必须通过固定 Revision、Document Revision 与摘要相互追踪。

Phase 15 增加以下 Scheduler 契约约束：

- `public-scheduler-v1.yaml` 只声明 Job 查询/取消、Dead Letter 查询/Redrive 和入站 Webhook；管理 API 使用 `scheduler:read`、`scheduler:cancel`、`scheduler:redrive` 分权，Webhook 由 HMAC、时间窗、Nonce 和持久防重放记录认证，不能把签名请求伪装成 JWT Principal；
- `internal-scheduler-v1.yaml` 只声明 Trigger 登记和 Durable Job 接单。调用方必须是 Audience 含 `agentark-scheduler` 的 Service Identity；同 Type/Business Key 或 Trigger Contract 重放只允许完全一致的租户、配置和 Payload Hash，异参返回稳定冲突；
- `internal-runtime-v1.yaml` 增加 Scheduler 实际调用的 `POST /internal/v1/runtime/turns`。Runtime 必须在本地事务提交 Turn、Run、Work Item、Event、Outbox 和幂等结果后返回稳定 `runId`；Scheduler 不能链接 Runtime 实现、共享 Mapper 或直接写 Runtime Schema；
- `scheduler-job/v1.json` 固定 Job Type、状态、Retry Policy、副作用幂等能力和 Hash 形态。写操作未声明 `INHERENT` 或 `PROVIDER_KEY` 时默认不得自动重试；Retry Budget 耗尽必须形成可查询 Dead Letter；
- 命令式取消和 Redrive 使用动作子资源并保留独立权限、人工原因、审计与 Outbox。Redrive 只能创建新的可执行周期，不能覆盖已有 Attempt、Delivery 或 Dead Letter 事实；
- Cron 只推进持久 Cursor 并创建 Durable Job，不在扫描事务内执行 Handler。Webhook、Channel、Knowledge Ingestion 和 Agent Turn 均通过版本化 Port/Client 交付，Scheduler 不拥有 Agent 推理循环；
- 入站 Webhook 正文最多 1 MiB；外发 Webhook 禁止重定向和非 HTTPS，并只保存有界响应摘要。契约、日志、Outbox、Dead Letter 和审计均不得保存 Secret、Credential、完整 Provider 正文或认证 Header。

Phase 16 增加以下 Gateway 契约约束：

- Gateway 固定按优先级路由 Runtime SSE、Runtime Public、Scheduler Webhook/Public 和 Control Public；`/internal/**` 必须在边缘返回 `404`，不能代理到任何下游；
- Bearer JWT 必须由 Foundation Decoder 校验时间、Issuer、Audience、JWK 签名和非对称 JWS 算法白名单。Gateway 只做认证前置，原始签名凭据继续传给目标服务独立验证，禁止把派生身份 Header 当作信任边界；
- `POST /internal/v1/auth/api-keys:verify` 只接受当前 API Key 自身，由 Control 本地摘要事实源验证并返回最小非秘密主体。Gateway 只缓存成功结果，缓存键为 SHA-256，TTL 不超过 30 秒；无效结果和依赖错误不得缓存；
- API Key 当前只用于 Control Public 路由；Runtime 和 Scheduler Public 继续要求 Bearer JWT。客户端租户 Header 只表达选择意图，不能替代下游资源归属与权限检查；
- SSE 路由单独禁用普通响应超时和代理缓冲，保留 `Last-Event-ID`，设置 `Cache-Control: no-store` 与 `X-Accel-Buffering: no`。连接中断不取消 Run，恢复仍以 Runtime 持久 Event Sequence 为事实；
- Gateway 认证、授权、API Key 依赖故障和限流拒绝统一返回 `application/problem+json`；错误体不得回显 Authorization、API Key、JWT、租户资源详情或下游异常。

Golden File、明文 Secret 负例和文档结构 Lint 由 `agentark-kernel` 测试执行，必需文件与 Kernel/Server 边界由知识门禁检查。首次发布后的修改必须增加 Breaking Change 检测；在不存在已发布前一版本的 Phase 03 不伪造兼容比较结果。
