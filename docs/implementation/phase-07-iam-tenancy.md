---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: docs/README.md
---

# Phase 07 IAM 与多租户执行报告

## 结论

Phase 07 在 Control Plane 建立了第一组业务能力：外部 OIDC 身份映射、Organization/Project/Environment 租户树、Membership、Permission Registry、内置与自定义 Role、Scope-aware Role Binding、项目 Service Account，以及只保存 SHA-256 摘要的 API Key。Public API 只调用应用服务，持久化只访问 `agentark_control`，客户端 Tenant Header 不参与授权判定。

## 上游取用结论

固定 AgentScope Worktree 中的 Aistio 使用本地用户名密码、七天 HS256 Token、CSV 角色和可见默认开发用户；Java Service 使用共享 Internal Token 与 Acting User Header，Gateway 负责清理外部身份 Header。这些只作为行为证据：

- `ADAPT`：OIDC/JWK、Audience 校验、边缘 Header 清洗语义和 API Key 扩展点；
- `REFERENCE`：Aistio 管理 API、Go/JPA 资源模型和前端权限体验；
- `REJECT`：生产本地密码库、默认用户、HS256 共享密钥、共享 Internal Token、客户端 Header 选择租户和明文 Seed 凭据。

AgentArk 的认证主体固定包含 Issuer/Subject；外部身份首次出现时在独立写事务中幂等建立 `user_identity`，授权只读取数据库角色事实。`user_identity` 本身不拥有租户资源，Membership 与 Role Binding 才建立租户 Scope。

## 领域与授权模型

资源层级为 Organization → Project → Environment。Project 以下的成员、角色绑定、服务账号和 API Key 均携带 Organization/Project Owner 链；复合外键验证链路，不能仅凭裸 UUID 命中资源。

Permission Registry 是可授予权限的唯一入口，V2 Flyway 固定写入十四个权限。每个新组织创建 `organization-owner`，每个新项目创建 `project-admin`、`project-developer` 和 `project-viewer`。自定义 Role 只能引用 Registry 权限；Role Binding 同时固定 Principal、Scope Type 和 Scope ID。Controller 使用 Method Security 作为入口门禁，应用服务逐个执行资源权限检查，Mapper 再以组织和项目条件限制 SQL。

短 TTL 授权缓存默认十五秒。Membership、Role Binding 和 API Key 状态变化发布事务事件，提交成功后按 Organization/Project Scope 主动失效；事件不在回滚事务中提前驱逐缓存。

## API Key 安全边界

API Key 采用 `ark_<12 字符前缀>_<43 字符秘密>` 格式。创建时只返回一次明文；数据库保存公开前缀、32 字节 SHA-256 摘要、项目服务账号、规范化 Scope、到期时间和吊销状态。认证使用常量时间摘要比较，并在完成后清零调用方 `char[]`。

请求 Scope 必须同时满足 Permission Registry 和目标 Service Account 的有效权限，因此 API Key 只能收窄权限。列表契约使用专用 `ApiKeyView`，不暴露摘要或明文。日志审计只记录动作、主体、资源、Owner、结果和时间，不采集凭据。

## 数据库与契约

`V2__phase_07_iam_tenancy.sql` 在 `agentark_control` 创建十二张业务表：

```text
organization       project             environment
user_identity      service_account     membership
permission         role                role_permission
role_binding       api_key             api_key_scope
```

所有 UUID 使用 `BINARY(16)`，时间使用 UTC `TIMESTAMP(6)`，状态和版本带 Check Constraint。十二张表及其全部字段都使用 MySQL 原生中文 `COMMENT`，状态、主体类型、作用域、风险等级和布尔值注释列出全部合法值；迁移测试从 `information_schema` 验证这些注释已实际落库。API Key Scope 使用关联表而不是 JSON；User/Service Account 多态关系由应用层存在性检查和完整 Scope SQL 共同保护。生产迁移一旦发布不得改写，后续修正只能新增 Flyway Forward Fix。

`contracts/openapi/public-control-v1.yaml` 只声明已经实现的十组 IAM 路径；`contracts/schemas/iam-public/v1.json` 统一定义 Public DTO。契约测试校验实际 Path 集合、JSON Schema 标识、API Key 安全视图以及 YAML 中文相邻注释。

## Dev Bootstrap

开发引导默认关闭，并由独立配置同时要求 `local` Profile 与 `agentark.control.iam.dev-bootstrap.enabled=true`。它只幂等创建指定 Organization、Project 和 Environment，不生成用户密码、共享 Token 或 API Key。生产 Profile 即使误设开关也不会装配引导执行器。

## 验证证据

2026-08-16 完成以下收官验证：

```bash
./mvnw -pl agentark-control,agentark-services/agentark-control-server -am clean verify
./mvnw -pl agentark-control,agentark-services/agentark-control-server -am dependency:tree
python3 tools/harness/knowledge_gate.py
python3 tools/harness/verify_upstreams.py --require-worktrees
rg -n "password *=|admin/admin|BUILDER_JWT_SECRET|BUILDER_INTERNAL_TOKEN" \
  agentark-control agentark-services
git diff HEAD --check
```

Maven Reactor 的十三个模块全部成功。关键测试包括 Kernel 54 项、Control 单元测试 2 项、Control MySQL/IAM 集成测试 7 项和 Control Server 测试 1 项；MySQL 8.4 Testcontainers 覆盖空库 V2、V1 → V2、重复迁移、表与字段中文注释元数据、约束、跨租户列表与直接对象访问、Tenant Header 绕过、稳定未认证/无权错误、授权缓存失效、API Key 摘要持久化、直接认证、HTTP 认证、吊销和到期行为。

依赖树未发现 `hibernate-core`、`spring-data-jpa` 或 `jakarta.persistence-api`。知识门禁检查了 35 份活动文档并通过；两个只读上游 Worktree 仍分别固定在 AgentScope `0c61e7494197ded54eefdeaf9bdeb51807beb752` 与 DeepSeek Harness `47f943859bef60e4160492346772ded9b24f765a`，状态无新增修改。Secret 模式、Control 跨 Schema 引用与 Git 空白错误检查均无命中。

测试过程仍会输出 Mockito 动态 Agent 和 JSON Schema `unknown keyword` 提示；它们不影响测试结果，也不是 Phase 07 引入的运行时失败。生产 OIDC/JWK、真实 Gateway Header 清洗和多副本缓存广播未在本阶段进行在线验收，分别留在部署环境与后续所属阶段验证。

## 回滚

- 代码和文档尚未发布时，按本阶段 Git Diff 精确反向修改，不覆盖其他未提交改动。
- 本地未共享的 V2 测试库可随 Testcontainers 销毁；不要对共享数据库执行手工删表。
- V2 一旦进入共享环境不得删除或改写；逻辑回滚应先停止新 IAM API，再通过新增 Flyway 做 Forward Fix。
- API Key 明文不可恢复；若创建响应丢失或回滚应用版本，应吊销旧 Key 后重新创建。

## 后续边界

Phase 08 的 Agent、Prompt、Model、MCP、Skill 等资产必须复用本阶段的 Project Owner 和应用授权服务，不得自行信任 Tenant Header、直接查询 IAM Mapper 或创建第二套角色模型。Gateway 身份 Header 清洗与服务间身份传递仍归 Phase 16；Phase 19 再将结构化审计 Port 落到不可变治理存储。
