# AgentArk Repository Instructions

## Project

AgentArk is an architecture-first Java Agent Application Platform built around a provider-neutral Runtime and AgentScope Java 2.0.2. Phases 07–10 established Control IAM, versioned AI and Knowledge assets, immutable Agent Revision/Snapshot, Deployment and Internal Contracts. Phases 11–13 established the provider-neutral Runtime domain, durable Event/Work/State persistence, AgentScope anti-corruption layer, Snapshot Compiler, managed Runtime API/SSE/HITL and recovery. Phase 14 established the safe Knowledge ingestion pipeline, Control result transaction, Qdrant adapter, fixed-Revision retrieval, Citation/Trace contracts and AgentScope Knowledge Tool; Scheduler Job/Attempt assembly remains owned by Phase 15.

## Authority

Resolve conflicts in this order:

1. This file for safety, workflow, commands, and knowledge routing.
2. `docs/architecture/overview.md` plus accepted ADRs for architecture.
3. `docs/database/`, `contracts/`, and `docs/config/` for persistence, contracts, and configuration.
4. `PLAN.md` for Phase 00–23 execution order and evidence gates.
5. Source and tests for current implementation facts.
6. Fixed-commit upstream source for reference behavior only.

Do not let `README.md`, a moving upstream branch, or an implementation shortcut override a higher authority.

## Current Commands

Run from the repository root:

```bash
./mvnw -version
./mvnw -N validate
./mvnw verify
./tools/dev-up.sh
./tools/dev-status.sh
./tools/verify-core.sh
./tools/dev-down.sh
python3 tools/harness/knowledge_gate.py
python3 tools/harness/verify_upstreams.py --require-worktrees
git diff HEAD --check
```

The Maven reactor validates module boundaries, Kernel behavior, contract Schema, Foundation auto-configuration, four service contexts, architecture rules, and build policy. The local Compose stack is development-only and contains empty-business Server shells; pnpm and Helm commands do not exist yet and must be introduced only by their owning PLAN phase.

## Workflow

1. Read `git status --short`, the current PLAN phase, and every routed document for the affected area.
2. Preserve user changes and staging. Do not reformat or refactor unrelated files.
3. For multi-file, architecture, API, database, dependency, CI, or infrastructure work, present a scoped plan before editing. Wait only for destructive/high-risk approval or a correctness-critical missing decision.
4. Implement the smallest coherent slice. Update the normative document before or with a changed architecture, schema, contract, or config fact.
5. After writing code, run the smallest relevant tests, the knowledge gate, and `git diff HEAD --check`. The repository deliberately has no automatic Java formatter; preserve the surrounding style and do not perform unrelated mass formatting.
6. Report changes, executed tests, risks, rollback, and the next directly related step. Never invent evidence.

Do not commit, push, publish, delete branches, or modify upstream repositories without explicit user authorization.

## Hard Boundaries

- Exactly four backend deployment units: Gateway, Control, Runtime, Scheduler.
- No cross-schema SQL, Mapper, foreign key, transaction, or shared database account.
- Runtime executes immutable snapshots and never reads editable Control catalog tables.
- `agentark-runtime` is provider-neutral; AgentScope Runtime imports exist only in `agentark-runtime-provider-agentscope`.
- Scheduler owns Jobs, not inference loops, Runtime state, or Knowledge metadata.
- Redis is cache/lease/notification infrastructure, never the sole copy of Revision, Run, Agent State, Approval, or Job facts.
- Return `202 Accepted` only after the owning MySQL transaction durably creates the work and idempotency result.
- Published revisions, snapshots, events, and migration history are immutable.
- Never store plaintext secrets, tokens, credentials, or connection strings in source, docs, logs, fixtures, events, or snapshots.

## 中文注释规范

- 手工维护的 Java 源码中，每个类、接口、记录、枚举、构造器和方法，无论可见性如何，都必须具有准确的中文 Javadoc；每个具名类型必须且只能声明 `@author refinex`。
- 每个字段、常量、枚举值和记录组件都必须有中文说明。记录组件通过记录类型 Javadoc 中的中文 `@param` 说明，普通字段和常量使用相邻中文 Javadoc。
- 方法注释必须说明职责，并按实际情况说明参数、返回值、单位、不变量、副作用、异常、安全约束、并发或空值语义。禁止只复述标识符或添加没有信息量的注释。
- 重写方法也必须用中文说明当前实现契约，不能只写 `{@inheritDoc}`。
- 每个手工维护的 XML、YAML 配置块和属性都必须具有相邻中文注释，说明用途、范围、允许值或所有者。禁止修改生成文件或向机器维护的输出复制注释。
- JSON 等不支持注释的格式必须通过 `title`、`description`、`$comment` 等标准元数据表达同等信息，禁止为满足注释要求破坏文件合法性。
- 每个手工维护的 Flyway `CREATE TABLE` 必须使用数据库原生 `COMMENT` 为表及全部字段写入准确中文注释；字段注释应说明业务含义、单位、空值或安全语义，不能用行首 `--` 说明代替数据库元数据。
- 状态、类型、等级、布尔值等可穷举字段必须在字段 `COMMENT` 中列出全部数据库合法值及其含义，并与 `CHECK` 约束和代码枚举保持一致；迁移测试必须验证表和字段注释实际写入数据库。
- 未发布迁移必须在发布前补齐注释；已发布迁移保持不可变，只能新增 Flyway 前向迁移补充或修正 `COMMENT`，不得原地改写历史文件。
- 测试代码遵循相同的类型、字段、夹具和方法注释要求。测试注释说明所证明的行为或边界，不复述实现步骤。
- License Header 是法律元数据，不能替代 API、类型、方法、字段或配置注释。

## Definition of Done

- Scope and affected owners are explicit.
- Code, Flyway, contracts, config, tests, and docs agree with the knowledge map.
- Relevant unit/integration/contract/architecture tests actually pass.
- Knowledge gate and `git diff HEAD --check` pass.
- Security, tenancy, transactions, idempotency, fencing, retries, timeouts, and rollback are reviewed where applicable.
- Stage status changes to `DONE` only with a linked phase report and reproducible evidence.

## Knowledge Map

- Documentation index: `docs/README.md`
- Architecture: `docs/architecture/overview.md`
- Decisions: `docs/architecture/decisions/`
- MySQL models: `docs/database/`
- Configuration governance: `docs/config/reference.md`
- Coding/API/security standards: `docs/standards/`
- Domain terms: `docs/domain/glossary.md`
- Current runbook: `docs/guides/runbook.md`
- Upstream baseline: `docs/migration/upstream-baseline.md`
- Executable roadmap: `PLAN.md`

Loop automation remains disabled until a bounded implementation slice and meaningful tests exist. See the runbook for readiness criteria.
