---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md#安全架构
---

# AgentArk 安全架构

## 安全控制链

```text
Internet
  -> Gateway：OIDC/API Key、CORS、限流、Header 清洗、SSE 代理
  -> Backend：独立认证、Tenant Authorization、Owner Repository
  -> Snapshot：版本/Hash/Provider Capability、无明文 Secret
  -> Runtime：持久 Work/Event、Lease + MySQL Fencing、HITL 参数 Hash
  -> AgentScope 防腐层：MCP Permit、Skill Hash、Sandbox Contract、Event Trust Label
  -> 外部资源：Vault / MCP / Qdrant / Object Store / 独立 Sandbox Trust Zone
```

安全事实不依赖单个 Filter 或 Redis：Gateway 是第一道边界，下游仍验证身份；MyBatis Tenant 插件是纵深防御，Repository 仍显式带 Scope；Redis Lease 丢失时 MySQL Fencing 仍拒绝陈旧写；Qdrant Collection 名和 Object URI 都不是授权机制。

## 身份、租户与内部调用

- Public API 使用 OIDC/JWK 或只保存摘要的 API Key；Issuer、Audience、Algorithm 均精确匹配。
- Gateway 删除客户端伪造身份 Header，Tenant Header 只表达选择意图。
- Organization → Project → Environment 由服务端资源关系和 Principal 权限共同解析。
- Internal API 不经过公共 Gateway；服务身份必须面向目标服务 Audience。Phase 22 已验证 NetworkPolicy 运行边界；生产 mTLS 仍由目标平台验收，二者都不替代当前 JWT 验证。
- API Key 只在创建时展示，数据库保存摘要，吊销后短 TTL 缓存受控失效。
- Control 的 API Key Filter 禁止 Servlet 容器自动注册，只能在 Spring Security Chain 的 Bearer Filter 之前执行；Gateway 预验证后仍转发原始方案供 Control 独立复核。

## Secret 生命周期

Control 只保存 `SecretMetadata`、外部路径/版本和 Environment Binding。Public API 不提供值读取；`rotate/disable/enable/revoke` 都使用乐观锁并写 Audit。`REVOKED` 是不可逆终态。Environment Binding 不单独覆盖 Metadata 生命周期：只有 Binding=`ACTIVE` 且 Metadata=`ENABLED` 的联查结果可以解析为 SecretRef，禁用或吊销 Metadata 会立即阻断新 Draft 验证、发布和运行时解析。

生产可验证集成为 Vault KV v2：

- 只接受 HTTPS 地址并禁止重定向；
- 按请求读取工作负载挂载的短期 Token 文件，拒绝符号链接、非普通文件和超限内容；
- 固定 KV v2 版本，响应最大 64 KiB，只读取 `data.data.value`；
- Token、响应正文、外部路径和版本不进入异常或 Audit；
- 返回值用可清零字符数组持有；
- `local` 文件 Provider 只在 local Profile 且显式开启时装配。

Runtime Worker 在没有生产 `SecretResolver`、Model 或 Component Factory 时保持禁用并失败关闭，不回退环境变量明文或测试实现。

## MCP 与 Tool

Catalog 固定 Transport、Endpoint、TLS/Auth SecretRef、Tool Descriptor、风险、读写性和幂等元数据。Runtime 在组件创建前签发 `ConnectionPermit`：远程只允许 HTTPS 443 和部署白名单主机，全部 DNS 地址必须是公网，回环、私网、链路本地、IPv6 ULA、CGNAT、基准测试网和云元数据地址均拒绝；STDIO 只允许固定命令白名单。

Provider Component Factory 必须使用 Permit 中的固定地址集合建立连接，重连前重新校验；不得再次直接信任主机名。Tool 参数不进入 Event，审批只暴露规范 Hash。写副作用需要 Provider Idempotency；未知或非幂等写默认不自动重试。Tool/MCP 返回携带 `UNTRUSTED_TOOL_OUTPUT`，不能提升 Permission。

## Skill 供应链

生产 Catalog 在创建 Skill Version 时强制：

