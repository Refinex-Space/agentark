---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: PLAN.md#phase-20--安全加固sandboxmcpskill-供应链与威胁测试
---

# Phase 20：安全加固、Sandbox、MCP/Skill 供应链与威胁测试

## 结论

Phase 20 已建立从威胁登记到可执行门禁的安全闭环：身份/租户沿用下游独立验证和 Owner Repository；Control 新增 Secret 轮换/禁用/启用/不可逆吊销与 Vault KV v2；Runtime Provider 新增 MCP 固定连接许可和 Sandbox 强制合同；Catalog 新增 Skill 签名/SBOM/扫描/许可证门禁；RAG、Tool、Model Event 明确不可信；仓库新增 SCA、Secret、IaC、CodeQL、CycloneDX、Cosign 和 OIDC Provenance 流程。

首次安全扫描真实发现 MCP Java SDK 0.17.0 的 DNS Rebinding High 风险和 Netty 4.2.15.Final 的 High 拒绝服务风险。收官没有写忽略项：AgentScope 仍固定 2.0.2，但排除其 MCP 0.17 聚合依赖，Provider 显式使用 MCP 1.0.0 Core + Jackson 2；Netty BOM 覆盖到 4.2.16.Final。最终 Maven 实际解析 SBOM和 Web Lockfile 均为 0 个 High/Critical，完整 AgentScope 编译、兼容和执行测试通过。

## Threat Model 与边界

[Threat Model](../security/threat-model.md) 按 Restricted/Confidential/Internal/Public 分类资产，列出 Internet、Gateway、Backend、Snapshot、AgentScope、MCP、Sandbox、Qdrant 和 Release Trust Boundary，并对租户越权、Secret、SSRF、Prompt Injection、Tool Side Effect、Sandbox Escape、供应链、外传、成本耗尽、Webhook Replay 和 Internal Spoofing 给出 Owner、严重度、缓解、验证和状态。

Critical/High 当前没有未解释开放项。生产 Kubernetes 的真实 NetworkPolicy CNI、Pod Security Admission、mTLS、工作负载身份、镜像 Admission 和逃逸测试仍由 Phase 22–23 在真实环境验证；这些是纵深部署验收，不允许削弱本阶段已经失败关闭的代码边界。

## Secret

Control Public API 新增：

```text
POST /api/v1/projects/{projectId}/secrets/{secretMetadataId}:rotate
POST /api/v1/projects/{projectId}/secrets/{secretMetadataId}:disable
POST /api/v1/projects/{projectId}/secrets/{secretMetadataId}:enable
POST /api/v1/projects/{projectId}/secrets/{secretMetadataId}:revoke
```

四个命令只接受外部版本或乐观锁版本，不接受 Secret 值。MyBatis 更新同时检查 Project、当前状态和版本；`REVOKED` 是终态。`IamTenancySecurityIT` 在真实 MySQL 上验证 Rotation → Disable → Enable → Revoke 及吊销后拒绝恢复，Audit 只记录 Metadata ID 和动作。

Vault Adapter 只允许 HTTPS、禁止重定向、固定 KV v2 版本、限制 64 KiB 响应，只读取 `data.data.value`。工作负载 Token 按请求从专用绝对普通文件读取，拒绝符号链接、空白和超限内容，使用后清零。Local Provider 仍受 local Profile 和显式开关双重限制。Runtime 缺少生产 Secret/Model/Component Factory 时 Worker 保持关闭，不回退测试实现。

## MCP、Tool 与敏感区域

`McpEndpointGuard` 将 Catalog 信息模型变为 Runtime 强制边界：

- 远程只允许 HTTPS 443 和部署主机白名单；
- 校验全部 DNS 结果，拒绝回环、私网、链路本地、IPv6 ULA、CGNAT、云元数据和混合地址；
- 为 Component Factory 签发固定地址、连接/请求超时和响应大小的 `ConnectionPermit`；
- 重连前地址集合变化即按 DNS Rebinding 拒绝；
- STDIO 只允许部署命令白名单。

Tool 参数流只记录长度，Approval 绑定规范参数 Hash；替换任一参数会产生不同 Hash。Tool/RAG/Model 输出分别标记 `UNTRUSTED_TOOL_OUTPUT`、`UNTRUSTED_RETRIEVAL_CONTENT` 和 `UNTRUSTED_MODEL_OUTPUT`。可选 `SENSITIVE` Permission Policy 要求 Model/MCP `dataRegion` 命中允许区域，发布前失败而不是运行时漂移。

## Skill 与 Sandbox

生产 Skill Version 创建默认强制：ObjectRef SHA-256/大小/媒体类型、来源、许可证白名单、CycloneDX JSON、扫描状态与 Artifact Hash、部署信任根中的 Ed25519 Key。签名稳定清单覆盖 Artifact、来源、许可证、SBOM、扫描器、时刻和状态，字段替换会失败。local E2E 可以显式关闭，但生产配置默认开启。

