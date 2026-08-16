---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: AGENTS.md#knowledge-map
---

# API 与 Event 契约标准

- Public API 使用 `/api/v1/**`，Internal API 使用 `/internal/v1/**`；身份、Audience 和权限模型不同。
- OpenAPI 3.1、AsyncAPI 和 JSON Schema 是语言中立契约；Java DTO 由契约生成或留在所属 Adapter。
- 每个写操作必须明确可否自动重试；发布、触发、决策和任何声明为可重试的创建操作必须定义 Idempotency Key、Request Hash 和冲突语义。
- 未建立持久 Idempotency Record 的同步资源创建默认不可自动重试，必须使用 Scope 内业务唯一键和稳定 `409` 避免静默重复；API Key 创建因明文只展示一次，明确属于不可重放操作。
- 异步请求只在持久工作与幂等结果提交后返回 `202`，并返回稳定 Operation/Run ID。
- 错误使用稳定错误码与 Problem Detail；不得暴露 Secret、内部栈或 Provider 原始对象。
- Event 有全局 `eventId`、Aggregate 内单调 Sequence、Schema Version 和明确终态；先持久化再通知。
- SSE 使用持久 Event ID 和 `Last-Event-ID` 恢复；连接生命周期不代表 Run 生命周期。
- Breaking Change 需要新版本或兼容迁移，支持当前与前一契约版本的滚动窗口。
- 跨平面只传不可变 Snapshot、Descriptor、Command 或 Event，不共享数据库模型。

Phase 03 已建立以下契约基线：

- `contracts/openapi/`：Public Control、Public Runtime 和三个 Internal API 的 OpenAPI 3.1 文档；除已经实现的 Public Control IAM 路径外，其他文档的 `paths` 仍必须为空，业务 Endpoint 由所属阶段按真实实现补充；
- `contracts/asyncapi/runtime-events-v1.yaml`：Runtime Event 的 AsyncAPI 3.0 消息骨架；
- `contracts/schemas/agent-revision-snapshot/v1.json`：不可变 Revision Snapshot 的 Draft 2020-12 Schema；
- `contracts/schemas/runtime-event/v1.json`：稳定 Runtime Event Envelope；
- `contracts/schemas/problem-detail/v1.json`：公共 Problem Detail 错误模型。

Phase 07 增加以下 IAM 契约约束：

- `public-control-v1.yaml` 只声明实现中存在的 Organization、Project、Environment、Membership、Role、Role Binding、Service Account、Permission 与 API Key 路径；契约测试对完整 Path 集合做精确比较；
- Public Control 全局安全方案只允许 Bearer JWT 或 `Authorization: ApiKey <credential>`；健康与 Info 端点不属于该业务 OpenAPI；
- Organization/Project/Environment 路径参数使用 UUIDv7。客户端 Tenant Header 不是授权输入，Owner Scope 必须由已认证 Principal、路径资源归属和数据库有效权限共同确定；
- IAM 无权、未认证、未找到、冲突和输入错误分别使用稳定 `ARK-IAM-*` Problem Detail 代码，不回显 SQL、约束名或内部异常；
- `contracts/schemas/iam-public/v1.json` 是 IAM Public DTO 的唯一 Schema。`ApiKeyView` 不得包含摘要或明文；`CreatedApiKeyResponse.plaintext` 是只在创建响应出现一次的 `readOnly` 字段；
- API Key 创建不允许调用方自动重试。创建响应丢失时先按非秘密元数据定位并吊销，再显式创建新 Key；不得缓存、记录或通过列表接口恢复明文。

Golden File、明文 Secret 负例和文档结构 Lint 由 `agentark-kernel` 测试执行，必需文件与 Kernel/Server 边界由知识门禁检查。首次发布后的修改必须增加 Breaking Change 检测；在不存在已发布前一版本的 Phase 03 不伪造兼容比较结果。
