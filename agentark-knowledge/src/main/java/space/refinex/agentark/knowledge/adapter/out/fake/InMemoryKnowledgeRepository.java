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

package space.refinex.agentark.knowledge.adapter.out.fake;

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.knowledge.application.port.KnowledgeRepository;
import space.refinex.agentark.knowledge.domain.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 提供线程安全的内存 Knowledge Repository，供状态机、应用服务和 Provider Contract 测试使用。
 *
 * @author refinex
 */
public final class InMemoryKnowledgeRepository implements KnowledgeRepository {

    /**
     * Knowledge Base 内存表。
     */
    private final Map<KnowledgeBaseId, KnowledgeBase> knowledgeBases = new ConcurrentHashMap<>();

    /**
     * 数据源内存表。
     */
    private final Map<DataSourceId, DataSource> dataSources = new ConcurrentHashMap<>();

    /**
     * 文档稳定身份内存表。
     */
    private final Map<DocumentId, Document> documents = new ConcurrentHashMap<>();

    /**
     * 文档修订内存表。
     */
    private final Map<DocumentRevisionId, DocumentRevision> documentRevisions =
        new ConcurrentHashMap<>();

    /**
     * Parser Profile 内存表。
     */
    private final Map<ParserProfileId, ParserProfile> parserProfiles = new ConcurrentHashMap<>();

    /**
     * Chunk Profile 内存表。
     */
    private final Map<ChunkProfileId, ChunkProfile> chunkProfiles = new ConcurrentHashMap<>();

    /**
     * Embedding Profile 内存表。
     */
    private final Map<EmbeddingProfileId, EmbeddingProfile> embeddingProfiles =
        new ConcurrentHashMap<>();

    /**
     * Retrieval Profile 内存表。
     */
    private final Map<RetrievalProfileId, RetrievalProfile> retrievalProfiles =
        new ConcurrentHashMap<>();

    /**
     * Knowledge Revision 内存表。
     */
    private final Map<KnowledgeRevisionId, KnowledgeRevision> knowledgeRevisions =
        new ConcurrentHashMap<>();

    /**
     * 摄取请求内存表。
     */
    private final Map<IngestionRequestId, IngestionJobDescriptor> ingestionRequests =
        new ConcurrentHashMap<>();

