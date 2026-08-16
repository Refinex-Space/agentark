---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: AGENTS.md#knowledge-map
---

# Control Schema 逻辑模型

Schema：`agentark_control`。唯一写入者是 Control Server。Knowledge 管理 API 也由 Control Server 装配；Scheduler 和 Runtime 只能通过 Internal Contract、Snapshot 或 Event 访问。

下表定义 Phase 07–10、19 的最低关系模型。Flyway 可以按 Phase 增量实现，但不得改变 Owner、稳定身份/不可变版本分离和关键约束。

## Flyway 归属

| Phase | 迁移范围 | 边界 |
|---|---|---|
| 06 | `V1__phase_06_schema_baseline.sql` | 只建立 Control 独立 Migration History 起点，不创建业务表 |
| 07 | `V2__phase_07_iam_tenancy.sql`：Organization、Project、Environment、Identity、Membership、Role、Permission、Binding、API Key | IAM 与租户授权事实；十二张业务表 |
| 08 | `V3__phase_08_asset_catalog.sql`：Agent、资产稳定身份与不可变版本、Secret Metadata 与 Environment Binding | 不包含发布 Revision/Snapshot；只保存外部 Secret 定位信息，不保存值 |
| 09 | `V4__phase_09_knowledge_metadata.sql`：Knowledge Metadata、Document Revision、Knowledge Revision、Profile 与 Ingestion Request | 只描述摄取工作，不执行 Embedding 或向量写入；V4 由 `agentark-knowledge` 所有 |
| 10 | `V5__phase_10_revision_deployment.sql`：Agent Draft、Validation、Revision、Snapshot、Publish Operation、Control Outbox、Deployment 与 Deployment Revision | 发布事务原子提交，Deployment 固定目标 Revision；独立 `agentark-control` 迁移测试按 V1/V2/V3/V5 运行，Control Server 组合迁移按 V1–V5 运行 |
| 14 | `V6__phase_14_knowledge_ingestion_result.sql`：Knowledge Ingestion Result，并扩展 Control Outbox 的 Knowledge Revision 聚合类型 | Scheduler 只经幂等 Internal Command 提交结果，不写 Control 表；Parser/Embedding/Vector Adapter 不拥有表 |
| 19 | Secret 轮换治理、Quota、Audit、Usage/Cost、Evaluation、可靠性补齐 | 延续 Phase 08 Secret Owner，不保存 Secret 明文 |

任何表提前、延后或转移 Owner 都必须先修改本模型；影响平台边界或发布一致性时同步提交 ADR。

## IAM

| 表 | 关键内容 | 关键约束与索引 |
|---|---|---|
| `organization` | id, slug, name, status, version | `slug` 全局唯一；状态查询 |
| `project` | id, organization_id, slug, name, status, version | `(organization_id, slug)` 唯一 |
| `environment` | id, organization_id, project_id, key, name, status, version | `(project_id, key)` 唯一 |
| `user_identity` | id, issuer, subject, display metadata, status | `(issuer, subject)` 唯一；不保存外部 Token |
| `service_account` | id, organization_id, project_id, name, status | `(project_id, name)` 唯一 |
| `membership` | id, organization_id, project_id, principal_type/id, status | `(project_id, principal_type, principal_id)` 唯一 |
| `role` | id, organization_id, project_id nullable, key, name, built_in, version | Scope 内 `key` 唯一 |
| `permission` | id, key, description, risk_level | `key` 全局唯一，Registry 管理 |
| `role_permission` | role_id, permission_id | 组合主键 |
| `role_binding` | id, organization_id, project_id, role_id, principal, scope_type/id | Scope + principal + role 唯一 |
| `api_key` | id, organization_id, project_id, service_account_id, name, prefix, digest, expires_at, revoked_at, version | `prefix`、`digest` 唯一；复合外键固定项目服务账号；只保存 SHA-256 摘要 |
| `api_key_scope` | api_key_id, permission_id, created_at | 组合主键；权限外键拒绝未注册 Scope；不使用 JSON 权限数组 |

`user_identity` 是跨租户的外部 Issuer/Subject 身份映射，不直接拥有租户资源；`membership` 和 `role_binding` 才把该身份纳入 Organization/Project Scope。Organization 是租户根，Project 以下的资源、成员、角色绑定、服务账号和 API Key 都显式保存完整 Owner 链。客户端 Tenant Header 不属于授权事实。

## Agent、发布与部署

