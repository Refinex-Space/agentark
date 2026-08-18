---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: AGENTS.md#knowledge-map
---

# 安全标准

- 外部身份使用 OIDC/JWK；内部服务使用 mTLS 或短时、Audience-bound 身份。
- Organization、Project、Environment Scope 由服务端授权上下文确定，不能信任客户端覆盖。
- SQL、对象存储、向量查询、Job 和 Event 查询都显式携带最小 Scope；插件/Interceptor 只是纵深防御。
- API Key 只保存摘要，可轮换、吊销和审计；Secret 明文不得进入数据库、快照、日志、Trace、Event、Fixture 或前端。
- API Key Servlet Filter 只能由 Spring Security Chain 注册，必须禁用容器自动注册；否则 OncePerRequest 标记可能使安全链跳过认证并清空已建立上下文。
- Environment SecretRef 只有在 Binding 为 ACTIVE 且关联 Secret Metadata 为 ENABLED 时有效；禁用或吊销 Metadata 必须立即使新验证、发布和解析失败关闭。
- Tool/MCP/Skill/Sandbox 默认最小权限；写操作、外联、文件和高风险能力需要策略与按需 HITL。
- Approval 固定 Tool/MCP 身份、参数摘要与 Hash、策略版本、过期时间和决策者，防止批准后替换参数。
- Parser、Skill 和不可信代码在受限环境执行，限制网络、文件系统、CPU、内存、时间和产物大小。
- Webhook 验证签名、时间窗、Nonce 和重放；外部副作用没有 Provider 幂等键时默认不自动重试。
- 依赖、镜像和上游源码固定版本/Digest/SHA，保留许可证、SBOM、来源和签名证据。
- 安全逻辑、权限、身份、Secret、CI/CD 或生产配置变更必须单独评审风险和回滚。

## Phase 20 强制控制

- 威胁登记、严重度、Owner、缓解和验证以 [Threat Model](../security/threat-model.md) 为准；Critical/High 不允许无解释开放。
- 生产 Secret Provider 至少使用经验证的 Vault KV v2 集成或同等受审 Adapter；Local Provider 只允许 local Profile。Secret Metadata 必须支持轮换、临时禁用和不可逆吊销，访问只记录脱敏 Audit。
- 远程 MCP 只允许部署主机白名单中的 HTTPS 443；必须解析并检查全部地址，阻断回环、私网、链路本地、IPv6 ULA、CGNAT、云元数据和 DNS Rebinding。组件实现只能消费已签发的固定地址 Permit。
- Skill Version 在生产必须具备来源、SHA-256、Ed25519 签名、CycloneDX SBOM、扫描证明、许可证和兼容性；签名覆盖全部供应链字段。通过签名不代表可以绕过 Sandbox。
- Sandbox 必须使用内容寻址镜像、非 Root、只读根、禁止提权/Privileged、drop ALL、Seccomp、默认断网、无 Docker Socket、Session Workspace 和 CPU/内存/PID/磁盘/时间/输出上限。缺少安全 Adapter 时失败关闭。
- RAG、Tool、MCP 与模型输出始终是不可信内容，不能改变 Permission、Secret Scope、Tool Allowlist 或审批要求。
- GitHub Action 固定 Commit SHA，Scanner/镜像固定 Digest；发布生成 CycloneDX、SCA/Secret/IaC/CodeQL 结果、Cosign 签名和 OIDC Provenance。

## Gateway 边缘安全

- 生产 Gateway 必须显式启用 OIDC/JWK Resource Server，并配置精确 Issuer、Audience 和非对称 JWS 算法白名单；缺少任一必需配置时失败启动，禁止回退共享 HMAC Secret、固定内部 Token 或匿名公共 API。
- Gateway 删除客户端提交的 Principal、Service Identity、Authorities、认证后租户和 Client Certificate 派生 Header。原始 Bearer/API Key 凭据可转发给目标服务重新验证；客户端 Tenant Header 只作为选择意图。
- API Key 明文只存在于当前请求链路，不写日志、Trace、Redis 或本地缓存。边缘正缓存只保存 SHA-256 键和非秘密 Principal，TTL 最大 30 秒；Control 不可用时未命中缓存的请求失败关闭。
- CORS 只接受精确 HTTPS Origin；本地仅允许 loopback HTTP。禁止 `*`、公网明文 HTTP 和隐式 Credential 通配。
- Redis 限流启用后，Redis 故障必须使受保护请求失败关闭；健康探针和内部必要通信不消耗公共额度。Redis 未启用时不得进入整体健康状态。
- `/internal/**` 不通过公共 Gateway 代理；Scheduler Webhook 只允许固定 POST 路由并由 Scheduler 独立完成 HMAC、时间窗、Nonce 和重放保护。
