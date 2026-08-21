---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: AGENTS.md#knowledge-map
---

# AgentArk 文档索引

本文只负责路由，不复制规范事实。冲突时遵循 `AGENTS.md` 中的 Authority 顺序。

| 主题 | 规范文档 | 何时读取 |
|---|---|---|
| 项目入口 | [README](../README.md) | 了解公开定位与真实开发状态 |
| 执行路线 | [PLAN](../PLAN.md) | 开始或验收任何 Phase/Work Package |
| 阶段执行证据 | [Phase 00 执行基线](implementation/phase-00-execution-baseline.md) | 复核仓库、工具链和固定 Worktree 基线 |
| Phase 01 执行证据 | [上游源码审计](implementation/phase-01-upstream-audit.md) | 复核 Service、Framework、Frontend 审计结论和真实验收边界 |
| Phase 02 执行证据 | [构建工程底座](implementation/phase-02-build-foundation.md) | 复核机械基线、Reactor/BOM、质量门禁、CI 和验收结果 |
| Phase 03 执行证据 | [Kernel 与契约基线](implementation/phase-03-kernel-contracts.md) | 复核强类型 ID、Snapshot、Schema、契约 Lint 和架构门禁 |
| Phase 04 执行证据 | [Focused Foundation Starters](implementation/phase-04-foundation-starters.md) | 复核六个职责单一 Starter、条件化配置、安全默认和架构规则 |
| Phase 05 执行证据 | [四服务骨架与本地 Core](implementation/phase-05-service-shells.md) | 复核四个启动单元、健康探针、Compose、Secret 和三 Schema 账号隔离 |
| Phase 06 执行证据 | [MySQL 持久化基线](implementation/phase-06-persistence-baseline.md) | 复核三套 Flyway、DataSource Owner、MySQL Contract Test 与门禁 |
| Phase 07 执行证据 | [IAM 与多租户基线](implementation/phase-07-iam-tenancy.md) | 复核身份映射、租户资源树、角色授权、API Key、越权测试与 Public API |
| Phase 08 执行证据 | [AI 资产目录](implementation/phase-08-asset-catalog.md) | 复核不可变资产版本、Secret 引用、Skill ObjectRef、Public API 与租户隔离 |
| Phase 09 执行证据 | [Knowledge 元数据与 Provider Ports](implementation/phase-09-knowledge-metadata.md) | 复核中立 Knowledge 领域、不可变版本、状态机、V4、Public API、Ports 与租户隔离 |
| Phase 10 执行证据 | [Agent Revision 与 Deployment](implementation/phase-10-revision-deployment.md) | 复核 Draft、发布事务、不可变 Snapshot、V5、Deployment 指针和 Runtime Internal Contract |
| Phase 11 执行证据 | [Runtime 中立领域](implementation/phase-11-runtime-domain.md) | 复核状态机、持久 Work Queue、双层 Event Sequence、Fencing、V2、Fake Engine 与 MySQL 恢复 |
| Phase 12 执行证据 | [AgentScope 防腐层](implementation/phase-12-agentscope-adapter.md) | 复核 Snapshot Compiler、Provider Descriptor、Event/State 防腐、Secret 生命周期、执行引擎与测试边界 |
| Phase 13 执行证据 | [Runtime API 与托管执行](implementation/phase-13-runtime-api.md) | 复核接单事务、Worker、Lease/Fencing、SSE、HITL、取消、恢复与服务装配 |
| Phase 14 执行证据 | [Knowledge Ingestion 与 RAG](implementation/phase-14-knowledge-rag.md) | 复核安全异步摄取、Qdrant、固定 Revision 检索、Citation/Trace 与 AgentScope Tool 防腐层 |
| Phase 15 执行证据 | [Scheduler 与持久 Job](implementation/phase-15-scheduler.md) | 复核 Trigger、Job/Attempt/Lease、Cron/Webhook、Channel、Retry/Dead Letter 与 Internal Client |
| Phase 16 执行证据 | [Gateway 公共入口](implementation/phase-16-gateway.md) | 复核路由、OIDC/JWT、API Key、CORS、限流、SSE 代理与安全边界 |
| Phase 17 执行证据 | [Web 工程基础](implementation/phase-17-web-foundation.md) | 复核独立工程、设计系统、生成式 API Client、SSE Client、CI 与浏览器验收 |
| Phase 18 执行证据 | [Web 核心产品流程](implementation/phase-18-web-features.md) | 复核真实 API 主链路、Web-readiness Contract、四服务 E2E、安全与验收边界 |
| Phase 19 执行证据 | [可观测与治理基线](implementation/phase-19-observability-governance.md) | 复核 OTel、Audit、Usage/Cost、Quota、Evaluation、Web 与部署验收 |
| Phase 20 执行证据 | [安全加固与威胁测试](implementation/phase-20-security-hardening.md) | 复核 Threat Model、Vault、MCP、Skill、Sandbox、RAG 与供应链门禁 |
| Phase 21 执行证据 | [Aistio 绞杀与 Java Control 切换](implementation/phase-21-aistio-strangler.md) | 复核固定源码审计、Contract Freeze、迁移/Shadow 工具、Java-only 默认部署与真实验收边界 |
| Phase 22 执行证据 | [Kubernetes、HA 与恢复](implementation/phase-22-production.md) | 复核生产镜像、Helm、三节点 HA、备份恢复、性能和故障演练证据 |
| Phase 23 执行证据 | [Release Readiness](implementation/phase-23-release-readiness.md) | 复核最终漂移审计、G0–G9、全链路 E2E、发布物和验收边界 |
| 总体架构 | [系统架构](architecture/overview.md) | 模块、平面、运行时、数据、安全或迁移决策 |
| 平台边界 | [ADR-0001](architecture/decisions/0001-platform-boundaries.md) | 增减服务、跨平面调用或改变 Owner |
| 发布与数据所有权 | [ADR-0002](architecture/decisions/0002-release-and-data-ownership.md) | Revision、Snapshot、Session 固定或 Outbox |
| Runtime Provider 隔离 | [ADR-0003](architecture/decisions/0003-runtime-provider-isolation.md) | Runtime/AgentScope 依赖和包边界 |
| 存储与异步任务 | [ADR-0004](architecture/decisions/0004-storage-and-async-work.md) | Agent State、Work Queue、Redis 或 Knowledge 摄取 |
| 上游与技术基线 | [ADR-0005](architecture/decisions/0005-upstream-and-technology-baseline.md) | 读取/升级 AgentScope、DeepSeek Harness 或基础版本 |
| Aistio 切换边界 | [ADR-0006](architecture/decisions/0006-aistio-cutover-scope.md) | 迁移 Go Control、活动 Session Owner、Fallback 或延后 Team/CRD/ASDP |
| OIDC BFF 历史决策 | [ADR-0007](architecture/decisions/0007-oidc-bff-and-local-identity.md) | 外部 Provider 的 Authorization Code BFF 与被 ADR-0008 替代的 Keycloak 本地方案 |
| Built-in Identity | [ADR-0008](architecture/decisions/0008-built-in-identity-mysql.md) | Gateway MySQL 账号、Argon2id、Redis Session、RS256/JWK 与用户治理 |
| MySQL 公共规则 | [MySQL/Flyway](database/mysql-conventions.md) | 设计表、索引、Migration 或 TypeHandler |
| Control 数据 | [Control Schema](database/control-schema.md) | IAM、资产、发布、Knowledge、治理表 |
| Identity 数据 | [Identity Schema](database/identity-schema.md) | 账号、密码摘要、平台角色、锁定、安全事件、Outbox 和签名密钥元数据 |
| Runtime 数据 | [Runtime Schema](database/runtime-schema.md) | Session、Run、Work Queue、Event、State、Checkpoint |
| Scheduler 数据 | [Scheduler Schema](database/scheduler-schema.md) | Trigger、Job、Attempt、Delivery、Dead Letter |
| 配置 | [配置参考](config/reference.md) | 新增环境变量、Profile、端口或外部依赖 |
| 编码 | [编码标准](standards/coding.md) | Java/模块/异常/并发实现与评审 |
| API/Event | [契约标准](standards/api.md) | Public/Internal API、Event、幂等和兼容性 |
| 契约文件 | [OpenAPI](../contracts/openapi/)、[AsyncAPI](../contracts/asyncapi/)、[JSON Schema](../contracts/schemas/) | 修改跨平面 API、Runtime Event、Snapshot 或通用 Error |
| Web 信息架构 | [Web IA](frontend/information-architecture.md) | 增减控制台导航、路由、Feature 或跨页面上下文 |
| Web 设计系统 | [Design System](frontend/design-system.md) | 修改 Token、主题、通用组件、可访问性或视觉基线 |
| Web 上游参考 | [Frontend Source Reference](frontend/source-reference.md) | 借鉴 AgentScope/DeepSeek 前端或评估复制、品牌和许可边界 |
| Web 交互证据 | [Phase 18 交互与截图](frontend/phase-18-interactions.md) | 复核产品主链路、截图生成、可访问性和安全展示边界 |
| 安全 | [安全标准](standards/security.md) | 身份、权限、Secret、租户、Sandbox 或供应链 |
| 安全架构 | [安全架构](security/security-architecture.md) | 复核 Trust Boundary、失败关闭、MCP/Skill/Sandbox/RAG 和供应链控制 |
| 威胁模型 | [Threat Model](security/threat-model.md) | 修改身份、Secret、MCP、Skill、Sandbox、RAG、部署或发布流程 |
| 术语 | [领域术语](domain/glossary.md) | 命名聚合、接口、表、事件或 UI 文案 |
| 运维与 Loop | [当前 Runbook](guides/runbook.md) | 本地检查、故障定位、回滚和 Loop 就绪性 |
| Runtime 运维 | [Runtime Runbook](guides/runtime-operations.md) | 排查 Runtime 接单、Lease、SSE、HITL、孤儿恢复、排空和回滚 |
| Knowledge/RAG 运维 | [Knowledge Runbook](guides/knowledge-operations.md) | 排查摄取、Qdrant、Revision 校验、检索、Snapshot、删除与回滚 |
| Scheduler 运维 | [Scheduler Runbook](guides/scheduler-operations.md) | 排查 Trigger、Job Claim、Lease/Fencing、Retry、Dead Letter、Webhook、Channel 与回滚 |
| Observability/Governance 运维 | [Observability Runbook](guides/observability-operations.md) | 启停 OTel/Prometheus/Tempo/Grafana，排查 Trace、Metric、Audit、Usage 与 Quota |
| Secret 轮换 | [Secret Rotation Runbook](runbooks/secret-rotation.md) | 轮换、禁用、重新启用或永久吊销 Secret Metadata 与 Vault Token |
| 安全事件 | [Security Incident Runbook](runbooks/security-incident.md) | Secret、越权、SSRF、Sandbox 或供应链事件的隔离、证据和恢复 |
| Aistio 切换 | [Aistio Cutover Runbook](runbooks/aistio-cutover.md) | 对已有 AgentScope Service 部署执行备份、Dry Run、Shadow、迁移、灰度和 Java-only 切换 |
| Aistio 回退 | [Aistio Rollback Runbook](runbooks/aistio-rollback.md) | 按 Cohort 回退 Route、保持 Session Owner 并通过 Owner API 补偿 |
| 生产备份恢复 | [Backup/Restore Runbook](runbooks/backup-restore.md) | 规划或执行 MySQL PITR、Object/Qdrant 恢复、Redis 重建和 Reconcile |
| Kubernetes 升级 | [Kubernetes Upgrade Runbook](runbooks/kubernetes-upgrade.md) | 执行 Helm 安装、Expand/Migrate/Contract、Drain、滚动升级与回退 |
| 扩缩容 | [Scaling and Capacity Runbook](runbooks/scaling-and-capacity.md) | 调整 HPA/KEDA、资源、连接池、Provider 并发或容量结论 |
| 故障演练 | [Fault Rehearsal Runbook](runbooks/fault-rehearsal.md) | 注入 Runtime/Redis/MySQL/Qdrant/Provider/OTel/NetworkPolicy 故障 |
| 容量与 RPO/RTO | [Phase 22 容量报告](operations/phase-22-capacity-rpo-rto.md) | 复核本机性能、HA、恢复实测与生产批准边界 |
| 架构漂移审计 | [0.1.0 Architecture Drift Audit](release/architecture-drift-audit.md) | 收官或评审模块、数据、Provider、安全与延后能力红线 |
| 统一 Gate | [G0–G9 报告](release/gates-g0-g9.md) | 复核首个完整开发基线的门禁与证据映射 |
| 发布物 | [Release Artifacts](release/release-artifacts.md) | 构建或校验 Source/Maven/Web/SBOM/Checksum/Signature/Provenance |
| 0.1.0 版本说明 | [Release Notes](releases/v0-1-0.md) | 查看能力、兼容性、升级边界和已知限制 |
| 发布流程 | [Release Runbook](runbooks/release.md) | 生成、签名、校验和交付版本化发布物 |
| 生产部署 | [Deployment Runbook](runbooks/deployment.md) | 准备外部依赖、Values、Migration、工作负载和 Smoke Test |
| 升级与回滚 | [Upgrade/Rollback Runbook](runbooks/upgrade-rollback.md) | 执行 N/N-1、Expand/Migrate/Contract、Drain 和应用回滚 |
| 灾难恢复 | [Disaster Recovery Runbook](runbooks/disaster-recovery.md) | 执行跨 MySQL/Object/Qdrant/Secret/Redis 的恢复与 Reconcile |
| 上游来源 | [上游基线](migration/upstream-baseline.md) | 读取参考源码或执行迁移审计 |
| 上游迁移审计 | [源码清单](migration/source-inventory.md) | 定位 Service/Core/Harness/Extensions/Frontend 的具体来源 |
| 迁移分类 | [迁移清单](migration/migration-manifest.md) | 决定候选路径的取用类型、目标模块和明确拒绝项 |
| 上游行为 | [行为基线](migration/behavior-baseline.md) | 实现或验收 Gateway、Runtime、Scheduler、HITL、SSE 等关键行为 |
| AgentScope 兼容性 | [AgentScope 兼容矩阵](migration/agentscope-compatibility-matrix.md) | 修改 AgentScope 版本、Builder、Event、State、HITL 或 Provider Adapter |
| JPA 语义迁移 | [JPA 到 MyBatis-Plus](migration/jpa-to-mybatis-plus.md) | 实现 Repository、事务、分页、乐观锁、唯一约束或 Tenant 防御 |
| PostgreSQL 风险 | [PostgreSQL 到 MySQL](migration/postgresql-to-mysql.md) | 迁移类型、SQL、索引、锁、DDL 或上游数据 |
| 机械迁入证据 | [机械迁入报告](migration/mechanical-import-report.md) | 复核 AgentScope Service 机械基线、文件 Hash、许可补证和原始测试结果 |
| 许可与资产 | [许可证与 NOTICE](migration/license-and-notice.md) | 复制源码、引入依赖、处理图片/品牌或制作发布物 |
| Go Control 绞杀 | [Aistio 绞杀与迁移规范](migration/aistio-strangler.md) | 执行固定 API/数据映射、兼容代理、Shadow Gate、切换和回滚 |

