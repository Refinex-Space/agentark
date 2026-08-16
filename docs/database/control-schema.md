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
| 08 | Agent、资产稳定身份与不可变版本、Secret Metadata 与 Environment Binding | 不包含发布 Revision/Snapshot；只保存外部 Secret 定位信息，不保存值 |
| 09 | Draft、Validation、Revision、Snapshot、Publish Operation、Control Outbox | 发布事务必须原子提交 |
| 10 | Deployment 与 Deployment Revision | 固定目标 Revision，不写 Runtime Session |
| 14 | Knowledge Metadata、Revision、Ingestion Result | Scheduler 只提交命令结果，不写 Control 表 |
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
| `agent_draft` | id, agent_id, draft_version, metadata, version | 每 Agent 一个活动 Draft；乐观锁 |
| `agent_draft_component` | draft_id, component_type, component_id, component_version_id, alias, config | Draft 内 alias/type 唯一；Sub-Agent DAG 发布时校验 |
| `agent_revision` | id, agent_id, revision_number, status, snapshot_id, content_hash | `(agent_id, revision_number)`、`content_hash` 唯一；Published 后不可更新 |
| `agent_revision_snapshot` | id, agent_revision_id, schema_version, runtime_provider, canonical_json/object_ref, content_hash | revision 一对一；Hash 唯一；无 Secret 明文 |
| `validation_report` | id, draft_id, request_hash, status, findings_json/object_ref | request_hash 索引；不可变结果 |
| `publish_operation` | id, agent_id, idempotency_key, request_hash, status, revision_id, error_code | `(agent_id, idempotency_key)` 唯一 |
| `deployment` | id, environment_id, agent_id, desired_revision_id, desired_status, traffic_policy, version | `(environment_id, agent_id)` 唯一；乐观锁 |
| `deployment_revision` | id, deployment_id, sequence, revision_id, action, actor, occurred_at | `(deployment_id, sequence)` 唯一；只追加 |

发布事务必须同时提交 `agent_revision`、`agent_revision_snapshot` 和 `control_outbox`。Rollback 只更新 `deployment.desired_revision_id` 并追加历史。

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
| `knowledge_base` | id, organization_id, project_id, key, name, status, version | `(project_id, key)` 唯一 |
| `data_source` | id, knowledge_base_id, type, config_without_secret, secret_ref, status | Source key 在 KnowledgeBase 内唯一 |
| `document` | id, knowledge_base_id, source_key, status | `(knowledge_base_id, source_key)` 唯一 |
| `document_revision` | id, document_id, revision_number, source_object_ref, content_hash, status | `(document_id, revision_number)`、Hash 唯一 |
| `parser_profile` | id, project_id, key, version_number, immutable_config, content_hash | `(project_id, key, version_number)` 唯一 |
| `chunk_profile` | id, project_id, key, version_number, immutable_config, content_hash | 同上 |
| `embedding_profile` | id, project_id, key, version_number, immutable_config, secret_ref, content_hash | 同上 |
| `retrieval_profile` | id, project_id, key, version_number, immutable_config, content_hash | 同上 |
| `knowledge_revision` | id, knowledge_base_id, revision_number, profile refs, status, count, checksum, artifact refs | `(knowledge_base_id, revision_number)` 唯一；READY 后不可修改 |
| `knowledge_ingestion_result` | id, knowledge_revision_id, scheduler_job_id, attempt_id, idempotency_key, count, checksum, artifact_refs, status | `(knowledge_revision_id, attempt_id)`、idempotency 唯一 |

Scheduler 不写这些表。它调用幂等完成命令；Control 校验 Result 后转换 Revision 状态并写 Outbox。

## Governance

| 表族 | 关键表与约束 |
|---|---|
| Secret | Phase 08 已建立 `secret_metadata`、`secret_binding`；Phase 19 只补轮换和过期治理；始终只保存 Provider Path/Version/Scope，不保存值 |
| Quota | `quota_policy`, `quota_reservation`；Scope + metric + effective version 唯一，并发预留可回收 |
| Audit | `audit_event`；全局 event_id 唯一、按 organization/time 查询、只追加、Payload 可外置 |
| Usage/Cost | `usage_aggregate`, `price_table`, `price_table_version`；聚合维度和价格版本固定 |
| Evaluation | `evaluation_dataset`, `evaluation_dataset_version`, `evaluation_test_case`, `evaluator`, `evaluator_version`, `evaluation_run`, `evaluation_score`, `release_gate`；所有 Run 固定 Snapshot/Dataset/Evaluator Version |
| Reliability | `control_outbox`, `control_idempotency_record`；使用 MySQL 规范中的 Claim 和幂等约束 |
