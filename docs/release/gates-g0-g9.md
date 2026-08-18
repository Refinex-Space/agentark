---
owner: refinex
updated: 2026-08-18
status: active
referenced_by: docs/README.md
---

# 0.1.0 Gate G0–G9 验收报告

## 结论

G0–G9 使用同一工作区、固定上游 Commit 和版本化脚本执行。详细命令、时间与剩余边界保留在 Phase 23 报告；本文件只保存 Gate 与证明的稳定映射。

| Gate | 证明范围 | 主要命令或证据 | 结论 |
|---|---|---|---|
| G0 | 路径、Commit、Git 状态、Phase 00–22 报告 | `verify_upstreams.py`、`verify-release-readiness.sh` | 通过 |
| G1 | Java/TypeScript 编译、Lint、格式检查 | Maven Verify、Web lint/typecheck/build | 通过 |
| G2 | 单元与集成测试 | Surefire/Failsafe、Vitest | 通过 |
| G3 | ArchUnit、Enforcer、模块与 Import 边界 | Reactor Verify、静态漂移审计 | 通过 |
| G4 | MySQL、Redis、MinIO、Qdrant | Testcontainers、Core/RAG Compose | 通过 |
| G5 | OpenAPI、AsyncAPI、JSON Schema、Snapshot/Event | Contract Test、0.1.0 SHA-256 Baseline、Client Check | 通过 |
| G6 | Build → Publish → Deploy → Run → HITL → Promote/Rollback | 真实四服务 Playwright E2E | 通过 |
| G7 | 身份、租户、Secret、MCP/Skill/Sandbox | Security Test、Trivy、跨租户 E2E | 通过 |
| G8 | SSE、Lease/Fencing、Job、恢复、性能、滚动升级 | Phase 22/23 故障、k6、kind、Restore | 通过 |
| G9 | 文档、迁移、许可、Runbook、版本与发布物 | Knowledge/Harness Gate、SBOM、Release Artifact Rehearsal | 通过 |

## 证据边界

Gate 通过表示仓库开发基线可复现，不表示任何具体生产集群、云 KMS、真实模型供应商、外部 Aistio Cohort、跨区数据库或业务容量已经获批。目标环境必须重新执行发布、部署、恢复和安全 Runbook。
