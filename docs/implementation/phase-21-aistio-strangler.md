---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: PLAN.md#phase-21--go-aistio-strangler数据迁移与-java-control-全量切换
---

# Phase 21：Go Aistio Strangler、数据迁移与 Java Control 全量切换

## 结论

Phase 21 已完成仓库侧收官：固定 Aistio 源码、Go API/表/Auth/Session/Team/Command/Registration/ASDP/CRD/UI/部署事实已审计；三份 Java Internal v1 Contract 以 SHA-256 冻结；只读 PostgreSQL 导出、幂等 API 迁移、Checkpoint/Resume、字段级 Shadow Compare、灰度模式和回滚流程已经实现；默认 Flags 与 Compose 都是 `JAVA_ONLY`，没有 Go Route、Go 写入、PostgreSQL Catalog 或第五个部署单元。

这不等于某个真实生产租户已完成迁移。本仓库没有外部 Aistio 数据库、生产流量、Gateway Cohort 或 Kubernetes 环境，因此真实 Count/Hash/Reference、连续观察窗口、活动 Session 排空和生产 Shadow 阈值仍须对具体部署执行 Runbook。当前验收证明的是迁移能力、确定性 Fixture、契约不漂移和 AgentArk 默认 Java-only 状态。

## 固定审计与决策

固定 AgentScope Commit `0c61e7494197ded54eefdeaf9bdeb51807beb752` 下，Aistio 有 311 个版本化文件、215 个 Go 文件、44 个 Go 测试、18 个 Runtime Migration SQL 和 34 个 YAML/Helm/CRD 文件。审计确认 149 条 Product Route、82 条 Runtime/Kubernetes Route、18 张 Product 表和 22 张 Runtime 表。

列级复核纠正了实现前假设：Product 时间使用 Epoch 毫秒，Runtime 使用 `TIMESTAMPTZ`；Agent 唯一键是 `(owner_id, agent_id)`；Environment `config_json` 是任意 JSON；Vault 和 Webhook 凭据存于 PostgreSQL。最终导出使用 `owner_id/agent_id` 复合来源 Key和 UTC RFC 3339 微秒时间，不导出 `password_hash`、`api_key_hash`、`config_json`、`ciphertext`、`webhook_token` 或 Runtime 错误正文。

[ADR-0006](../architecture/decisions/0006-aistio-cutover-scope.md) 固定以下结果：

- IAM、Catalog、Agent Revision/Snapshot、Environment、Secret Metadata、Knowledge Metadata 与 Deployment 归 Java Control；
- Session/Run/Event/Approval/State/Instance 归 Java Runtime，Trigger/Job/Delivery 归 Java Scheduler；
- 活动来源 Session 固定 `GO_UNTIL_TERMINAL`，终态历史只读归档，新 Session 只用 Java Snapshot；
- Team/Task/Message、CRD、ASDP/BYO 为 `DEFER`，Hosted Store、Go 本地认证、Aistio UI 为 `REJECT`；
- 当前无 Helm Chart；Phase 22 创建 Chart 时必须保持 Java-only。

## Contract Freeze

`contracts/migration/aistio-cutover-v1.json` 固定上游文件锚点、Go API Family、字段/状态/错误/分页映射、批准阈值和默认切换状态。Java Consumer Contract Hash 为：

| Owner | Contract | SHA-256 |
|---|---|---|
| Control | `contracts/openapi/internal-control-v1.yaml` | `09e626016220d19dea803577590be15770964d3349ebbf99bcb797d47c6a3f5e` |
| Runtime | `contracts/openapi/internal-runtime-v1.yaml` | `c8721c26e07004a3b96cc3aa52b7fe0ccbb414791dfa909ad6d30393b75c78e8` |
| Scheduler | `contracts/openapi/internal-scheduler-v1.yaml` | `1d9af00e3c56573c98bdbe736a7ce384d8950a12197a60748bf25408e45c7062` |

Kernel Contract Test 会同时校验这三份 Hash、`JAVA_ONLY` 默认值、非核心 DEFER/REJECT 和 Compose 不含 Aistio/Go/PostgreSQL 17。Runtime、Scheduler 和 Gateway Client 没有因迁移新增 Go DTO、跨库读表或兼容依赖。

## 迁移与 Shadow 工具

`tools/migration/export-aistio.sql` 在 `REPEATABLE READ READ ONLY` 事务导出 NDJSON，并绑定切换前备份 ObjectRef/SHA-256。`aistio_migrate.py` 提供 Validate、Dry Run 和 Apply：

