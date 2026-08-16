---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: AGENTS.md#knowledge-map
---

# 配置参考

## 当前状态

Phase 06 已为 Control、Runtime、Scheduler 接入各自独立的 MySQL DataSource、HikariCP 与 Flyway Baseline。Phase 07 已在 Control 接入 IAM V2；Phase 08 已接入资产目录 V3、Skill Object Store 和开发 Local Secret Provider；Phase 09 已接入 Knowledge 元数据 V4、原文件 Object Store、不可变 Revision 与摄取意图描述；Phase 10 已接入 Draft、不可变 Snapshot、Deployment 和 Control Outbox V5；Phase 11 已接入 Runtime V2 权威表；Phase 13 已装配 Runtime API、持久 Worker、Redis/MySQL 双层 Lease、SSE 与 HITL；Phase 14 已接入 Control V6 摄取结果、Qdrant Adapter、安全异步摄取管线、固定 Revision 检索和 AgentScope Tool 防腐层。Scheduler 仍只有 Migration History，Phase 15 才装配 Knowledge Job Handler；Gateway 尚无业务路由。生产 Object Store、恶意文件扫描、Embedding/Reranker、云 Secret 和真实 AgentScope Model/Component Provider 仍必须由部署方提供受支持 Adapter。

## Server 与本地 Profile

| 进程 | 默认端口 | Web 栈 | `spring.application.name` | `local` 内部 URL |
|---|---:|---|---|---|
| Gateway | `8080` | Spring Cloud Gateway WebFlux | `agentark-gateway-server` | `AGENTARK_CONTROL_BASE_URL`、`AGENTARK_RUNTIME_BASE_URL` |
| Control | `8081` | Spring MVC | `agentark-control-server` | `AGENTARK_RUNTIME_BASE_URL`、`AGENTARK_SCHEDULER_BASE_URL` |
| Runtime | `8082` | Spring WebFlux/Reactor | `agentark-runtime-server` | `AGENTARK_CONTROL_BASE_URL` |
| Scheduler | `8083` | Worker + 最小 Spring MVC 管理端点 | `agentark-scheduler-server` | `AGENTARK_CONTROL_BASE_URL`、`AGENTARK_RUNTIME_BASE_URL` |

四个 Server 的 `application.yml` 共同执行以下安全默认：优雅停机；每个关闭阶段最多 `20s`；只暴露 `health,info`；开启 Liveness/Readiness；`health.show-details=never`；Info 只允许 Maven Build Info，禁止环境与 Java 运行时细节。Gateway 配置中不存在业务 Route。

## Compose Profile

| Profile | 服务 | 固定镜像 | 用途 |
|---|---|---|---|
| `core` | MySQL、Redis、MinIO、四个 Server | `mysql:8.4.11`、`redis:8.10.0`、`minio/minio:RELEASE.2025-09-07T16-13-09Z`、`eclipse-temurin:21.0.10_7-jre-alpine-3.23` | 默认本地基础设施与空业务应用壳 |
| `rag` | Core 全部服务 + Qdrant | 额外 `qdrant/qdrant:v1.18.3` | Phase 14 Qdrant Adapter 的本地集成 Profile，默认不启动；不自动启用摄取 Handler |

Compose 对 MySQL `3306`、Redis `6379`、MinIO `9000/9001`、Qdrant `6333/6334` 和四个 Server 端口均只绑定 `127.0.0.1`。宿主基础设施端口可在本地 `.env` 中使用 `deploy/compose/.env.example` 列出的非敏感变量覆盖；四个 Server 端口为 Phase 05 固定值。

MySQL Core 容器显式使用 `--log-bin-trust-function-creators=ON`，使最小权限 Flyway 账号可以创建 V5 的 Revision/Snapshot 不可变触发器。该参数不授予应用账号 `SUPER`，也不扩大三个 Schema 的权限。生产 MySQL 若启用 Binary Log，数据库管理员必须在执行 V5 前配置并核验同一变量；缺失时 Flyway 会以 `ERROR 1419` 失败，禁止通过扩大应用账号权限或关闭 Flyway 绕过。

## 本地 Secret 和数据库账号