Sandbox Snapshot 要求 `UNTRUSTED/KUBERNETES`、内容寻址镜像、签名/Provenance/扫描状态、非 Root、只读根、禁止提权/Privileged、drop ALL、RuntimeDefault Seccomp、默认断网、无 Docker Socket、Session Workspace、CPU/内存/PID/磁盘/时间/输出上限，以及输出 Secret/PII 隔离策略。`deploy/security/sandbox-policy.yaml` 提供 restricted Namespace、default-deny NetworkPolicy、ResourceQuota、LimitRange 和暂停 Job 基线；没有受信 Adapter 时不执行宿主 Shell。

## 供应链

- `.trivy.yaml` 与 `tools/security/scan-repository.sh`：Secret/IaC、Maven 实际解析 CycloneDX、Web Lockfile High/Critical 门禁；Trivy 官方镜像固定多架构 Digest。
- `tools/security/scan-image.sh`：只接受 `repository@sha256:<64-hex>`，先阻断 High/Critical 和嵌入 Secret。
- `tools/security/generate-sbom.sh`：生成 Web/非 Maven 仓库 CycloneDX；Java 以 Maven Aggregate BOM 为权威。
- `security.yml`：Dependency Review、Trivy、Java/Web CodeQL；所有 Action 固定 Commit。
- `supply-chain.yml`：GitHub OIDC SBOM/Build Attestation、Cosign Keyless 镜像签名；镜像输入必须固定 Digest。
- 根 `NOTICE`、`THIRD_PARTY_NOTICES.md` 和许可迁移文档固定发布物证据与 DeepSeek/AgentScope 参考边界。

## 验证证据

实际执行：

```bash
./mvnw -T 1C clean verify
pnpm --dir agentark-web api:check
pnpm --dir agentark-web lint
pnpm --dir agentark-web typecheck
pnpm --dir agentark-web test
pnpm --dir agentark-web build
./tools/security/scan-repository.sh
./tools/security/generate-sbom.sh target/security
pnpm --dir agentark-web licenses list --prod --json
go run github.com/rhysd/actionlint/cmd/actionlint@v1.7.7 .github/workflows/*.yml
bash -n tools/security/*.sh
python3 tools/harness/knowledge_gate.py
python3 tools/harness/verify_upstreams.py --require-worktrees
git diff HEAD --check
```

| 验证 | 实际结果 |
|---|---|
| Maven 全量 | 20 个 Reactor 模块全部通过，耗时 2 分 41 秒；含 MySQL、Redis、Qdrant、四 Server Context、架构、Contract 与 AgentScope 测试 |
| Phase 20 定向测试 | Vault/Access Audit 4、Skill Supply Chain 3、敏感区域 1、MCP SSRF/Rebinding 5、Sandbox 合同/部署 4、Event/Approval 8 均通过 |
| AgentScope/MCP 兼容 | AgentScope Compatibility、Compiler、Fake Model 多 Session 与 Harness 构建通过；MCP 1.0.0 Jackson 2 覆盖未出现二进制回归 |
| Web | OpenAPI Client 无漂移；Lint、Typecheck、7 个测试文件/12 个用例、生产 Build 通过 |
| Trivy | Secret 0、IaC 0；Maven SBOM High/Critical 0；pnpm Lockfile High/Critical 0 |
| SBOM/License | Maven CycloneDX 232 个组件；仓库 CycloneDX 生成并通过 JSON 检查；Web 生产许可证为 0BSD/Apache-2.0/ISC/MIT |
| Workflow/Shell | Actionlint v1.7.7、YAML 解析和 `bash -n` 通过；镜像脚本拒绝非 Digest 引用 |

## 真实边界与后续

- 本地没有 GitHub OIDC 身份和受控 Registry，因此没有伪称已产生真实 Cosign 签名或 GitHub Attestation；工作流语义已通过 Actionlint，真实 Tag/手工发布 Run 由 Phase 22–23 留存证明 URL。
- 没有对某个生产服务镜像执行漏洞扫描，因为本阶段没有授权发布目标 Registry/Digest；任何镜像发布都必须先通过 `scan-image.sh`，不能用仓库扫描替代。
- Kubernetes 清单通过静态安全测试和 Trivy IaC；真实 CNI Egress、Admission、资源耗尽与 Sandbox Escape 必须在 Phase 22 集群验证。
- Trivy 扫描 Maven CycloneDX 时会提示部分非 SHA-256 Hash 算法不用于验证；漏洞解析结果以 PURL/版本为准，Artifact 完整性仍使用 Maven/仓库 SHA-256 和签名链。
