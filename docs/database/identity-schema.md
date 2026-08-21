---
owner: refinex
updated: 2026-08-21
status: active
referenced_by: docs/README.md#knowledge-map
---

# Identity Schema 逻辑模型

Schema：`agentark_identity`。唯一写入者是 Gateway Server。Control、Runtime、Scheduler 禁止持有该账号或执行跨 Schema 查询；Control 只经签名 Internal API 接收非敏感用户投影。

## V1 表

| 表 | 权威事实 | 核心约束 |
|---|---|---|
| `identity_account` | 用户名、邮箱、展示名、状态、首次改密、认证版本 | 规范用户名/邮箱唯一；状态 `ACTIVE/SUSPENDED/DISABLED` |
| `identity_password_credential` | 当前 Argon2id PHC 摘要 | 一账号一行；只允许 `ARGON2ID`；不存 Pepper |
| `identity_password_history` | 最近历史摘要 | 账号内单调序号；只用于拒绝短期复用 |
| `identity_permission` | 平台身份权限注册表 | Flyway 固定注册，业务请求不可扩展 |
| `identity_role` | 平台身份角色 | 内置 `platform-admin/identity-admin/identity-viewer` |
| `identity_role_permission` | 角色权限绑定 | 双外键、组合主键 |
| `identity_account_role` | 账号平台角色 | 不包含租户 Scope |
| `identity_bootstrap_state` | 一次性初始化状态 | 单例键；完成后不能重新抢占 |
| `identity_login_guard` | 失败次数与锁定截止 | MySQL 权威，Redis 只做快速限流 |
| `identity_security_event` | 追加式登录与账号安全事件 | 不保存敏感正文 |
| `identity_idempotency` | 账号管理幂等事实 | Actor + Operation + Key 唯一，同键异参拒绝 |
| `identity_outbox` | Control 用户投影待投递事件 | 持久 Claim、租约、重试和终态失败 |
| `identity_signing_key` | JWT 公钥与私钥 SecretRef 元数据 | 私钥正文不进入 MySQL |

## 密码与会话

Gateway 在 boundedElastic 密码执行路径使用 Pepper + Argon2id。Bootstrap 与管理员重置生成随机临时密码，并设置 `password_change_required=true`。首次强制改密完成前只存在短期 Redis Pre-auth Challenge，不能访问业务 API。日常本人修改密码必须在完整 Session 中验证当前摘要，并锁定凭据行后原子追加历史、写入新摘要、递增 `auth_version` 和记录 `PASSWORD_CHANGED`；管理员重置继续记录 `PASSWORD_RESET`，两者语义不可互换。

完整 Session 使用 Redis Indexed WebSession，Cookie 为 HttpOnly、SameSite=Lax，生产必须 Secure。Session Principal 保存账号 UUID、用户名、展示名、邮箱、平台权限和 `auth_version`，不保存密码或摘要。每次向下游签发短期 JWT 前重新校验 MySQL 状态和认证版本；本人修改密码、管理员重置、暂停或禁用账号都会删除该用户名的全部 Indexed Session。

## Outbox 投影

Identity 事务只投递 `issuer`、`subject`、`username`、`displayName`、`email` 和 `status`。Control 使用同一 UUIDv7 创建 `user_identity`，随后 Membership 和 Role Binding 可以在首次登录前引用。任何密码、摘要、失败次数、锁定原因、Session 或安全事件正文都不得跨平面。