`tools/dev-up.sh` 首次运行时用 OpenSSL 生成 256 bit 十六进制随机值，写入已忽略的 `deploy/compose/.secrets/`，目录权限为 `0700`、文件权限为 `0600`。已有 Secret 不覆盖，但启动前必须通过“恰好 64 个十六进制字符”校验，避免换行或 SQL/Shell 元字符进入初始化流程。Compose 只挂载文件，不把密码渲染到 YAML 或 `.env`。

| Schema | 独立账号 | 授权范围 | 禁止范围 |
|---|---|---|---|
| `agentark_control` | `agentark_control` | `agentark_control.*` | Runtime/Scheduler Schema |
| `agentark_runtime` | `agentark_runtime` | `agentark_runtime.*` | Control/Scheduler Schema |
| `agentark_scheduler` | `agentark_scheduler` | `agentark_scheduler.*` | Control/Runtime Schema |

账号只在 MySQL 空数据卷首次启动时初始化。不得在保留 `mysql-data` 卷的同时删除或替换 `.secrets/`；否则文件凭据会与库内账号失配。`dev-up.sh` 在生成凭据前检查 `agentark_mysql-data` 卷；旧卷存在且任一 MySQL Secret 丢失时会拒绝启动，不会静默生成无法登录的新凭据。

## 三平面 DataSource 与 Flyway

| Server | 必填环境变量 | Schema/Location | 默认连接池 |
|---|---|---|---|
| Control | `AGENTARK_CONTROL_DB_URL`、`AGENTARK_CONTROL_DB_USERNAME`、`AGENTARK_CONTROL_DB_PASSWORD` | `agentark_control` / `classpath:db/migration/control` | max `10`、min idle `1` |
| Runtime | `AGENTARK_RUNTIME_DB_URL`、`AGENTARK_RUNTIME_DB_USERNAME`、`AGENTARK_RUNTIME_DB_PASSWORD` | `agentark_runtime` / `classpath:db/migration/runtime` | max `20`、min idle `1` |
| Scheduler | `AGENTARK_SCHEDULER_DB_URL`、`AGENTARK_SCHEDULER_DB_USERNAME`、`AGENTARK_SCHEDULER_DB_PASSWORD` | `agentark_scheduler` / `classpath:db/migration/scheduler` | max `10`、min idle `1` |

三个 URL、Username、Password 均没有生产默认值。Compose 只在本地 Profile 提供非秘密 URL/Username，并通过 `configtree:/run/secrets/` 把密码文件映射到同名属性；值不进入 Compose 环境或渲染输出。连接池上限可分别由 `AGENTARK_*_DB_POOL_MAX_SIZE` 覆盖，最小空闲可由 `AGENTARK_*_DB_POOL_MIN_IDLE` 覆盖。

每条新连接设置 UTC 与固定严格模式；Flyway 固定 `create-schemas=false`、`clean-disabled=true`、`validate-migration-naming=true`。只有部署初始化脚本能创建 Schema/账号，业务应用不能自动扩大权限。

## Foundation Starter 配置

