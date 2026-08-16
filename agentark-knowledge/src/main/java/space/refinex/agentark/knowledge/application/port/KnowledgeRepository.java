/*
 * Copyright 2026 refinex.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package space.refinex.agentark.knowledge.application.port;

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.knowledge.domain.*;

import java.util.List;
import java.util.Optional;

/**
 * 定义 Control Schema 内 Knowledge 元数据、不可变版本和状态更新的持久化 Port。
 *
 * @author refinex
 */
public interface KnowledgeRepository {

    /**
     * @param knowledgeBase 待插入 Knowledge Base
     * @param actor         创建主体稳定引用
     */
    void insertKnowledgeBase(KnowledgeBase knowledgeBase, String actor);

    /**
     * @param projectId       项目标识
     * @param knowledgeBaseId Knowledge Base 标识
     * @return 同项目资源
     */
    Optional<KnowledgeBase> findKnowledgeBase(
        ProjectId projectId, KnowledgeBaseId knowledgeBaseId);

    /**
     * @param projectId 项目标识
     * @param afterId   上一页最后一个 Knowledge Base 标识
     * @param limit     最大结果数
     * @return 同项目 Knowledge Base
     */
    List<KnowledgeBase> listKnowledgeBases(
        ProjectId projectId, Optional<KnowledgeBaseId> afterId, int limit);

    /**
     * @param dataSource 待插入数据源
     * @param actor      创建主体稳定引用
     */
    void insertDataSource(DataSource dataSource, String actor);

    /**
     * @param projectId       项目标识
     * @param knowledgeBaseId Knowledge Base 标识
     * @param afterId         上一页最后一个数据源标识
     * @param limit           最大结果数
     * @return 同项目数据源
     */
    List<DataSource> listDataSources(
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        Optional<DataSourceId> afterId,
        int limit);

    /**
     * @param projectId    项目标识
     * @param dataSourceId 数据源标识
     * @return 同项目数据源
     */
    Optional<DataSource> findDataSource(ProjectId projectId, DataSourceId dataSourceId);

    /**
     * 原子插入文档稳定身份、ACL 与首个不可变原文件修订。
     *
     * @param document 文档稳定身份
     * @param revision 首个文档修订
     * @param actor    创建主体稳定引用
     */
    void insertDocument(Document document, DocumentRevision revision, String actor);

    /**
     * @param projectId  项目标识
     * @param documentId 文档标识
     * @return 同项目文档
     */
    Optional<Document> findDocument(ProjectId projectId, DocumentId documentId);

    /**
     * @param projectId       项目标识
     * @param knowledgeBaseId Knowledge Base 标识
     * @param afterId         上一页最后一个文档标识
     * @param limit           最大结果数
     * @return 同项目文档
     */
    List<Document> listDocuments(
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        Optional<DocumentId> afterId,
        int limit);

    /**
     * @param projectId  项目标识
     * @param revisionId 文档修订标识
     * @return 同项目不可变文档修订
     */
    Optional<DocumentRevision> findDocumentRevision(
        ProjectId projectId, DocumentRevisionId revisionId);

    /**
     * @param profile 待插入 Parser Profile
     * @param actor   创建主体稳定引用
     */
    void insertParserProfile(ParserProfile profile, String actor);

    /**
     * @param profile 待插入 Chunk Profile
     * @param actor   创建主体稳定引用
     */
    void insertChunkProfile(ChunkProfile profile, String actor);

    /**
     * @param profile 待插入 Embedding Profile
     * @param actor   创建主体稳定引用
     */
    void insertEmbeddingProfile(EmbeddingProfile profile, String actor);

    /**
     * @param profile 待插入 Retrieval Profile
     * @param actor   创建主体稳定引用
     */
    void insertRetrievalProfile(RetrievalProfile profile, String actor);

    /**
     * @param projectId 项目标识
     * @param id        Parser Profile 标识
     * @return 同项目 Profile
     */
    Optional<ParserProfile> findParserProfile(ProjectId projectId, ParserProfileId id);

