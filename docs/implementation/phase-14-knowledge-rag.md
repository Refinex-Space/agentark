---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: docs/README.md
---

# Phase 14：Knowledge Ingestion、Qdrant 与 RAG Retrieval

## 结论

Phase 14 在 Phase 09 的中立 Knowledge 领域之上完成了可复用的异步摄取管线、Qdrant 1.18.3 Adapter、固定 Revision 检索、Citation/Trace 契约和 AgentScope Harness Tool 防腐层。Control 仍是 Knowledge Metadata 与状态事实唯一写入者；Worker 只经受保护的 Internal API 读取固定计划并幂等提交结果，不能使用 Control DataSource。

本阶段没有把摄取管线装进 Scheduler 的持久 Job/Attempt 循环。该装配由 Phase 15 负责，届时 Scheduler 只能向 `KnowledgeIngestionWorker` 提交固定 Attempt 命令，不能获得 Control Schema 权限。生产恶意文件扫描器、Embedding Provider、Object Store 和 Qdrant 服务凭据也只保留真实 Port/Provider 注入边界，没有伪造云实现或默认凭据。

## 上游证据与取用决策

审计基于 `.agentark/upstreams/agentscope-java-2.0.2` 固定 Worktree，不读取移动分支。

| 上游源码 | 观察到的行为 | AgentArk 决策 |
|---|---|---|
| `agentscope-core/.../rag/Knowledge.java` | AgentScope 2.0.2 中旧 Knowledge 抽象已标记过时 | `REFERENCE`；不把旧类型作为 AgentArk 领域或 API |
| `agentscope-extensions-rag-simple/.../SimpleKnowledge.java` | 在单对象内组合 Reader、Chunk、Embedding、Store | `REFERENCE/REJECT`；保留行为分解，拒绝让 Provider 反向拥有状态机和租户授权 |
| `.../store/VDBStoreBase.java`、`QdrantStore.java` | 提供向量写入、检索和 Qdrant 客户端实现 | `REFERENCE`；AgentArk 使用自己的中立 Port 和服务端强制 Filter，不复制实现 |
| `.../QdrantStoreTest.java`、`VDBStoreBaseTest.java` | 覆盖 Store 基础写入和查询行为 | `ADAPT`；增加组织、项目、Revision、文档 ACL、摘要校验、删除和重启初始化 E2E |
| `.../reader/*Reader.java`、`TextChunker.java` 及测试 | 解析与切分由具体 Reader 实现决定 | `REFERENCE`；Profile 固定策略，Parser 进入独立受限进程，原文不进入 HTTP 同步流程 |
| `.../KnowledgeRetrievalToolsTest.java`、`GenericRAGHookTest.java` | 检索可作为 Tool/Hook 接入 Agent | `ADAPT`；注册只读 `knowledge_retrieve` Tool，固定租户、Revision 和 ACL 请求模板 |

没有从 AgentScope 复制任何 Reader、Qdrant Store、Embedding 或 Knowledge 实现源码。`agentark-knowledge` 只新增 `agentscope-core` 依赖以使用正式 `Toolkit`/`@Tool` 扩展点；AgentScope Import 只允许出现在 `adapter.out.vector.agentscope`。

## 摄取闭环