| 属性 | 默认值 | 启用/必填条件 | 安全与所有权说明 |
|---|---|---|---|
| `agentark.foundation.web.enabled` | `true` | 引入 Web Starter | 仅装配公共 Web 基础；Servlet/WebFlux 按 Classpath 和应用类型互斥生效 |
| `agentark.foundation.web.request-id-header` | `X-Request-ID` | Web 启用 | 只接受格式受限的请求 ID；Trace ID 由 W3C `traceparent` 解析或安全生成 |
| `agentark.foundation.web.max-page-size` | `200` | Web 启用 | Cursor Page 的平台上限，调用方不能绕过 |
| `agentark.foundation.security.enabled` | `false` | 必须显式设为 `true` | 未完整配置时保持关闭，避免错误地宣称已建立认证边界 |
| `agentark.foundation.security.issuer-uri` | 无 | Security 启用时与 `jwk-set-uri` 至少配置一个 | 只允许绝对 HTTPS URI；配置后参与 `iss` 校验 |
| `agentark.foundation.security.jwk-set-uri` | 无 | Security 启用时与 `issuer-uri` 至少配置一个 | 只允许绝对 HTTPS URI；优先于 OIDC Discovery |
| `agentark.foundation.security.audiences` | 空 | Security 启用时必填且非空 | 至少命中一个服务端 Audience，防止跨服务 Token 重放 |
| `agentark.foundation.security.organization-claim` | `org_id` | Security 启用 | 受信 JWT 内 Organization UUIDv7 Claim 名称 |
| `agentark.foundation.security.project-claim` | `project_id` | Security 启用 | 受信 JWT 内 Project UUIDv7 Claim 名称 |
| `agentark.foundation.security.environment-claim` | `environment_id` | Security 启用 | 受信 JWT 内 Environment UUIDv7 Claim 名称 |
| `agentark.foundation.security.principal-type-claim` | `principal_type` | Security 启用 | 缺失时按 `USER`；`SERVICE` 必须同时提供 Service ID |
| `agentark.foundation.security.service-id-claim` | `service_id` | `SERVICE` 主体必填 | 稳定服务标识，不是 Secret 或共享 Token |
| `agentark.foundation.security.authorities-claim` | `scope` | Security 启用 | 只形成候选权限；资源授权仍由 Control IAM 决定 |
| `agentark.foundation.persistence.enabled` | `true` | 存在 `DataSource` | 只装配 MyBatis-Plus 插件和 TypeHandler，不自动建表 |
| `agentark.foundation.persistence.max-page-size` | `500` | Persistence 生效 | MySQL 分页上限；超页请求不静默回绕 |
| `agentark.foundation.persistence.tenant-defense-enabled` | `true` | Persistence 生效且 Owner 提供 `TenantLineHandler` | 只作 SQL 纵深防御；没有 Handler 时不虚构 Tenant，不替代授权与显式 Scope |
| `agentark.foundation.persistence.sql-telemetry-enabled` | `true` | Persistence 生效 | 只记录 Statement ID、Operation、Outcome、Duration，禁止 SQL 与参数正文 |
| `agentark.foundation.persistence.slow-query-threshold` | `500ms` | SQL Telemetry 启用 | 非负 Duration；达到阈值时输出脱敏告警 |
| `agentark.foundation.redis.enabled` | `false` | 必须显式设为 `true` 且存在 `StringRedisTemplate` | Redis 只承担缓存和协调，不得作为业务事实唯一副本 |
| `agentark.foundation.redis.key-prefix` | `agentark` | Redis 启用 | 平台级小写 Key 前缀 |
| `agentark.foundation.redis.application-name` | 无 | Redis 启用时必填 | 服务级命名空间，防止四平面 Key 冲突 |
| `agentark.foundation.redis.max-ttl` | `7d` | Redis 启用 | 业务请求 TTL 的统一上限，硬上限 30 天 |
| `agentark.foundation.storage.enabled` | `false` | 必须显式设为 `true` | 当前只自动装配 Local Profile；生产对象存储仍需后续 Adapter |
| `agentark.foundation.storage.root` | `.agentark/data/objects` | Storage 启用 | 必须是专用子目录；拒绝文件系统根、用户主目录和当前工作目录 |
| `agentark.foundation.storage.authority` | 无 | Storage 启用时必填 | ObjectRef 归属边界；调用方不能借其他 Authority 读写或删除 |
| `agentark.foundation.storage.max-object-size` | `67108864` | Storage 启用 | 单对象最大字节数；写入时同时验证声明大小和实际大小 |
| `agentark.foundation.storage.max-sign-ttl` | `15m` | Storage 启用 | Local 临时 URI 上限，硬上限 24 小时；重启后旧签名失效 |
| `agentark.foundation.observability.enabled` | `true` | 引入 Observability Starter | 只复用调用方已有 Micrometer/OpenTelemetry 实例，不创建孤立 Registry |
| `agentark.foundation.observability.allowed-tags` | `operation,outcome,error.category,runtime.provider` | Observability 启用 | Metric Tag 与 Span Attribute 低基数白名单 |
| `agentark.foundation.observability.collect-prompt-text` | `false` | 仅显式风险评审后开启 | Prompt 正文默认不采集；Secret 始终脱敏 |
| `agentark.foundation.observability.collect-tool-arguments` | `false` | 仅显式风险评审后开启 | Tool 参数默认不采集；Secret 始终脱敏 |
| `agentark.foundation.observability.collect-document-text` | `false` | 仅显式风险评审后开启 | 文档正文默认不采集；Secret 始终脱敏 |

## Control IAM 配置

