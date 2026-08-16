---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: docs/README.md
---

# Phase 10 Agent Revision 与 Deployment 执行报告

## 结论

Phase 10 建立了 AgentArk 的发布边界：Agent 保持稳定身份，Draft 是唯一可编辑配置，发布器在 Control 本地事务内解析固定资产版本并生成不可变 `AgentRevisionSnapshot`，Environment Deployment 只保存目标 Revision 指针。Runtime 只能经受 Service Identity 保护的 Internal Contract 读取 Snapshot 与 Deployment Descriptor，不能读取 Control 数据库、Draft 或可变 Catalog。

本阶段没有接入 `HarnessAgent`、执行 Session、实现 Canary 分流或生成具体语言 Client。Internal OpenAPI 是语言中立的稳定手写 Client Contract；Phase 12 的 AgentScope Provider 必须据此实现转换和兼容性测试。

## 固定上游取用边界

只读复核使用 AgentScope 固定 Commit `0c61e7494197ded54eefdeaf9bdeb51807beb752`。Aistio Agent Version 的追加意图、Dataplane Control Resolve API 和 `HarnessAgentBuildService` 的组装输入被归类为 `ADAPT/REFERENCE/DEPENDENCY`。共享 Internal Token、可变 Agent 回退、Runtime 直接 Catalog/Control DB 读取和上游 DTO/Entity 外泄均明确 `REJECT`。

上游 Deployment 主要表达 Cron、Webhook 与 Channel 触发，不具备 Environment 内 `desiredRevisionId`、Promote/Rollback 指针和 Canonical Snapshot 事务。因此 AgentArk Deployment、Snapshot Schema、Content Hash、Outbox 和 ETag Contract 均为独立设计，不宣称来自上游现成能力。

## Draft、Publisher 与不可变 Snapshot

`AgentDraftSpec` 以强类型引用固定 Model Provider/Profile、Prompt Version、MCP Server Version 与 Tool Allowlist、Skill Version、READY Knowledge Revision、Memory/Workspace/Sandbox Profile Version、Permission Policy Version 和 Runtime Limits。Draft 更新使用 `expectedVersion` 乐观锁；Runtime API 不提供 Draft 读取路径。

`AgentPublisher` 在锁定 Draft 后执行以下门禁：

1. 所有 Catalog 资产必须与目标 Project 同 Scope、稳定身份为 `ACTIVE`、版本为 `PUBLISHED`；
2. Model Profile 必须满足 Tool、Vision、Structured Output、Streaming 等声明能力；Credential 只能是可见 `SecretRef`；
3. MCP Tool 必须存在于版本 Descriptor，跨 Server 名称不得冲突，`CRITICAL` 拒绝发布，`HIGH` 必须有显式 `ASK` Permission Rule；
4. Knowledge Revision 必须可由中立 Bridge 解析为 `READY`，Profile 和 Policy 引用必须固定到不可变版本；
5. Canonical Serializer 固定 `schemaVersion=1` 和 `runtimeProvider`，先序列化不含顶层 `contentHash` 的字段计算 SHA-256，再写入完整 JSON；
6. Revision、Snapshot、Publish Operation、成功 Validation Report 和 `agent.revision.published` Outbox 在同一本地事务写入；失败不留下半成品；
7. `(projectId, agentId, idempotencyKey)` 永久绑定 Draft Version；相同请求重放返回首次 Revision，不再次解析资产或写 Outbox；
8. 发布审计在事务提交后交给真实 Audit Port。Outbox 的 Diff Summary 只记录上一 Revision ID 和变化的顶层区段名，不记录 Prompt、文档、Tool 参数或 Secret 内容。

## Deployment 与 Rollback

Deployment 在 Environment 内按 Agent 保持稳定身份，保存 `desiredRevisionId`、`desiredStatus`、`TrafficPolicy` 和乐观锁版本。Create、Promote、Rollback、Enable、Disable 均验证 Organization → Project → Environment Owner 链、Revision 与 Agent 归属，并在同事务追加 `deployment_revision` 与 Outbox。

`FULL` 是 Phase 10 唯一可执行策略。`CANARY` 的 1–99 百分比模型和数据库约束已经定义，但应用服务主动拒绝执行，避免在 Runtime/Routing 尚未实现时制造虚假的分流能力。Rollback 只移动 `desiredRevisionId` 指针，不更新、复制或删除旧 Revision/Snapshot。

## Public 与 Internal Contract

Public Control API 实现 Agent、Draft、Validate、Publish、Revision List/Detail，以及 Environment Deployment 的 Create/Get/Promote/Rollback/Enable/Disable。`contracts/schemas/release-public/v1.json` 固定请求与响应结构，Public OpenAPI 只引用实际 Controller 路径。

Internal Control API 实现：

- `GET /internal/v1/agent-revisions/{revisionId}/snapshot`：校验 Service Identity 的 `agentark-control` Audience、Runtime Provider、支持的 Snapshot Schema Version 和 Capabilities；成功返回 Canonical JSON 与基于内容 Hash 的 `ETag`，`If-None-Match` 命中返回 `304`；
- `GET /internal/v1/deployments/{deploymentId}`：返回不含 Control Entity、Mapper 或 Draft 的语言中立 Deployment Descriptor。

