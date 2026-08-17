---
owner: refinex
updated: 2026-08-17
status: active
referenced_by: PLAN.md#phase-18-agentark-web-核心产品流程
---

# Phase 18：AgentArk Web 核心产品流程

## 结论

Phase 18 已完成 Build → Publish → Deploy → Run → Approve → Observe 主链路，并覆盖 Govern、AI 资产、Knowledge 和 Scheduler 操作。浏览器只调用真实 Gateway Public API/SSE；生产构建没有 Mock。为避免 UI 虚构能力，本阶段先补齐 Control、Runtime、Scheduler 的最小 Web-readiness Public Contract，再生成 Client 和实现 Feature。

统一 Audit、Usage/Cost、Quota 与 Evaluation 的事实存储和检索仍由 Phase 19 拥有。本阶段 Approval 只提供关联 Run 和参数摘要 Hash，不伪造尚不存在的 Audit API。

## Web-readiness Public Contract

| Owner | 新增公共能力 | 边界 |
|---|---|---|
| Control | Agent Cursor 列表、Environment Deployment 列表、公开 Snapshot Inspector、Revision Diff | 继续使用 Control 授权；Snapshot 返回契约投影，不暴露 Entity 或 Secret |
| Runtime | 聚合 Runtime 状态、受授权的 Event Payload 下载 | Payload 下载先验证 Run、租户和 Event 归属，不能把 Object URI 当授权 |
| Scheduler | Job Cursor 列表、Trigger Cursor 列表与创建 | Scheduler 仍不依赖 Runtime 实现或 Control/Runtime 数据库 |

这些 API 都是增量 Public Contract，没有新增表、跨 Schema SQL、共享 Mapper 或 Flyway。`Checksum` Jackson 表示统一为规范字符串，Runtime Event 的 `payload`/`payloadRef` 使用非空互斥输出，与 OpenAPI/JSON Schema 保持一致。

## 产品 Feature

- `Govern`：Organization/Project/Environment 上下文，Membership、Role/Binding、Service Account/API Key、Secret Metadata/Binding 和只读/拒绝状态；
- `Build`：Agent 列表与 Draft、Prompt/Model/MCP/Skill/Profile/Policy、Skill Artifact、Knowledge 元数据/Revision/Ingestion；
- `Release`：Validation、Publish、Snapshot、Diff、Deployment、Promote、Rollback、生产风险确认和乐观锁冲突；
- `Runtime`：Session、Turn、Run、持久 Event Replay/SSE、Timeline、Message Streaming、调用树、Usage、Cancel 和 Artifact；
- `Approval`：Pending/终态列表、Tool 与参数 Hash、Risk/Policy、Approve/Reject/Expired/冲突和 Run 关联；
- `Operate`：Trigger、Job、Retry、Dead Letter/Redrive、Webhook/Channel 投影、Knowledge Ingestion、Runtime Instance 与 Deployment 摘要。

Server State 由 TanStack Query 管理；表单 Draft 保持独立本地状态。发布、部署、权限、审批和 Redrive 不做危险乐观更新。Problem Detail 统一展示稳定 Code 和 Trace ID。

真实产品页 axe 首轮发现浅色主题 `text-subtle` 在白色/浅灰表面仅有 3.44–3.70:1 对比度。本阶段将共享浅色辅助文本 Token 调整为 `#58667b`，覆盖 Session 固定信息、Timeline、调用树和 Inspector，而不是在单个页面覆盖颜色；深色 Token 保持最差场景高于 4.5:1。390px 产品页复核还发现多项 Operate Tabs 和固定底部导航的滚动内容扩大根节点，现由 Tabs/导航容器承担局部横向滚动，并在固定导航边界裁剪滚动内容，不通过裁剪产品主内容掩盖溢出。

## 真实 E2E Harness

`agentark-web/tools/e2e-stack.mjs` 创建随机端口的临时 MySQL/Redis，编译并以 Test Classpath 启动 Gateway、Control、Runtime、Scheduler。测试身份使用临时 RSA 密钥签发 RS256 JWT，并真实校验 Issuer、Audience 和有效期。Runtime 使用确定性测试执行引擎产生消息、Usage、Approval、Checkpoint 和 Resume 结果；它验证平台状态机和浏览器交互，不声称覆盖真实模型或 AgentScope Provider 的外部网络行为。

`agentark-web/e2e/real-product-flow.spec.ts` 已覆盖：

1. 创建 Project/Environment 和版本化 Prompt/Model/MCP/Skill/Knowledge；
2. Agent Draft 验证失败、修正、发布、Snapshot 检查和 Deployment；
3. Session/Turn/SSE/HITL/完成；
4. 新 Revision、Promote、旧 Session 保持旧 Revision、Rollback；
5. Service Account/Role/API Key 一次展示与吊销状态；
6. 跨租户 Scheduler 访问拒绝；
7. Dead Letter Redrive；
8. Run 页面 axe 严重违规为零、390px 窄屏无根节点横向溢出。

E2E 成功或失败都按精确 Manifest 清理它创建的进程和容器，不复用或删除开发者已有 Compose 资源。

## 执行证据

2026-08-17 实际执行：

| 命令 | 结果 |
|---|---|
| `./mvnw clean verify` | 20/20 Reactor 模块成功；Control/Runtime/Scheduler MySQL 和 Gateway Redis Testcontainers 通过 |
| `pnpm --dir agentark-web test:e2e:real` | 真实四服务产品主流程通过，最终 1 个场景 22.2 秒，完整 Harness 约 1.3 分钟 |
| `pnpm --dir agentark-web api:check` | 生成 Client 与三套 Public OpenAPI 一致 |
| `pnpm --dir agentark-web lint` | ESLint 与 Prettier 检查通过 |
| `pnpm --dir agentark-web typecheck` | TypeScript Project Reference 检查通过 |
| `pnpm --dir agentark-web test` | Vitest/Testing Library 单元测试通过 |
| `pnpm --dir agentark-web build` | Vite 生产构建通过，Feature 路由保持 Lazy Chunk |
| `pnpm --dir agentark-web test:e2e` | Chromium 设计系统、键盘、axe 和窄屏基线通过 |

以上前端门禁将在文档收口后再次执行；最终状态以本次交付记录为准。

## 已知边界与风险

- 真实 E2E 的 Runtime Engine 是确定性测试实现，不覆盖真实 Model/MCP/Qdrant/AgentScope Provider 的网络、限流或费用行为；
- 组合 E2E 的自定义 JWT Decoder 不用于验证 API Key 认证链；API Key 创建/摘要/一次展示/吊销状态由真实 Control MySQL 验证，认证和缓存吊销继续由 Phase 07/16 的专门测试覆盖；
- Approval 的统一 Audit 检索、Usage/Cost 治理、Quota 和 Evaluation 属于 Phase 19；
- 浏览器自动化当前只运行 Chromium；Firefox、WebKit、屏幕阅读器、高对比模式和 200% 缩放仍是发布前验证项；
- 本阶段没有生产 OIDC、真实 Provider 凭据、静态站点部署或 Helm 交付。

## 回滚

本阶段没有数据库迁移。回滚时可撤销 `agentark-web` Feature、生成 Client、三套增量 Public Contract/Adapter、测试 Harness、CI 和文档更新。已存在数据无需迁移或清理；增量 API 的撤销会影响 Phase 18 Web，因此 Contract 与 Web 必须在同一回滚窗口恢复。临时 E2E 容器和进程由 Harness 自动清理。