1. Control 先由 Public API 上传不可变原文、注册 Data Source、创建 Profile/Knowledge Revision，并记录 `knowledge_ingestion_request`。
2. Scheduler Attempt 通过 `GET /internal/v1/knowledge/ingestions/{requestId}/plan` 读取固定 Revision、DocumentRevision 和四类 Profile。响应只含字符串化 UUID、`ObjectRef`、`Checksum` 和可选 `SecretRef`，不含凭据值。
3. `KnowledgeIngestionWorker` 在专用 Executor 中执行媒体类型、大小、魔数、恶意文件 Port、ZIP Entry/路径/解压大小/压缩率检查；HTTP 请求线程不解析或生成 Embedding。
4. Parser 在独立 JVM 进程中运行，使用受限 Heap、空环境、标准输入输出和超时；生产仍应在容器或平台 Sandbox 叠加 OS 级网络、文件和系统调用限制。
5. Profile 固定的 Chunk Strategy 生成稳定 Chunk Key 和 `UNTRUSTED_EXTERNAL` 元数据，NDJSON Chunk Artifact 写入 Object Store。
6. Embedding 按固定批次执行有界重试；Worker 对 Chunk 身份、文本摘要和向量位模式计算清单 SHA-256。
7. Qdrant Adapter Upsert 后执行固定 Revision 的精确 Count/Checksum Verify。失败产生稳定 `VECTOR_UPSERT_FAILED`、`VECTOR_VERIFY_FAILED` 或 `VECTOR_VERIFY_MISMATCH` 结果，不伪造 READY。
8. Worker 通过 `POST /internal/v1/knowledge/ingestions/{requestId}:complete` 幂等提交 `attempt/count/checksum/artifactRefs`。Control 在本地事务内写 `knowledge_ingestion_result`，执行 `INGESTING → VERIFYING → READY` 或 `INGESTING → FAILED`，并写 `control_outbox`。
9. 失败重试必须创建新的 `attemptId`；同一项目幂等键绑定不同结果返回冲突。

`KnowledgeDerivedDataCleaner` 只接受可信 `VectorScope` 和已登记 Artifact 列表，先按组织、项目、Revision 删除向量，再删除派生对象。原始 DocumentRevision Object 不在该清理器的删除范围内，避免误删审计事实。

## Qdrant 边界

- 本地 RAG Profile 固定 `qdrant/qdrant:v1.18.3`，Core Profile 默认不启动 Qdrant。
- Collection 是部署配置，不是租户边界。单个受控 Collection 通过 Payload 隔离多个 Revision。
- 每个 Point 强制携带 `organization_id`、`project_id`、`knowledge_revision_id`、`document_id`、`document_revision_id`、`chunk_key`、`source_trust` 和 `revision_checksum`。
- Adapter 为组织、项目、Revision、文档字段创建 Keyword Payload Index；Filter 完全由 Adapter 从强类型 Scope 和服务端已授权文档白名单构造，客户端不能提供原始 Filter。
- Point ID 从租户、Revision、文档修订和 Chunk Key 确定性生成；新 Revision 使用不同 Scope，不覆盖旧 Revision。
- REST 失败异常不包含 Provider 响应正文或 API Key。API Key 只由 Provider 按需返回字符数组，写入请求头后清零，不进入配置对象、日志或缓存。

