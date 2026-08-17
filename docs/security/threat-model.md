---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: PLAN.md#phase-20--安全加固sandboxmcpskill-供应链与威胁测试
---

# AgentArk 威胁模型

## 1. 结论与适用范围

本威胁模型覆盖 Gateway、Control、Runtime、Scheduler、Web、MySQL、Redis、Object Store、Qdrant、MCP、Skill、Sandbox 和软件供应链。四个部署单元及三套 Schema 所有权不变。固定上游只作为行为证据；AgentScope Provider 仍被限制在防腐层，DeepSeek Harness 仍只作视觉和交互参考。

截至 Phase 20 收官，表中 Critical/High 风险均已处置为 `MITIGATED` 或因能力未装配而 `FAIL_CLOSED`，没有未解释开放项。`MITIGATED` 表示已有代码、配置与自动测试，不表示替代生产渗透、集群策略或云账号验收；后者归 Phase 22–23。

## 2. 资产与数据分类

| 等级 | 资产 | 处理要求 |
|---|---|---|
| Restricted | Secret 值、OIDC/API Key、服务身份、签名私钥、用户上传原文、Tool 参数、敏感检索内容 | 不进入源码、数据库明文字段、Snapshot、Event、Trace、Metric、日志或浏览器持久存储；按需解析、最短驻留、可清零 |
| Confidential | Prompt、Agent Snapshot、Checkpoint、Workspace、审计详情、成本、用户身份、文档 ACL | 强租户授权、加密传输、最小 Owner、严格查询与保留策略 |
| Internal | MCP Tool 描述、模型配置、Job、非敏感 Secret Metadata、SBOM、漏洞报告 | 认证后按 Project/Environment Scope 访问；禁止公共索引 |
| Public | OpenAPI、非敏感产品文档、开源许可证 | 仍需完整性、来源和版本控制 |

权威事实始终在所属 MySQL/Object Store；Redis 只承载缓存、Lease 和通知。Published Revision、Snapshot、Runtime Event、Audit 和迁移历史只追加或不可变。

## 3. Trust Boundary

| 边界 | 不可信一侧 | 受信一侧 | 强制控制 |
|---|---|---|---|
| Internet → Gateway | 浏览器、API Client、Webhook Caller | 公共入口 | OIDC/JWK、API Key 摘要验证、精确 CORS、限流、大小/超时、Webhook 签名/Nonce |
| Gateway → Backend | 可伪造 Header、断线和重放 | Control/Runtime/Scheduler | 下游独立认证；删除客户端身份 Header；Audience-bound Service Identity |
| Control → Runtime | 可编辑 Catalog、部署指针 | 固定 Session/Run | 完整 Snapshot、SHA-256、Schema/Provider Capability、ETag；Runtime 不读 Control DB |
| Runtime → AgentScope | 语言中立 Event/State | Provider 防腐层 | 仅指定包允许 import；Event/Error 映射；Thinking 丢弃；可变 State 走 AgentArk Port |
| Runtime → MCP | 外部主机、DNS、Tool 返回 | MCP Transport | HTTPS 443、主机白名单、全地址公网校验、DNS 固定许可、元数据阻断、超时/大小、Tool Allowlist |
| Runtime → Sandbox | 不可信 Skill、Tool、文件和命令 | 独立执行区 | 非 Root、只读根、无提权/Capability、默认断网、无 Docker Socket、资源/时间/输出上限、按 Session Workspace |
| Knowledge → Qdrant | 用户查询与文档 | 固定 Revision 索引 | 服务端租户/ACL Filter、Payload Scope、READY 状态、Citation 信任标签；Collection 名不作授权 |
| Repository → Release | 依赖、Action、镜像、Artifact | 可分发制品 | 固定 Commit/Digest、SCA/Secret/IaC/CodeQL、CycloneDX、签名、OIDC Provenance、License/NOTICE |

## 4. Actor 与 Abuse Case

- 匿名攻击者：伪造 JWT、API Key、Tenant Header、Webhook，扫描管理端点或耗尽公共额度。
- 低权限租户用户：猜测跨租户 ID、越权列出 Event/Object/Vector、审批不属于自己的 Tool。
- 恶意项目成员：提交私网 MCP、恶意 Skill、Zip Bomb、Prompt Injection 或高成本循环。
- 被攻陷外部服务：MCP DNS Rebinding、异常大响应、返回指令诱导数据外传或重复副作用。
- 被攻陷 Worker/Pod：使用陈旧 Lease 写终态、访问 Control/其他 Schema、窃取 ServiceAccount 或 Docker Socket。
- 供应链攻击者：替换 GitHub Action 标签、镜像标签、Skill Artifact/SBOM/扫描字段或许可证声明。
- 运维误配置：生产启用 Local Secret、匿名安全、通配 CORS、Root Sandbox、无界 Metric Label 或可写根文件系统。

