---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# ADR-0005：上游源码与技术版本基线

## 状态

Accepted

## 基线

| 项目 | 版本或 Commit | 约束 |
|---|---|---|
| AgentScope Java | Maven `2.0.1` | 初始兼容版本 |
| AgentScope source | `35f52181fb37eed97cf0adacf2d1c13a63bbfb7d` | `pom.xml` 将 revision 固定为 2.0.1 的源码证据；无对应远端 Tag，必须按 SHA 读取 |
| DeepSeek Harness | `47f943859bef60e4160492346772ded9b24f765a` | 仅视觉、交互和前端工程参考 |
| Java/Spring | JDK 21 / Boot 4.1.0 / Cloud 2025.1.2 | 最终基线 |
| Persistence | MyBatis-Plus 3.5.17 / Flyway / MySQL 8.4 | 不保留 JPA 自动 DDL |
| Core infra | Redis 8.10.x / S3-compatible Object Storage | Redis 非权威业务事实 |
| RAG | Qdrant 1.18.3 | 仅 RAG Profile |

## 源码使用规则

移动的 `main` 工作区不能作为版本证据。读取上游实现前必须验证目标 Commit 存在，并使用该 Commit 的只读 Worktree、`git show` 或等价固定视图。不得修改上游仓库内容，不得用滚动在线文档替代固定源码和测试。

AgentScope Maven 版本、Source SHA、Compatibility Matrix 必须同时更新；只有其中一项变化时不得验收升级。

