---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# 0.1.0 架构漂移审计

## 审计范围与结论

本审计以 `AGENTS.md`、`docs/architecture/overview.md`、ADR、`docs/database/`、`contracts/` 和 `docs/config/reference.md` 为权威输入，对 Maven Reactor、Java/TypeScript 源码、Flyway、Compose、Helm、CI 与阶段证据进行静态和动态核验。

0.1.0 基线未留下 Critical/High 架构红线违规。审计修复了两项高风险文档漂移：Phase 23 仍引用已废弃的 `system-architecture.md`，根 `AGENTS.md` 与 README 仍停留在 Phase 17/“尚无 Helm、尚未迁移实现”的旧状态。动态 E2E 还修复了 Cron Priority 类型、API Key Filter 重复注册和已吊销 Environment Secret 仍可见三项行为漂移。权威架构文件保持为 `docs/architecture/overview.md`。

## 红线核验

| 红线 | 实现证据 | 自动门禁 | 结论 |
|---|---|---|---|
| 只有四个后端部署单元 | `agentark-services/agentark-*-server` | `verify-release-readiness.sh` 统计 `@SpringBootApplication` | 通过 |
| 无跨 Schema 实现依赖 | 三个 Owner 独立 Flyway、Mapper、账号 | ArchUnit、MySQL Sentinel、生产源码扫描 | 通过 |
| Runtime 不读 Control Catalog | Snapshot Internal API、Runtime MySQL | Runtime 架构测试和跨 Schema 扫描 | 通过 |
| AgentScope 类型受控 | Provider 包与 Knowledge RAG Adapter | Import 白名单、兼容测试 | 通过 |
| Scheduler 不拥有推理循环 | Job/Trigger/Delivery + Internal Client | POM/源码规则 | 通过 |
| Redis 非权威存储 | Event/State/Job 事实位于 MySQL/Object | 恢复、Lease/Fencing 测试 | 通过 |
| Snapshot/Session 固定 | Canonical Snapshot、Session Pin | Golden、Publish、Runtime E2E | 通过 |
| Secret 不落明文 | `SecretRef`、Vault/Local Dev Provider | Schema、日志、扫描与 E2E | 通过 |
| Sandbox/MCP/Skill 失败关闭 | 独立策略和安全 Adapter | Phase 20 定向安全测试 | 通过 |
| 渐进式基础设施 | Core MySQL/Redis/Object，RAG Qdrant 可选 | Compose/Helm 默认依赖扫描 | 通过 |
| 延后能力不偷渡 | Team/CRD/ASDP、ES/Neo4j/Kafka 保持 DEFER/REJECT | ADR-0006、默认部署扫描 | 通过 |

## Maven 与包依赖

- Reactor 保持 20 个 Project、9 个根模块与 4 个 Server 子模块；Enforcer 执行 Release Dependency、Convergence、Upper Bound、重复版本与重复类检查。
- `agentark-kernel` 不含 Spring、ORM、Redis、Jackson 或 AgentScope 生产依赖。
- `agentark-runtime-provider-agentscope` 不依赖 Control、Persistence Starter 或 Server；`agentark-scheduling` 不依赖 Runtime 实现。
- AgentScope Import 只存在于专用 Provider，以及 Knowledge 的 `adapter.out.vector.agentscope` 白名单。
- Gateway 无 Mapper、业务 Entity、Control/Runtime 实现依赖或业务数据库连接。

## 数据与状态不变量

- Control、Runtime、Scheduler 使用独立 Flyway 历史；空库和上一版本升级均由 MySQL 8.4 集成测试覆盖。
- Revision、Snapshot、Runtime Event 与历史 Migration 保持不可变；Scheduler V3 只通过前向迁移修复 Trigger Outbox 约束。
- Turn 接单事务在返回 `202` 前持久化 Turn、Run、WorkItem、幂等结果、首事件和 Outbox；Snapshot 获取与编译在 Worker 阶段发生。
- Event 先持久化再通过 SSE 消费；Lease 丢失后陈旧 Fencing Token 被数据库拒绝。

## 漂移分级

### Critical / High

收官后无开放项，也没有风险接受项。

### Medium

- 标准 `harness_audit.py` 会递归读取被忽略的 `.agentark/upstreams/`，并把上游 `AGENTS.md` 的路径当作 AgentArk 路径；发布门禁在排除只读 Worktree 的临时快照上运行该共享审计，同时以仓库 `knowledge_gate.py` 校验真实知识地图。
- 0.1.0 是首个 Snapshot/Event Schema，尚不存在更早的 N-1 版本；兼容策略明确为 N=1，下一版本引入 v2 时才形成 N/N-1 双版本窗口。

### Low

- 根 README 保留英文产品入口，规范和运维文档以中文为主；这不改变实现或契约。

## 禁止的收官方式

审计没有删除测试、增加跳过、降低阈值、改写已发布 Flyway、放宽身份/租户/Sandbox 策略，或把目标环境未执行的证明写成通过。
