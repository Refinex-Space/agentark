---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: PLAN.md#phase-21--go-aistio-strangler数据迁移与-java-control-全量切换
---

# Aistio → AgentArk Java Control 绞杀与全量切换

## 1. 收官结论

固定 Commit `0c61e7494197ded54eefdeaf9bdeb51807beb752` 中的 `aistiod` 不是单纯 Control：它把 Product Control、Runtime Store、Session Command、Data Plane Registration、ASDP gRPC、Kubernetes/CRD、Team/Task、Hosted DistributedStore 和 Console SPA 放在一个 Go 进程。AgentArk 不逐行翻译，也不把这个进程重新包装成第五个服务。

Phase 21 最终边界：

- IAM、Catalog、Agent Revision/Snapshot、Environment、Secret Metadata、Knowledge Metadata 和 Deployment 由 Java Control 拥有；
- Session/Turn/Run/Event/Approval/Checkpoint/Agent State/Runtime Instance 由 Java Runtime 拥有；
- Trigger/Job/Delivery/Channel Runtime 由 Java Scheduler 拥有；
- Gateway 只路由，不保存迁移状态；
- Internal API v1 的文件 Hash 已冻结，Runtime/Scheduler/Gateway Consumer 不改 DTO 和路径；
- 迁移写入只调用现有 Java API，不连接 MySQL、不跨 Schema、不双写；
- 活动 Go Session 固定 `GO_UNTIL_TERMINAL`，新 Session 只在 Java Runtime 创建；
- 临时兼容代理只允许 GET，默认 `JAVA_ONLY`，Go Fallback 最长 24 小时且最终关闭；
- Agent Team、CRD、ASDP/BYO 和 Hosted Store 已明确 `DEFER/REJECT`，不阻塞 C1–C8 核心切换；
- AgentArk 默认 Compose 已是 Java-only；当前没有 Helm Chart，Phase 22 新建 Chart 时必须通过同一门禁。

机器可读冻结证据见 `contracts/migration/aistio-cutover-v1.json`，取舍决策见 [ADR-0006](../architecture/decisions/0006-aistio-cutover-scope.md)。

## 2. 固定源码审计

### 2.1 规模

| 项目 | 固定事实 |
|---|---:|
| Aistio 版本化文件 | 311 |
| Go 文件 | 215 |
| Go 测试文件 | 44 |
| SQL Migration Up/Down | 18 |
| YAML/Helm/CRD | 34 |
| Product Route | 149 |
| Runtime/Kubernetes Route | 82 |
| Product 表 | 18 |
| Runtime 表 | 22 |

`go test ./...` 在固定 Worktree 和本机 Go 1.26.6 上全部通过；无测试 Package 也已列入执行输出。该结果冻结的是上游行为基线，不表示 AgentArk 采用其部署和数据模型。

### 2.2 Go Module 与 Package

| Package | 实际职责 | AgentArk 决策 |
|---|---|---|
| `cmd/aistiod` | 组装 Product、Runtime Store、HTTP、gRPC、Kubernetes Controller、Retention Worker | `REFERENCE`；拒绝整体迁入 |
| `internal/product` | User/Auth/Admin、Agent/Version、Environment、Vault、Memory、Workspace、File、Deployment、Channel、Session Metadata | 核心 `MIGRATE/ADAPT` 到 Control/Runtime/Scheduler |
| `internal/httpapi` | `/api/v1` Agent/Session/Team/Model/MCP/Sandbox/DataPlane/Hosted Store | 按最终 Owner `ADAPT/DEFER/REJECT` |
| `internal/store` | Memory/PostgreSQL Runtime Store 抽象 | `REFERENCE`；拒绝共享 Store 和表翻译 |
| `internal/sessionops` | compress/terminate/abort/undo/redo/plan、busy queue、错误码 | `ADAPT` 到 Runtime Command/Cancel/Checkpoint |
| `internal/asdp` | 双向 gRPC Config Push、Session/Event/Context/Inventory、Team Event | `DEFER`，仅未来 BYO Adapter 使用 |
| `internal/dataplane` | Registration、Heartbeat、Presence、轮询 | `ADAPT` 为 Runtime Instance 自注册，不迁旧心跳 |
| `internal/controller` | Agent/Team/MCP/Model/SandboxClaim Controller、BYO、Retention | `DEFER/REJECT` 为 Catalog 权威；Phase 22 可作部署投影参考 |
| `internal/discovery` | CRD Admission/Defaulting、Label Discovery、Adopt | `DEFER` |
| `internal/team` | Team Lifecycle、Session Spawn、Task/Message/Plan | `DEFER` 到独立 Collaboration Contract |
| `internal/sandbox` | Sandbox Broker 与生命周期 | `REFERENCE`；AgentArk 使用 Phase 20 安全合同 |
| `api/v1alpha1` | Agent、AgentTeam、ModelConfig、MCPServer、SandboxClaim CRD | `DEFER/REFERENCE` |
| `connector`/`sdk/python` | 多 Framework 注册、HTTP/gRPC Bridge | `DEFER` |
| `ui` | Aistio Console 静态 Bundle | `REJECT`；不复制品牌/资源 |