| 表 | 关键内容 | 关键约束与索引 |
|---|---|---|
| `agent` | id, organization_id, project_id, key, name, status, version | `(project_id, key)` 唯一 |
| `agent_draft` | agent_id, owner chain, spec_json, version, audit | `agent_id` 一对一主键；Draft 可编辑且使用乐观锁；Runtime 禁止读取 |
| `agent_draft_component` | agent_id, owner chain, component_type/order, owner_id, version_id, binding_json | `(agent_id, component_type, component_order)` 主键；是 `spec_json` 的可查询投影，Draft 更新时同事务重建 |
| `validation_report` | id, owner chain, agent_id, draft_version, status, findings_json, audit | `VALID/INVALID`；只保存路径、稳定代码、严重程度和说明，不保存资产正文 |
| `agent_revision` | id, owner chain, agent_id, snapshot_id, revision_number, schema_version, runtime_provider, content_hash, required_capabilities_json, status, audit | `(agent_id, revision_number)` 与 `(agent_id, content_hash)` 唯一；`PUBLISHED` 后由数据库触发器拒绝更新和删除 |
| `agent_revision_snapshot` | id, owner chain, revision_id, schema_version, runtime_provider, content_hash, snapshot_json, audit | Revision 一对一，Hash 全局唯一；Canonical Snapshot 只含 `SecretRef`；触发器拒绝更新和删除 |
| `publish_operation` | id, owner chain, agent_id, idempotency_key, draft_version, status, revision_id, audit | `(project_id, agent_id, idempotency_key)` 唯一；同一键绑定一个 Draft 版本和成功 Revision |
| `deployment` | id, owner chain, environment_id, agent_id, desired_revision_id, desired_status, traffic_policy_type, canary_percent, version, audit | `(environment_id, agent_id)` 唯一；乐观锁；Phase 10 只执行 `FULL`，`CANARY` 只保留合法模型 |
| `deployment_revision` | id, owner chain, deployment_id, action, from_revision_id, to_revision_id, audit | `CREATE/PROMOTE/ROLLBACK/ENABLE/DISABLE` 只追加历史 |
| `control_outbox` | id, aggregate_type/id, event_type, payload_json, status, attempts, available_at, published_at | `(status, available_at, id)` Claim 索引；发布与 Deployment 变更均和所属聚合同一事务写入 |

发布事务必须同时提交 `agent_revision`、`agent_revision_snapshot`、`publish_operation`、成功 `validation_report` 和 `control_outbox`。Outbox 的发布差异摘要只包含上一 Revision ID 和发生变化的顶层区段名，不包含 Prompt、文档、Tool 参数或 Secret 内容。Rollback 只更新 `deployment.desired_revision_id` 并追加 `deployment_revision` 与 Outbox，不复制或修改 Snapshot。

V5 使用四个 `BEFORE UPDATE/DELETE` 触发器保护 Published Revision 与 Snapshot。MySQL 运行账户不授予 `SUPER`；目标实例必须由基础设施显式启用 `log_bin_trust_function_creators=ON`，否则 Flyway 会因二进制日志触发器创建限制失败。该变量只放宽触发器创建前提，不改变应用账号的 Schema Owner 和最小权限边界。

## 版本化资产

稳定身份表使用 `(project_id, key)` 唯一；版本表使用 `(owner_id, version_number)` 和 `content_hash` 唯一，发布/被引用版本不可修改。

| 稳定身份 | 不可变版本 | 版本关键内容 |
|---|---|---|
| `prompt` | `prompt_version` | template, variable_schema, content_hash, status |
| `model_provider` | `model_profile` | provider/model, capabilities, parameters, secret_ref |
| `mcp_server` | `mcp_server_version` | transport, endpoint, TLS/Auth SecretRef, content_hash |
| `skill` | `skill_version` | object_ref, hash, source, license, signature metadata |
| `memory_profile` | `memory_profile_version` | memory policy and limits |
| `workspace_profile` | `workspace_profile_version` | filesystem/isolation policy |
| `sandbox_profile` | `sandbox_profile_version` | runtime, resource, network and image policy |
| `permission_policy` | `permission_policy_version` | hierarchical rules, approval/timeout policy |

`mcp_tool_descriptor` 从 `mcp_server_version` 派生，保存 tool name、argument schema、read/write、risk、idempotency 和 permission metadata；`(mcp_server_version_id, tool_name)` 唯一。

## Secret Metadata 与 Environment Binding

| 表 | 关键内容 | 关键约束与索引 |
|---|---|---|
| `secret_metadata` | id, organization_id, project_id, key, provider, external_path, external_version, scope, status, version | `(project_id, key)` 唯一；只保存外部 Provider 定位和版本，不保存 Secret 值 |
| `secret_binding` | id, organization_id, project_id, environment_id, secret_metadata_id, binding_key, status, version | `(environment_id, binding_key)` 唯一；复合外键保证 Environment 与 Secret 属于同一 Project |

Phase 08 建立 Secret 元数据、Environment Binding、Resolver Port 和开发 Local Provider；Phase 19 在同一 Owner 下追加轮换、过期和治理流程，不另建第二套 Secret 模型。Model/MCP 等资产版本只保存 `secret://<scope>/<name>` 形式的 `SecretRef`，Resolver 解析出的字符数组不得进入数据库、API、日志、审计或事件。

## Knowledge Metadata

