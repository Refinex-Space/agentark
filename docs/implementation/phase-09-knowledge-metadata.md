---
owner: refinex
updated: 2026-08-16
status: active
referenced_by: docs/README.md
---

# Phase 09 Knowledge 元数据与 Provider Ports 执行报告

## 结论

Phase 09 在不引入 Qdrant、Embedding SDK 或 AgentScope Runtime 类型的前提下，建立了 Project Scope 的 Knowledge 元数据平面：Knowledge Base、Data Source、Document/Document Revision、Document ACL、四类不可变 Profile、Knowledge Revision、完整状态机、摄取意图描述和六类 Provider Port。Control Server 负责组合 IAM、审计、MyBatis、Object Store 与 Public API；`agentark-knowledge` 不依赖 `agentark-control` 实现。

本阶段没有执行真实 Parser、Chunk、Embedding、向量写入、Retriever 或 Reranker。`202` 摄取响应仅表示 `DESCRIBED` 意图和 Revision 已进入 `INGESTING`，不代表 Scheduler Job、Qdrant Collection 或可检索索引已经创建。

## 固定上游审计

审计使用 AgentScope 固定 Commit `0c61e7494197ded54eefdeaf9bdeb51807beb752`。Core 的旧 `rag.Knowledge`、`Document` 和 `DocumentMetadata` 自 2.0.0 起已 deprecated；Simple RAG 的 `SimpleKnowledge` 直接组合 `EmbeddingModel` 与 `VDBStoreBase`，Reader 负责解析和切分，Qdrant、Elasticsearch、Milvus、PgVector 各自实现 Store。相关行为由 `SimpleKnowledgeTest`、`VDBStoreBaseTest`、`TextChunkerTest`、各 Store Test 和 `RAGInMemoryE2ETest` 覆盖。

这些实现没有平台级 Organization/Project Owner、Document ACL、不可变 Knowledge Revision、READY 引用门禁或摄取清理状态。因此取用决策为：

- `REFERENCE`：Knowledge 检索、Reader/Chunker、Embedding、Vector Store、Retriever/Rerank 行为；
- `ADAPT`：把能力边界重建为 AgentArk Provider Ports 和版本化 Profile；
- `DEFER`：Qdrant/Elasticsearch/Milvus/PgVector、真实 Reader/Embedding/Reranker Adapter 到 Phase 14；
- `REJECT`：复制 AgentScope Core/RAG 类型、同步大文档摄取、以 Collection 名代替租户授权。

固定 AgentScope Service 的 Java Controller、Aistio Product Handler 和 Frontend 中没有独立 Knowledge Base、Document、Ingestion 管理 API/UI。本阶段是 AgentArk 独立平台建模，不是对现成 Service 功能的源码搬运。

## 领域与状态机

`KnowledgeBase` 是项目内稳定身份；`Document` 保存稳定标题、非敏感 Metadata 和显式 ACL；每次原文件提交形成不可变 `DocumentRevision`，其中 `ObjectRef` 固定 URI、SHA-256、大小与媒体类型。Parser、Chunk、Embedding、Retrieval Profile 使用 `(projectId, key, versionNumber)` 只追加，只有 `PUBLISHED` 可创建 Knowledge Revision。

`KnowledgeRevision` 固定文档修订集合、稳定顺序、四类 Profile 和整体 `contentHash`。内容绑定没有更新入口，状态字段使用乐观锁按以下白名单转换：

```text
CREATED -> INGESTING -> VERIFYING -> READY -> DEPRECATED -> DELETING -> DELETED
                    \-> FAILED -> INGESTING
CREATED/FAILED/READY -> DELETING
```

只有 `READY` 能通过 `KnowledgeRevisionResolver`；`CREATED/INGESTING/VERIFYING/FAILED/DEPRECATED/DELETING/DELETED` 均不能作为新 Agent Revision 的 Knowledge 输入。READY 后仍允许执行明确的 DEPRECATED 或 DELETING 生命周期转换，但不可修改文档/Profile 内容绑定。

## Provider Ports 与 Fake Adapter

中立 Port 包括 `DocumentParser`、`ChunkingStrategy`、`EmbeddingProvider`、`VectorIndex`、`Retriever` 和 `Reranker`，统一使用异步 `CompletionStage`。向量写入、删除和检索显式携带可信 `ProjectId` 与 `KnowledgeRevisionId`；Provider 内部资源名只用于实现定位，不能充当授权。

`FakeKnowledgeProviders` 提供确定性的解析、切分、三维 Embedding、内存索引、检索和 Rerank，用于状态机与 Port Contract Test。`InMemoryKnowledgeRepository` 同样按 Project 过滤并支持 UUIDv7 Cursor；两者只进入测试或显式 Fixture，不冒充生产 Provider。

