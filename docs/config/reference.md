---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# 配置参考

## 当前状态

Phase 05 已建立四个可启动的空业务 Server、`local` Profile 和本地 Compose Core/RAG 基线。当前应用只装配 Web/Actuator 与 Foundation Web/Observability；MySQL、Redis 和 Object Storage 的业务接线、Flyway 与连接池属于 Phase 06，不得把基础设施容器存在误解为持久化基线已完成。

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
| `rag` | Core 全部服务 + Qdrant | 额外 `qdrant/qdrant:v1.18.3` | 显式开启的向量存储预留，默认不启动 |

Compose 对 MySQL `3306`、Redis `6379`、MinIO `9000/9001`、Qdrant `6333/6334` 和四个 Server 端口均只绑定 `127.0.0.1`。宿主基础设施端口可在本地 `.env` 中使用 `deploy/compose/.env.example` 列出的非敏感变量覆盖；四个 Server 端口为 Phase 05 固定值。

## 本地 Secret 和数据库账号

`tools/dev-up.sh` 首次运行时用 OpenSSL 生成 256 bit 十六进制随机值，写入已忽略的 `deploy/compose/.secrets/`，目录权限为 `0700`、文件权限为 `0600`。已有 Secret 不覆盖，但启动前必须通过“恰好 64 个十六进制字符”校验，避免换行或 SQL/Shell 元字符进入初始化流程。Compose 只挂载文件，不把密码渲染到 YAML 或 `.env`。

| Schema | 独立账号 | 授权范围 | 禁止范围 |
|---|---|---|---|
| `agentark_control` | `agentark_control` | `agentark_control.*` | Runtime/Scheduler Schema |
| `agentark_runtime` | `agentark_runtime` | `agentark_runtime.*` | Control/Scheduler Schema |
| `agentark_scheduler` | `agentark_scheduler` | `agentark_scheduler.*` | Control/Runtime Schema |

账号只在 MySQL 空数据卷首次启动时初始化。不得在保留 `mysql-data` 卷的同时删除或替换 `.secrets/`；否则文件凭据会与库内账号失配。`dev-up.sh` 在生成凭据前检查 `agentark_mysql-data` 卷；旧卷存在且任一 MySQL Secret 丢失时会拒绝启动，不会静默生成无法登录的新凭据。

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

连接、池化、TLS、事务和 Migration 继续使用 Spring Boot 所属标准属性，例如 `spring.datasource.*`、`spring.flyway.*` 和 `spring.data.redis.*`。这些属性的服务接线、测试容器与 Flyway 基线归 Phase 06 所有；Phase 05 不提供可误用于生产的默认连接串。

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
