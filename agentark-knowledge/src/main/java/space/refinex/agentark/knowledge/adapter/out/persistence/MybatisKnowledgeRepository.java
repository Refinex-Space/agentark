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

package space.refinex.agentark.knowledge.adapter.out.persistence;

import org.springframework.dao.DuplicateKeyException;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.kernel.ref.SecretRef;
import space.refinex.agentark.knowledge.application.port.KnowledgeRepository;
import space.refinex.agentark.knowledge.domain.*;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/**
 * 使用显式项目 Scope 的 MyBatis Mapper 实现 Knowledge Repository，并隔离数据库行对象。
 *
 * @author refinex
 */
public final class MybatisKnowledgeRepository implements KnowledgeRepository {

    /**
     * Knowledge 知识元数据 MyBatis Mapper。
     */
    private final KnowledgeMapper mapper;

    /**
     * 应用统一 JSON Mapper，仅用于 Adapter 序列化。
     */
    private final JsonMapper jsonMapper;

    /**
     * 创建 MyBatis Knowledge Repository。
     *
     * @param mapper     显式项目 Scope Mapper
     * @param jsonMapper 应用统一 JSON Mapper
     */
    public MybatisKnowledgeRepository(KnowledgeMapper mapper, JsonMapper jsonMapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public void insertKnowledgeBase(KnowledgeBase knowledgeBase, String actor) {
        mapper.insertKnowledgeBase(new KnowledgePersistenceRows.BaseRow(
            knowledgeBase.id().value(), knowledgeBase.organizationId().value(),
            knowledgeBase.projectId().value(), knowledgeBase.key(), knowledgeBase.name(),
            knowledgeBase.description(), knowledgeBase.status().name(), knowledgeBase.version(),
            knowledgeBase.createdAt(), actor, knowledgeBase.updatedAt(), actor));
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public Optional<KnowledgeBase> findKnowledgeBase(
        ProjectId projectId, KnowledgeBaseId knowledgeBaseId) {
        return mapper.findKnowledgeBase(projectId.value(), knowledgeBaseId.value())
            .map(this::knowledgeBase);
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public List<KnowledgeBase> listKnowledgeBases(
        ProjectId projectId, Optional<KnowledgeBaseId> afterId, int limit) {
        return mapper.listKnowledgeBases(
                projectId.value(), afterId.map(KnowledgeBaseId::value).orElse(null), limit).stream()
            .map(this::knowledgeBase)
            .toList();
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public void insertDataSource(DataSource dataSource, String actor) {
        mapper.insertDataSource(new KnowledgePersistenceRows.DataSourceRow(
            dataSource.id().value(), dataSource.organizationId().value(),
            dataSource.projectId().value(), dataSource.knowledgeBaseId().value(),
            dataSource.type().name(), dataSource.name(), dataSource.descriptorJson(),
            dataSource.createdAt(), actor));
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public List<DataSource> listDataSources(
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        Optional<DataSourceId> afterId,
        int limit) {
        return mapper.listDataSources(
                projectId.value(), knowledgeBaseId.value(),
                afterId.map(DataSourceId::value).orElse(null), limit).stream()
            .map(this::dataSource)
            .toList();
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public Optional<DataSource> findDataSource(ProjectId projectId, DataSourceId dataSourceId) {
        return mapper.findDataSource(projectId.value(), dataSourceId.value()).map(this::dataSource);
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public void insertDocument(Document document, DocumentRevision revision, String actor) {
        mapper.insertDocument(new KnowledgePersistenceRows.DocumentRow(
            document.id().value(), document.organizationId().value(), document.projectId().value(),
            document.knowledgeBaseId().value(), document.dataSourceId().value(), document.title(),
            jsonMapper.writeValueAsString(document.metadata()), document.status().name(),
            document.version(), document.createdAt(), actor, document.updatedAt(), actor));
        for (DocumentAcl acl : document.acl()) {
            mapper.insertDocumentAcl(new KnowledgePersistenceRows.AclRow(
                document.id().value(), document.organizationId().value(), document.projectId().value(),
                acl.subjectType().name(), UUID.fromString(acl.subjectId()), acl.accessLevel().name(),
                document.createdAt(), actor));
        }
        mapper.insertDocumentRevision(documentRevisionRow(revision, actor));
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public Optional<Document> findDocument(ProjectId projectId, DocumentId documentId) {
        return mapper.findDocument(projectId.value(), documentId.value()).map(this::document);
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public List<Document> listDocuments(
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        Optional<DocumentId> afterId,
        int limit) {
        return mapper.listDocuments(
                projectId.value(), knowledgeBaseId.value(),
                afterId.map(DocumentId::value).orElse(null), limit).stream()
            .map(this::document)
            .toList();
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public Optional<DocumentRevision> findDocumentRevision(
        ProjectId projectId, DocumentRevisionId revisionId) {
        return mapper.findDocumentRevision(projectId.value(), revisionId.value())
            .map(this::documentRevision);
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public void insertParserProfile(ParserProfile profile, String actor) {
        mapper.insertProfile(ProfileTable.PARSER, profileRow(
            profile.id().value(), profile.organizationId(), profile.projectId(), profile.key(),
            profile.versionNumber(), profile.configJson(), null, profile.contentHash(),
            profile.status(), profile.createdAt(), actor));
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public void insertChunkProfile(ChunkProfile profile, String actor) {
        mapper.insertProfile(ProfileTable.CHUNK, profileRow(
            profile.id().value(), profile.organizationId(), profile.projectId(), profile.key(),
            profile.versionNumber(), profile.configJson(), null, profile.contentHash(),
            profile.status(), profile.createdAt(), actor));
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public void insertEmbeddingProfile(EmbeddingProfile profile, String actor) {
        mapper.insertProfile(ProfileTable.EMBEDDING, profileRow(
            profile.id().value(), profile.organizationId(), profile.projectId(), profile.key(),
            profile.versionNumber(), profile.configJson(),
            profile.credentialRef().map(SecretRef::asString).orElse(null), profile.contentHash(),
            profile.status(), profile.createdAt(), actor));
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public void insertRetrievalProfile(RetrievalProfile profile, String actor) {
        mapper.insertProfile(ProfileTable.RETRIEVAL, profileRow(
            profile.id().value(), profile.organizationId(), profile.projectId(), profile.key(),
            profile.versionNumber(), profile.configJson(), null, profile.contentHash(),
            profile.status(), profile.createdAt(), actor));
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public Optional<ParserProfile> findParserProfile(ProjectId projectId, ParserProfileId id) {
        return mapper.findProfile(ProfileTable.PARSER, projectId.value(), id.value())
            .map(row -> new ParserProfile(
                new ParserProfileId(row.id()), new OrganizationId(row.organizationId()),
                new ProjectId(row.projectId()), row.profileKey(), row.versionNumber(),
                row.configJson(), checksum(row.contentHash()),
                KnowledgeProfileStatus.valueOf(row.status()), row.createdAt()));
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public Optional<ChunkProfile> findChunkProfile(ProjectId projectId, ChunkProfileId id) {
        return mapper.findProfile(ProfileTable.CHUNK, projectId.value(), id.value())
            .map(row -> new ChunkProfile(
                new ChunkProfileId(row.id()), new OrganizationId(row.organizationId()),
                new ProjectId(row.projectId()), row.profileKey(), row.versionNumber(),
                row.configJson(), checksum(row.contentHash()),
                KnowledgeProfileStatus.valueOf(row.status()), row.createdAt()));
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public Optional<EmbeddingProfile> findEmbeddingProfile(
        ProjectId projectId, EmbeddingProfileId id) {
        return mapper.findProfile(ProfileTable.EMBEDDING, projectId.value(), id.value())
            .map(row -> new EmbeddingProfile(
                new EmbeddingProfileId(row.id()), new OrganizationId(row.organizationId()),
                new ProjectId(row.projectId()), row.profileKey(), row.versionNumber(),
                row.configJson(), Optional.ofNullable(row.credentialSecretRef()).map(SecretRef::parse),
                checksum(row.contentHash()), KnowledgeProfileStatus.valueOf(row.status()),
                row.createdAt()));
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public Optional<RetrievalProfile> findRetrievalProfile(
        ProjectId projectId, RetrievalProfileId id) {
        return mapper.findProfile(ProfileTable.RETRIEVAL, projectId.value(), id.value())
            .map(row -> new RetrievalProfile(
                new RetrievalProfileId(row.id()), new OrganizationId(row.organizationId()),
                new ProjectId(row.projectId()), row.profileKey(), row.versionNumber(),
                row.configJson(), checksum(row.contentHash()),
                KnowledgeProfileStatus.valueOf(row.status()), row.createdAt()));
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public long nextParserProfileVersion(ProjectId projectId, String key) {
        return nextProfileVersion(ProfileTable.PARSER, projectId, key);
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public long nextChunkProfileVersion(ProjectId projectId, String key) {
        return nextProfileVersion(ProfileTable.CHUNK, projectId, key);
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public long nextEmbeddingProfileVersion(ProjectId projectId, String key) {
        return nextProfileVersion(ProfileTable.EMBEDDING, projectId, key);
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public long nextRetrievalProfileVersion(ProjectId projectId, String key) {
        return nextProfileVersion(ProfileTable.RETRIEVAL, projectId, key);
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public void insertKnowledgeRevision(KnowledgeRevision revision, String actor) {
        mapper.insertKnowledgeRevision(revisionRow(revision, actor));
        for (int ordinal = 0; ordinal < revision.documentRevisionIds().size(); ordinal++) {
            mapper.insertKnowledgeRevisionDocument(
                revision.id().value(), revision.organizationId().value(), revision.projectId().value(),
                revision.documentRevisionIds().get(ordinal).value(), ordinal, revision.createdAt(), actor);
        }
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public long nextKnowledgeRevisionNumber(
        ProjectId projectId, KnowledgeBaseId knowledgeBaseId) {
        mapper.lockKnowledgeBase(projectId.value(), knowledgeBaseId.value())
            .orElseThrow(() -> new IllegalStateException("knowledge base lock target is missing"));
        return mapper.nextKnowledgeRevisionNumber(projectId.value(), knowledgeBaseId.value());
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public Optional<KnowledgeRevision> findKnowledgeRevision(
        ProjectId projectId, KnowledgeRevisionId revisionId) {
        return mapper.findKnowledgeRevision(projectId.value(), revisionId.value())
            .map(this::knowledgeRevision);
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public List<KnowledgeRevision> listKnowledgeRevisions(
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        Optional<KnowledgeRevisionId> afterId,
        int limit) {
        return mapper.listKnowledgeRevisions(
                projectId.value(), knowledgeBaseId.value(),
                afterId.map(KnowledgeRevisionId::value).orElse(null), limit)
            .stream().map(this::knowledgeRevision).toList();
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public int updateKnowledgeRevisionState(
        KnowledgeRevision revision, long expectedVersion, String actor) {
        return mapper.updateKnowledgeRevisionState(
            revision.projectId().value(), revision.id().value(), revision.status().name(),
            revision.failureCode().isBlank() ? null : revision.failureCode(), expectedVersion,
            revision.updatedAt(), actor);
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public IngestionJobDescriptor insertOrFindIngestionRequest(
        IngestionJobDescriptor descriptor, String actor) {
        try {
            mapper.insertIngestionRequest(new KnowledgePersistenceRows.IngestionRow(
                descriptor.id().value(), descriptor.organizationId().value(),
                descriptor.projectId().value(), descriptor.knowledgeRevisionId().value(),
                descriptor.idempotencyKey(), descriptor.status().name(), descriptor.requestedAt(),
                actor));
            return descriptor;
        } catch (DuplicateKeyException exception) {
            return findIngestionRequest(descriptor.projectId(), descriptor.idempotencyKey())
                .orElseThrow(() -> exception);
        }
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public Optional<IngestionJobDescriptor> findIngestionRequest(
        ProjectId projectId, String idempotencyKey) {
        return mapper.findIngestionRequestByKey(projectId.value(), idempotencyKey)
            .map(this::ingestion);
    }

    /**
     * 按显式项目范围实现 MyBatis 仓储端口操作。
     */
    @Override
    public Optional<IngestionJobDescriptor> findIngestionRequest(
        ProjectId projectId, IngestionRequestId requestId) {
        return mapper.findIngestionRequestById(projectId.value(), requestId.value())
            .map(this::ingestion);
    }

    /**
     * 仅供已认证 Internal Service 按全局请求标识读取可信租户范围。
     */
    @Override
    public Optional<IngestionJobDescriptor> findIngestionRequestInternal(
        IngestionRequestId requestId) {
        return mapper.findIngestionRequestInternal(requestId.value()).map(this::ingestion);
    }

    /**
     * 读取并转换 Knowledge Base 行。
     *
     * @param row 数据库行
     * @return 领域对象
     */
    private KnowledgeBase knowledgeBase(KnowledgePersistenceRows.BaseRow row) {
        return new KnowledgeBase(
            new KnowledgeBaseId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), row.knowledgeKey(), row.name(), row.description(),
            KnowledgeBaseStatus.valueOf(row.status()), row.version(), row.createdAt(),
            row.updatedAt());
    }

    /**
     * 读取并转换数据源行。
     *
     * @param row 数据库行
     * @return 领域对象
     */
    private DataSource dataSource(KnowledgePersistenceRows.DataSourceRow row) {
        return new DataSource(
            new DataSourceId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new KnowledgeBaseId(row.knowledgeBaseId()),
            DataSourceType.valueOf(row.sourceType()), row.name(), row.descriptorJson(),
            row.createdAt());
    }

    /**
     * 读取并转换文档行，同时加载同项目 ACL。
     *
     * @param row 数据库行
     * @return 领域对象
     */
    @SuppressWarnings("unchecked")
    private Document document(KnowledgePersistenceRows.DocumentRow row) {
        Map<String, Object> raw = jsonMapper.readValue(row.metadataJson(), Map.class);
        Map<String, String> metadata = new LinkedHashMap<>();
        raw.forEach((key, value) -> metadata.put(key, Objects.toString(value, "")));
        List<DocumentAcl> acl = mapper.listDocumentAcl(row.projectId(), row.id()).stream()
            .map(value -> new DocumentAcl(
                DocumentAcl.SubjectType.valueOf(value.subjectType()), value.subjectId().toString(),
                DocumentAcl.AccessLevel.valueOf(value.accessLevel())))
            .toList();
        return new Document(
            new DocumentId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new KnowledgeBaseId(row.knowledgeBaseId()),
            new DataSourceId(row.dataSourceId()), row.title(), metadata, acl,
            DocumentStatus.valueOf(row.status()), row.version(), row.createdAt(), row.updatedAt());
    }

    /**
     * 转换文档修订领域对象为数据库行。
     *
     * @param revision 领域对象
     * @param actor    创建主体
     * @return 数据库行
     */
    private static KnowledgePersistenceRows.DocumentRevisionRow documentRevisionRow(
        DocumentRevision revision, String actor) {
        return new KnowledgePersistenceRows.DocumentRevisionRow(
            revision.id().value(), revision.organizationId().value(), revision.projectId().value(),
            revision.knowledgeBaseId().value(), revision.documentId().value(),
            revision.revisionNumber(), revision.originalFileName(),
            revision.objectRef().uri().toASCIIString(),
            HexFormat.of().parseHex(revision.objectRef().checksum().hex()),
            revision.objectRef().size(), revision.objectRef().mediaType(), revision.createdAt(), actor);
    }

    /**
     * 读取并转换文档修订行。
     *
     * @param row 数据库行
     * @return 领域对象
     */
    private DocumentRevision documentRevision(
        KnowledgePersistenceRows.DocumentRevisionRow row) {
        return new DocumentRevision(
            new DocumentRevisionId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new KnowledgeBaseId(row.knowledgeBaseId()),
            new DocumentId(row.documentId()), row.revisionNumber(), row.originalFileName(),
            ObjectRef.of(row.objectUri(), checksum(row.contentHash()), row.contentSize(),
                row.contentType()),
            row.createdAt());
    }

    /**
     * 构造通用 Profile 数据库行。
     *
     * @param id             Profile UUID
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param key            稳定 Key
     * @param versionNumber  版本号
     * @param configJson     配置 JSON
     * @param credentialRef  可选 SecretRef
     * @param contentHash    内容摘要
     * @param status         发布状态
     * @param createdAt      创建时间
     * @param actor          创建主体
     * @return Profile 数据库行
     */
    private static KnowledgePersistenceRows.ProfileRow profileRow(
        UUID id,
        OrganizationId organizationId,
        ProjectId projectId,
        String key,
        long versionNumber,
        String configJson,
        String credentialRef,
        Checksum contentHash,
        KnowledgeProfileStatus status,
        java.time.Instant createdAt,
        String actor) {
        return new KnowledgePersistenceRows.ProfileRow(
            id, organizationId.value(), projectId.value(), key, versionNumber, configJson,
            credentialRef, HexFormat.of().parseHex(contentHash.hex()), status.name(), createdAt,
            actor);
    }

    /**
     * 在项目行锁保护下分配下一个 Profile 版本号。
     *
     * @param table     受控 Profile 表
     * @param projectId 项目标识
     * @param key       稳定 Key
     * @return 下一个版本号
     */
    private long nextProfileVersion(ProfileTable table, ProjectId projectId, String key) {
        mapper.lockProject(projectId.value())
            .orElseThrow(() -> new IllegalStateException("project lock target is missing"));
        return mapper.nextProfileVersion(table, projectId.value(), key);
    }

    /**
     * 转换 Revision 领域对象为数据库行。
     *
     * @param revision 领域对象
     * @param actor    创建或更新主体
     * @return 数据库行
     */
    private static KnowledgePersistenceRows.RevisionRow revisionRow(
        KnowledgeRevision revision, String actor) {
        return new KnowledgePersistenceRows.RevisionRow(
            revision.id().value(), revision.organizationId().value(), revision.projectId().value(),
            revision.knowledgeBaseId().value(), revision.revisionNumber(),
            revision.parserProfileId().value(), revision.chunkProfileId().value(),
            revision.embeddingProfileId().value(), revision.retrievalProfileId().value(),
            HexFormat.of().parseHex(revision.contentHash().hex()), revision.status().name(),
            revision.failureCode().isBlank() ? null : revision.failureCode(), revision.version(),
            revision.createdAt(), actor, revision.updatedAt(), actor);
    }

    /**
     * 读取并转换 Knowledge Revision 行及其有序文档绑定。
     *
     * @param row 数据库行
     * @return 领域对象
     */
    private KnowledgeRevision knowledgeRevision(KnowledgePersistenceRows.RevisionRow row) {
        List<DocumentRevisionId> documents = mapper.listKnowledgeRevisionDocuments(
            row.projectId(), row.id()).stream().map(DocumentRevisionId::new).toList();
        return new KnowledgeRevision(
            new KnowledgeRevisionId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new KnowledgeBaseId(row.knowledgeBaseId()),
            row.revisionNumber(), documents, new ParserProfileId(row.parserProfileId()),
            new ChunkProfileId(row.chunkProfileId()),
            new EmbeddingProfileId(row.embeddingProfileId()),
            new RetrievalProfileId(row.retrievalProfileId()), checksum(row.contentHash()),
            KnowledgeRevisionStatus.valueOf(row.status()),
            row.failureCode() == null ? "" : row.failureCode(), row.version(), row.createdAt(),
            row.updatedAt());
    }

    /**
     * 转换摄取请求数据库行。
     *
     * @param row 数据库行
     * @return 领域对象
     */
    private IngestionJobDescriptor ingestion(KnowledgePersistenceRows.IngestionRow row) {
        return new IngestionJobDescriptor(
            new IngestionRequestId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new KnowledgeRevisionId(row.knowledgeRevisionId()),
            row.idempotencyKey(), IngestionRequestStatus.valueOf(row.status()), row.requestedAt());
    }

    /**
     * 从原始数据库摘要构造规范 Checksum。
     *
     * @param hash SHA-256 原始 32 字节
     * @return 规范 Checksum
     */
    private static Checksum checksum(byte[] hash) {
        return new Checksum("sha256:" + HexFormat.of().formatHex(hash));
    }
}
