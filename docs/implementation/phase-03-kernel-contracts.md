---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: docs/README.md#阶段执行证据
---

# Phase 03 — Kernel、语言中立契约与架构测试

## Status

- Status: DONE
- Started: 2026-08-15
- Completed: 2026-08-15
- Branch: `main`
- AgentArk HEAD at start: `7b813a377331d1bbb0501e344d78fad5f44afb47`
- AgentScope fixed view: `0c61e7494197ded54eefdeaf9bdeb51807beb752`

## Scope

本阶段只实现不依赖框架的 Kernel、契约骨架和边界测试。AgentScope 固定源码仅用于核对 RuntimeContext 身份维度、Message/Event/Permission 行为语义，以及 State、Workspace、Sandbox、Skill 的能力边界；没有复制 AgentScope 类型或修改固定 Worktree。

明确不包含 Spring Boot Server、数据库 Entity/Mapper/Migration、Redis、HTTP Adapter、AgentScope Runtime Adapter、业务 Endpoint、前端或部署资源。

## Kernel Baseline

`agentark-kernel` 使用 `space.refinex.agentark.kernel` 包，生产代码没有第三方依赖。核心产物包括：

- RFC 9562 UUIDv7 生成、严格解析和时间提取，以及 Organization、Project、Environment、Agent、Revision、Snapshot、Deployment、KnowledgeRevision、Session、Turn、Run、Approval、Job 等强类型 ID；
- `SchemaVersion`、规范 SHA-256 `Checksum`、拒绝凭据/Query/Fragment 的 `SecretRef` 与完整性保护 `ObjectRef`；
- 稳定 `DomainErrorCode`、结构化 `Violation` 和不可变 `DomainException`；
- Agent、Model、Prompt、MCP、Skill、Knowledge、Memory、Workspace、Sandbox、Permission 与 Runtime Limits Snapshot Spec；
- `AgentRevisionSnapshot` 完整依赖闭包，包含组织/项目作用域、版本、`Instant` 时间、`runtimeProvider`、`contentHash` 和防御性复制集合。

Snapshot 不包含任意 `Map`、ORM/Transport 类型或 Provider SDK 类型。Credential 只能由 `SecretRef` 与解析策略构成；MCP Endpoint 和 Object URI 禁止携带 User Info、Query 或 Fragment，避免授权材料进入快照。

## Contract Baseline

| 契约 | 当前范围 |
|---|---|
| 5 份 OpenAPI 3.1 | Public Control、Public Runtime、Internal Control、Internal Runtime、Internal Scheduler；仅包含 Info、空 `paths` 和公共 Problem Detail |
| Runtime AsyncAPI 3.0 | 只声明版本化 Runtime Event 消息；Channel/Operation 等真实交互留给所属实现阶段 |
| Snapshot Schema | Draft 2020-12；强制作用域、UUIDv7、资产版本、Runtime Provider、Checksum、SecretRef 和限制字段 |
| Runtime Event Schema | Draft 2020-12；强制 Event ID、Sequence、类型、时间、组织/项目/Session/Turn/Run 与 Trace 关联 |
| Problem Detail Schema | RFC 9457 兼容字段、稳定 AgentArk Error Code 和结构化 Violations |

Snapshot `contentHash` 的规范事实在架构文档中明确为：对除顶层 `contentHash` 外的完整 Snapshot 做 RFC 8785 Canonical JSON 后计算 SHA-256。Kernel 只提供 `Checksum` 值对象；JSON 编解码与 Canonicalizer 属于后续 Adapter，不在 Kernel 引入 Jackson。

## Architecture Governance

ArchUnit 当前执行四条生产代码规则：Kernel 不得依赖框架/持久化/Redis/AgentScope/Jackson，Domain 不得依赖 Adapter，Library 不得依赖 Server Package，`@SpringBootApplication` 只能位于 Server Package。测试内置一组 Domain → Adapter 故意违规 Fixture，并断言规则确实抛出失败。

