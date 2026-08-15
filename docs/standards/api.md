---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# API 与 Event 契约标准

- Public API 使用 `/api/v1/**`，Internal API 使用 `/internal/v1/**`；身份、Audience 和权限模型不同。
- OpenAPI 3.1、AsyncAPI 和 JSON Schema 是语言中立契约；Java DTO 由契约生成或留在所属 Adapter。
- 创建、发布、触发和决策类写操作必须定义 Idempotency Key、Request Hash 和冲突语义。
- 异步请求只在持久工作与幂等结果提交后返回 `202`，并返回稳定 Operation/Run ID。
- 错误使用稳定错误码与 Problem Detail；不得暴露 Secret、内部栈或 Provider 原始对象。
- Event 有全局 `eventId`、Aggregate 内单调 Sequence、Schema Version 和明确终态；先持久化再通知。
- SSE 使用持久 Event ID 和 `Last-Event-ID` 恢复；连接生命周期不代表 Run 生命周期。
- Breaking Change 需要新版本或兼容迁移，支持当前与前一契约版本的滚动窗口。
- 跨平面只传不可变 Snapshot、Descriptor、Command 或 Event，不共享数据库模型。

Phase 03 已建立以下契约基线：

- `contracts/openapi/`：Public Control、Public Runtime 和三个 Internal API 的 OpenAPI 3.1 骨架；当前 `paths` 必须为空，业务 Endpoint 由所属阶段按真实实现补充；
- `contracts/asyncapi/runtime-events-v1.yaml`：Runtime Event 的 AsyncAPI 3.0 消息骨架；
- `contracts/schemas/agent-revision-snapshot/v1.json`：不可变 Revision Snapshot 的 Draft 2020-12 Schema；
- `contracts/schemas/runtime-event/v1.json`：稳定 Runtime Event Envelope；
- `contracts/schemas/problem-detail/v1.json`：公共 Problem Detail 错误模型。

Golden File、明文 Secret 负例和文档结构 Lint 由 `agentark-kernel` 测试执行，必需文件与 Kernel/Server 边界由知识门禁检查。首次发布后的修改必须增加 Breaking Change 检测；在不存在已发布前一版本的 Phase 03 不伪造兼容比较结果。