Qdrant Payload/Filter、Upsert 和 Query 行为以官方文档为参考：[Payload](https://qdrant.tech/documentation/concepts/payload/)、[Filtering](https://qdrant.tech/documentation/concepts/filtering/)、[Upsert](https://api.qdrant.tech/api-reference/points/upsert-points)、[Query](https://api.qdrant.tech/api-reference/search/query-points/)。备份与恢复步骤见 [Knowledge/RAG 运维](../guides/knowledge-operations.md)。

## Retrieval 与 AgentScope

`KnowledgeRetrievalService` 只接受状态为 `READY`、四类 Profile 绑定一致的固定 Revision。调用方必须先把 Document ACL 解析为文档白名单；空白名单直接返回空结果且不访问 Provider。服务执行 Query Embedding、向量召回、可选 Hybrid Port、去重、Rerank、结果上限和上下文字符预算，并生成：

- 每个结果的 Document、DocumentRevision、Chunk Key、标题和固定 `UNTRUSTED_EXTERNAL` Citation；
- 不含查询/文档正文的候选数、返回数、字符 Usage、耗时和固定 Revision Trace；
- 无结果时的空 `items` 与完整 Trace；Provider 失败则显式失败，不伪装成无结果。

`AgentScopeKnowledgeAdapter` 把固定 `RetrievalRequest` 模板注册为只读、可并发的 `knowledge_retrieve` Tool。模型只能提交查询文本，不能更换租户、Revision、ACL、预算或 Provider；返回值是 AgentArk Citation/Trace JSON，不暴露 AgentScope RAG Event 或旧 Knowledge 类型。

## 数据库与契约

- Control Flyway `V6__phase_14_knowledge_ingestion_result.sql` 创建 `knowledge_ingestion_result`，并前向扩展 `control_outbox.aggregate_type` 允许 `knowledge_revision`。
- Result、Revision 状态和 Outbox 在同一 Control 事务提交；Worker 不写 Control DB。
- `contracts/schemas/knowledge-ingestion-internal/v1.json` 固定计划和结果 Wire DTO。
- `contracts/schemas/knowledge-retrieval/v1.json` 固定 Citation、Trace 和 Usage 字段。
- `contracts/openapi/internal-control-v1.yaml` 只暴露加载计划和提交结果两个 Knowledge Internal Endpoint，统一要求 Service Bearer 身份。

## 已执行验证

以下结果必须与本文件一起复核，不能从文档推断未执行的生产验收：

- Phase 14 定向单元测试：12 项通过，覆盖安全扫描、摄取成功/失败、Control 幂等事务、删除传播、检索、Wire DTO 和 AgentScope Tool。
- Contract Schema/Lint：16 项通过，覆盖新增 Internal Ingestion 与 Retrieval Golden File。
- Qdrant 1.18.3 Testcontainers：1 项通过，覆盖 Upsert、Count/Checksum、ACL、跨租户隔离、Adapter 重启初始化和删除。
- `./mvnw -pl agentark-knowledge,agentark-runtime,agentark-runtime-provider-agentscope -am clean verify`：12 个 Reactor 模块全部通过；Knowledge 22 项单元测试、7 项集成测试，Runtime 与 AgentScope Provider 回归均通过。
- MySQL 8.4 组合迁移：Control V1–V6 空库和 V5→V6 迁移通过；真实租户集成测试验证同一结果重放只保留 1 条 `knowledge_ingestion_result`、1 条 `knowledge_revision` Outbox，并把 Revision 转换为 `READY`。
- Scheduler MySQL 权限集成测试：6 项通过，明确验证 `agentark_scheduler` 账号无法读取 `agentark_control` 和 `agentark_runtime` 的所有权哨兵表。
- `docker compose -f deploy/compose/docker-compose.yml --profile rag config`：通过；Qdrant 固定为 1.18.3 且 Core Profile 默认不启动。
- Domain/Application Provider 中立扫描、AgentScope Import 专用包扫描、`python3 tools/harness/knowledge_gate.py`（45 份活动文档）、`python3 tools/harness/verify_upstreams.py --require-worktrees` 和 `git diff HEAD --check`：全部通过。
- 完整 Reactor 首次重跑曾因 Testcontainers 随机 MySQL 端口 `64342` 与本机 IDEA 监听端口碰撞自动换端口重试；最终独立完整 `clean verify` 未发生该碰撞并以退出码 0 完成。

## 已知边界

- Phase 15 才实现 Scheduler Trigger/Job/Attempt、Retry/Dead Letter 和 `KnowledgeIngestionWorker` 生产装配；本阶段不声称摄取请求会被常驻 Scheduler 自动消费。
- `MalwareScanner` 必须由生产部署接入真实扫描服务；没有安全扫描 Provider 时禁止启用摄取 Handler。
- `ProcessIsolatedTextParserSandbox` 提供进程、Heap、环境和超时隔离，不等价于容器级 Sandbox；生产需叠加文件系统、网络和系统调用策略。
- 当前 Qdrant Adapter 使用 JDK REST 客户端，避免引入 Qdrant SDK 类型；Hybrid、Embedding、Reranker 的生产 Provider 由后续 Provider/部署工作包实现。

## 回滚

代码回滚应同时撤销 Phase 14 Java、测试、契约和文档。数据库已应用 V6 时禁止修改或删除迁移历史：应新增前向迁移停止 Internal Endpoint 写入并保留 `knowledge_ingestion_result` 审计数据；如需停用 RAG，先停止 Phase 15 Handler，再撤销 Qdrant Adapter 配置。Qdrant 派生向量可按固定 Revision 重建，但删除前必须确认 MySQL Revision、Object Store 原文和 Chunk Artifact 均可用。
