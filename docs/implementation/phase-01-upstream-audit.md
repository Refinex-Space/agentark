---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: docs/README.md#阶段执行证据
---

# Phase 01 — 上游源码审计、行为基线与迁移清单

## Status

- Status: DONE
- Started: 2026-08-15
- Completed: 2026-08-15
- Branch: `main`
- AgentArk HEAD at start: `d012f766e37c827c8f505e74312616aa7e15eb1a`
- AgentScope Source Commit / Worktree HEAD: `0c61e7494197ded54eefdeaf9bdeb51807beb752`
- DeepSeek Harness Source Commit / Worktree HEAD: `47f943859bef60e4160492346772ded9b24f765a`

## Scope

### Included

- AgentScope Service 四个 Java 模块、Go Aistio、React Frontend；
- AgentScope Core、Harness、Extensions、BOM 的具体依赖点、测试和示例；
- DeepSeek Harness 真正 Web Shell、Theme、Layout、Conversation、Terminal、Timeline、Workspace、组件与测试；
- Endpoint、Entity/Table、配置、启动方式、行为基线、迁移分类、许可和 Aistio 绞杀初稿。

### Excluded

- 不迁移、修改或格式化上游源码；
- 不创建 AgentArk 业务模块、不引入依赖、不修改数据库或公共 API；
- 不运行会向固定 detached Worktree 写入构建产物的 Maven/npm/pnpm/Go Make 命令；
- 不验证真实模型、Docker Stack、Kubernetes Controller、浏览器像素或生产部署。

## Inputs Read

按 `PLAN.md` Phase 01 读取了：

- `agentscope-service/{pom.xml,README.md,docker-compose.yml,docker,scripts,docs}`；
- `service-common`、`service-gateway`、`service-dataplane`、`service-scheduler` 的 POM、源码、资源和测试；
- `aistio` 的 Go Module、Makefile、API/Controller/Product/HTTP/Store/Migration/Test；
- `frontend` 的 Package、Router、Pages、API Client、Event/HITL/SSE 和状态管理；
- `agentscope-core`、`agentscope-harness`、`agentscope-extensions`、`agentscope-dependencies-bom`；
- DeepSeek 根 Package/Workspace、`apps/web`、`packages/client/*`、`assets`、`docs`、AGENTS、LICENSE 和 Third-party Notices。

## Work Packages

| Work Package | 状态 | 输入 | 产物 | Gate |
|---|---|---|---|---|
| P01-W01 Service Audit | DONE | Java Service、Aistio、Frontend | [源码清单](../migration/source-inventory.md) | 四 Java 模块 + Go + Frontend 均有具体清单 |
| P01-W02 Harness Audit | DONE | Core/Harness/Extensions/BOM/tests | [源码清单](../migration/source-inventory.md)、[行为基线](../migration/behavior-baseline.md) | 每项能力定位到包/类/测试，框架核心为 dependency/reference |
| P01-W03 Frontend Audit | DONE | AgentScope Frontend + DeepSeek `apps/web/packages/client` | [源码清单](../migration/source-inventory.md) | 真入口、双参考边界、测试和拒绝项明确 |
| P01-W04 Manifest | DONE | 全部审计证据 | [迁移清单](../migration/migration-manifest.md)、[许可](../migration/license-and-notice.md)、[Aistio 绞杀](../migration/aistio-strangler.md) | 每个候选区有分类、目标、行为与许可 Gate |

## Outputs

| 产物 | 用途 |
|---|---|
| [source-inventory.md](../migration/source-inventory.md) | 模块、包、类、Endpoint、表、配置、测试、启动与前端入口事实 |
| [migration-manifest.md](../migration/migration-manifest.md) | `REUSE/ADAPT/REFERENCE/REJECT/DEFER`、目标模块/Phase、拒绝项 |
| [behavior-baseline.md](../migration/behavior-baseline.md) | Runtime/Gateway/Scheduler/Aistio/Frontend/Framework 行为与后续测试门禁 |
| [license-and-notice.md](../migration/license-and-notice.md) | 文件头、LICENSE/NOTICE、第三方闭包、品牌资产和复制阻断 |
| [aistio-strangler.md](../migration/aistio-strangler.md) | Cohort、Route、数据迁移、Read Shadow、切换和回滚初稿 |

## 关键审计结论