    /**
     * 创建空的内存 Repository。
     */
    public InMemoryKnowledgeRepository() {
        // 所有内存表按需初始化，无外部资源。
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public synchronized void insertKnowledgeBase(KnowledgeBase knowledgeBase, String actor) {
        boolean duplicate = knowledgeBases.values().stream().anyMatch(
            existing -> existing.projectId().equals(knowledgeBase.projectId())
                && existing.key().equals(knowledgeBase.key()));
        if (duplicate || knowledgeBases.putIfAbsent(knowledgeBase.id(), knowledgeBase) != null) {
            throw new IllegalStateException("knowledge base already exists");
        }
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public Optional<KnowledgeBase> findKnowledgeBase(
        ProjectId projectId, KnowledgeBaseId knowledgeBaseId) {
        return scoped(knowledgeBases.get(knowledgeBaseId), projectId, KnowledgeBase::projectId);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public List<KnowledgeBase> listKnowledgeBases(
        ProjectId projectId, Optional<KnowledgeBaseId> afterId, int limit) {
        return knowledgeBases.values().stream()
            .filter(value -> value.projectId().equals(projectId))
            .filter(value -> isAfter(value.id(), afterId))
            .sorted(Comparator.comparing(value -> value.id().asString()))
            .limit(limit)
            .toList();
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public void insertDataSource(DataSource dataSource, String actor) {
        if (dataSources.putIfAbsent(dataSource.id(), dataSource) != null) {
            throw new IllegalStateException("data source already exists");
        }
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public List<DataSource> listDataSources(
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        Optional<DataSourceId> afterId,
        int limit) {
        return dataSources.values().stream()
            .filter(value -> value.projectId().equals(projectId))
            .filter(value -> value.knowledgeBaseId().equals(knowledgeBaseId))
            .filter(value -> isAfter(value.id(), afterId))
            .sorted(Comparator.comparing(value -> value.id().asString()))
            .limit(limit)
            .toList();
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public Optional<DataSource> findDataSource(ProjectId projectId, DataSourceId dataSourceId) {
        return scoped(dataSources.get(dataSourceId), projectId, DataSource::projectId);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public synchronized void insertDocument(
        Document document, DocumentRevision revision, String actor) {
        if (!document.id().equals(revision.documentId())
            || !document.projectId().equals(revision.projectId())
            || documents.putIfAbsent(document.id(), document) != null) {
            throw new IllegalStateException("document insert precondition failed");
        }
        if (documentRevisions.putIfAbsent(revision.id(), revision) != null) {
            documents.remove(document.id());
            throw new IllegalStateException("document revision already exists");
        }
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public Optional<Document> findDocument(ProjectId projectId, DocumentId documentId) {
        return scoped(documents.get(documentId), projectId, Document::projectId);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public List<Document> listDocuments(
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        Optional<DocumentId> afterId,
        int limit) {
        return documents.values().stream()
            .filter(value -> value.projectId().equals(projectId))
            .filter(value -> value.knowledgeBaseId().equals(knowledgeBaseId))
            .filter(value -> isAfter(value.id(), afterId))
            .sorted(Comparator.comparing(value -> value.id().asString()))
            .limit(limit)
            .toList();
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public Optional<DocumentRevision> findDocumentRevision(
        ProjectId projectId, DocumentRevisionId revisionId) {
        return scoped(
            documentRevisions.get(revisionId), projectId, DocumentRevision::projectId);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public void insertParserProfile(ParserProfile profile, String actor) {
        requireNew(parserProfiles, profile.id(), profile);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public void insertChunkProfile(ChunkProfile profile, String actor) {
        requireNew(chunkProfiles, profile.id(), profile);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public void insertEmbeddingProfile(EmbeddingProfile profile, String actor) {
        requireNew(embeddingProfiles, profile.id(), profile);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public void insertRetrievalProfile(RetrievalProfile profile, String actor) {
        requireNew(retrievalProfiles, profile.id(), profile);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public Optional<ParserProfile> findParserProfile(ProjectId projectId, ParserProfileId id) {
        return scoped(parserProfiles.get(id), projectId, ParserProfile::projectId);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public Optional<ChunkProfile> findChunkProfile(ProjectId projectId, ChunkProfileId id) {
        return scoped(chunkProfiles.get(id), projectId, ChunkProfile::projectId);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public Optional<EmbeddingProfile> findEmbeddingProfile(
        ProjectId projectId, EmbeddingProfileId id) {
        return scoped(embeddingProfiles.get(id), projectId, EmbeddingProfile::projectId);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public Optional<RetrievalProfile> findRetrievalProfile(
        ProjectId projectId, RetrievalProfileId id) {
        return scoped(retrievalProfiles.get(id), projectId, RetrievalProfile::projectId);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public synchronized long nextParserProfileVersion(ProjectId projectId, String key) {
        return nextProfileVersion(parserProfiles.values(), projectId, key, ParserProfile::key,
            ParserProfile::versionNumber);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public synchronized long nextChunkProfileVersion(ProjectId projectId, String key) {
        return nextProfileVersion(chunkProfiles.values(), projectId, key, ChunkProfile::key,
            ChunkProfile::versionNumber);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public synchronized long nextEmbeddingProfileVersion(ProjectId projectId, String key) {
        return nextProfileVersion(embeddingProfiles.values(), projectId, key, EmbeddingProfile::key,
            EmbeddingProfile::versionNumber);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public synchronized long nextRetrievalProfileVersion(ProjectId projectId, String key) {
        return nextProfileVersion(retrievalProfiles.values(), projectId, key, RetrievalProfile::key,
            RetrievalProfile::versionNumber);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public void insertKnowledgeRevision(KnowledgeRevision revision, String actor) {
        requireNew(knowledgeRevisions, revision.id(), revision);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public synchronized long nextKnowledgeRevisionNumber(
        ProjectId projectId, KnowledgeBaseId knowledgeBaseId) {
        return knowledgeRevisions.values().stream()
            .filter(value -> value.projectId().equals(projectId))
            .filter(value -> value.knowledgeBaseId().equals(knowledgeBaseId))
            .mapToLong(KnowledgeRevision::revisionNumber)
            .max()
            .orElse(0) + 1;
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public Optional<KnowledgeRevision> findKnowledgeRevision(
        ProjectId projectId, KnowledgeRevisionId revisionId) {
        return scoped(knowledgeRevisions.get(revisionId), projectId, KnowledgeRevision::projectId);
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public List<KnowledgeRevision> listKnowledgeRevisions(
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        Optional<KnowledgeRevisionId> afterId,
        int limit) {
        return knowledgeRevisions.values().stream()
            .filter(value -> value.projectId().equals(projectId))
            .filter(value -> value.knowledgeBaseId().equals(knowledgeBaseId))
            .filter(value -> isAfter(value.id(), afterId))
            .sorted(Comparator.comparing(value -> value.id().asString()))
            .limit(limit)
            .toList();
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public synchronized int updateKnowledgeRevisionState(
        KnowledgeRevision revision, long expectedVersion, String actor) {
        KnowledgeRevision current = knowledgeRevisions.get(revision.id());
        if (current == null
            || !current.projectId().equals(revision.projectId())
            || current.version() != expectedVersion) {
            return 0;
        }
        knowledgeRevisions.put(revision.id(), revision);
        return 1;
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public synchronized IngestionJobDescriptor insertOrFindIngestionRequest(
        IngestionJobDescriptor descriptor, String actor) {
        Optional<IngestionJobDescriptor> existing = findIngestionRequest(
            descriptor.projectId(), descriptor.idempotencyKey());
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        ingestionRequests.put(descriptor.id(), descriptor);
        return descriptor;
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public Optional<IngestionJobDescriptor> findIngestionRequest(
        ProjectId projectId, String idempotencyKey) {
        return ingestionRequests.values().stream()
            .filter(value -> value.projectId().equals(projectId))
            .filter(value -> value.idempotencyKey().equals(idempotencyKey))
            .findFirst();
    }

    /**
     * 按显式项目范围实现内存仓储端口操作。
     */
    @Override
    public Optional<IngestionJobDescriptor> findIngestionRequest(
        ProjectId projectId, IngestionRequestId requestId) {
        return scoped(
            ingestionRequests.get(requestId), projectId, IngestionJobDescriptor::projectId);
    }

    /**
     * 按项目过滤可选内存值。
     *
     * @param value            可为空的内存值
     * @param projectId        项目标识
     * @param projectExtractor 项目标识读取器
     * @param <T>              值类型
     * @return 同项目时返回值
     */
    private static <T> Optional<T> scoped(
        T value, ProjectId projectId, Function<T, ProjectId> projectExtractor) {
        return Optional.ofNullable(value).filter(candidate -> projectExtractor.apply(candidate)
            .equals(projectId));
    }

    /**
     * 判断候选 UUIDv7 是否位于不透明游标之后。
     *
     * @param candidate 候选标识
     * @param afterId   可选上一页末标识
     * @param <I>       强类型标识
     * @return 未提供游标或候选标识字典序更大时返回 {@code true}
     */
    private static <I extends StrongId> boolean isAfter(I candidate, Optional<I> afterId) {
        return afterId.isEmpty()
            || candidate.asString().compareTo(afterId.orElseThrow().asString()) > 0;
    }

    /**
     * 向内存表插入唯一值。
     *
     * @param table 目标内存表
     * @param id    主键
     * @param value 待插入值
     * @param <I>   主键类型
     * @param <T>   值类型
     */
    private static <I, T> void requireNew(Map<I, T> table, I id, T value) {
        if (table.putIfAbsent(id, value) != null) {
            throw new IllegalStateException("immutable knowledge value already exists");
        }
    }

    /**
     * 计算同项目同 Key 的下一个 Profile 版本号。
     *
     * @param values           Profile 值集合
     * @param projectId        项目标识
     * @param key              稳定 Key
     * @param keyExtractor     Key 读取器
     * @param versionExtractor 版本号读取器
     * @param <T>              Profile 类型
     * @return 下一个版本号
     */
    private static <T> long nextProfileVersion(
        java.util.Collection<T> values,
        ProjectId projectId,
        String key,
        Function<T, String> keyExtractor,
        java.util.function.ToLongFunction<T> versionExtractor) {
        List<T> scoped = new ArrayList<>(values);
        return scoped.stream()
            .filter(value -> extractProjectId(value).equals(projectId))
            .filter(value -> keyExtractor.apply(value).equals(key))
            .mapToLong(versionExtractor)
            .max()
            .orElse(0) + 1;
    }

    /**
     * 从四种 Profile 值读取项目标识。
     *
     * @param value Profile 值
     * @return 项目标识
     */
    private static ProjectId extractProjectId(Object value) {
        if (value instanceof ParserProfile profile) {
            return profile.projectId();
        }
        if (value instanceof ChunkProfile profile) {
            return profile.projectId();
        }
        if (value instanceof EmbeddingProfile profile) {
            return profile.projectId();
        }
        if (value instanceof RetrievalProfile profile) {
            return profile.projectId();
        }
        throw new IllegalArgumentException("unsupported profile type");
    }
}
