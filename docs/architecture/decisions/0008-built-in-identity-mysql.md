---
owner: refinex
updated: 2026-08-21
status: accepted
referenced_by: docs/README.md#knowledge-map
---

# ADR-0008：Gateway 内置 Identity、MySQL 凭据与 Redis Session

## 状态

Accepted，替代 ADR-0007 中“本地 Keycloak 作为默认账号身份”的部分；外部 OIDC BFF 仍作为可选企业身份模式保留。

## 背景

AgentArk 独立后台需要在 Docker/Linux 和内网部署中开箱即用地提供用户名或电子邮箱、密码、随机初始管理员、首次强制改密、新用户创建、暂停、禁用、解锁、密码重置和安全审计。Keycloak `start-dev`、H2 和额外身份容器不符合该默认拓扑；把密码摘要放入 Control 又会让业务 IAM 与认证凭据共享数据库和调用栈。

## 决策

- Gateway 内嵌 Built-in Identity 模块并独占 `agentark_identity` MySQL Schema/账号；它只能保存认证、安全与平台身份事实，仍禁止保存 Agent、Project、Membership、Deployment 等业务状态。
- 密码使用部署 Pepper 预处理后生成 Argon2id PHC 摘要；MySQL 不保存明文或可逆密文。正式密码至少 15 个 Unicode Code Point、最多 128，不要求字符组合，不周期强制轮换；随机临时密码必须首次修改。
- Redis 只保存 HttpOnly WebSession、首次改密 Challenge、账号/IP 摘要限流和认证版本缓存；账号、摘要、锁定、安全事件和 Outbox 的权威副本在 MySQL。
- Gateway 为完整浏览器 Session 签发 30–300 秒的 RS256 内部 JWT；Control、Runtime、Scheduler 继续独立验证 Issuer、Audience、时间和 JWK。私钥只由 SecretRef 注入，JWK 端点只公开公钥。
- `identity_account` ID 同时作为本地 JWT Subject。账号创建与非敏感 Outbox 同一 Identity 事务提交；Outbox 使用 Gateway 服务 JWT 幂等投影到 Control `user_identity`，禁止跨 Schema SQL、外键或事务。
- Identity 平台角色只管理账号、安全事件和首个 Organization 创建权限；Organization/Project/Environment Membership、Role 和 Permission 继续由 Control 独占。
- 默认 Compose 启用内置 Identity；`--no-identity` 只用于纯 API 或显式外部 OIDC。Keycloak Realm、主题、H2 卷不再属于默认实现。
- 密码生命周期区分三种操作：临时密码登录后的“强制改密”只依赖受限 Pre-auth Challenge；已登录用户“修改密码”必须验证当前密码并由用户选择新密码；管理员“重置密码”不需要旧密码，但只能生成一次性临时密码并强制目标用户下次改密。

## 安全不变量

- 登录错误不区分账号不存在、密码错误、暂停、禁用或锁定，避免账号枚举。
- MySQL 连续失败与 `locked_until` 是账号锁定权威；Redis 只做更早的快速限流。
- 改密、重置、暂停和禁用递增 `auth_version` 并清除 Redis Session；短期 JWT 最大残留由 90 秒 TTL 限制。
- 本人修改密码按账号限流，当前凭据行以 `FOR UPDATE` 锁定，使当前密码校验、历史检查、新摘要、认证版本和安全事件在同一 Identity MySQL 事务内提交；成功后包括当前浏览器在内的全部 Session 失效。
- 临时密码只在首次成功响应展示一次；幂等重放不重复返回。丢失后只能重新重置。
- 安全事件只保存稳定事件码、Actor、Target 和地址/User-Agent 摘要，不保存密码、Cookie、Token 或请求正文。
- Gateway Identity 数据库账号不能访问 Control、Runtime 或 Scheduler Schema；其他平面账号也不能访问 Identity。

## 部署影响

- 仍然只有 Gateway、Control、Runtime、Scheduler 四个部署单元。
- MySQL 增加 `agentark_identity` Schema 与独立账号；Gateway 增加有界 JDBC/Hikari、Flyway、Argon2id 和 Bouncy Castle 依赖。
- Redis Session 丢失只要求重新登录，不改变账号或租户授权事实。
- 生产必须从 Secret Manager 注入 Identity 数据库密码、Bootstrap 密码、Pepper 和 PKCS#8 RSA 私钥，并使用 HTTPS Cookie。

## 回滚

关闭 `agentark.gateway.identity.enabled` 并切换到显式外部 OIDC BFF。`agentark_identity` Schema 保留为只读回滚证据，不执行 Flyway Clean、不删除账号或安全事件。旧 Keycloak 卷如仍存在只能人工确认后删除，不能作为持续双写身份源。