Internal Contract 不使用共享静态 Token。当前 Security Starter 负责把受信 JWT 转换为 `AgentArkPrincipal`/`ServiceIdentity`；生产 Issuer、JWK 与 Audience 仍由部署环境显式配置，不提供默认 Secret。

## V5 与数据库不可变性

`V5__phase_10_revision_deployment.sql` 创建 `agent_draft`、`agent_draft_component`、`validation_report`、`agent_revision`、`agent_revision_snapshot`、`publish_operation`、`deployment`、`deployment_revision` 和 `control_outbox`，并注册 Agent/Deployment 五项权限。全部表和字段使用 MySQL 原生中文 `COMMENT`，可穷举字段同时具有完整合法值注释与 `CHECK` 约束。

`agent_revision` 与 `agent_revision_snapshot` 的 Update/Delete 均由数据库触发器拒绝。启用 MySQL Binary Log 时，基础设施必须设置 `log_bin_trust_function_creators=ON`；应用和 Flyway 账号不获得 `SUPER`。Compose 和 Testcontainers 使用相同参数。`agentark-control` 独立迁移制品不拥有 Phase 09 V4，因此它的升级测试是 V1/V2/V3/V5；Control Server 组合 `agentark-control + agentark-knowledge` 后运行完整 V1–V5。

## 测试与验收范围

分层测试覆盖 Canonical Snapshot Hash 与 Golden File、明文 Secret 负例、发布幂等重放与 Draft Version 冲突、Outbox Diff Summary、Internal Service Audience、Provider/Schema/Capability 兼容性、OpenAPI 路径和中文 YAML 注释，以及 MySQL V5 空库/升级/字段注释/跨 Schema 隔离和数据库级不可变触发器。

真实 MySQL E2E 进一步贯穿 IAM Project/Environment、Secret Metadata、版本化 Model/Profile/Policy、Agent Draft、两次发布、幂等重放、Canonical Snapshot、五条同事务 Outbox、Deployment Create/Promote/Rollback、Internal Snapshot/Deployment Contract 和跨租户拒绝。该测试暴露并修复了 `AgentPublisher`、`ReleaseApplicationService` 被声明为 `final` 导致 Spring 无法创建事务代理的问题，也修复了 API Key 测试使用分隔符切割合法 Base64 URL 前缀造成的随机失败。

最终 `clean verify` 汇总 36 个测试套件、165 项测试，失败 0、错误 0、跳过 0。知识门禁、两套上游固定 Worktree 校验、Core Compose 配置解析、AgentScope 生产源码边界与 `git diff HEAD --check` 均通过。秘密静态扫描已收紧为识别可疑明文字段声明或 JSON 属性；旧表达式会把合法 `SecretRef`、`SecretScope.valueOf` 和安全黑名单自身误报为泄露。

## 风险与后续边界

- Canary 只有中立模型，没有流量分配、粘性、指标或自动回滚；必须由后续 Runtime/Gateway 阶段实现并补 E2E 后才能启用。
- Internal Contract 当前是稳定手写 OpenAPI，没有生成 Java Client；Phase 12 可生成或手写实现，但必须通过同一 Contract Test，不能改为跨库查询。
- Outbox 已持久化但没有发布 Worker；投递、重试、Dead Letter 和监控由后续可靠性阶段实现。当前不得把 `PENDING` 事件描述为已经送达 Runtime。
- Snapshot v1 对 MCP TLS Secret 绑定主动拒绝并要求未来 Schema v2；不能以忽略 TLS 凭据的方式兼容。
- 数据库触发器依赖实例参数。共享或生产环境若未启用 `log_bin_trust_function_creators=ON`，V5 会明确失败；禁止临时授予 `SUPER` 绕过。
- 当前 Mockito 仍通过 Byte Buddy 动态挂载 Inline Mock Maker；JDK 21 只产生警告，但未来 JDK 默认禁止动态 Agent 后必须在统一 Build Phase 显式配置测试 Agent，不能在 Phase 10 临时新增依赖掩盖。

## 回滚

- 未发布代码、契约和文档按本阶段 Git Diff 精确反向修改，不覆盖 Phase 09 或用户其他未提交改动。
- V5 测试数据库随 Testcontainers 销毁；不得在共享数据库手工删除表或触发器。
- V5 一旦进入共享环境不得删除、重命名或改写，只能通过更高版本 Flyway Forward Fix。应用回滚前必须确认旧版本能够忽略 V5 新表、权限与 Outbox。
- Deployment 回滚使用业务 `rollback` 命令移动 Revision 指针；这不是 Flyway 回滚，也不会删除历史 Snapshot。

```bash
./mvnw -pl agentark-control,agentark-knowledge,agentark-services/agentark-control-server -am clean verify

rg -n "HarnessAgent|io\.agentscope" agentark-control/src/main/java && exit 1 || true
rg -ni '"(secret(Value|Plain(text)?|Credential)|plain(text)?Secret|credentialValue)"[[:space:]]*:|\b(String|VARCHAR|TEXT|JSON)[[:space:]]+[a-zA-Z0-9_]*(secret(Value|Plain(text)?|Credential)|plain(text)?Secret|credentialValue)\b' \
  contracts/schemas agentark-control/src/main && exit 1 || true

python3 tools/harness/knowledge_gate.py
git diff HEAD --check
```