### 2.3 API Route Family

Product `/api/*` 包含：

- `/api/auth/**`、`/api/user/**`、`/api/admin/**`；
- `/api/agents/**`、Agent Version、Share、Workspace Skill、Tool Catalog、Clone；
- `/api/workspaces/**`、`/api/files/**`、`/api/marketplaces/**`；
- `/api/environments/**`、`/api/memory-stores/**`、`/api/vaults/**`；
- `/api/deployments/**`、Webhook Fire、Pause/Run；
- `/api/channels/**`、Agent Binding/Presence；
- `/api/sessions/**` Metadata；
- `/api/internal/**` Session Resolve、Agent Version、Vault/Memory Mount、Deployment Fire、Channel Config/Runtime Report。

Runtime/Kubernetes `/api/v1/*` 包含：

- Version、Overview、Token/Agent Metric；
- Agent CRD Push/Patch/Delete/Health/Revision/Rollback/Adopt/Inventory；
- Session Context/Event/Message/Task/Turn/Command/Compress/Terminate/Abort/Archive/Restore/Delete；
- ModelConfig、MCPServer、SandboxClaim；
- AgentTeam Member/Plan/Task/Message/Event；
- Data Plane Register/Heartbeat/Delete；
- Hosted `dp/kv/locks/snapshots/bus/async-tools/tasks`。

全部 Route 的来源文件、总数和代表路径已写入机器清单；迁移不复刻这些旧路径为长期 Public API。临时 Proxy 只处理明确白名单 GET，并把旧 ID 通过 Checkpoint 映射到 Java 路径。

### 2.4 Auth 与错误

上游同时支持 Console HMAC JWT、Kubernetes TokenReview、静态 Bearer Token、`X-Builder-Internal-Token` 和可为空的认证默认值。Product 默认还包含开发用户、开发 JWT Secret、共享内部 Token 和 Vault Master Key 回退。

AgentArk 决策：

- 本地 User/Password 和 HMAC JWT `REJECT`；只迁外部 `issuer/subject` 引用；
- Public 认证继续使用 OIDC/JWK 或 API Key 摘要；
- Internal API 使用 Audience-bound Service Identity；
- 旧 `busy/unsupported/unreachable/not_found` 归一化为 RFC 9457 ProblemDetail；
- 任一 Go 权限允许而 Java 拒绝可以讨论，Go 拒绝而 Java 允许属于阻断差异；安全 Case Match Rate 必须 100%。

### 2.5 数据模型

Product `cp` Schema 的 18 张表：

```text
users
agents
agent_versions
environments
sessions
memory_stores
memories
memory_versions
vaults
vault_credentials
deployments
resource_shares
channels
agent_bindings
files
workspaces
workspace_files
marketplaces
```

Runtime `rt` Schema 的 22 张表：

```text
sessions
session_snapshots
session_events
context_snapshots
token_usage_metrics
team_messages
team_tasks
team_task_history
agent_metrics
data_planes
session_commands
session_turns
dp_kv
dp_locks
dp_snapshots
dp_bus_entries
dp_async_tools
dp_tasks
session_transcript_index
teams
team_members
schema_migrations
```

同名 `cp.sessions` 与 `rt.sessions` 分别表达产品配置和运行观测，不能合并复制。Aistio DDL 大量使用 `TEXT/JSONB/BIGINT Epoch/BIGSERIAL`，AgentArk 使用 UUIDv7 `BINARY(16)`、`TIMESTAMP(6)/UTC`、明确状态约束和不可变 Snapshot。迁移按 Domain 映射，不按表名映射。

### 2.6 Agent/Environment/Session/Team/Runtime Command