## 5. STRIDE 分析与风险登记

状态：`MITIGATED` 为当前实现已建立防御；`FAIL_CLOSED` 为生产能力缺少受信实现时不启动或不执行；`DEFERRED_DEFENSE` 仅允许作为 Phase 22 的额外纵深防御，不能削弱当前控制。

| ID | STRIDE | 严重度 | 威胁 | Owner | 当前缓解 | 验证 | 状态 |
|---|---|---:|---|---|---|---|---|
| TM-IAM-01 | Spoofing | Critical | JWT Algorithm/Audience/Issuer 混淆 | Gateway + 各 Backend | 非对称 JWK、Issuer/Audience/Algorithm 白名单、下游独立验证 | Gateway Security 集成测试 | MITIGATED |
| TM-IAM-02 | Elevation | Critical | Tenant Header 或对象 ID 猜测跨租户 | Control/Runtime/Scheduler/Knowledge | Principal 决定 Scope；Repository/Vector/Object 显式租户过滤；Tenant 插件仅纵深防御 | 跨租户 SQL/API/Qdrant/Object/SSE 测试 | MITIGATED |
| TM-SEC-01 | Information disclosure | Critical | Secret 出现在源码、DB、Snapshot、日志、Trace、Event | Control/Runtime/Foundation | SecretRef、摘要存储、可清零 Resolver、Telemetry 默认正文关闭、Trivy Secret 门禁 | Vault/脱敏/Event/Snapshot/Secret Scan 测试 | MITIGATED |
| TM-SEC-02 | Spoofing | High | 长期 Vault Token 泄漏或不能轮换 | Control | 按请求读取非符号链接短期 Token 文件；HTTPS；禁止重定向；访问审计 | `VaultKvV2SecretResolverTest`、`FileVaultTokenSource` | MITIGATED |
| TM-SEC-03 | Tampering | High | 已吊销 Secret 被重新启用 | Control | `REVOKED` 终态、乐观锁、启停/轮换/吊销审计 | Secret Lifecycle API/Repository 测试 | MITIGATED |
| TM-MCP-01 | Spoofing/SSRF | Critical | MCP 访问私网、回环、云元数据或混合 DNS | Runtime Provider | HTTPS 443、部署主机白名单、解析全部地址、私网/ULA/CGNAT/元数据阻断 | `McpEndpointGuardTest` | MITIGATED |
| TM-MCP-02 | Tampering | High | DNS Rebinding 在校验后换地址 | Runtime Provider | 签发固定地址 `ConnectionPermit`；组件 SPI 必须消费 Permit；重连重新校验地址集合 | 公网地址变化测试 | MITIGATED |
| TM-TOOL-01 | Tampering/Repudiation | Critical | 审批后替换 Tool 参数 | Runtime | Tool 身份+规范参数 Hash；幂等 Decision；Checkpoint/Fencing Resume | Approval Hash 与重复决策测试 | MITIGATED |
| TM-TOOL-02 | Tampering | High | 非幂等写副作用被自动重试 | Scheduler/Runtime | Tool/Job 显式 Idempotency；无声明默认不重试；Provider 幂等键 | Retry/Dead Letter/Approval 测试 | MITIGATED |
| TM-SKL-01 | Tampering | Critical | Skill Artifact、SBOM、扫描或许可证被替换 | Control Catalog | Artifact Hash、Ed25519 签名稳定清单、CycloneDX、扫描摘要、许可证/Key 白名单 | `SkillSupplyChainVerifierTest` | MITIGATED |
| TM-SKL-02 | Elevation | Critical | 未知或未签名 Skill 直接执行 | Control/Runtime | 生产 Catalog 默认强制供应链证明；Runtime 再校验 ObjectRef Hash；不可信 Skill 只能进入 Sandbox | 供应链验证与 Runtime Hash 测试 | MITIGATED |
| TM-SBX-01 | Elevation | Critical | Sandbox Root/提权/Capability/Docker Socket 逃逸 | Runtime Provider + Platform | 强制非 Root、只读根、`allowPrivilegeEscalation=false`、drop ALL、Seccomp、无 Token/Socket | `SandboxSecurityPolicyTest`、部署清单静态测试 | MITIGATED |
| TM-SBX-02 | DoS | High | 不可信代码耗尽 CPU/内存/PID/磁盘/时间/输出 | Runtime Provider + Platform | Snapshot 六类资源上限；Namespace Quota/LimitRange；Job Deadline/TTL | Sandbox 合同与清单测试 | MITIGATED |
| TM-SBX-03 | Information disclosure | Critical | Sandbox 默认出网外传数据 | Platform | Namespace `default-deny-all`，无 DNS 例外；新增 Egress 必须安全评审 | NetworkPolicy 清单测试；Phase 22 集群探测 | MITIGATED + DEFERRED_DEFENSE |
| TM-RAG-01 | Elevation | High | 文档 Prompt Injection 提升 Tool 权限 | Knowledge/Runtime | RAG/Tool/Model 输出显式不可信标签；Permission 不消费文档指令；Tool Allowlist/HITL 独立 | Event Mapping、RAG ACL/Citation 测试 | MITIGATED |
| TM-RAG-02 | Information disclosure | Critical | Qdrant Filter 可被客户端移除 | Knowledge | 服务端强制 Organization/Project/Revision/Document ACL；固定 Revision | Qdrant Tenant/ACL E2E | MITIGATED |
| TM-RAG-03 | DoS | High | Zip Bomb、恶意文档或超大 Context | Knowledge/Scheduler | 类型/大小/压缩比扫描 Port、受限 Parser、异步批次、Context Budget | Malicious Document/Zip Bomb 测试 | MITIGATED |
| TM-RUN-01 | Tampering | Critical | 陈旧 Worker 绕过 Lease 写 Event/终态 | Runtime/Scheduler | MySQL 单调 Fencing Token 为权威；Redis Lease 只是协调 | 多实例 Lease/Fencing/Recovery 测试 | MITIGATED |
| TM-EVT-01 | Information disclosure | High | SSE/日志暴露 Chain-of-Thought 或正文 | Runtime/Gateway | Thinking Event 丢弃；先持久化后 SSE；敏感正文不采集；有界回放/缓冲 | Event Golden、SSE 重连、脱敏测试 | MITIGATED |
| TM-WHK-01 | Spoofing/Replay | High | Webhook 重放或伪造 | Scheduler/Gateway | HMAC、时间窗、Nonce、持久 Replay Key、限流 | Webhook Replay 测试 | MITIGATED |
| TM-INT-01 | Spoofing | Critical | 伪造 Internal API Service Identity | 四服务 | Public Gateway 不代理 `/internal/**`；Audience-bound 服务身份；下游认证；生产 TLS/NetworkPolicy 为额外纵深 | Internal Security 测试；Phase 22 mTLS 演练 | MITIGATED + DEFERRED_DEFENSE |
| TM-CST-01 | DoS | High | 并发 Run/Token/成本耗尽 | Control/Runtime/Gateway | Rate Limit、Hard Quota 行锁 Reservation、Timeout、Tool/Sub-Agent 上限 | 并发 Quota 与 Runtime Timeout 测试 | MITIGATED |
| TM-SUP-01 | Tampering | Critical | Action/Scanner/容器标签被替换 | Release Engineering | GitHub Action 固定 Commit；Trivy 固定官方多架构 Digest；发布镜像固定 Digest | 工作流静态检查与 Trivy 执行 | MITIGATED |
| TM-SUP-02 | Tampering/Repudiation | High | 发布物无 SBOM、签名或来源证明 | Release Engineering | Maven/仓库 CycloneDX；GitHub SBOM/Build Attestation；Cosign OIDC Keyless 签名 | `supply-chain.yml`、生成脚本 | MITIGATED |
| TM-LIC-01 | Repudiation | High | 上游品牌或特殊许可资产进入分发物 | Architecture + Web | 来源 Manifest、NOTICE、拒绝 DeepSeek 品牌/Plugin Runtime、AgentScope 只依赖/参考 | 许可报告、品牌扫描、迁移清单 | MITIGATED |