仓库知识门禁补充了跨模块静态规则：

- 必需 Phase 03 契约文件不得缺失或为空；
- Kernel 源码不得出现被禁止的框架/Provider Import；
- Library POM 不得依赖四个 Server Artifact；
- `@SpringBootApplication` 不得位于四个批准 Server 模块之外。

规则在后续出现对应源码时自动覆盖，不以当前空模块作为虚假通过证据。

## Dependencies and Licensing

新增依赖均为 Test Scope：

| 依赖 | 版本 | 用途 |
|---|---:|---|
| `com.tngtech.archunit:archunit` | 1.5.0 | 架构边界与故意违规 Fixture |
| `com.github.erosb:json-sKema` | 0.31.0 | Draft 2020-12 JSON Schema 校验，不引入 Jackson |
| `org.yaml:snakeyaml` | BOM 管理 | 安全解析并 Lint OpenAPI/AsyncAPI YAML |

Phase 03 曾启用 `license-maven-plugin` 的默认文件头处理器，但该处理器强制写入 `#%L / %% / #L%` 控制标记，不符合 AgentArk 的简洁源码规范。当前保留插件负责第三方许可证汇总，64 个 Java 文件改用标准 Apache-2.0 文件头，并由知识门禁按唯一模板精确校验。

Phase 03 手工维护的生产代码、测试代码、POM 配置与 OpenAPI/AsyncAPI YAML 已补齐中文说明。JSON Schema 不写非法注释，统一通过中文 `description` 元数据解释字段与定义；`ChineseDocumentationTest` 使用 JDK 编译器语法树持续检查仓库 Java 类型、显式构造器、方法、字段、枚举值和 Record 组件，后续代码继续遵循 `AGENTS.md` 和 `docs/standards/coding.md` 的中文注释标准。

当前 65 个 Java 具名类型均声明唯一的 `@author refinex`。仓库新增 `tools/harness/format_code.sh` 作为任务收尾的唯一格式化入口，IDE 快捷键和 Agent Hook 只允许调用该入口，不再维护独立格式化规则。

全部 20 份 Maven POM 的 `description` 已改为中文表达，37 个依赖、19 个插件和 7 个插件执行块均具有紧邻中文职责说明。知识门禁会持续校验这些 POM 文档约束，但不改变任何依赖坐标、版本、作用域或插件生命周期配置。

## Verification

阶段执行中已完成的窄验证：

- Kernel Main Compile：52 个生产类型编译通过；
- Kernel Unit/Contract/Architecture Test：46 tests，0 failures，0 errors，0 skipped；
- Snapshot、Runtime Event、Problem Detail Golden File 均通过 Draft 2020-12 Schema；
- Snapshot 明文凭据、Runtime Event 缺少 `runId`、Domain → Adapter Fixture 均被负例测试拒绝；
- Spotless 与知识门禁的标准 Java License Header 检查通过。

最终验收结果：

- `./mvnw -pl agentark-kernel -am clean verify`：PASS；52 个生产类型、12 个测试类型，46 tests；
- `./mvnw -DskipTests dependency:tree`：PASS；Kernel Compile Scope 无依赖，Test Scope 无 Jackson；
- `./mvnw verify`：PASS；20/20 Reactor Project 成功；
- `python3 tools/harness/knowledge_gate.py`：PASS；29 个 Active 文档；
- `python3 tools/harness/verify_upstreams.py --require-worktrees`：PASS；两套固定 Commit 未漂移；
- Kernel 禁止依赖搜索、通用 Base/Utils 搜索、JSON 语法、`git diff HEAD --check`：PASS。

## Rollback

本阶段没有数据库、运行数据、外部服务或公共 Endpoint 变更。回滚时只需撤销 `agentark-kernel/src/`、`contracts/`、POM/BOM 依赖、知识门禁和本阶段文档改动；不得删除或改写 `.agentark/upstreams/` 固定 Worktree。