- Agent `agent_versions.snapshot_json` 保存每次完整定义；迁移工具按版本升序转换 Draft/Publish，Checkpoint 保留双边 Hash。
- Environment 同时保存运行类型、Config 和 API Key Hash；只迁 Environment 身份/Profile 引用，API Key Hash 不进入导出。
- Product Session 保存 Agent/Version/Environment/Mount/Override；Runtime Session 保存 Framework/Instance/Phase/Event/Context。活动 Session 不改变 Owner。
- Runtime Command 支持 compress/terminate/abort/undo/redo/plan、Busy Queue 和 Command Audit；AgentArk 映射到 Cancel/Checkpoint/Runtime Command，历史不重放。
- Team 持有 Lead/Member/Plan/Task/Message/Session Spawn 和恢复语义，超出 AgentArk 核心发布/运行模型，本阶段明确延后。

### 2.7 Registration、CRD、gRPC 与 Kubernetes

上游可通过 Data Plane HTTP Registration、ASDP `Connect` 双向流、Kubernetes Agent/Team CRD 和 BYO Workload 同时发现实例。ASDP 还上报 Session/Event/Context/Inventory，包含 Tool Input/Output 和 Framework State，直接迁入会破坏 AgentArk Event/Secret/CoT 边界。

本阶段只保留 Runtime Instance 重新注册语义。旧 Registration/Heartbeat 不迁当前状态，ASDP/CRD/BYO 进入 ADR Backlog。Phase 22 如实现 Kubernetes 投影，也不能让 CRD 成为 AgentArk Catalog 或 Runtime State 权威。

### 2.8 UI、测试、配置和部署

- Aistio SPA 从 `AISTIO_STATIC_DIR` 服务；AgentScope Frontend Build 输出到 `aistio/ui`。AgentArk Web 已独立，全部静态资源 `REJECT`。
- 上游 `go test ./...` 覆盖 Store、ASDP、Controller、HTTP API、SessionOps、Product、Team 等；PostgreSQL 特定测试按环境变量启用。
- 上游默认 `AISTIO_ENABLE_KUBERNETES=true`、`enable-asdp=true`、Memory Runtime Store、Seed Users，并提供开发 JWT/Internal Token/数据库口令；这些默认均不迁。
- 上游 Compose 使用 PostgreSQL 17、Go Control 8081、Java Data 8082、Scheduler 8083、Gateway 8080，并共享静态身份与数据库。AgentArk Compose 使用四个 Java Server、MySQL 三 Schema/账号、Redis、MinIO 和可选 Qdrant，不包含 Go/Aistio/PostgreSQL。
- 上游 Helm Chart、CRD、gRPC 15010、Admission Webhook 和 Controller RBAC 不复制到 AgentArk。Phase 22 创建 Java Chart 时重新设计。

## 3. Contract Freeze

冻结 Hash：

| Owner | Contract | SHA-256 |
|---|---|---|
| Control | `contracts/openapi/internal-control-v1.yaml` | `09e626016220d19dea803577590be15770964d3349ebbf99bcb797d47c6a3f5e` |
| Runtime | `contracts/openapi/internal-runtime-v1.yaml` | `c8721c26e07004a3b96cc3aa52b7fe0ccbb414791dfa909ad6d30393b75c78e8` |
| Scheduler | `contracts/openapi/internal-scheduler-v1.yaml` | `1d9af00e3c56573c98bdbe736a7ce384d8950a12197a60748bf25408e45c7062` |

Consumer Contract 保持：

```text
GET  /internal/v1/agent-revisions/{revisionId}/snapshot
GET  /internal/v1/deployments/{deploymentId}
POST /internal/v1/runtime/turns
POST /internal/v1/scheduler/jobs
POST /internal/v1/scheduler/triggers
```

迁移工具不增加 Go Entity/DTO Contract。分页比较按稳定 Key/ID 规范排序，游标本身不比较。Java 使用 Cursor/hasMore，Go `offset/limit` 或 `metadata.continue` 只在 Shadow Case 中归一化。

## 4. 领域映射

