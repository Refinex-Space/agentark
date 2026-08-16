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

package space.refinex.agentark.knowledge.application;

import org.springframework.transaction.annotation.Transactional;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.storage.ObjectNamespace;
import space.refinex.agentark.foundation.storage.ObjectStore;
import space.refinex.agentark.foundation.storage.PutObjectCommand;
import space.refinex.agentark.foundation.web.CursorPage;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.kernel.ref.SecretRef;
import space.refinex.agentark.knowledge.application.port.KnowledgeAccessPort;
import space.refinex.agentark.knowledge.application.port.KnowledgeAuditPort;
import space.refinex.agentark.knowledge.application.port.KnowledgeRepository;
import space.refinex.agentark.knowledge.domain.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * 编排 Knowledge 项目授权、Object Store、不可变版本、状态机、审计和元数据持久化。
 *
 * @author refinex
 */
public class KnowledgeApplicationService {

    /**
     * 原始文档专用 Object Store 命名空间。
     */
    private static final ObjectNamespace DOCUMENT_NAMESPACE =
        new ObjectNamespace("knowledge-documents");

    /**
     * Knowledge 元数据仓储。
     */
    private final KnowledgeRepository repository;

    /**
     * Control 组合根授权 Port。
     */
    private final KnowledgeAccessPort accessPort;

    /**
     * 事务感知审计 Port。
     */
    private final KnowledgeAuditPort auditPort;

