---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# API 与 Event 契约标准

- Public API 使用 `/api/v1/**`，Internal API 使用 `/internal/v1/**`；身份、Audience 和权限模型不同。
- OpenAPI 3.1、AsyncAPI 和 JSON Schema 是线中立契约；Java DTO 由契约生成或留在所属 Adapter。
- 创建、发布、触发和决策类写操作必须定义 Idempotency Key、Request Hash 和冲突语义。
- 异步请求只在持久工作与幂等结果提交后返回 `202`，并返回稳定 Operation/Run ID。
- 错误使用稳定错误码与 Problem Detail；不得暴露 Secret、内部栈或 Provider 原始对象。
- Event 有全局 `eventId`、Aggregate 内单调 Sequence、Schema Version 和明确终态；先持久化再通知。
- SSE 使用持久 Event ID 和 `Last-Event-ID` 恢复；连接生命周期不代表 Run 生命周期。
- Breaking Change 需要新版本或兼容迁移，支持当前与前一契约版本的滚动窗口。
- 跨平面只传不可变 Snapshot、Descriptor、Command 或 Event，不共享数据库模型。

契约文件尚未由 Phase 03 创建；创建后必须加入知识门禁和 Breaking Change 检查。