| Aistio | AgentArk | 迁移规则 |
|---|---|---|
| `users` | `UserIdentity/Membership/RoleBinding` | `principalMappings` 显式指向目标 Principal；不迁 password_hash |
| `environments` | `Environment + Profile/Secret Binding` | Owner→Project 由配置决定；不导出任意 config_json，Profile 使用显式版本映射 |
| `agents` | `Agent + AgentDraft` | 稳定 Key/名称/说明；行为字段进入版本资产引用 |
| `agent_versions.snapshot_json` | `AgentRevisionSnapshot` | Agent 来源 Key 为 `owner_id/agent_id`；每版本 Draft→Publish 并保存 source/target Hash |
| `vault_credentials` | `SecretMetadata` | `secretMappings` 必须指向已迁真实 Provider/External Path；ciphertext 不导出也不伪造路径 |
| `memory_stores/memories` | `MemoryProfile/Knowledge/ObjectRef` | 大正文走 Object Store；不复制路径即授权 |
| `files/workspace_files` | `ObjectRef/Workspace Artifact` | Count/Size/SHA-256；正文不进入 NDJSON |
| `deployments` | `Deployment + Scheduler Trigger` | Revision 指针归 Control；Cron/Webhook 归 Scheduler |
| `cp.sessions` | Runtime Session Migration Manifest | 活动 Owner Pin，终态归档，不伪造 Run |
| `rt.sessions/events/turns` | Runtime 权威模型 | 新运行只由 Java 写；旧历史只读保留 |
| `data_planes` | Runtime Instance | 旧心跳归档，Java 实例重新注册 |
| `session_commands` | Runtime Command/Audit | 不重放副作用 |
| `teams/team_*` | Future Collaboration | `DEFER` |
| `dp_*` | Runtime Ports | 拒绝表翻译；按 AgentState/Lease/WorkQueue/ObjectStore Owner 处理 |

## 5. 数据迁移工具

### 5.1 只读导出

`tools/migration/export-aistio.sql` 使用 PostgreSQL `REPEATABLE READ READ ONLY`，输出 NDJSON。第一行绑定固定 Commit、UTC 和切换前只读备份 ObjectRef；后续行是资源。SQL 明确不选择：

```text
password_hash
ciphertext
api_key_hash
webhook_token
JWT/Internal Token/Vault Master Key
```

导出脚本已在临时 PostgreSQL 17 上由固定 `aistiod` 实际创建 `cp/rt` Schema 后执行，并使用覆盖 User/Environment/Agent/Version/Deployment/Session/File、Runtime Instance/Command/Team 的种子行通过后续 Validate。上游把 `pgcrypto` 安装到 `rt` Schema，文件 Hash 因此显式调用 `rt.digest`，不依赖调用方默认 `search_path`。

### 5.2 Validate/Dry Run/Apply/Resume

`tools/migration/aistio_migrate.py` 提供：

```bash
python3 tools/migration/aistio_migrate.py validate --export export.json --config cutover.json
python3 tools/migration/aistio_migrate.py dry-run --export export.json --config cutover.json
python3 tools/migration/aistio_migrate.py apply --export export.json --config cutover.json
```

强制能力：

- 固定来源 Commit、Schema Version、UTC 和只读备份 Hash；
- 复合来源主键唯一、Foreign Reference 完整；
- Payload Canonical Hash、Agent Version Snapshot Hash；
- Secret/Password/Ciphertext/Token 字段递归拒绝；
- 状态显式映射，不认识即失败；
- 每操作稳定 ID、Publish/Runtime Idempotency Key；
- 调用前 `IN_FLIGHT`、调用后 `SUCCEEDED/FAILED` 原子 Checkpoint；
- Crash Resume 先按稳定 Key Reconcile，不静默重复创建；
- 单资源错误只记录 Operation/Source Key/Code，不记录响应正文；
- 大对象只生成 Object Store 搬运清单；
- Checkpoint 保留每个来源 Agent Version 对应的 Revision/Snapshot/Hash；
- Runtime Command 不重放，活动 Session 不改 Owner。

来源身份、Secret 和 Webhook 不能自动推断：`principalMappings`、`secretMappings` 与 `webhookSecretMappings` 缺任一实际引用就停止生成计划。Model、MCP、Skill、Memory、Workspace、Sandbox 和 Permission 也必须指向目标项目已有的不可变版本；工具不会创建假 Provider、假 Secret 或生产默认配置。

报告格式由 `contracts/schemas/migration/aistio-validation/v1.json` 定义。

## 6. Shadow Compare 与兼容代理

`tools/migration/aistio_shadow.py` 支持：

| 模式 | 返回给调用方 | 对比 | Go Fallback |
|---|---|---|---|
| `SHADOW` | Go | Java | 无 |
| `JAVA_PRIMARY` | Java | Go | 无 |
| `GO_FALLBACK` | Java；仅 Java 5xx/不可达时 GET 回 Go | 可选 | 必须未来 24 小时内到期 |
| `JAVA_ONLY` | Java | 不调用 Go | 禁止 |