## 6. 安全不变量

1. 客户端声明的 Tenant、Collection、Object URI、MCP Endpoint、镜像或 Tool 参数都不是授权事实。
2. Secret 值只能存在于 Provider 请求与当前 RuntimeHandle 的短生命周期内；关闭后主动清零。
3. Approval 只对固定 Tool 身份、参数 Hash、策略版本和有效期生效。
4. 任何陈旧 Lease Owner 都不能提交 Event、State、Job Result 或终态。
5. Tool、RAG、MCP、模型和文档输出默认不可信，不因进入 Prompt 而获得权限。
6. Skill 必须同时满足来源、Hash、签名、许可证、SBOM、扫描和兼容要求。
7. Sandbox 能力缺失或配置不满足全部强制字段时失败关闭，不回退本地 Shell。
8. 安全扫描器、Action、镜像和最终制品都使用 Commit/Digest/Hash，而不是可变标签。

## 7. 验证与复核触发条件

以下变化必须更新本模型并重新评估严重度：新增 Provider/Sandbox 类型；放开 MCP Egress；引入新模型或向量后端；改变 Secret 解析链；新增 GitHub Action/Registry；允许新的 Skill 许可证；增加外部 Webhook/Channel；改变身份、Schema Owner 或 Runtime Snapshot Contract。

生产发布前还必须完成 Phase 22 的真实 Kubernetes Egress/Privilege、mTLS、KMS/Vault 工作负载身份、镜像 Admission、备份恢复和故障演练，以及 Phase 23 的独立安全审查。静态清单通过不能冒充集群执行证据。
