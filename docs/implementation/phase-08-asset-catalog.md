---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: docs/README.md
---

# Phase 08 AI 资产目录执行报告

## 结论

Phase 08 在 Control Plane 建立了项目级 AI 资产目录：Agent 稳定身份、Prompt、Model Provider/Profile、MCP Server/Version/Tool Descriptor、Skill/Artifact、Memory/Workspace/Sandbox Profile、Permission Policy，以及 Secret Metadata 和 Environment Binding。行为资产采用“稳定身份 + 只追加版本”，Public API 不暴露 AgentScope 或厂商 SDK 类型，也不执行 MCP、Skill、模型或 Agent。

## 上游取用

固定 AgentScope `0c61e7494197ded54eefdeaf9bdeb51807beb752` 中定位了 Model Provider、MCP Transport、Skill Repository、Memory、Workspace、Sandbox、Permission 和 Secret/Environment 语义：

- `DEPENDENCY/REFERENCE`：AgentScope Core/Harness/Extensions 的运行能力留给 Provider Adapter，Control 只保存平台中立描述；
- `ADAPT`：Aistio Managed Agent、Model Config、MCP Server、Vault 和环境绑定语义重新建模为 AgentArk Owner、Version、`SecretRef` 与 Public Contract；
- `REFERENCE`：AgentScope Frontend 字段只用于检查功能覆盖；
- `REJECT`：明文 Vault 请求、厂商 SDK DTO、在 Control 执行 Skill/MCP，以及通过 Endpoint 健康状态修改版本内容。

## 领域与版本约束

九类稳定身份由 `CatalogAssetKind` 固定映射到受信表和强类型 UUIDv7，调用方不能提供动态表名。Agent 仅建立稳定身份，Draft、Revision 和 Snapshot 仍归 Phase 10。其余八类行为资产都有 Owner 内单调版本号、规范 JSON、SHA-256 `contentHash` 和 `DRAFT/PUBLISHED/ARCHIVED` 创建语义。

版本 Repository 只提供追加与读取，不提供更新和物理删除；稳定身份使用乐观锁归档，历史版本继续可读。Prompt 版本保存模板、变量 JSON Schema、用途和 Diff；Model Profile 保存 Tool/Vision/Structured Output/Streaming 能力与参数约束；Profile/Policy 保存语言中立策略，不进入 Runtime 执行。

## MCP、Skill 与 Secret 安全边界

MCP Remote Endpoint 只接受无 UserInfo、Query、Fragment 的 HTTPS URI，并拒绝 localhost、明确私网 IPv4 和云元数据地址；版本必须声明拒绝私网、拒绝云元数据、DNS 解析固定三项信息模型。Tool Descriptor 固定参数 Schema、READ/WRITE/READ_WRITE、LOW/MEDIUM/HIGH/CRITICAL、幂等语义、Allowlist/权限/审批元数据。健康状态与版本内容分离，本阶段不发起连接。

Skill Artifact 先经受控 Multipart 接口写入 `ObjectStore`，返回不含授权参数的 `ObjectRef`；提交 SkillVersion 时再次 `head` 复核 URI、SHA-256、大小和媒体类型，并保存来源 URI、许可证、签名元数据和兼容要求。本阶段不解包、不安装、不执行 Skill。上传成功而数据库提交失败时可能形成未引用对象，后续对象生命周期任务必须按引用扫描清理，不能在请求失败路径盲删并发引用对象。

Secret 数据库只保存 Provider、External Path/Version、Scope 和状态。项目引用固定为 `secret://project/{projectId}/{key}`，环境引用固定为 `secret://environment/{environmentId}/{bindingKey}`；创建资产版本时逐个验证引用属于同一项目且处于启用状态。`SecretResolver` 返回可关闭并清零的 `char[]`；只有 `local` Profile 且显式开启时装配文件 Provider，生产云 Provider 只有 SPI，不提供伪实现。

## 数据库、授权与 API

`V3__phase_08_asset_catalog.sql` 新增二十张 Control 业务表，所有表和字段都有 MySQL 原生中文 `COMMENT`，可穷举字段注释与 `CHECK` 完整一致。V3 同时注册 `catalog:read`、`catalog:manage`、`secret:read`、`secret:manage`，内置角色按最小权限获得资产或 Secret 能力。

Catalog 与 Secret Controller 复用 Phase 07 的 Project Owner、Principal → Tenant Context 和应用授权，不读取 IAM Mapper，不信任 Tenant Header。列表统一使用最大一百条的 Cursor Pagination；错误分别使用稳定 `ARK-CATALOG-*`、`ARK-SECRET-*` Problem Detail。创建、归档、版本追加、Metadata 和 Binding 都通过真实审计 Port 在事务提交后输出非敏感事实。

`contracts/openapi/public-control-v1.yaml` 只增加实现中存在的七组路径，`contracts/schemas/catalog-public/v1.json` 定义资产、版本、ObjectRef、Secret Metadata/Binding 和游标页。版本响应把规范 JSON 还原为对象，不执行 JSON 双重编码。

## 验收结果与边界

2026-08-16 实际执行 `./mvnw -pl agentark-control,agentark-services/agentark-control-server -am clean verify`，十三个 Reactor 模块全部 `SUCCESS`，共执行 126 个单元、契约、架构、迁移、租户安全和 Server 上下文测试，失败、错误和跳过均为零。MySQL 8.4 Testcontainers 实际验证空库 V1→V3、V2→V3、二十张 V3 表的表/字段中文注释和约束；资产集成测试覆盖 Prompt 两版本 Diff、Model `SecretRef`、MCP Tool Descriptor、Skill Object Store 上传/提交与双向跨租户 HTTP 拒绝。

`ContractDocumentLintTest` 与 `ContractSchemaTest` 共十项契约测试通过；中文 Javadoc 门禁、四十种强类型 ID 参数化测试、非法 UTF-8 Secret 拒绝、MCP SSRF 与明文凭据负例均通过。`knowledge_gate.py` 检查三十六份活跃文档通过；`verify_upstreams.py --require-worktrees` 重新确认两套固定 Commit；`io.agentscope` 和厂商 SDK 边界扫描无命中；`git diff HEAD --check` 通过。

生产 OIDC、真实云 Secret Provider、S3-compatible Adapter、DNS 解析与重绑定在线防护、MCP 健康探测、Skill 签名验证和对象垃圾回收不属于本阶段在线验收。它们分别留给部署 Adapter、Runtime/安全加固和治理阶段；当前实现不声称已提供这些能力。

## 回滚

- 代码、契约和文档未发布时，按本阶段 Git Diff 精确反向修改，不覆盖 Phase 07 或用户其他未提交改动。
- V3 测试库可随 Testcontainers 销毁；不要对共享数据库手工删表。
- V3 一旦进入共享环境不得删除或改写；应用回滚前必须确认旧版本不会因未知 V3 权限和表而失败，数据库修正只能新增 Flyway Forward Fix。
- 已上传但未提交的本地 Artifact 可在确认无任何引用后清理；不得按目录批量删除。

## 后续边界

Phase 10 必须引用 Phase 08 的稳定资产版本和 Phase 09 的 READY KnowledgeRevision 构建 Draft、Validation、Revision 和 Snapshot，不能覆盖旧版本或把 Secret 值编译进 Snapshot。Phase 12 Provider Adapter 才把平台中立描述转换为 AgentScope 类型；Phase 19 延续同一 Secret Owner 补轮换、过期和治理，不另建第二套 Secret 模型。