| 属性/环境变量 | 默认值 | 启用/必填条件 | 安全与所有权说明 |
|---|---|---|---|
| `agentark.control.iam.enabled` | `true` | Control 正常运行 | 只允许无数据库的 `test` Profile 显式关闭；关闭后不提供 IAM API |
| `agentark.control.iam.authorization-cache-ttl` | `15s` | IAM 启用 | 必须为正且不超过一分钟；MySQL 始终是事实源，授权变化提交后主动失效本实例缓存 |
| `AGENTARK_SECURITY_ENABLED` | `true` | 非 `local` Control | 控制 Foundation OIDC Resource Server；生产不得以关闭认证方式运行 Public API |
| `AGENTARK_OIDC_ISSUER_URI` | 无 | 生产 Security 启用时必填 | 必须为绝对 HTTPS Issuer，参与 `iss` 与 JWK 校验 |
| `AGENTARK_LOCAL_SECURITY_ENABLED` | `false` | `local` 可选 | 本地未连接 IdP 时仍不允许匿名访问 IAM API；可使用显式 Dev Bootstrap 创建资源但不提供认证凭据 |
| `AGENTARK_LOCAL_OIDC_ISSUER_URI` | 无 | 本地 Security 启用时必填 | 只接受 HTTPS Issuer，不提供宽松默认值 |
| `agentark.control.iam.dev-bootstrap.enabled` / `AGENTARK_IAM_DEV_BOOTSTRAP_ENABLED` | `false` | 仅 `local` Profile | 生产 Profile 即使误设为 `true` 也不装配；不生成口令、Token 或 API Key |
| `AGENTARK_IAM_DEV_ISSUER`、`AGENTARK_IAM_DEV_SUBJECT` | `urn:agentark:local-dev`、`local-developer` | Dev Bootstrap 启用 | 仅作为本地资源 Owner 身份引用，不是认证凭据 |
| `AGENTARK_IAM_DEV_ORGANIZATION_SLUG/NAME` | `local-org`、`本地开发组织` | Dev Bootstrap 启用 | 幂等创建本地 Organization |
| `AGENTARK_IAM_DEV_PROJECT_SLUG/NAME` | `local-project`、`本地开发项目` | Dev Bootstrap 启用 | 幂等创建本地 Project |
| `AGENTARK_IAM_DEV_ENVIRONMENT_KEY/NAME` | `local`、`本地环境` | Dev Bootstrap 启用 | 幂等创建本地 Environment |

## Control Catalog 与 Secret 配置

| 属性/环境变量 | 默认值 | 启用/必填条件 | 安全与所有权说明 |
|---|---|---|---|
| `agentark.control.catalog.enabled` | `true` | Control 正常运行 | 装配 Catalog、Secret Metadata、Public API 和各自 MyBatis Adapter；不装配 Runtime 执行能力 |
| `agentark.control.catalog.max-artifact-size` | `10MB` | Skill Artifact 上传 | 必须在 1 byte–64 MiB；同时受 ObjectStore `max-object-size` 约束 |
| `agentark.foundation.storage.enabled` | `false`；`local` 为 `true` | Skill Artifact 上传必须有 ObjectStore | 本地根目录为 `.agentark/data/objects`；生产必须提供受支持 Bean，不能依赖 Local 实现 |
| `AGENTARK_LOCAL_OBJECT_ROOT` | `.agentark/data/objects` | `local` Storage 启用 | 已忽略的运行数据目录，不得指向仓库根、用户主目录或文件系统根 |
| `agentark.control.secret.local-provider-enabled` / `AGENTARK_LOCAL_SECRET_PROVIDER_ENABLED` | `false` | 仅 `local` Profile 可开启 | 显式开启后才装配 Local File Resolver；生产 Profile 即使误设也不装配 |
| `agentark.control.secret.local-root` / `AGENTARK_LOCAL_SECRET_ROOT` | `.agentark/secrets` | Local Provider 启用 | 只允许根目录内普通文件；拒绝目录穿越、符号链接和超过 64 KiB 的值 |

生产 Vault、AWS、Azure、GCP 和 Custom Provider 当前只有枚举与 `SecretResolver` SPI。没有配置实际 Provider Bean 时不会伪造解析成功，也不存在读取 Secret 值的 Public API。

## Control Knowledge 配置

