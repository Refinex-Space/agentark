---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: PLAN.md#phase-21--go-aistio-strangler数据迁移与-java-control-全量切换
---

# ADR-0006：Aistio 绞杀范围、活动 Session Owner 与最终切换

## 状态

Accepted

## 背景

固定 AgentScope Commit 中的 `aistiod` 同时承载 Product Control、Runtime Store、Data Plane Registration、ASDP gRPC、Kubernetes CRD Controller、Team/Task、Hosted DistributedStore 和 Console 静态资源。它有 231 条注册 Route、18 张 Product 表和 22 张 Runtime 表。这一进程边界与 AgentArk 已接受的四平面和三 Schema Owner 不一致，不能整体翻译或整体保留。

AgentArk 默认 Compose 从创建时就是 Java-only，没有运行 Go `aistiod`，也没有可回切的生产 Go 数据库。Phase 21 仍需提供对已有 AgentScope Service 部署的可验证导出、迁移、影子比较和回退机制，但这些机制不能成为第五个长期服务。

## 决策

### 核心迁移

- Aistio User 只通过显式 `principalMappings` 对应外部 `UserIdentity`、Membership 和 Role Binding；拒绝迁移本地密码摘要、把旧 ID 当授权事实或保留 HMAC 登录体系。
- Environment、Agent、Agent Version、非敏感 Secret Metadata、Deployment 和可表达的 Catalog 资产迁入 Java Control。
- 每个旧 Agent Version 先映射为 AgentArk Draft，再通过现有 Publish API 生成不可变 `AgentRevisionSnapshot`。Checkpoint 保存来源 Commit、来源版本、来源 Snapshot Hash、目标 Revision/Snapshot ID 和目标 Snapshot Hash。
- Deployment Revision 指针归 Control；Cron/Webhook/Channel 执行归 Scheduler，不保留 Go Deployment 单表混合 Owner。
- Vault Credential 必须先迁到真实 Secret Provider，再通过显式 `secretMappings` 创建 Metadata；Webhook 必须换新 SecretRef。禁止导出密文/旧 Token 或合成虚假 External Path。
- Runtime/Scheduler/Gateway 继续使用既有 v1 Contract，不因迁移新增 Go DTO 或数据库列语义。

### Session 与 Run

- 切换期不允许 Runtime 双读 Go Catalog 和 Java Catalog。
- 已在 Go Owner 上活动的 Session/Run 标记为 `GO_UNTIL_TERMINAL`，只接受原 Owner 完成或显式排空；新 Session 只从 Java Deployment/Snapshot 创建。
- 终态 Go Session/Event/Command 只读归档，不通过创建新 Java Run 伪造执行历史。
- Go Runtime Instance、Heartbeat 和 Registration 不迁当前状态；Java Runtime 使用新的 Service Identity 重新注册。

### 临时兼容与回退

- `tools/migration/aistio_shadow.py` 是 Loopback、只读、离线/运维工具，不是默认部署单元。它拒绝 POST/PUT/PATCH/DELETE。
- 模式按 `SHADOW → JAVA_PRIMARY → GO_FALLBACK（仅紧急、只读、有到期时间）→ JAVA_ONLY` 单向推进。
- Go Fallback 只允许 GET，必须设置不超过 24 小时的绝对 UTC 到期时间；到期后失败关闭。最终提交的默认配置为 `JAVA_ONLY`，没有 Go Route、Go 写入或 Fallback。
- 回滚单位是 Cohort。任何回滚都不得改变活动 Session Owner，也不得恢复双写。

### 明确延后和拒绝

| 能力 | 决策 | 原因与后续 |
|---|---|---|
| Agent Team/Task/Message/Plan | `DEFER` | 与 Runtime/Control 核心切换不同 Owner；需要独立 Collaboration Contract、ACL 和恢复模型 |
| Agent/AgentTeam/ModelConfig/MCPServer/SandboxClaim CRD | `DEFER/REFERENCE` | Phase 22 只在明确部署需求时作为投影；CRD 不成为 AgentArk Catalog 权威 |
| ASDP gRPC 与多 Framework SDK Adapter | `DEFER` | 只有 BYO/外部 Framework 产品需求成立时建立版本化 Adapter Contract |
| Hosted `dp_kv/dp_locks/dp_snapshots/dp_bus/dp_async_tools` | `REJECT` 表迁移 | AgentArk 已有 AgentState、Checkpoint、Lease、WorkQueue、ObjectStore Ports；禁止共享 Store 或表翻译 |
| Go 本地 User/Password/JWT HMAC | `REJECT` | AgentArk 使用 OIDC/JWK、API Key 摘要和 Service Identity |
| Aistio Console 静态资源与 SPA Fallback | `REJECT` | AgentArk Web 已独立实现，不复制品牌和 UI Bundle |
| Go Kubernetes Controller 进程 | `REJECT` 为默认依赖 | AgentArk Phase 22 部署不能重新引入第五个后端服务 |

## 影响

该拒绝项禁止迁移 Aistio 密码摘要、共享 HS256 Secret 和旧 Token，不禁止 ADR-0007 定义的标准 OIDC BFF、默认内置账号身份与可替换外部 Provider。

- 不新增 Maven 业务模块、数据库 Schema、部署单元或跨库连接。
- 迁移工具只读 PostgreSQL 导出，写入仅调用 Java Public/Internal API；Secret 只迁引用，大对象单独进入 Object Store 清单。
- 默认 Compose 与 `deploy/helm/agentark/` 保持 Java-only；Helm 生产门禁通过相同规则拒绝 Aistio/Go 依赖回流。
- Team/CRD/ASDP 不阻塞 C1–C8 核心切换，但进入明确 Backlog，后续提升分类必须新增或修订 ADR。

## 验证

- 固定 Internal Contract SHA-256 与 Compose Java-only 架构测试；
- Aistio 导出/报告 JSON Schema；
- 幂等 Dry Run、Apply、Resume、Checkpoint、Hash、Reference 和 Secret 拒绝测试；
- Shadow Match/Error/Latency/Permission Gate；
- `JAVA_ONLY` 模式不调用 Go 的测试；
- 固定 Aistio `go test ./...` 行为基线。
