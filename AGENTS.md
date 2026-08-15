# AgentArk Repository Instructions

## Project

AgentArk is an architecture-first Java Agent Application Platform built around a provider-neutral Runtime and AgentScope Java 2.0.2. The repository currently contains planning and governance documents; implementation modules are introduced only by the matching `PLAN.md` phase.

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
python3 tools/harness/knowledge_gate.py
python3 tools/harness/verify_upstreams.py
git diff HEAD --check
```

Maven, pnpm, Compose, and Helm commands do not exist yet. Introduce and document them only in their owning PLAN phase; never claim they ran before the corresponding files exist.

## Workflow

1. Read `git status --short`, the current PLAN phase, and every routed document for the affected area.
2. Preserve user changes and staging. Do not reformat or refactor unrelated files.
3. For multi-file, architecture, API, database, dependency, CI, or infrastructure work, present a scoped plan before editing. Wait only for destructive/high-risk approval or a correctness-critical missing decision.
4. Implement the smallest coherent slice. Update the normative document before or with a changed architecture, schema, contract, or config fact.
5. Run the smallest relevant tests, then the knowledge gate and `git diff HEAD --check`.
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

Loop automation is disabled until build/test commands and a bounded implementation slice exist. See the runbook for readiness criteria.
