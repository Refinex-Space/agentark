---
owner: refinex
updated: 2026-08-15
status: active
referenced_by: AGENTS.md#knowledge-map
---

# Control Schema 逻辑模型

Schema：`agentark_control`。唯一写入者是 Control Server。Knowledge 管理 API 也由 Control Server 装配；Scheduler 和 Runtime 只能通过 Internal Contract、Snapshot 或 Event 访问。

下表定义 Phase 07–10、19 的最低关系模型。Flyway 可以按 Phase 增量实现，但不得改变 Owner、稳定身份/不可变版本分离和关键约束。

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
| `api_key` | id, organization_id, project_id, prefix, digest, scopes, expires_at, revoked_at | `prefix`、`digest` 唯一；只保存摘要 |

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
| Secret | `secret_metadata`, `secret_binding`；只保存 Provider Path/Version/Scope，不保存值 |
| Quota | `quota_policy`, `quota_reservation`；Scope + metric + effective version 唯一，并发预留可回收 |
| Audit | `audit_event`；全局 event_id 唯一、按 organization/time 查询、只追加、Payload 可外置 |
| Usage/Cost | `usage_aggregate`, `price_table`, `price_table_version`；聚合维度和价格版本固定 |
| Evaluation | `evaluation_dataset`, `evaluation_dataset_version`, `evaluation_test_case`, `evaluator`, `evaluator_version`, `evaluation_run`, `evaluation_score`, `release_gate`；所有 Run 固定 Snapshot/Dataset/Evaluator Version |
| Reliability | `control_outbox`, `control_idempotency_record`；使用 MySQL 规范中的 Claim 和幂等约束 |