| 属性/环境变量 | 默认值 | 启用/必填条件 | 安全与所有权说明 |
|---|---|---|---|
| `agentark.control.knowledge.enabled` | `true` | Control 正常运行 | 装配 Knowledge 元数据、Document ACL、不可变 Revision、Public API 与 MyBatis Adapter；不执行 Parser、Embedding、向量写入或检索 |
| `agentark.foundation.storage.enabled` | `false`；`local` 为 `true` | Document 原文件上传必须有 ObjectStore | 本地与 Skill 共用受 Authority 隔离的 Local ObjectStore；生产必须提供受支持 Bean，数据库只保存带 Hash、大小和媒体类型的 `ObjectRef` |
| `AGENTARK_LOCAL_OBJECT_ROOT` | `.agentark/data/objects` | `local` Storage 启用 | 原文件路径由服务端生成且保持在专用根目录；客户端文件名不能选择授权路径 |

Knowledge 摄取 Endpoint 返回 `202` 只表示幂等请求已经记录且 Revision 进入 `INGESTING`。Phase 14 已实现 Qdrant Adapter、受限 Parser、安全扫描 Port、批次 Embedding/重试、校验、删除和 Retrieval 管线，但 Phase 15 前没有 Scheduler Job Handler 自动消费该请求。不得通过启用 `rag` Compose Profile 推断摄取 Worker 已装配。

## Knowledge RAG Adapter 配置边界

`QdrantProperties` 由 Phase 15 的 Scheduler Handler 或受控 Runtime Provider 装配，不由 Control Public API 构造。当前稳定非敏感字段为：Qdrant REST 根地址、平台受控 Collection 名、固定向量维度和单请求超时。远程 Endpoint 必须使用 HTTPS；只有 `localhost`、`127.0.0.1` 或 `::1` 可使用 HTTP。Endpoint 禁止 User Info、Query 和 Fragment，Collection 必须是 3–64 位小写受限名称，维度为 1–65536，超时大于零且不超过两分钟。

本地建议值为 `http://qdrant:6333`、`agentark_knowledge`、与固定 Embedding Profile 一致的维度和显式超时。维度变化必须使用新 Collection/Deployment 配置和新 Knowledge Revision，不能修改既有 Collection 的向量语义。Qdrant API Key 只能由 Secret Provider 按请求解析，不能进入 `QdrantProperties`、YAML、环境变量示例、日志或缓存。生产恶意文件扫描器缺失时必须拒绝启用摄取 Handler，禁止配置“永远通过”的默认实现。

摄取 Worker 的批次大小、最大尝试次数和退避由其 Owner 显式构造：批次 1–1024、尝试 1–10、基础退避 0–1 分钟。Parser 的进程 Heap、超时和 Classpath 必须由受控部署配置提供；生产还需叠加容器或平台 Sandbox。Qdrant Snapshot、恢复、删除和故障处理见 [Knowledge/RAG 运维](../guides/knowledge-operations.md)。

## Control Release 配置

| 属性/环境变量 | 默认值 | 启用/必填条件 | 安全与所有权说明 |
|---|---|---|---|
| `agentark.control.release.enabled` | `true` | Control 正常运行且存在 `KnowledgeSnapshotLookup` | 装配 Agent Draft、Publisher、Deployment、V5 Repository 与 Public/Internal API；关闭后不提供 Release API，不能作为绕过 V5 校验或权限的生产降级方式 |

Runtime 调用 Snapshot Internal API 时必须携带受信 Service Identity，以及 `X-AgentArk-Runtime-Provider`、`X-AgentArk-Snapshot-Schema-Versions` 和可选 `X-AgentArk-Runtime-Capabilities`。这些 Header 是单次能力协商，不是环境 Secret 或租户选择。Internal Service JWT 的 Issuer、JWK 和 Audience 继续使用 Security Starter 配置，禁止配置共享静态 Token。

## Runtime 托管执行配置

