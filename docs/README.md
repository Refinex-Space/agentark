---
owner: refinex
updated: 2026-08-15
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
| 总体架构 | [系统架构](architecture/overview.md) | 模块、平面、运行时、数据、安全或迁移决策 |
| 平台边界 | [ADR-0001](architecture/decisions/0001-platform-boundaries.md) | 增减服务、跨平面调用或改变 Owner |
| 发布与数据所有权 | [ADR-0002](architecture/decisions/0002-release-and-data-ownership.md) | Revision、Snapshot、Session 固定或 Outbox |
| Runtime Provider 隔离 | [ADR-0003](architecture/decisions/0003-runtime-provider-isolation.md) | Runtime/AgentScope 依赖和包边界 |
| 存储与异步任务 | [ADR-0004](architecture/decisions/0004-storage-and-async-work.md) | Agent State、Work Queue、Redis 或 Knowledge 摄取 |
| 上游与技术基线 | [ADR-0005](architecture/decisions/0005-upstream-and-technology-baseline.md) | 读取/升级 AgentScope、DeepSeek Harness 或基础版本 |
| MySQL 公共规则 | [MySQL/Flyway](database/mysql-conventions.md) | 设计表、索引、Migration 或 TypeHandler |
| Control 数据 | [Control Schema](database/control-schema.md) | IAM、资产、发布、Knowledge、治理表 |
| Runtime 数据 | [Runtime Schema](database/runtime-schema.md) | Session、Run、Work Queue、Event、State、Checkpoint |
| Scheduler 数据 | [Scheduler Schema](database/scheduler-schema.md) | Trigger、Job、Attempt、Delivery、Dead Letter |
| 配置 | [配置参考](config/reference.md) | 新增环境变量、Profile、端口或外部依赖 |
| 编码 | [编码标准](standards/coding.md) | Java/模块/异常/并发实现与评审 |
| API/Event | [契约标准](standards/api.md) | Public/Internal API、Event、幂等和兼容性 |
| 契约文件 | [OpenAPI](../contracts/openapi/)、[AsyncAPI](../contracts/asyncapi/)、[JSON Schema](../contracts/schemas/) | 修改跨平面 API、Runtime Event、Snapshot 或通用 Error |
| 安全 | [安全标准](standards/security.md) | 身份、权限、Secret、租户、Sandbox 或供应链 |
| 术语 | [领域术语](domain/glossary.md) | 命名聚合、接口、表、事件或 UI 文案 |
| 运维与 Loop | [当前 Runbook](guides/runbook.md) | 本地检查、故障定位、回滚和 Loop 就绪性 |
| 上游来源 | [上游基线](migration/upstream-baseline.md) | 读取参考源码或执行迁移审计 |
| 上游迁移审计 | [源码清单](migration/source-inventory.md) | 定位 Service/Core/Harness/Extensions/Frontend 的具体来源 |
| 迁移分类 | [迁移清单](migration/migration-manifest.md) | 决定候选路径的取用类型、目标模块和明确拒绝项 |
| 上游行为 | [行为基线](migration/behavior-baseline.md) | 实现或验收 Gateway、Runtime、Scheduler、HITL、SSE 等关键行为 |
| JPA 语义迁移 | [JPA 到 MyBatis-Plus](migration/jpa-to-mybatis-plus.md) | 实现 Repository、事务、分页、乐观锁、唯一约束或 Tenant 防御 |
| PostgreSQL 风险 | [PostgreSQL 到 MySQL](migration/postgresql-to-mysql.md) | 迁移类型、SQL、索引、锁、DDL 或上游数据 |
| 机械迁入证据 | [机械迁入报告](migration/mechanical-import-report.md) | 复核 AgentScope Service 机械基线、文件 Hash、许可补证和原始测试结果 |
| 许可与资产 | [许可证与 NOTICE](migration/license-and-notice.md) | 复制源码、引入依赖、处理图片/品牌或制作发布物 |
| Go Control 绞杀 | [Aistio 绞杀计划](migration/aistio-strangler.md) | 规划 Control/API/数据 Cohort 迁移、切换和回滚 |

新增 Active 文档必须带 `owner`、`updated`、`status`、`referenced_by` front matter，并在本索引或 `AGENTS.md` 中建立直接路由。

Harness 门禁使用以下仓库根路径建立直接引用：

- `docs/architecture/decisions/0001-platform-boundaries.md`
- `docs/architecture/decisions/0002-release-and-data-ownership.md`
- `docs/architecture/decisions/0003-runtime-provider-isolation.md`
- `docs/architecture/decisions/0004-storage-and-async-work.md`
- `docs/architecture/decisions/0005-upstream-and-technology-baseline.md`
- `docs/database/mysql-conventions.md`
- `docs/database/control-schema.md`
- `docs/database/runtime-schema.md`
- `docs/database/scheduler-schema.md`
- `docs/standards/coding.md`
- `docs/standards/api.md`
- `docs/standards/security.md`
