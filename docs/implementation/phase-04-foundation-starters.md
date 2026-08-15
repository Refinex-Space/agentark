---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: docs/README.md#阶段执行证据
---

# Phase 04 — Focused Foundation Starters

## Status

- Status: DONE
- Started: 2026-08-15
- Completed: 2026-08-15
- Branch: `main`
- AgentArk HEAD at start: `a041a2f9b6af3ac63641173b607a705bcd6fb95b`
- AgentScope fixed view: `0c61e7494197ded54eefdeaf9bdeb51807beb752`

## Scope

本阶段把 `agentark-foundation` 建成六个可独立引入的 Maven Starter，只从固定 AgentScope Service 源码提炼 Error、JWT 和 Coordination 行为语义。没有复制 `service-common`、JPA Entity/Repository、DTO、Harness 类型或任何上游实现文件，也没有修改固定 Worktree。

明确不包含业务 Controller/Mapper/DO、User/Role/Membership、业务数据库 Migration、Durable Work、生产对象存储 SDK、Exporter、Server 启动类、公共 Endpoint、运行 Profile 或部署资源。

## Implemented Baseline

| Starter | 已实现能力 | 默认与边界 |
|---|---|---|
| Web | RFC 9457 Problem Detail、稳定 Error Code、Request/Trace/Tenant Context、Cursor Page、Jackson 3 的时间/枚举/强类型 ID、Validation、MVC/WebFlux 条件化异常处理 | 默认启用；不提供业务 DTO、Controller 或 `Result<T>`；未知异常不回显原始消息 |
| Security | OIDC/JWK JWT Decoder、Issuer/时间/Audience 校验、严格 JWT → `AgentArkPrincipal`、Service Identity、Tenant Selection、API Key SPI、Method Security | 默认关闭；Issuer/JWK 只允许 HTTPS；启用后 Audience 必填；不拥有 IAM 生命周期或资源授权 |
| Persistence | MyBatis-Plus Boot 4、MySQL 分页和乐观锁、UUIDv7 `BINARY(16)`、UTC `Instant`、Jackson 3 `JsonNode` TypeHandler、审计字段接口、Boot Hikari/事务/Flyway 基础 | `DataSource` 存在时生效；不含业务 Mapper/DO；不启用自动 DDL |
| Redis | `TypedCache`、`DistributedLeaseManager`、`FencingTokenSource`、`IdempotencyStore`、`RateLimiter`、Key Namespace/TTL/Codec 规范和 Lua 原子实现 | 默认关闭；Application Name 必填；Redis 只作加速与协调，不能成为 Revision、Run、Approval 或 Job 的唯一事实源 |
| Storage | `ObjectStore`、Put/Get/Head/Delete/Sign、Local 原子写入、SHA-256/大小/媒体类型校验、服务端生成对象路径、Authority/目录穿越保护、S3-compatible Factory SPI | 默认关闭；拒绝文件系统根/用户目录/工作目录；Local HMAC 密钥仅驻留当前进程，重启使旧签名失效 |
| Observability | OTel/Micrometer 可选适配、W3C Trace Context、JSON Structured Logging、Agent/Model/Tool/RAG/Sandbox Span 约定、Metric Tag 白名单和内容脱敏 | 不创建孤立 Registry；Secret 始终脱敏；Prompt、Tool Argument 和文档正文默认不采集 |

每个模块都包含 `AutoConfiguration.imports` 和中文 `spring-configuration-metadata.json`。Starter POM 继承 `agentark-foundation` 聚合父项，由该父项统一导入 `agentark-bom`；模块之间不存在生产 Scope 的相互依赖。

## Architecture and Security Decisions