新增 Active 文档必须带 `owner`、`updated`、`status`、`referenced_by` front matter，并在本索引或 `AGENTS.md` 中建立直接路由。

Harness 门禁使用以下仓库根路径建立直接引用：

- `docs/architecture/decisions/0001-platform-boundaries.md`
- `docs/architecture/decisions/0002-release-and-data-ownership.md`
- `docs/architecture/decisions/0003-runtime-provider-isolation.md`
- `docs/architecture/decisions/0004-storage-and-async-work.md`
- `docs/architecture/decisions/0005-upstream-and-technology-baseline.md`
- `docs/architecture/decisions/0006-aistio-cutover-scope.md`
- `docs/architecture/decisions/0007-oidc-bff-and-local-identity.md`
- `docs/database/mysql-conventions.md`
- `docs/database/control-schema.md`
- `docs/database/runtime-schema.md`
- `docs/database/scheduler-schema.md`
- `docs/standards/coding.md`
- `docs/standards/api.md`
- `docs/standards/security.md`

共享 Harness 审计只识别仓库根形式的 `docs/*.md` 引用，下面的机器清单与上方可点击路由一一对应。它不包含 `.agentark/upstreams/` 的只读上游文档：

- `docs/architecture/overview.md`
- `docs/architecture/decisions/0001-platform-boundaries.md`
- `docs/architecture/decisions/0002-release-and-data-ownership.md`
- `docs/architecture/decisions/0003-runtime-provider-isolation.md`
- `docs/architecture/decisions/0004-storage-and-async-work.md`
- `docs/architecture/decisions/0005-upstream-and-technology-baseline.md`
- `docs/architecture/decisions/0006-aistio-cutover-scope.md`
- `docs/architecture/decisions/0007-oidc-bff-and-local-identity.md`
- `docs/config/reference.md`
- `docs/database/control-schema.md`
- `docs/database/mysql-conventions.md`
- `docs/database/runtime-schema.md`
- `docs/database/scheduler-schema.md`
- `docs/domain/glossary.md`
- `docs/frontend/design-system.md`
- `docs/frontend/information-architecture.md`
- `docs/frontend/phase-18-interactions.md`
- `docs/frontend/source-reference.md`
- `docs/guides/knowledge-operations.md`
- `docs/guides/observability-operations.md`
- `docs/guides/runbook.md`
- `docs/guides/runtime-operations.md`
- `docs/guides/scheduler-operations.md`
- `docs/implementation/phase-00-execution-baseline.md`
- `docs/implementation/phase-01-upstream-audit.md`
- `docs/implementation/phase-02-build-foundation.md`
- `docs/implementation/phase-03-kernel-contracts.md`
- `docs/implementation/phase-04-foundation-starters.md`
- `docs/implementation/phase-05-service-shells.md`
- `docs/implementation/phase-06-persistence-baseline.md`
- `docs/implementation/phase-07-iam-tenancy.md`
- `docs/implementation/phase-08-asset-catalog.md`
- `docs/implementation/phase-09-knowledge-metadata.md`
- `docs/implementation/phase-10-revision-deployment.md`
- `docs/implementation/phase-11-runtime-domain.md`
- `docs/implementation/phase-12-agentscope-adapter.md`
- `docs/implementation/phase-13-runtime-api.md`
- `docs/implementation/phase-14-knowledge-rag.md`
- `docs/implementation/phase-15-scheduler.md`
- `docs/implementation/phase-16-gateway.md`
- `docs/implementation/phase-17-web-foundation.md`
- `docs/implementation/phase-18-web-features.md`
- `docs/implementation/phase-19-observability-governance.md`
- `docs/implementation/phase-20-security-hardening.md`
- `docs/implementation/phase-21-aistio-strangler.md`
- `docs/implementation/phase-22-production.md`
- `docs/implementation/phase-23-release-readiness.md`
- `docs/migration/agentscope-compatibility-matrix.md`
- `docs/migration/aistio-strangler.md`
- `docs/migration/behavior-baseline.md`
- `docs/migration/jpa-to-mybatis-plus.md`
- `docs/migration/license-and-notice.md`
- `docs/migration/mechanical-import-report.md`
- `docs/migration/migration-manifest.md`
- `docs/migration/postgresql-to-mysql.md`
- `docs/migration/source-inventory.md`
- `docs/migration/upstream-baseline.md`
- `docs/operations/phase-22-capacity-rpo-rto.md`
- `docs/release/architecture-drift-audit.md`
- `docs/release/gates-g0-g9.md`
- `docs/release/release-artifacts.md`
- `docs/releases/v0-1-0.md`
- `docs/runbooks/aistio-cutover.md`
- `docs/runbooks/aistio-rollback.md`
- `docs/runbooks/backup-restore.md`
- `docs/runbooks/deployment.md`
- `docs/runbooks/disaster-recovery.md`
- `docs/runbooks/fault-rehearsal.md`
- `docs/runbooks/kubernetes-upgrade.md`
- `docs/runbooks/release.md`
- `docs/runbooks/scaling-and-capacity.md`
- `docs/runbooks/secret-rotation.md`
- `docs/runbooks/security-incident.md`
- `docs/runbooks/upgrade-rollback.md`
- `docs/security/security-architecture.md`
- `docs/security/threat-model.md`
- `docs/standards/api.md`
- `docs/standards/coding.md`
- `docs/standards/security.md`