1. `service-common` 混合错误、安全、配置、协调、Control/Runtime 实体、资源 Service 和 AgentScope 依赖，必须拆解，禁止整体复制；
2. Dataplane 已有先 Lease 后 Admission、per-session Event Seq、跨副本 Event 轮询、HITL Ticket、Hands Work Queue 和 Snapshot 构建思路；但缺 fencing、标准 SSE resume，且 Override 不持久化；
3. Scheduler 只有分钟 Cron Loop、Channel reconcile、阻塞 Event Reply 和 Worker，没有 AgentArk 目标所需的 durable Job/Attempt/Dead Letter；
4. Gateway 具备静态四平面 Route、Internal 拒绝、Header 清洗和 SSE 长超时，但没有 Gateway 认证、CORS 与 Rate Limit 实现；
5. Aistio 同进程混合 Product Control、Hosted Runtime Store、Kubernetes/ASDP 三套职责，且产品 DDL 不版本化；必须按 Cohort 绞杀；
6. AgentScope Service Frontend 提供丰富功能语义，但没有测试，SSE Parser 无自动重连且静默丢弃坏帧，只能作 Reference；
7. Core/Harness 已提供 RuntimeContext、Message/Event、Permission、Middleware、Workspace、Memory、Skill、Sub-Agent、State、Sandbox、MCP、RAG、Channel 等，应直接依赖；
8. DeepSeek 真 Web 入口是 `@deepseek-ai/dsh-web-frontend` 的 `apps/web/src/main.ts`，Shell 在 `@deepseek-ai/dsh-client-web`；视觉与交互可以参考，Cordis Plugin 内核明确拒绝；
9. AgentScope 固定 Commit 声明 Apache-2.0 且文件有 Apache Header，但根 `LICENSE`/`NOTICE` 缺失、README License 链接失效，源码复制在证据补齐前阻断；
10. DeepSeek 为 MIT，但品牌/Logo、社区图片、近似 Glyph/Icon、Cordis 内核和官方 Claude Platform Payload 不进入 AgentArk。

## Verification

### 已执行

```text
python3 tools/harness/verify_upstreams.py --require-worktrees  PASS
python3 tools/harness/knowledge_gate.py                       PASS（26 份 Active 文档）
Phase 01 文件/分类/来源关键词验收                             PASS
git diff HEAD --check                                         PASS
上游来源仓库与固定 Worktree 前后 status                       PASS（当前均无输出）
```

### 未执行及原因

未运行 Maven/Go/npm/pnpm 上游测试。固定 Worktree 是 Phase 00 建立的只读证据视图，而这些命令会生成 `target/`、`cover.out`、`node_modules/`、`dist/` 或改写 `aistio/ui`；DeepSeek 还要求 pnpm 11.7.0，本机为 10.33.0。可复现命令已写入 [行为基线](../migration/behavior-baseline.md)，Phase 02 必须在隔离可写机械迁入 Worktree 中实际运行。

这意味着本阶段确认的是“源码结构与行为证据完整”，不是“上游全量测试已通过”，也不是浏览器、模型、服务栈或部署验收。

## Risks

- AgentScope 许可包缺口会阻断 Phase 02 的源码复制，但不阻断继续做依赖兼容测试和独立重写；
- Go Product、Go Runtime Store、Java JPA 三套 Session/State 语义重叠，若 Phase 06–13 不先确定 Owner，会形成跨库或双写；
- DeepSeek Token/组件参考很容易滑向 Fork，Phase 17 必须建立 AgentArk 自有 Token、路由和状态模型；
- 上游开发默认 Secret、默认用户和 Auto-DDL 只适合本地，任何向 AgentArk 配置的迁移都应被 Secret/Config Gate 拒绝；
- 本阶段未运行原测试，具体构建兼容性和上游测试健康度仍由 Phase 02 隔离基线证明。

## Rollback

本阶段只修改 AgentArk 的 `PLAN.md` 和 Markdown 文档。可按当前 Git Diff 逐文件撤销；没有业务代码、依赖、数据库、容器、上游 Branch/HEAD 或运行数据需要回滚。固定 Worktree 没有改动。

## Next

Phase 02 只能从本清单的分类出发：先解决 AgentScope LICENSE/NOTICE 证据和建立隔离可写机械迁入基线，实际运行 Java/Go/Frontend 测试，再回到 AgentArk 创建 Maven/BOM/CI 底座。不得跳过许可 Gate，也不得将机械迁入与目标架构重构混在同一证据包。