代理只绑定 Loopback，只允许白名单 GET，使用独立 Token File，不转发浏览器凭据，拒绝重定向和超大响应。POST/PUT/PATCH/DELETE 固定返回 `405 ARK-MIGRATION-READ-ONLY`，因此不能形成隐式双写。

Go/Java DTO 外壳不同，Case/Route 必须通过 `normalization.fieldMappings`，或集合的 `itemFieldMappings + go/javaCollectionPointer + stableItemKey` 显式投影为相同业务字段；缺字段即失败，禁止靠大范围 Ignore 掩盖差异。安全 Case 只允许忽略创建/更新时间、Request/Trace ID 和分页游标。记录只包含投影后的 Status、Canonical Hash、JSON Pointer 差异和两侧延迟，不包含响应值；原始响应出现 Password/Token/Ciphertext/Private Key 字段也直接阻断。批准阈值：

| 指标 | Gate |
|---|---:|
| Canonical Read Match | ≥ 99.9% |
| 5xx Error Rate | ≤ 0.1% |
| Java/Go p95 比 | ≤ 1.20 |
| Permission/Security Case | 100% |
| Count/Hash/Reference | 100% |

确定性 Fixture 达到 100% Match、0 Error、0 Security Mismatch；生产 Cohort 仍需用真实数据执行同一 Gate，报告必须归档。

## 7. 六个 Wave 最终状态

### Wave 1 — Contract Freeze

完成 Go API/表/错误/状态/分页审计、Java v1 Hash、机器映射清单、Consumer Contract 和只读 Compatibility Proxy。

### Wave 2 — Catalog 与资产

迁移工具支持 UserIdentity 引用、Environment、Catalog、Secret Metadata、Knowledge Metadata、Agent 资产引用，以及 Count/Hash/Reference。Aistio 不存在独立 Knowledge 表，不能伪造知识数据；只有规范化导出存在时迁移。

### Wave 3 — Revision 与 Deployment

旧 Agent Version 逐个 Publish 为 Snapshot；Checkpoint 保留来源 Commit/Version/Hash。Environment→Deployment 只移动 Revision 指针；Cron/Webhook 被拆到 Scheduler。Rollback 仍只改变 Java Deployment 指针。

### Wave 4 — Session/Runtime Command

活动 Session 标记 `GO_UNTIL_TERMINAL`，终态 `ARCHIVE_ONLY`；Runtime Instance 重新注册；Command History 不重放。Scheduler Client 路径不变。

### Wave 5 — Team/Registration/非核心

Registration 为 `ADAPT`，Team/Task/Message/Plan 为 `DEFER`，CRD/ASDP/BYO 为 `DEFER`，Hosted Store 与 Console 为 `REJECT`。详见 ADR-0006。

### Wave 6 — Cutover

Tenant/Capability Allowlist 支持灰度；默认 Cutover Flags 为 `JAVA_ONLY`，Go Writes/Fallback 均 `DISABLED`。AgentArk Compose 没有 Go Aistio。观察和真实生产数据迁移只能在存在外部 Aistio 部署时执行，不能用虚构流量冒充。

## 8. 回滚与保留窗口

- 回滚单位是 Cohort，不回滚全平台；
- 活动 Session 始终回原 Owner 完成，不能中途换 Catalog；
- Go Fallback 只读、最长 24 小时，超过到期时间失败关闭；
- Java 写入完成后不做自动逆向双写；需要补偿时依据 Checkpoint/Audit 清单人工审批；
- Flyway 不回滚，AgentArk 已写 Revision/Snapshot/Event 保持不可变；
- PostgreSQL 先做只读备份并保留到观察期结束，不立即删除；
- 默认 Java-only 后，Go Route、Go Write 和共享 Token 必须关闭。

具体步骤见 [Cutover Runbook](../runbooks/aistio-cutover.md) 与 [Rollback Runbook](../runbooks/aistio-rollback.md)。

## 9. 许可与来源

没有复制 Go 源码、SQL、CRD、Proto、测试、UI 或品牌资产。工具和契约均为 AgentArk 独立实现；Aistio 源码只用于固定行为/字段审计，分类为 `REFERENCE/ADAPT`。固定来源 Commit、路径、Hash 和许可证边界继续由迁移 Manifest、Upstream Baseline、License/NOTICE 文档共同约束。