    /**
     * @param projectId 项目标识
     * @param id        Chunk Profile 标识
     * @return 同项目 Profile
     */
    Optional<ChunkProfile> findChunkProfile(ProjectId projectId, ChunkProfileId id);

    /**
     * @param projectId 项目标识
     * @param id        Embedding Profile 标识
     * @return 同项目 Profile
     */
    Optional<EmbeddingProfile> findEmbeddingProfile(ProjectId projectId, EmbeddingProfileId id);

    /**
     * @param projectId 项目标识
     * @param id        Retrieval Profile 标识
     * @return 同项目 Profile
     */
    Optional<RetrievalProfile> findRetrievalProfile(ProjectId projectId, RetrievalProfileId id);

    /**
     * @param projectId 项目标识
     * @param key       Parser Profile Key
     * @return 下一个单调版本号
     */
    long nextParserProfileVersion(ProjectId projectId, String key);

    /**
     * @param projectId 项目标识
     * @param key       Chunk Profile Key
     * @return 下一个单调版本号
     */
    long nextChunkProfileVersion(ProjectId projectId, String key);

    /**
     * @param projectId 项目标识
     * @param key       Embedding Profile Key
     * @return 下一个单调版本号
     */
    long nextEmbeddingProfileVersion(ProjectId projectId, String key);

    /**
     * @param projectId 项目标识
     * @param key       Retrieval Profile Key
     * @return 下一个单调版本号
     */
    long nextRetrievalProfileVersion(ProjectId projectId, String key);

    /**
     * @param revision 待插入 Knowledge Revision
     * @param actor    创建主体稳定引用
     */
    void insertKnowledgeRevision(KnowledgeRevision revision, String actor);

    /**
     * @param projectId       项目标识
     * @param knowledgeBaseId Knowledge Base 标识
     * @return 下一个单调版本号
     */
    long nextKnowledgeRevisionNumber(ProjectId projectId, KnowledgeBaseId knowledgeBaseId);

    /**
     * @param projectId  项目标识
     * @param revisionId Knowledge Revision 标识
     * @return 同项目 Revision
     */
    Optional<KnowledgeRevision> findKnowledgeRevision(
        ProjectId projectId, KnowledgeRevisionId revisionId);

    /**
     * @param projectId       项目标识
     * @param knowledgeBaseId Knowledge Base 标识
     * @param afterId         上一页最后一个 Knowledge Revision 标识
     * @param limit           最大结果数
     * @return 同项目 Revision
     */
    List<KnowledgeRevision> listKnowledgeRevisions(
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        Optional<KnowledgeRevisionId> afterId,
        int limit);

    /**
     * 使用乐观锁更新且仅更新 Knowledge Revision 状态字段。
     *
     * @param revision        已完成状态机校验的新聚合值
     * @param expectedVersion 更新前版本
     * @param actor           更新主体稳定引用
     * @return 更新行数
     */
    int updateKnowledgeRevisionState(
        KnowledgeRevision revision, long expectedVersion, String actor);

    /**
     * 幂等插入摄取请求描述。
     *
     * @param descriptor 摄取请求描述
     * @param actor      请求主体稳定引用
     * @return 新插入或已存在的请求描述
     */
    IngestionJobDescriptor insertOrFindIngestionRequest(
        IngestionJobDescriptor descriptor, String actor);

    /**
     * @param projectId      项目标识
     * @param idempotencyKey 项目内幂等键
     * @return 同项目已存在摄取请求
     */
    Optional<IngestionJobDescriptor> findIngestionRequest(
        ProjectId projectId, String idempotencyKey);

    /**
     * @param projectId 项目标识
     * @param requestId 摄取请求标识
     * @return 同项目摄取请求
     */
    Optional<IngestionJobDescriptor> findIngestionRequest(
        ProjectId projectId, IngestionRequestId requestId);

    /**
     * 仅供已认证 Internal Service 按全局 UUIDv7 加载摄取计划，不得暴露到 Public API。
     *
     * @param requestId 摄取请求标识
     * @return 摄取请求及其可信租户范围
     */
    Optional<IngestionJobDescriptor> findIngestionRequestInternal(
        IngestionRequestId requestId);
}
