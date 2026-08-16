---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: docs/README.md
---

# Knowledge/RAG 运维

## 适用范围

本文覆盖 Phase 14 的 Qdrant 1.18.3、固定 Knowledge Revision、摄取结果、派生对象和检索故障。Scheduler Job/Attempt、Dead Letter 和自动重试在 Phase 15 落地；在此之前只能通过测试或显式受控装配调用摄取 Worker。

## 本地检查

解析 RAG Profile：

```bash
docker compose -f deploy/compose/docker-compose.yml --profile rag config
```

启动并检查 Qdrant：

```bash
docker compose -f deploy/compose/docker-compose.yml --profile rag up -d qdrant
docker compose -f deploy/compose/docker-compose.yml --profile rag ps qdrant
curl --fail --silent http://127.0.0.1:6333/readyz
```

停止时不要附加 `-v`，否则会删除本地向量卷：

```bash
docker compose -f deploy/compose/docker-compose.yml --profile rag down
```

## Collection 与 Filter

Collection 名由部署配置固定，不能从组织、项目或客户端请求拼接。每次查询必须同时包含 `organization_id`、`project_id`、`knowledge_revision_id`，并从授权结果加入 `document_id` 白名单；缺少任一可信范围都不得访问 Qdrant。排障时即使知道 Collection 或 Point ID，也不能绕过 AgentArk Internal Port 直接向用户返回 Payload。

新 Revision 在同一 Collection 使用新的 `knowledge_revision_id`。Control 只有在 Worker 完成 Upsert 和精确 Count/Checksum Verify 后才把 Revision 标为 `READY`；Deployment/Snapshot 继续引用旧 Revision 直至明确发布新 Revision，因此重建不会污染正在运行的检索。

## 故障分类

| 现象 | 权威状态 | 处理 |
|---|---|---|
| Upsert/Verify 不可用 | MySQL Revision 保持 `INGESTING` 或由结果转换为 `FAILED` | 不手工标 READY；排查 Qdrant 后使用新 Attempt 重试 |
| Verify 数量或摘要不匹配 | Qdrant 派生数据不可信 | 删除该固定 Revision Scope 后重新摄取，禁止复用旧 Attempt |
| Retrieval Qdrant 不可用 | Snapshot、Revision 和原文仍在 MySQL/Object Store | 向调用链传播明确 Provider 失败；不得返回伪造空结果 |
| Retrieval 无命中 | Trace 的候选数和返回数为零 | 作为正常空结果处理，检查 ACL、阈值和固定 Revision，不降低租户 Filter |
| 单个 Revision 删除中断 | `DELETING` 是权威状态 | 重跑幂等清理：先向量 Scope，再登记 Artifact；完成后由 Control 转 `DELETED` |

## Snapshot 与备份

Qdrant Snapshot 是派生索引备份，不替代 Control MySQL 和 Object Storage 备份。一次可恢复备份至少同时记录：

1. Control MySQL 备份点和 Flyway 版本；
2. 原始 DocumentRevision Object 与 `knowledge_ingestion_result.artifact_refs_json` 指向的 Chunk Artifact；
3. Qdrant Collection Snapshot 名、Qdrant 小版本、节点和创建时间；
4. 每个 READY Revision 的期望 Count/Checksum。

单节点本地环境创建并列出 Collection Snapshot：

```bash
curl --fail --silent --request POST \
  http://127.0.0.1:6333/collections/agentark_knowledge/snapshots
curl --fail --silent \
  http://127.0.0.1:6333/collections/agentark_knowledge/snapshots
```

生产环境必须通过 Secret Provider 注入 API Key，禁止把 Header 值写入命令历史、文档或脚本。分布式 Qdrant 需要逐节点创建 Collection Snapshot；Collection Snapshot 不包含 Alias。官方恢复还要求源、目标共享同一小版本，并建议新 Collection 恢复使用 `priority=snapshot`。完整限制和 API 以 [Qdrant Snapshot 官方文档](https://qdrant.tech/documentation/operations/snapshots/) 为准。

恢复流程：

1. 停止新摄取和删除 Handler，记录待处理 Attempt；
2. 恢复 Control MySQL 与 Object Storage 到一致备份点；
3. 在隔离环境恢复 Qdrant Snapshot，不直接覆盖正在服务的 Collection；
4. 对全部 READY Revision 重新执行 Count/Checksum Verify，并抽查 ACL/Tenant Filter；
5. 用部署配置显式切换 Collection，观察 Retrieval Trace 和错误率；
6. 验证完成后再恢复摄取；失败时切回原 Collection，不修改 Snapshot/Revision 指针。

## 删除与重建

Qdrant 是可重建派生存储。删除操作必须从 Control 的 `DELETING` 状态和固定 Revision Scope 发起，不能按客户端 Collection/Filter 执行。顺序固定为：停止新检索引用、删除 Revision 向量、删除登记 Chunk/Manifest Artifact、验证派生数据为空、由 Control 标记 `DELETED`。原始 DocumentRevision 的保留或销毁遵循 Document 聚合和审计策略，不由向量清理器决定。

## 回滚

应用回滚先停摄取 Handler，再回退调用方到上一兼容版本。不要删除 V6 表或 Flyway History。Qdrant Adapter 变更可通过部署配置切回已验证 Collection；若新 Revision 已发布，只回滚 Deployment/Agent Revision 指针，不修改或覆盖旧 Knowledge Revision。