- Web 的 Tenant Context 默认解析器返回空值，不信任客户端自行注入的租户 Header；后续 Server 必须从已认证 Principal 建立上下文。
- Security Starter 只提供认证基础和候选权限声明。JWT Claim 形成的 Tenant Selection 仍须在 Control IAM 做资源级授权；项目或环境 Claim 脱离组织 Claim 时直接拒绝。
- Persistence 复用 Spring Boot 标准 DataSource/Hikari/Flyway 自动配置，但没有 Migration、连接串、数据库账号或启动时 DDL。
- Redis Lease 携带单调递增 Fencing Token；Lua 脚本以 Owner 与 Token 原子续租/释放。Idempotency 只缓存判断，Durable Result 必须由所属 MySQL 事务保存。
- Local Object Store 只接受服务端生成的相对路径，并校验 Scheme、Authority、Query、Fragment、根目录和文件归属；生产 S3-compatible Adapter 与 SecretRef 留给所属阶段。
- Observability 使用字段名和内容类型双重策略脱敏；即使显式允许 Prompt/Tool/Document 正文，也不允许 Secret、Token、Credential 或认证材料进入日志。

## Quality Gates

六个 Starter 均使用 `ApplicationContextRunner` 验证启用、禁用、缺失依赖或错误配置边界。跨模块 `FoundationArchitectureTest` 扫描全部 Foundation 生产包，禁止 AgentScope/JPA/Server 依赖和 Controller/Mapper/Entity/DO 命名，并检查包 Slice 无环。

`ChineseDocumentationTest` 会从 Kernel 测试阶段扫描仓库全部 Java 源文件，继续执行中文 Javadoc 和唯一 `@author refinex` 规则；POM 的中文依赖/插件说明和 JSON Metadata 由知识门禁检查。

## Upstream Disposition

| 上游范围 | Phase 04 处置 |
|---|---|
| `service-common/web/api/error` | `ADAPT`：只保留稳定错误映射语义，改为 Kernel Error + RFC 9457 Adapter |
| `service-common/web/auth` | `ADAPT`：替换 Shared Secret/JWT 设计，使用 HTTPS Issuer/JWK、Audience 和协议中立 Principal |
| `service-common/web/coord` | `ADAPT`：只提炼 Lease/协调语义，并补充 Fencing；HITL/Queue/Cron 仍归 Owner |
| JPA Entity/Repository、共享 DTO、Harness 依赖 | `REJECT`：未进入任一 Starter |
| AgentScope Redis/Object Store Extension | `DEFER`：本阶段独立实现基础契约，Provider 级依赖仍留到 Phase 12 评审 |

## Verification

阶段验证结果：

- 六个 Starter 显式 Reactor `clean verify`：9/9 Project SUCCESS；Kernel 46 tests、Foundation 34 tests，合计 80 tests，0 failures，0 errors，0 skipped；
- Foundation 分项：Web 7、Security 6、Persistence 4、Redis 4、Storage 6、Observability/Architecture 7；
- 六模块 Compile Scope 内部依赖树：Web、Security、Persistence、Storage 只依赖 Kernel，Redis 和 Observability 不依赖其他 AgentArk 生产模块；不存在 Starter 间生产依赖边；
- Java 源码保持相邻模块风格；仓库不再提供或执行自动格式化脚本；
- `python3 tools/harness/knowledge_gate.py`：PASS；30 个 Active 文档；
- `python3 tools/harness/verify_upstreams.py --require-worktrees`：PASS；AgentScope 与 DeepSeek Harness 固定 Commit 未漂移；
- Foundation 禁止类型/上游类型扫描、自动 DDL 搜索、JSON Metadata 语法和 `git diff HEAD --check`：PASS。

Security 缺少 Audience、Redis 缺少 Application Name 的 Spring Context WARN 来自预期失败负例。Mockito 在 JDK 21 测试进程仍提示未来 JDK 将禁止动态加载 Agent，CycloneDX 也对自身 Schema 的扩展关键字输出非阻断 WARNING；两者不影响当前 JDK 21 验收，但升级 JDK 或测试工具链时必须复核。

本阶段没有启动真实 MySQL、Redis 或 S3 服务。TypeHandler、Redis Lua 和 S3-compatible SPI 的真实基础设施集成验证分别归 Phase 05–06 的本地 Core 基础设施和持久化基线；本阶段只声明并验证 Library 契约、条件化装配与纯实现边界。

## Rollback

本阶段没有数据库 Migration、运行数据、外部账户、公共 Endpoint 或部署变更。回滚时撤销 `agentark-foundation` 六个模块源码/资源/POM、配置参考、迁移清单和本阶段报告即可；测试创建的对象只位于 JUnit 临时目录。不得删除或改写 `.agentark/upstreams/` 固定 Worktree。