    /**
     * 可选 Object Store；未配置时上传显式失败。
     */
    private final Optional<ObjectStore> objectStore;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 创建 Knowledge 应用服务。
     *
     * @param repository  Knowledge 元数据仓储
     * @param accessPort  项目授权 Port
     * @param auditPort   审计 Port
     * @param objectStore 可选 Object Store
     * @param clock       UTC 时钟
     */
    public KnowledgeApplicationService(
        KnowledgeRepository repository,
        KnowledgeAccessPort accessPort,
        KnowledgeAuditPort auditPort,
        Optional<ObjectStore> objectStore,
        Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.accessPort = Objects.requireNonNull(accessPort, "accessPort must not be null");
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort must not be null");
        this.objectStore = Objects.requireNonNull(objectStore, "objectStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建项目内 Knowledge Base 稳定身份。
     *
     * @param principal   已认证主体
     * @param projectId   项目标识
     * @param key         稳定 Key
     * @param name        显示名称
     * @param description 用途说明
     * @return 新 Knowledge Base
     */
    @Transactional
    public KnowledgeBase createKnowledgeBase(
        AgentArkPrincipal principal,
        ProjectId projectId,
        String key,
        String name,
        String description) {
        KnowledgeProjectContext context = authorize(principal, projectId, KnowledgePermissions.MANAGE);
        Instant now = Instant.now(clock);
        KnowledgeBase knowledgeBase = new KnowledgeBase(
            KnowledgeBaseId.generate(),
            context.organizationId(),
            projectId,
            key,
            name,
            description,
            KnowledgeBaseStatus.ACTIVE,
            0,
            now,
            now);
        repository.insertKnowledgeBase(knowledgeBase, context.actorReference());
        audit(context, "knowledge.base.create", "knowledge_base", knowledgeBase.id().value().toString(), now);
        return knowledgeBase;
    }

    /**
     * 列出当前主体可访问项目内的 Knowledge Base。
     *
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @param cursor    可选不透明游标
     * @param limit     最大结果数
     * @return Knowledge Base 列表
     */
    @Transactional(readOnly = true)
    public CursorPage<KnowledgeBase> listKnowledgeBases(
        AgentArkPrincipal principal, ProjectId projectId, String cursor, int limit) {
        authorize(principal, projectId, KnowledgePermissions.READ);
        int pageSize = requireLimit(limit);
        List<KnowledgeBase> loaded = repository.listKnowledgeBases(
            projectId, KnowledgeCursorCodec.decode(cursor, KnowledgeBaseId::parse), pageSize + 1);
        return page(loaded, pageSize, KnowledgeBase::id);
    }

    /**
     * 创建不含连接凭据的数据源元数据。
     *
     * @param principal       已认证主体
     * @param projectId       项目标识
     * @param knowledgeBaseId Knowledge Base 标识
     * @param type            数据源类型
     * @param name            显示名称
     * @param descriptorJson  规范化非敏感 JSON
     * @return 新数据源
     */
    @Transactional
    public DataSource createDataSource(
        AgentArkPrincipal principal,
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        DataSourceType type,
        String name,
        String descriptorJson) {
        KnowledgeProjectContext context = authorize(principal, projectId, KnowledgePermissions.MANAGE);
        requireActiveBase(projectId, knowledgeBaseId);
        Instant now = Instant.now(clock);
        DataSource dataSource = new DataSource(
            DataSourceId.generate(),
            context.organizationId(),
            projectId,
            knowledgeBaseId,
            type,
            name,
            requireJson(descriptorJson),
            now);
        repository.insertDataSource(dataSource, context.actorReference());
        audit(context, "knowledge.source.create", "data_source", dataSource.id().value().toString(), now);
        return dataSource;
    }

    /**
     * 列出 Knowledge Base 的数据源元数据。
     *
     * @param principal       已认证主体
     * @param projectId       项目标识
     * @param knowledgeBaseId Knowledge Base 标识
     * @param cursor          可选不透明游标
     * @param limit           最大结果数
     * @return 数据源列表
     */
    @Transactional(readOnly = true)
    public CursorPage<DataSource> listDataSources(
        AgentArkPrincipal principal,
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        String cursor,
        int limit) {
        authorize(principal, projectId, KnowledgePermissions.READ);
        requireBase(projectId, knowledgeBaseId);
        int pageSize = requireLimit(limit);
        List<DataSource> loaded = repository.listDataSources(
            projectId, knowledgeBaseId,
            KnowledgeCursorCodec.decode(cursor, DataSourceId::parse), pageSize + 1);
        return page(loaded, pageSize, DataSource::id);
    }

    /**
     * 上传原始文件并原子创建 Document、项目默认 ACL 与首个 Document Revision。
     *
     * @param principal        已认证主体
     * @param projectId        项目标识
     * @param knowledgeBaseId  Knowledge Base 标识
     * @param dataSourceId     数据源标识
     * @param title            文档标题
     * @param originalFileName 原始文件名
     * @param metadata         非敏感元数据
     * @param content          文件流，调用后由 Object Store 关闭
     * @param size             声明字节数
     * @param contentType      具体媒体类型
     * @param expectedChecksum 可选调用方 SHA-256
     * @return 新文档修订
     * @throws IOException Object Store 读写或补偿删除失败时抛出
     */
    @Transactional
    public DocumentRevision uploadDocument(
        AgentArkPrincipal principal,
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        DataSourceId dataSourceId,
        String title,
        String originalFileName,
        Map<String, String> metadata,
        InputStream content,
        long size,
        String contentType,
        Optional<Checksum> expectedChecksum) throws IOException {
        KnowledgeProjectContext context = authorize(principal, projectId, KnowledgePermissions.MANAGE);
        requireActiveBase(projectId, knowledgeBaseId);
        DataSource source = repository.findDataSource(projectId, dataSourceId)
            .filter(value -> value.knowledgeBaseId().equals(knowledgeBaseId))
            .orElseThrow(() -> new KnowledgeNotFoundException("data source is not visible"));
        if (source.type() != DataSourceType.UPLOAD) {
            throw new KnowledgeConflictException("only UPLOAD data sources accept direct files");
        }
        ObjectStore store = objectStore.orElseThrow(
            () -> new KnowledgeConflictException("object store is not configured"));
        ObjectRef objectRef = store.put(new PutObjectCommand(
            DOCUMENT_NAMESPACE, content, size, contentType, expectedChecksum));
        Instant now = Instant.now(clock);
        DocumentId documentId = DocumentId.generate();
        Document document = new Document(
            documentId,
            context.organizationId(),
            projectId,
            knowledgeBaseId,
            dataSourceId,
            title,
            metadata,
            List.of(new DocumentAcl(
                DocumentAcl.SubjectType.PROJECT,
                projectId.value().toString(),
                DocumentAcl.AccessLevel.MANAGE)),
            DocumentStatus.ACTIVE,
            0,
            now,
            now);
        DocumentRevision revision = new DocumentRevision(
            DocumentRevisionId.generate(),
            context.organizationId(),
            projectId,
            knowledgeBaseId,
            documentId,
            1,
            originalFileName,
            objectRef,
            now);
        try {
            repository.insertDocument(document, revision, context.actorReference());
        } catch (RuntimeException exception) {
            compensateUpload(store, objectRef, exception);
            throw exception;
        }
        audit(context, "knowledge.document.upload", "document_revision", revision.id().value().toString(), now);
        return revision;
    }

    /**
     * 列出 Knowledge Base 的文档稳定身份和 ACL 元数据。
     *
     * @param principal       已认证主体
     * @param projectId       项目标识
     * @param knowledgeBaseId Knowledge Base 标识
     * @param cursor          可选不透明游标
     * @param limit           最大结果数
     * @return 文档列表
     */
    @Transactional(readOnly = true)
    public CursorPage<Document> listDocuments(
        AgentArkPrincipal principal,
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        String cursor,
        int limit) {
        authorize(principal, projectId, KnowledgePermissions.READ);
        requireBase(projectId, knowledgeBaseId);
        int pageSize = requireLimit(limit);
        List<Document> loaded = repository.listDocuments(
            projectId, knowledgeBaseId,
            KnowledgeCursorCodec.decode(cursor, DocumentId::parse), pageSize + 1);
        return page(loaded, pageSize, Document::id);
    }

    /**
     * 创建不可变 Parser Profile 版本。
     *
     * @param principal  已认证主体
     * @param projectId  项目标识
     * @param key        稳定 Key
     * @param configJson 规范化配置 JSON
     * @param status     发布状态
     * @return 新 Parser Profile
     */
    @Transactional
    public ParserProfile createParserProfile(
        AgentArkPrincipal principal,
        ProjectId projectId,
        String key,
        String configJson,
        KnowledgeProfileStatus status) {
        KnowledgeProjectContext context = authorize(principal, projectId, KnowledgePermissions.MANAGE);
        String config = requireJson(configJson);
        ParserProfile profile = new ParserProfile(
            ParserProfileId.generate(),
            context.organizationId(),
            projectId,
            key,
            repository.nextParserProfileVersion(projectId, key),
            config,
            Checksum.sha256(config),
            status,
            Instant.now(clock));
        repository.insertParserProfile(profile, context.actorReference());
        audit(context, "knowledge.profile.create", "parser_profile", profile.id().value().toString(), profile.createdAt());
        return profile;
    }

    /**
     * 创建不可变 Chunk Profile 版本。
     *
     * @param principal  已认证主体
     * @param projectId  项目标识
     * @param key        稳定 Key
     * @param configJson 规范化配置 JSON
     * @param status     发布状态
     * @return 新 Chunk Profile
     */
    @Transactional
    public ChunkProfile createChunkProfile(
        AgentArkPrincipal principal,
        ProjectId projectId,
        String key,
        String configJson,
        KnowledgeProfileStatus status) {
        KnowledgeProjectContext context = authorize(principal, projectId, KnowledgePermissions.MANAGE);
        String config = requireJson(configJson);
        ChunkProfile profile = new ChunkProfile(
            ChunkProfileId.generate(),
            context.organizationId(),
            projectId,
            key,
            repository.nextChunkProfileVersion(projectId, key),
            config,
            Checksum.sha256(config),
            status,
            Instant.now(clock));
        repository.insertChunkProfile(profile, context.actorReference());
        audit(context, "knowledge.profile.create", "chunk_profile", profile.id().value().toString(), profile.createdAt());
        return profile;
    }

    /**
     * 创建不可变 Embedding Profile 版本，只保存可选 SecretRef。
     *
     * @param principal     已认证主体
     * @param projectId     项目标识
     * @param key           稳定 Key
     * @param configJson    规范化配置 JSON
     * @param credentialRef 可选凭据引用
     * @param status        发布状态
     * @return 新 Embedding Profile
     */
    @Transactional
    public EmbeddingProfile createEmbeddingProfile(
        AgentArkPrincipal principal,
        ProjectId projectId,
        String key,
        String configJson,
        Optional<SecretRef> credentialRef,
        KnowledgeProfileStatus status) {
        KnowledgeProjectContext context = authorize(principal, projectId, KnowledgePermissions.MANAGE);
        String config = requireJson(configJson);
        Optional<SecretRef> checkedRef = Objects.requireNonNull(
            credentialRef, "credentialRef must not be null");
        String hashInput = config + "\n" + checkedRef.map(SecretRef::toString).orElse("");
        EmbeddingProfile profile = new EmbeddingProfile(
            EmbeddingProfileId.generate(),
            context.organizationId(),
            projectId,
            key,
            repository.nextEmbeddingProfileVersion(projectId, key),
            config,
            checkedRef,
            Checksum.sha256(hashInput),
            status,
            Instant.now(clock));
        repository.insertEmbeddingProfile(profile, context.actorReference());
        audit(context, "knowledge.profile.create", "embedding_profile", profile.id().value().toString(), profile.createdAt());
        return profile;
    }

    /**
     * 创建不可变 Retrieval Profile 版本。
     *
     * @param principal  已认证主体
     * @param projectId  项目标识
     * @param key        稳定 Key
     * @param configJson 规范化配置 JSON
     * @param status     发布状态
     * @return 新 Retrieval Profile
     */
    @Transactional
    public RetrievalProfile createRetrievalProfile(
        AgentArkPrincipal principal,
        ProjectId projectId,
        String key,
        String configJson,
        KnowledgeProfileStatus status) {
        KnowledgeProjectContext context = authorize(principal, projectId, KnowledgePermissions.MANAGE);
        String config = requireJson(configJson);
        RetrievalProfile profile = new RetrievalProfile(
            RetrievalProfileId.generate(),
            context.organizationId(),
            projectId,
            key,
            repository.nextRetrievalProfileVersion(projectId, key),
            config,
            Checksum.sha256(config),
            status,
            Instant.now(clock));
        repository.insertRetrievalProfile(profile, context.actorReference());
        audit(context, "knowledge.profile.create", "retrieval_profile", profile.id().value().toString(), profile.createdAt());
        return profile;
    }

    /**
     * 绑定文档修订与四类已发布 Profile，创建不可变 Knowledge Revision。
     *
     * @param principal           已认证主体
     * @param projectId           项目标识
     * @param knowledgeBaseId     Knowledge Base 标识
     * @param documentRevisionIds 文档修订标识
     * @param parserProfileId     Parser Profile 标识
     * @param chunkProfileId      Chunk Profile 标识
     * @param embeddingProfileId  Embedding Profile 标识
     * @param retrievalProfileId  Retrieval Profile 标识
     * @return CREATED Knowledge Revision
     */
    @Transactional
    public KnowledgeRevision createKnowledgeRevision(
        AgentArkPrincipal principal,
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        List<DocumentRevisionId> documentRevisionIds,
        ParserProfileId parserProfileId,
        ChunkProfileId chunkProfileId,
        EmbeddingProfileId embeddingProfileId,
        RetrievalProfileId retrievalProfileId) {
        KnowledgeProjectContext context = authorize(principal, projectId, KnowledgePermissions.MANAGE);
        requireActiveBase(projectId, knowledgeBaseId);
        List<DocumentRevisionId> documents = List.copyOf(documentRevisionIds);
        if (documents.isEmpty()
            || documents.stream().map(id -> repository.findDocumentRevision(projectId, id)
                .filter(value -> value.knowledgeBaseId().equals(knowledgeBaseId))
                .orElseThrow(() -> new KnowledgeNotFoundException("document revision is not visible")))
            .count() != documents.size()) {
            throw new KnowledgeNotFoundException("document revision is not visible");
        }
        requirePublished(repository.findParserProfile(projectId, parserProfileId)
            .orElseThrow(() -> new KnowledgeNotFoundException("parser profile is not visible"))
            .status());
        requirePublished(repository.findChunkProfile(projectId, chunkProfileId)
            .orElseThrow(() -> new KnowledgeNotFoundException("chunk profile is not visible"))
            .status());
        requirePublished(repository.findEmbeddingProfile(projectId, embeddingProfileId)
            .orElseThrow(() -> new KnowledgeNotFoundException("embedding profile is not visible"))
            .status());
        requirePublished(repository.findRetrievalProfile(projectId, retrievalProfileId)
            .orElseThrow(() -> new KnowledgeNotFoundException("retrieval profile is not visible"))
            .status());
        Instant now = Instant.now(clock);
        long number = repository.nextKnowledgeRevisionNumber(projectId, knowledgeBaseId);
        KnowledgeRevision revision = new KnowledgeRevision(
            KnowledgeRevisionId.generate(),
            context.organizationId(),
            projectId,
            knowledgeBaseId,
            number,
            documents,
            parserProfileId,
            chunkProfileId,
            embeddingProfileId,
            retrievalProfileId,
            revisionHash(documents, parserProfileId, chunkProfileId, embeddingProfileId,
                retrievalProfileId),
            KnowledgeRevisionStatus.CREATED,
            "",
            0,
            now,
            now);
        repository.insertKnowledgeRevision(revision, context.actorReference());
        audit(context, "knowledge.revision.create", "knowledge_revision", revision.id().value().toString(), now);
        return revision;
    }

    /**
     * 列出 Knowledge Base 的不可变 Revision。
     *
     * @param principal       已认证主体
     * @param projectId       项目标识
     * @param knowledgeBaseId Knowledge Base 标识
     * @param cursor          可选不透明游标
     * @param limit           最大结果数
     * @return Revision 列表
     */
    @Transactional(readOnly = true)
    public CursorPage<KnowledgeRevision> listKnowledgeRevisions(
        AgentArkPrincipal principal,
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        String cursor,
        int limit) {
        authorize(principal, projectId, KnowledgePermissions.READ);
        requireBase(projectId, knowledgeBaseId);
        int pageSize = requireLimit(limit);
        List<KnowledgeRevision> loaded = repository.listKnowledgeRevisions(
            projectId, knowledgeBaseId,
            KnowledgeCursorCodec.decode(cursor, KnowledgeRevisionId::parse), pageSize + 1);
        return page(loaded, pageSize, KnowledgeRevision::id);
    }

    /**
     * 幂等描述摄取请求并把 CREATED 或 FAILED Revision 推进到 INGESTING。
     *
     * @param principal      已认证主体
     * @param projectId      项目标识
     * @param revisionId     Knowledge Revision 标识
     * @param idempotencyKey 项目内幂等键
     * @return 新建或既有摄取请求描述
     */
    @Transactional
    public IngestionJobDescriptor requestIngestion(
        AgentArkPrincipal principal,
        ProjectId projectId,
        KnowledgeRevisionId revisionId,
        String idempotencyKey) {
        KnowledgeProjectContext context = authorize(principal, projectId, KnowledgePermissions.INGEST);
        Optional<IngestionJobDescriptor> existing = repository.findIngestionRequest(
            projectId, idempotencyKey);
        if (existing.isPresent()) {
            IngestionJobDescriptor descriptor = existing.orElseThrow();
            if (!descriptor.knowledgeRevisionId().equals(revisionId)) {
                throw new KnowledgeConflictException("idempotency key belongs to another revision");
            }
            return descriptor;
        }
        KnowledgeRevision revision = requireRevision(projectId, revisionId);
        KnowledgeRevision changed = transition(
            revision, KnowledgeRevisionStatus.INGESTING, "", context.actorReference());
        Instant now = Instant.now(clock);
        IngestionJobDescriptor descriptor = new IngestionJobDescriptor(
            IngestionRequestId.generate(),
            context.organizationId(),
            projectId,
            revisionId,
            idempotencyKey,
            IngestionRequestStatus.DESCRIBED,
            now);
        IngestionJobDescriptor registered = repository.insertOrFindIngestionRequest(
            descriptor, context.actorReference());
        audit(context, "knowledge.ingestion.describe", "ingestion_request", registered.id().value().toString(), now);
        return registered;
    }

    /**
     * 在执行器或管理流程完成前置检查后推进 Revision 状态。
     *
     * @param principal   已认证主体
     * @param projectId   项目标识
     * @param revisionId  Knowledge Revision 标识
     * @param target      目标状态
     * @param failureCode FAILED 状态失败代码
     * @return 更新后的 Revision
     */
    @Transactional
    public KnowledgeRevision transitionRevision(
        AgentArkPrincipal principal,
        ProjectId projectId,
        KnowledgeRevisionId revisionId,
        KnowledgeRevisionStatus target,
        String failureCode) {
        KnowledgeProjectContext context = authorize(principal, projectId, KnowledgePermissions.MANAGE);
        KnowledgeRevision changed = transition(
            requireRevision(projectId, revisionId), target, failureCode, context.actorReference());
        audit(context, "knowledge.revision.transition", "knowledge_revision", changed.id().value().toString(),
            changed.updatedAt());
        return changed;
    }

    /**
     * 执行一次状态机与乐观锁保护的 Revision 更新。
     *
     * @param current     当前 Revision
     * @param target      目标状态
     * @param failureCode 失败代码
     * @param actor       更新主体稳定引用
     * @return 更新后的 Revision
     */
    private KnowledgeRevision transition(
        KnowledgeRevision current,
        KnowledgeRevisionStatus target,
        String failureCode,
        String actor) {
        KnowledgeRevision changed;
        try {
            changed = current.transitionTo(target, failureCode, Instant.now(clock));
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new KnowledgeConflictException("knowledge revision transition is not allowed");
        }
        if (repository.updateKnowledgeRevisionState(changed, current.version(), actor) != 1) {
            throw new KnowledgeConflictException("knowledge revision version changed concurrently");
        }
        return changed;
    }

    /**
     * 校验并返回活动 Knowledge Base。
     *
     * @param projectId       项目标识
     * @param knowledgeBaseId Knowledge Base 标识
     * @return 活动 Knowledge Base
     */
    private KnowledgeBase requireActiveBase(ProjectId projectId, KnowledgeBaseId knowledgeBaseId) {
        KnowledgeBase knowledgeBase = requireBase(projectId, knowledgeBaseId);
        if (knowledgeBase.status() != KnowledgeBaseStatus.ACTIVE) {
            throw new KnowledgeConflictException("knowledge base is archived");
        }
        return knowledgeBase;
    }

    /**
     * 按显式项目读取 Knowledge Base。
     *
     * @param projectId       项目标识
     * @param knowledgeBaseId Knowledge Base 标识
     * @return 同项目 Knowledge Base
     */
    private KnowledgeBase requireBase(ProjectId projectId, KnowledgeBaseId knowledgeBaseId) {
        return repository.findKnowledgeBase(projectId, knowledgeBaseId)
            .orElseThrow(() -> new KnowledgeNotFoundException("knowledge base is not visible"));
    }

    /**
     * 按显式项目读取 Knowledge Revision。
     *
     * @param projectId  项目标识
     * @param revisionId Revision 标识
     * @return 同项目 Revision
     */
    private KnowledgeRevision requireRevision(
        ProjectId projectId, KnowledgeRevisionId revisionId) {
        return repository.findKnowledgeRevision(projectId, revisionId)
            .orElseThrow(() -> new KnowledgeNotFoundException("knowledge revision is not visible"));
    }

    /**
     * 校验 Profile 已发布。
     *
     * @param status Profile 发布状态
     */
    private static void requirePublished(KnowledgeProfileStatus status) {
        if (status != KnowledgeProfileStatus.PUBLISHED) {
            throw new KnowledgeConflictException("knowledge profile is not PUBLISHED");
        }
    }

    /**
     * 计算文档和四类 Profile 绑定的稳定摘要。
     *
     * @param documents 文档修订标识
     * @param parser    Parser Profile 标识
     * @param chunk     Chunk Profile 标识
     * @param embedding Embedding Profile 标识
     * @param retrieval Retrieval Profile 标识
     * @return SHA-256 摘要
     */
    private static Checksum revisionHash(
        List<DocumentRevisionId> documents,
        ParserProfileId parser,
        ChunkProfileId chunk,
        EmbeddingProfileId embedding,
        RetrievalProfileId retrieval) {
        String documentPart = documents.stream()
            .map(value -> value.value().toString())
            .sorted(Comparator.naturalOrder())
            .reduce((left, right) -> left + "," + right)
            .orElseThrow();
        return Checksum.sha256(String.join("\n", documentPart, parser.value().toString(),
            chunk.value().toString(), embedding.value().toString(), retrieval.value().toString()));
    }

    /**
     * 要求配置文本至少是 JSON 对象或数组形态。
     *
     * @param value 待校验规范化 JSON
     * @return 去除首尾空白后的 JSON
     */
    private static String requireJson(String value) {
        if (value == null || value.isBlank() || value.length() > 65535) {
            throw new IllegalArgumentException("json configuration has invalid length");
        }
        String normalized = value.strip();
        if (!((normalized.startsWith("{") && normalized.endsWith("}"))
            || (normalized.startsWith("[") && normalized.endsWith("]")))) {
            throw new IllegalArgumentException("configuration must be a JSON object or array");
        }
        return normalized;
    }

    /**
     * 校验列表读取上限。
     *
     * @param limit 调用方上限
     * @return 一到一百之间的上限
     */
    private static int requireLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return limit;
    }

    /**
     * 将多取一条的稳定标识结果折叠为公共 Cursor Page。
     *
     * @param loaded      仓储读取结果
     * @param pageSize    请求页大小
     * @param idExtractor 末项稳定标识读取器
     * @param <T>         资源类型
     * @return 带可选下一页游标的结果页
     */
    private static <T> CursorPage<T> page(
        List<T> loaded,
        int pageSize,
        java.util.function.Function<T, ? extends space.refinex.agentark.kernel.id.StrongId>
            idExtractor) {
        boolean hasMore = loaded.size() > pageSize;
        List<T> items = loaded.stream().limit(pageSize).toList();
        Optional<String> nextCursor = hasMore
            ? Optional.of(KnowledgeCursorCodec.encode(idExtractor.apply(items.get(items.size() - 1))))
            : Optional.empty();
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    /**
     * 通过组合根 Port 完成项目授权。
     *
     * @param principal  已认证主体
     * @param projectId  项目标识
     * @param permission 权限代码
     * @return 可信项目上下文
     */
    private KnowledgeProjectContext authorize(
        AgentArkPrincipal principal, ProjectId projectId, String permission) {
        return accessPort.requireProjectPermission(principal, projectId, permission);
    }

    /**
     * 写入事务提交后审计事实。
     *
     * @param context      可信项目上下文
     * @param action       操作代码
     * @param resourceType 资源类型
     * @param resourceId   资源标识
     * @param occurredAt   发生时间
     */
    private void audit(
        KnowledgeProjectContext context,
        String action,
        String resourceType,
        String resourceId,
        Instant occurredAt) {
        auditPort.afterCommit(new KnowledgeAuditRecord(
            action,
            context.actorReference(),
            resourceType,
            resourceId,
            context.organizationId(),
            context.projectId(),
            occurredAt));
    }

    /**
     * 在数据库写入失败时删除已上传对象，并把补偿失败附加到原异常。
     *
     * @param store     对象存储
     * @param objectRef 已上传引用
     * @param original  原数据库异常
     */
    private static void compensateUpload(
        ObjectStore store, ObjectRef objectRef, RuntimeException original) {
        try {
            store.delete(objectRef);
        } catch (IOException compensationFailure) {
            original.addSuppressed(compensationFailure);
        }
    }
}