| 表 | 关键内容 | 关键约束与索引 |
|---|---|---|
| `knowledge_base` | id, organization_id, project_id, knowledge_key, name, description, status, version, audit | `(project_id, knowledge_key)` 唯一；`ACTIVE/ARCHIVED` |
| `data_source` | id, owner chain, knowledge_base_id, source_type, name, descriptor_json, audit | `(project_id, id)` Owner 复合约束；`UPLOAD/URI/CONNECTOR`；descriptor 不含凭据 |
| `document` | id, owner chain, knowledge_base_id, data_source_id, title, metadata_json, status, version, audit | 复合外键保证 Base 与 Source 同 Project；`ACTIVE/DELETING/DELETED` |
| `document_acl` | document_id, owner chain, subject_type/id, access_level, audit | `(document_id, subject_type, subject_id)` 主键；主体为 `PROJECT/USER/SERVICE_ACCOUNT/ROLE`，级别为 `READ/WRITE/MANAGE` |
| `document_revision` | id, owner chain, knowledge_base_id, document_id, revision_number, original_file_name, object_uri, content_hash/size/type, audit | `(document_id, revision_number)` 唯一；原文件 Hash 可追踪；Object URI 不含授权参数 |
| `parser_profile` | id, owner chain, profile_key, version_number, config_json, content_hash, status, audit | `(project_id, profile_key, version_number)` 与 `(project_id, content_hash)` 唯一；只追加 |
| `chunk_profile` | 同 Parser Profile | 同上；切分策略只保存中立配置 |
| `embedding_profile` | 同 Parser Profile，另含可空 credential_secret_ref | 只允许 SecretRef，不保存凭据值；只追加 |
| `retrieval_profile` | 同 Parser Profile | 同上；不保存向量库 Collection 名作为授权事实 |
| `knowledge_revision` | id, owner chain, knowledge_base_id, revision_number, 四类 Profile 引用, content_hash, status, failure_code, version, audit | `(knowledge_base_id, revision_number)` 和 `(project_id, content_hash)` 唯一；内容绑定不可更新，只有状态字段通过乐观锁转换 |
| `knowledge_revision_document` | knowledge_revision_id, owner chain, document_revision_id, ordinal_value, audit | `(knowledge_revision_id, document_revision_id)` 主键且 ordinal 唯一；固定文档修订集合与顺序 |
| `knowledge_ingestion_request` | id, owner chain, knowledge_revision_id, idempotency_key, status, requested_at/by | `(project_id, idempotency_key)` 唯一；Phase 09 只保存 `DESCRIBED/CANCELLED`，不代表 Scheduler Job 已创建 |
| `knowledge_ingestion_result` | id, request_id, owner chain, knowledge_revision_id, scheduler_job_id, attempt_id, idempotency_key, document_count, chunk_count, checksum, artifact_refs_json, status, failure_code, completed_at, audit | Phase 14 V6 实现；`(knowledge_revision_id, attempt_id)` 与 `(project_id, idempotency_key)` 唯一；成功必须有正计数和制品，失败必须有稳定代码 |

V4 只创建前十二张 Phase 09 表及 `knowledge:read/manage/ingest` 权限，不创建 `knowledge_ingestion_result`。四类 Profile 与 Document Revision 均不可变；变更解析、切分、Embedding 或检索配置必须新建 Profile 和 Knowledge Revision。只有 `READY` Revision 可被 Agent Revision Resolver 引用。

Scheduler 不写这些表。Phase 14 由 Scheduler 调用幂等完成命令；Control 在同一本地事务内插入不可变 Result、转换 Revision 状态并写 `knowledge_revision` 类型 Outbox。相同幂等键只能重放完全相同结果；新失败重试必须使用新的 Attempt。Qdrant Collection、Embedding Provider 资源名和派生索引都不是租户授权依据，任何 Adapter 请求必须同时携带可信 Organization、Project、KnowledgeRevision 和已授权 Document 集合。

## Governance

| 表族 | 关键表与约束 |
|---|---|
| Secret | Phase 08 已建立 `secret_metadata`、`secret_binding`；Phase 19 只补轮换和过期治理；始终只保存 Provider Path/Version/Scope，不保存值 |
| Quota | `quota_policy`, `quota_reservation`；Scope + metric + effective version 唯一，并发预留可回收 |
| Audit | `audit_event`；全局 event_id 唯一、按 organization/time 查询、只追加、Payload 可外置 |
| Usage/Cost | `usage_aggregate`, `price_table`, `price_table_version`；聚合维度和价格版本固定 |
| Evaluation | `evaluation_dataset`, `evaluation_dataset_version`, `evaluation_test_case`, `evaluator`, `evaluator_version`, `evaluation_run`, `evaluation_score`, `release_gate`；所有 Run 固定 Snapshot/Dataset/Evaluator Version |
| Reliability | `control_outbox`, `control_idempotency_record`；使用 MySQL 规范中的 Claim 和幂等约束 |