未来 AgentScope 转换只能进入 `adapter.out.vector.agentscope`。Domain、Application、Public DTO、Flyway 和 OpenAPI 都不得导入 AgentScope、Qdrant、Elasticsearch、Milvus 或 PgVector 类型。

## V4、Object Store、授权与审计

`V4__phase_09_knowledge_metadata.sql` 在 Control Schema 新增十二张表：`knowledge_base`、`data_source`、`document`、`document_acl`、`document_revision`、四类 Profile、`knowledge_revision`、`knowledge_revision_document` 和 `knowledge_ingestion_request`。所有表和字段都有 MySQL 原生中文 `COMMENT`，可穷举字段注释完整列出合法值；Organization/Project Owner 链通过复合外键和项目条件 SQL 固定。

V4 同时注册 `knowledge:read`、`knowledge:manage`、`knowledge:ingest`。Control Server 的 Bridge 复用 IAM Permission Registry 和项目授权，客户端 Header、路径外 Project 或向量 Collection 均不能选择租户。审计通过真实 `KnowledgeAuditPort` 转为事务提交后的 IAM Audit；没有空实现吞掉事实。

文档上传只允许 `UPLOAD` Data Source，服务端生成 Object Store 路径，客户端文件名不参与授权路径。写入后复核 Hash、大小和媒体类型；数据库提交失败时尝试补偿删除本次新对象。原文件内容、Prompt 和 Secret 不进入 Metadata、日志或审计。

## Public Contract

Public Control 增加八组实际 Controller 路径，覆盖 Knowledge Base、Data Source、Document、Profile、Revision、摄取意图、Deprecate 和 Deletion。四类列表使用最大一百条的不透明 Cursor，按 Project 内 UUIDv7 稳定排序；API Adapter 把持久化 JSON 还原为对象，不暴露双重编码字符串、MyBatis Row 或领域内部引用类型。

`contracts/schemas/knowledge-public/v1.json` 定义 Public DTO、请求、ObjectRef、SecretRef 与 Cursor Page；`public-control-v1.yaml` 只声明实际存在的 Endpoint。Golden File、完整 Path 集合、中文 YAML 属性注释和 Provider 中立负例均由 Kernel 契约测试检查。

## 测试与验收证据

本阶段测试覆盖：

- 全状态转换白名单与终态拒绝；
- Profile 变更创建新 Knowledge Revision、旧内容保持不变；
- READY Resolver、幂等摄取描述和删除状态；
- Fake Parser/Chunk/Embedding/Vector/Retriever/Reranker；
- Cursor 无重复分页和非法游标拒绝；
- MySQL 8.4 空库 V1→V4、V3→V4、十二张表与全部字段中文 COMMENT；
- 原文件 Hash/ObjectRef 往返、MyBatis 持久化、IAM 审计和跨租户 Repository/HTTP 拒绝；
- Domain/Application 对 AgentScope 和向量后端的架构隔离；
- Control Server 同时组合 IAM、Phase 08 Catalog/Secret 与 Phase 09 Knowledge。

2026-08-16 最终执行 `./mvnw -pl agentark-knowledge,agentark-services/agentark-control-server -am clean verify`，十三个 Reactor 模块全部 `SUCCESS`，总耗时 1 分 52 秒。执行中发现并修复 Knowledge Bridge 在 `agentark.control.knowledge.enabled=false` 时仍装配的问题；修复后 Control Server 无数据库测试 2/2 通过。随后知识门禁检查 37 份 active 文档通过，AgentScope Java 与 DeepSeek Harness 固定 Commit 校验通过，领域/Application Provider 中立扫描及 `git diff HEAD --check` 通过，Phase 09 据此标记为 DONE。

## 风险与后续边界

- Object Store 与 MySQL 不是分布式事务。当前失败补偿只处理本请求新对象；生产对象垃圾回收仍需按数据库引用扫描，禁止目录级盲删。
- Cursor 使用 UUIDv7 稳定顺序，提供一致前向翻页但不是全局快照隔离；并发新增只会出现在后续位置，删除由生命周期状态表达而不是物理删除。
- Phase 09 未验证真实文档解析质量、Embedding 模型、Qdrant 索引、召回、Rerank 或 Citation；这些属于 Phase 14，不能从 Fake Adapter 测试推导为生产能力。
- `READY` 的验证转换当前由受控应用命令承载；Phase 14 必须先校验摄取结果完整性、计数和 Hash，再允许进入 READY。

## 回滚

- 未发布代码、契约和文档按本阶段 Git Diff 精确反向修改，不覆盖 Phase 08 或用户其他未提交改动。
- V4 测试库随 Testcontainers 销毁；不得在共享数据库手工删表。
- V4 一旦进入共享环境不可删除或改写，只能增加 Flyway Forward Fix；应用回滚前确认旧版本对新增权限和表保持兼容。
- 本地测试 Object Store 位于系统临时目录；仅在确认无数据库引用后清理，不对仓库或共享对象前缀执行递归删除。