- 校验固定 Commit、Schema、备份 Hash、复合主键、Foreign Reference、状态、UTC、Payload/Snapshot Hash；
- Principal、Secret、Webhook、Model/MCP/Skill/Profile/Policy 必须显式映射，目标 ID 必须是 UUIDv7；
- 目标 Key 规范化碰撞会阻断，不静默覆盖；
- 每个外部操作写前落 `IN_FLIGHT`，写后原子落 `SUCCEEDED/FAILED`，稳定 Key Reconcile 后可 Resume；
- 所有写入只调用现有 Control/Runtime/Scheduler API；Scheduler Trigger 请求与 Public Contract 必填字段一致；
- Checkpoint 保存来源版本与目标 Revision/Snapshot/Hash；Command 不重放，大对象只生成带 Size/Checksum/MediaType 的搬运清单。

`aistio_shadow.py` 只绑定 Loopback、只接受白名单 GET。Case 显式投影 Go/Java 字段语义或集合项，安全 Case 不能忽略授权字段；报告只记录状态、Hash、JSON Pointer 和延迟。任一响应出现 Password/Token/Ciphertext/Private Key 字段即阻断。`GO_FALLBACK` 最长 24 小时；`JAVA_ONLY` 忽略旧 Go Allowlist，缺目标映射时失败关闭且不调用 Go。

## 确定性证据

Fixture Dry Run 实际结果：8 个来源资源、14 个操作、1 个大对象、0 个失败；Primary Key/Reference、状态、UTC、Plan/Backup Hash 与 Secret Reference-only 全部通过。Fixture 的活动 Session 保持 Go Owner，两个来源 Agent Version 均产生独立 Revision/Snapshot 映射；第二次 Resume 没有重复 API 调用。

Shadow Fixture 以不同 Go/Java DTO 外壳验证语义投影，达到 100% Match、0% 5xx、0 Security Mismatch 和 0 Secret Redaction Mismatch。另有负向测试覆盖权限差异、危险 Ignore、Secret 字段、缺映射、到期 Fallback 和 Java-only 回 Go。

## 验证证据

实际执行：

```bash
./mvnw -T 1C clean verify
python3 -m unittest discover -s tools/migration/tests -v
python3 tools/migration/aistio_migrate.py dry-run \
  --export tools/migration/fixtures/aistio-export-v1.json \
  --config tools/migration/fixtures/aistio-cutover-test.json \
  --plan .agentark/migration/phase21-fixture-plan.json \
  --report .agentark/migration/phase21-fixture-report.json
docker compose -f deploy/compose/docker-compose.yml --profile core config
cd .agentark/upstreams/agentscope-java-2.0.2/agentscope-service/aistio && go test ./...
# 临时 PostgreSQL 17 + 固定 aistiod 创建 cp/rt Schema 后执行 export-aistio.sql，
# 将含 Product/Runtime 代表行的 NDJSON 直接送入 aistio_migrate.py validate。
python3 tools/harness/verify_upstreams.py --require-worktrees
python3 tools/harness/knowledge_gate.py
git diff HEAD --check
```

| 验证 | 实际结果 |
|---|---|
| Maven 全量 | 20 个 Reactor 模块全部 `SUCCESS`，总耗时 2 分 52 秒；包含 MySQL、Redis、Qdrant、四 Server、Contract 与 AgentScope 测试 |
| Phase 21 Python | 13 个迁移/Shadow 单元与 Loopback 集成测试全部通过 |
| Kernel Contract | `ContractSchemaTest` 14 个、`AistioCutoverContractTest` 3 个通过；全量 Kernel 共 93 个测试通过 |
| Aistio 上游 | 固定 detached Worktree `go test ./...` 全部通过，上游工作区无新增改动 |
| PostgreSQL 导出 | 临时 PostgreSQL 17 + 固定 `aistiod` Migration 后，空库与 10 类代表行导出均成功；输出直接通过迁移 Validate；临时进程和容器已停止删除 |
| Compose | `core config` 可解析，输出不含 `aistio`、`aistiod`、`golang` 或 `postgres:17` |
| Dry Run | 8 个来源、14 个操作、1 个 Object、0 失败，报告符合迁移 JSON Schema |

## 运维、回滚与剩余边界

[Cutover Runbook](../runbooks/aistio-cutover.md) 固定备份、只读导出、Dry Run、Shadow、Apply/Resume、Tenant/Capability 灰度、最终同步和归档顺序；[Rollback Runbook](../runbooks/aistio-rollback.md) 固定按 Cohort 回 Route、保持 Session Owner 和通过 Owner API 补偿，禁止 Flyway Down、删不可变事实或恢复 Java/Go 双写。

真实部署执行前仍需提供：Aistio 只读 PostgreSQL 备份、目标 Principal/Asset/Profile/Secret 映射、独立 Service Token File、Shadow Case、Gateway 灰度 Route、活动 Session 清单、观察窗口和批准人。临时 PostgreSQL 只证明固定 Schema/列/类型和导出链路，不代表真实数据质量或规模；没有上述输入时工具会失败关闭，不能把本阶段 Fixture 报告用于生产批准。