| 属性/环境变量 | 默认值 | 启用/必填条件 | 安全与所有权说明 |
|---|---|---|---|
| `agentark.runtime.control-base-url` / `AGENTARK_CONTROL_BASE_URL` | `http://localhost:8081` | Runtime 非测试环境 | 只访问 Control Internal API；生产应使用受控网络和 TLS，不得连接 Control DB |
| `agentark.runtime.internal-service-token` / `AGENTARK_RUNTIME_INTERNAL_TOKEN` | 无 | Control Internal API 要求服务认证时必填 | 只进入 Authorization Header，不得写入 Snapshot、Redis、Event、日志或本地默认配置 |
| `agentark.runtime.instance-key` / `AGENTARK_RUNTIME_INSTANCE_KEY` | `runtime-local-1` | Runtime 实例启动 | 多副本必须使用唯一稳定的 Pod/实例标识，参与 Work Claim、心跳和 Lease Owner 校验 |
| `agentark.runtime.lease-ttl` / `AGENTARK_RUNTIME_LEASE_TTL` | `30s` | Worker 启用 | Redis/MySQL 双层 Lease TTL；续租失败触发 Provider Cancel，MySQL Fencing 仍是权威写边界 |
| `agentark.runtime.worker-enabled` / `AGENTARK_RUNTIME_WORKER_ENABLED` | `false` | 真实 Model/Component/Secret Provider 均已装配 | 默认安全关闭；缺少生产 SPI 时显式启用会启动失败，不回退 Fake Engine |
| `agentark.runtime.worker-poll-delay` / `AGENTARK_RUNTIME_WORKER_POLL_DELAY` | `250ms` | Worker 启用 | 每次最多领取一个持久 Work Item，排空后停止轮询 |
| `agentark.runtime.instance-heartbeat-delay` / `AGENTARK_RUNTIME_HEARTBEAT_DELAY` | `10s` | Runtime 实例启动 | 只更新 Runtime MySQL Instance 心跳，不把 Redis 当作实例事实源 |
| `spring.data.redis.host` / `AGENTARK_REDIS_HOST` | `localhost` | 非测试 Runtime | Redis 只承载 Lease 与可丢失加速状态；全量丢失后从 MySQL/Object Storage 恢复 |
| `spring.data.redis.port` / `AGENTARK_REDIS_PORT` | `6379` | 非测试 Runtime | 固定 Redis TCP 端口；生产网络与 TLS 由部署环境显式配置 |
| `spring.data.redis.password` / `AGENTARK_REDIS_PASSWORD` | 空 | Redis 启用且服务要求认证 | 敏感值只由 Secret 注入，禁止写入版本库或日志 |
| `agentark.foundation.storage.root` / `AGENTARK_RUNTIME_OBJECT_ROOT` | `.agentark/data/runtime-objects` | Local Object Store | 只保存大 Event/State Payload；生产应替换为受支持 Object Store Adapter |

Runtime API 权限固定为 `runtime:execute`、`runtime:read`、`runtime:cancel` 和 `runtime:approve`，并要求 JWT 中精确选择资源所属 Organization/Project。安全未启用时只开放脱敏 Actuator，所有 Runtime API 拒绝；生产不能把关闭认证作为可用降级方案。

连接、池化、TLS、事务和 Migration 使用 Spring Boot 所属标准属性，例如 `spring.datasource.*`、`spring.flyway.*` 和 `spring.data.redis.*`。Phase 06 已固定 MySQL/Flyway 基线；生产 TLS 信任材料与强制模式仍必须由实际部署环境显式提供，不能依赖本地 Compose 的明文内部网络设置。

## 规范

- 配置由所属 `*-server` 读取；Library 不直接读取进程环境。
- 环境变量、Spring 属性、默认值、是否必填、敏感级别、适用 Profile 和重载方式必须逐项记录。
- Production 不提供默认密码、共享 JWT Secret、长期服务 Token 或宽松 CORS。
- Secret 配置只保存 Provider 引用，不记录值；示例使用明显的非秘密占位符。
- Control、Runtime、Scheduler 各自只能配置所属 Schema 的最小权限账号。
- 端口、对象存储、Redis、Qdrant 和外部 Provider 的超时、重试、连接池与 TLS 不能依赖未记录默认值。
- 删除或重命名配置先提供兼容窗口、弃用日志、迁移步骤和回滚方式。

## 上游取证变量

`PLAN.md` 定义 `AGENTSCOPE_REPO`、`AGENTSCOPE_SOURCE_COMMIT`、`AGENTSCOPE_ROOT`、`DEEPSEEK_HARNESS_REPO`、`DEEPSEEK_HARNESS_SOURCE_COMMIT`、`DEEPSEEK_HARNESS_ROOT`。这些变量只用于固定上游源码视图，不进入产品运行配置。
