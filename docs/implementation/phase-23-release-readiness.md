---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# Phase 23：Release Readiness、架构漂移审计与首个完整基线

## 结论

Phase 23 已建立 0.1.0 首个完整开发基线的架构漂移审计、G0–G9 映射、契约摘要冻结、发布物构建入口、Release/Deployment/Upgrade/Rollback/DR Runbook、Changelog、Contributing 与 Security Policy。最终状态只在全量后端、前端、真实 E2E、Compose、Helm、安全、性能、恢复、供应链和干净临时仓库发布物演练全部完成后更新为 DONE。

## 收官修复

- 修正 Phase 23 对已删除 `system-architecture.md` 的引用，统一指向权威 `docs/architecture/overview.md`。
- 更新根 `AGENTS.md` 和 README 的 Phase、Helm、迁移与发布状态，删除“仍未迁移实现”的旧声明。
- 冻结全部 OpenAPI、AsyncAPI、JSON Schema 和迁移 Contract 的 0.1.0 SHA-256；任何变化必须显式版本化。
- 新增发布静态门禁和只接受干净 Git/精确 Tag 的 Source/Maven/Web/SBOM/Checksum 构建入口。
- 补齐根级 CHANGELOG、CONTRIBUTING、SECURITY 与发布、部署、升级回滚、灾难恢复文档。
- 扩展真实四服务 E2E 覆盖 Knowledge Ingestion、Revision 2 新 Session、API Key/Secret 吊销失败关闭和 Scheduler 定时触发；权威状态恢复由独立 Restore Rehearsal 从空环境验证。
- 修复 Cron Trigger 字符串配置无法被 Runtime Turn Handler 解析的问题，并用真实 Scheduler Worker 证明 Trigger 会产生成功接单的 Runtime Turn Job。
- 修复 API Key Filter 被 Servlet 容器与 Spring Security Chain 重复注册导致有效 Key 返回 401；容器注册现显式禁用，Gateway 与 Control 均重新认证成功。
- 修复 Environment Binding 未联查 Secret Metadata 生命周期的问题；Metadata 禁用或吊销后，Draft 验证、发布和新解析立即失败关闭。
- 把 E2E MySQL 就绪条件从单次成功改为连续四次成功，避免初始化重启窗口造成 Flyway 偶发连接失败。

## 验证证据

原始性能、HA 与恢复证据保留在已忽略的 `.agentark/evidence/`；敏感值、备份正文和 Token 不进入仓库。

| Gate | 实际执行结果 | 结论 |
|---|---|---|
| 后端全量 | `./mvnw -B -ntp -T 1C clean verify`；20/20 Reactor Project 成功，总计 `02:50` | 通过 |
| 前端全量 | Frozen Install、OpenAPI Client Check、ESLint/Prettier、TypeScript、7 个 Vitest 文件/12 个测试、Vite Build、2 个基础 Playwright 和 1 个真实产品流 Playwright 全通过 | 通过 |
| 真实产品流 | 四服务与三套 MySQL 从空库启动；Knowledge 经真实 Scheduler Job 进入 READY；Publish/Deploy/Session/SSE/HITL/Promote/Rollback/Cron/Dead Letter/Redrive/API Key 与 Secret 吊销均通过，移除关键测试 Skip 后复验为 `31.5s` | 通过 |
| Contract/Compose | 0.1.0 Contract SHA-256 无漂移；Core 与 RAG Compose 均可解析 | 通过 |
| Helm/HA | 仓库固定 Helm Wrapper 完成 Lint/Template/Schema，共 114 个资源全部有效；三节点 kind/Calico、每工作负载 2 副本，安装 `48s`、Drain 恢复 `16s`、滚动升级 `22s` | 通过 |
| 数据恢复 | MySQL Full/PITR、Qdrant Snapshot、Redis 非权威状态恢复与 Reconcile 从隔离空环境执行；PITR `9s`、总恢复 `23s` | 通过 |
| 性能/故障 | 5,783 个 HTTP 请求零失败；读/写/Turn/Event/Scheduler P95 为 `38.84ms`/`605.58ms`/`329.62ms`/`446.65ms`/`1.77s`；SSE 首事件 `192.36ms`；Runtime/Provider/Scheduler/Qdrant/OTel 故障测试通过 | 通过 |
| 安全/供应链 | 仓库 Secret/IaC、Maven 与 pnpm High/Critical 扫描通过；六个生产镜像本地重建；Trivy 例外仅限 Vault Token 挂载路径、指定文件和到期日 | 通过 |
| 发布物 | Node `v24.14.1`、pnpm `11.22.0` 的干净临时候选仓库生成 Source/Maven/Web、两份 CycloneDX、Release Notes、Manifest 与 `SHA256SUMS`，逐文件校验通过 | 通过 |
| 上游/知识 | 两个固定 Worktree HEAD 正确且干净；Knowledge Gate 通过 80 个活动文档；排除只读 `.agentark` 后共享 Harness Audit 为 0 Error/0 Warning | 通过 |

JaCoCo 汇总 16 个有生产代码的模块：Instruction `50,338/82,673`（`60.89%`）、Branch `2,266/5,499`（`41.21%`）、Line `9,345/15,105`（`61.87%`）。覆盖率用于识别薄弱区域，没有通过删测试或降低阈值制造通过结果。

`dependency:analyze` 的 20 个 Reactor Project 构建成功。它对 Spring Boot Starter、自动配置反射入口和聚合测试依赖报告了 Used Undeclared/Unused Declared 警告；这些依赖仍由真实上下文测试、Enforcer、依赖收敛和 SBOM 证明使用边界，不按字节码静态结果盲删 Starter。后续变更必须按模块逐项证明后再移除依赖。

## 风险与批准边界

0.1.0 证明仓库开发基线可构建、测试、部署和恢复，不批准具体生产环境。真实 IdP、Vault/KMS、托管 MySQL/Redis/Object/Qdrant、Registry OIDC、Sandbox RuntimeClass、跨区容量和外部 Aistio Cohort 仍由目标环境 Owner 执行相同 Runbook 并审批。本次真实产品 E2E 与 Restore Rehearsal 属于同一收官批次的两个隔离门禁：恢复演练使用可确定校验的权威状态 Fixture，而不是把浏览器 E2E 数据当作生产备份样本。

## 回滚

本阶段未改写已发布 Flyway，也未自动提交、打 Tag、推送、发布或写入 Registry。代码和文档变更可按文件回退；一旦未来发布 0.1.0，数据库回滚仍遵循只回退兼容应用 Digest、Migration 只前向修复的规则。