1. ObjectRef 的 SHA-256、大小和媒体类型与 Object Store 一致；
2. 来源 URI 和 SPDX 许可证存在，许可证命中部署白名单；
3. CycloneDX JSON SBOM 有界且内容 Hash 匹配；
4. 扫描证明状态为 `PASSED`，且绑定同一个 Artifact Hash；
5. Ed25519 签名的 Key ID 存在于部署信任根；
6. 签名稳定清单同时覆盖 Artifact、来源、许可证、SBOM、扫描器、时刻和结果。

`local` E2E 可以显式关闭供应链强制，但生产默认开启。Runtime 仍复核 Artifact Hash；任何 Skill 只在独立 Sandbox 执行，不因签名而获得宿主权限。

## Sandbox

Snapshot Sandbox Contract 强制 `UNTRUSTED/KUBERNETES`、内容寻址镜像、非 Root、只读根、禁止提权/Privileged、drop ALL、RuntimeDefault Seccomp、默认断网、无 Docker Socket、Session Workspace 和 CPU/内存/PID/磁盘/时间/输出六类上限。

`deploy/security/sandbox-policy.yaml` 提供 restricted Namespace、default-deny NetworkPolicy、ResourceQuota、LimitRange 和暂停的安全 Job 基线。缺少生产 Sandbox Adapter 时 Runtime 不执行不可信代码；不会回退宿主 Shell。Phase 22 已用 Calico 验证主应用 NetworkPolicy CNI，Helm 提供 Sandbox Namespace/RuntimeClass；Pod Security Admission、镜像签名 Admission 和逃逸测试仍必须在安装真实隔离 Runtime 的目标集群验收。

## RAG 与 Prompt Injection

- System/User/RAG/Tool/Model 使用不同来源和信任语义；RAG、Tool、Model 输出不可信。
- 文档文本不能修改 Permission、Tool Allowlist、Secret Scope 或审批要求。
- Retrieval 固定 Snapshot 中的 KnowledgeRevision，服务端强制租户/Document ACL Filter。
- Context Budget、Dedupe 和 Citation 限制数据量；Citation 标记 `UNTRUSTED_EXTERNAL`。
- 敏感项目只能使用策略允许的 Model/MCP 区域和数据外传 Tool。
- 安全拒绝形成稳定错误/审计，不返回内部规则细节。

## 软件供应链

- Maven Verify 生成聚合 CycloneDX 与许可证报告；Trivy 生成仓库级 CycloneDX，补充 Web/IaC。
- `security.yml` 执行 Dependency Review、Trivy SCA/Secret/IaC 和 Java/Web CodeQL。
- `.trivyignore.yaml` 只允许带路径、理由和到期日的精确例外；当前唯一例外是 Vault Token 文件挂载路径被 `KSV-0109` 误判为 ConfigMap Secret，真实 Token 仍只来自 Secret Volume。
- Trivy 使用官方镜像的固定多架构 Digest，不使用可变 Action 标签。
- `supply-chain.yml` 通过 GitHub OIDC 签发 SBOM/Build Attestation；人工指定的镜像必须固定 Digest，先扫描，再 Cosign Keyless 签名。
- GitHub Action 全部固定 Commit SHA。任何升级都重新核对上游发布、安全公告与 SHA。
- DeepSeek Logo、品牌、插件内核和特殊许可 Payload 未迁入；AgentScope Core/Harness 继续作为固定版本依赖与参考。

## 失败关闭与剩余边界

生产缺少 OIDC、Vault/Runtime Secret Resolver、Model/Component Factory、恶意文件扫描器或 Sandbox Adapter 时，对应能力不可用，不使用 Dev/Fake 实现维持“看似可用”。OTel Backend 不可用可以降级，因为 Audit/Event/Usage 权威事实仍在 MySQL；安全认证、授权、Fencing、供应链和 Secret 校验不可降级。

本架构不声称已经完成真实云 Vault 工作负载身份、Kubernetes Admission、mTLS、镜像 Registry 权限、托管服务 HA/DR 或第三方渗透。Phase 22 已完成仓库级 HA/恢复基线；上述外部能力仍由目标环境和 Phase 23 验收，不是允许绕过当前安全边界的开放项。
