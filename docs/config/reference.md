---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# 配置参考

## 当前状态

Phase 04 已实现六个 Foundation Starter 的类型安全配置属性和 IDE Metadata。仓库仍未创建可运行 Server Profile、`.env.example` 或部署清单；下列键只在调用方引入对应 Starter 且满足条件时生效，不代表四个后端服务已经可运行。

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

连接、池化、TLS、事务和 Migration 继续使用 Spring Boot 所属标准属性，例如 `spring.datasource.*`、`spring.flyway.*` 和 `spring.data.redis.*`。这些外部基础设施的真实值、SecretRef 解析和各服务 Profile 归 Phase 05–06 所有；当前仓库不提供可误用于生产的默认连接串。

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
