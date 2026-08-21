---
owner: refinex
updated: 2026-08-19
status: superseded
referenced_by: docs/README.md#knowledge-map
---

# ADR-0007：OIDC BFF 与默认内置账号身份

## 状态

Superseded by ADR-0008。外部 OIDC Authorization Code BFF 部分仍有效；本地 Keycloak 默认身份部分已被 Gateway Built-in Identity 替代。

## 背景

AgentArk 0.1.0 已建立四个平面独立验证 OIDC/JWK JWT 的 Resource Server、安全失败关闭和 `issuer + subject` 用户映射，但 Web 只提供测试 Bearer 与只读预览，没有可用的 Authorization Code 登录。AgentScope Service 的本地 `users + BCrypt + HS256 JWT + localStorage` 便于开发，却会把密码存储、共享对称签名密钥和浏览器 Token 持久化责任引入 AgentArk，不能直接沿用。

独立部署仍需要开箱即用的账号密码入口；企业部署则需要接入已有 OIDC Provider。两者必须共享同一 JWT/JWK 下游验证模型，不能形成两套授权事实。

## 决策

### 统一身份协议

- Gateway 同时作为 OAuth 2.0/OIDC Confidential Client 和现有 JWT Resource Server；使用 Authorization Code Flow，不启用 Implicit Flow 或 Resource Owner Password Grant。
- 浏览器只持有 `HttpOnly`、`SameSite=Lax` 的不透明 Session Cookie；`Secure` 在生产强制开启。Access Token、Refresh Token、ID Token 和 Client Secret 不进入前端 JavaScript、Web Storage、URL 或日志。
- Gateway 将服务端 Authorized Client 的短期 Access Token 转发到 Control、Runtime、Scheduler；各下游继续按 Issuer、Audience、时间、JWK 签名和非对称算法独立验证，不信任 Gateway 派生身份 Header。
- Gateway 多副本会话保存到 Redis。Redis 丢失只使用户重新登录，不改变 UserIdentity、Membership、Role Binding 或业务事实。
- 带 Session Cookie 的非安全 HTTP 方法必须校验 CSRF；无 Session Cookie 的 Bearer/API Key 客户端保持无状态。

### 两种部署身份来源

- 企业模式可以连接部署方批准的 HTTPS OIDC Provider。Provider 负责密码、MFA、Passkey、找回、锁定、验证和账号生命周期。
- 本地与独立后台默认通过 `tools/dev-up.sh` 启用固定版本与 Digest 的 Keycloak Identity Profile；纯 API 或外部 IdP 场景使用 `--no-identity` 显式关闭。它不属于新的 AgentArk 后端平面，也不允许把 `start-dev`、HTTP Issuer 或 H2 数据库直接解释为生产身份服务。
- 本地 Profile 创建固定 Subject 的 `agentark-admin`，临时密码由 OpenSSL 随机生成到 Git 忽略的 `0600` 文件，首次登录必须修改。禁止默认 `admin/admin`、提交 Realm Secret 或把临时密码打印到启动日志。
- AgentArk Control 仍只保存 Provider 的 Issuer/Subject 映射和授权关系，不保存本地或企业密码摘要。

### 浏览器与入口拓扑

- Web 通过同源 `/api`、`/oauth2`、`/login` 和 Logout 路径访问 Gateway。本地 Vite 使用代理；生产 Ingress 在 Web Host 上把这些路径路由到 Gateway。
- `GET /api/v1/auth/session` 只返回登录入口、非敏感主体投影和 CSRF 参数；严禁返回任何 OIDC Token。
- Logout 使用 CSRF 保护的 POST，先清除 Gateway Session，再执行 Provider 的 RP-Initiated Logout。
- 身份服务可用时，React 入口直接发起 Authorization Code 跳转；Keycloak 使用与 React 一致的 `login-05` 单列主题展示“用户名或电子邮箱 + 密码”，不保留额外中转操作。
- 技术协议名只出现在运维和配置文档；用户登录页只展示账号、密码和产品语言。

## 安全约束

- 生产 OIDC、回调和跳转 URI 必须为精确 HTTPS；明文 HTTP 只允许显式本地 Profile，并由独立配置开关控制。
- Client Secret、Redis 密码和本地身份临时密码只通过 Secret/Config Tree 注入。
- 登录后跳转地址必须来自受控配置，不能接受请求参数提供的任意外部 URL。
- 本地 Keycloak Realm 开启暴力破解保护、短期 Access Token、Refresh Token Rotation 和首次密码修改；生产密码与 MFA 策略仍由受审 Provider 承担。
- API Key 继续面向机器调用，不作为浏览器用户登录方案。

## 影响

- 四个 AgentArk 后端部署单元不变；Gateway 新增 OAuth2 Client 和 Redis WebSession 依赖。
- 现有 Public Control/Runtime/Scheduler Contract 和 JWT 校验语义不变；新增独立 Gateway Session Contract。
- ADR-0006 仍拒绝迁移 Aistio 密码摘要、HS256 Secret 和旧 Token；本 ADR 只允许通过标准 OIDC Provider 提供新的本地身份。
- 本地 Core/RAG 默认启动身份容器并开启 JWT/BFF；`--identity` 作为兼容参数继续接受，`--no-identity` 恢复纯外部 Bearer/API Key 或外部 IdP 配置入口。

## 回滚

关闭 Gateway BFF 配置并停止本地 Identity Profile，即恢复纯外部 Bearer/API Key Resource Server 行为。回滚不删除 `user_identity`、Membership 或 Keycloak 本地卷；删除身份卷属于破坏性操作，必须单独确认。
