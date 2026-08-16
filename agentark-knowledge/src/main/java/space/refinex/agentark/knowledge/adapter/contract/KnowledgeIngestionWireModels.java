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

package space.refinex.agentark.knowledge.adapter.contract;

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.kernel.ref.SecretRef;
import space.refinex.agentark.knowledge.application.IngestionModels.*;
import space.refinex.agentark.knowledge.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 集中定义 Scheduler 与 Control 之间显式、语言中立且不泄漏领域对象的摄取 Wire DTO。
 *
 * @author refinex
 */
public final class KnowledgeIngestionWireModels {

    /**
     * 禁止实例化 Wire DTO 容器。
     */
    private KnowledgeIngestionWireModels() {
    }

    /**
     * @param uri       不含授权参数的对象 URI
     * @param checksum  规范 SHA-256
     * @param size      对象字节数
     * @param mediaType 具体媒体类型
     * @author refinex
     */
    public record ObjectRefView(String uri, String checksum, long size, String mediaType) {

        /**
         * 将 Kernel 对象引用转换为语言中立视图。
         *
         * @param source Kernel 对象引用
         * @return 字符串化对象引用
         */
        public static ObjectRefView from(ObjectRef source) {
            return new ObjectRefView(
                source.uri().toASCIIString(), source.checksum().value(), source.size(),
                source.mediaType());
        }

        /**
         * 将 Wire 视图还原为经过 Kernel 强校验的对象引用。
         *
         * @return Kernel 对象引用
         */
        public ObjectRef toDomain() {
            return ObjectRef.of(uri, new Checksum(checksum), size, mediaType);
        }
    }

    /**
     * @param id                  Knowledge Revision UUIDv7 标识
     * @param organizationId      组织 UUIDv7
     * @param projectId           项目 UUIDv7
     * @param knowledgeBaseId     Knowledge Base UUIDv7 标识
     * @param revisionNumber      Knowledge Base 内版本号
     * @param documentRevisionIds 固定文档修订 UUIDv7 集合
     * @param parserProfileId     Parser Profile UUIDv7 标识
     * @param chunkProfileId      Chunk Profile UUIDv7 标识
     * @param embeddingProfileId  Embedding Profile UUIDv7 标识
     * @param retrievalProfileId  Retrieval Profile UUIDv7 标识
     * @param contentHash         Revision 内容 SHA-256
     * @param status              完整状态枚举
     * @param failureCode         失败代码，非失败状态为空
     * @param version             乐观锁版本
     * @param createdAt           创建时间
     * @param updatedAt           更新时间
     * @author refinex
     */
    public record RevisionView(
        String id,
        String organizationId,
        String projectId,
        String knowledgeBaseId,
        long revisionNumber,
        List<String> documentRevisionIds,
        String parserProfileId,
        String chunkProfileId,
        String embeddingProfileId,
        String retrievalProfileId,
        String contentHash,
        String status,
        String failureCode,
        long version,
        Instant createdAt,
        Instant updatedAt) {

        /**
         * 将固定 Knowledge Revision 映射为 Wire 视图。
         *
         * @param source Knowledge Revision
         * @return Wire Revision
         */
        public static RevisionView from(KnowledgeRevision source) {
            return new RevisionView(
                source.id().asString(), source.organizationId().asString(),
                source.projectId().asString(), source.knowledgeBaseId().asString(),
                source.revisionNumber(), source.documentRevisionIds().stream()
                .map(DocumentRevisionId::asString).toList(),
                source.parserProfileId().asString(), source.chunkProfileId().asString(),
                source.embeddingProfileId().asString(), source.retrievalProfileId().asString(),
                source.contentHash().value(), source.status().name(), source.failureCode(),
                source.version(), source.createdAt(), source.updatedAt());
        }

        /**
         * 将 Wire Revision 还原为领域对象并重新执行状态不变量校验。
         *
         * @return Knowledge Revision
         */
        public KnowledgeRevision toDomain() {
            return new KnowledgeRevision(
                KnowledgeRevisionId.parse(id), OrganizationId.parse(organizationId),
                ProjectId.parse(projectId), KnowledgeBaseId.parse(knowledgeBaseId),
                revisionNumber, documentRevisionIds.stream().map(DocumentRevisionId::parse).toList(),
                ParserProfileId.parse(parserProfileId), ChunkProfileId.parse(chunkProfileId),
                EmbeddingProfileId.parse(embeddingProfileId),
                RetrievalProfileId.parse(retrievalProfileId), new Checksum(contentHash),
                KnowledgeRevisionStatus.valueOf(status), failureCode, version, createdAt, updatedAt);
        }
    }

    /**
     * @param id               文档修订 UUIDv7
     * @param organizationId   组织 UUIDv7
     * @param projectId        项目 UUIDv7
     * @param knowledgeBaseId  Knowledge Base UUIDv7 标识
     * @param documentId       文档 UUIDv7
     * @param revisionNumber   文档版本号
     * @param originalFileName 原文件名
     * @param objectRef        不可变原文引用
     * @param createdAt        创建时间
     * @author refinex
     */
    public record DocumentRevisionView(
        String id,
        String organizationId,
        String projectId,
        String knowledgeBaseId,
        String documentId,
        long revisionNumber,
        String originalFileName,
        ObjectRefView objectRef,
        Instant createdAt) {

        /**
         * 将不可变文档修订转换为 Wire 视图。
         *
         * @param source 文档修订
         * @return Wire 文档修订
         */
        public static DocumentRevisionView from(DocumentRevision source) {
            return new DocumentRevisionView(
                source.id().asString(), source.organizationId().asString(),
                source.projectId().asString(), source.knowledgeBaseId().asString(),
                source.documentId().asString(), source.revisionNumber(),
                source.originalFileName(), ObjectRefView.from(source.objectRef()), source.createdAt());
        }

        /**
         * 将 Wire 文档修订还原为领域对象。
         *
         * @return 不可变文档修订
         */
        public DocumentRevision toDomain() {
            return new DocumentRevision(
                DocumentRevisionId.parse(id), OrganizationId.parse(organizationId),
                ProjectId.parse(projectId), KnowledgeBaseId.parse(knowledgeBaseId),
                DocumentId.parse(documentId), revisionNumber, originalFileName,
                objectRef.toDomain(), createdAt);
        }
    }

    /**
     * @param id                  Profile UUIDv7 标识
     * @param organizationId      组织 UUIDv7
     * @param projectId           项目 UUIDv7
     * @param key                 项目内稳定 Key
     * @param versionNumber       Profile 版本号
     * @param configJson          规范配置 JSON 字符串
     * @param credentialSecretRef 可选 SecretRef；仅 Embedding Profile 使用
     * @param contentHash         Profile 内容 SHA-256
     * @param status              完整发布状态枚举
     * @param createdAt           创建时间
     * @author refinex
     */
    public record ProfileView(
        String id,
        String organizationId,
        String projectId,
        String key,
        long versionNumber,
        String configJson,
        String credentialSecretRef,
        String contentHash,
        String status,
        Instant createdAt) {

        /**
         * 将 Parser Profile 转换为 Wire 视图。
         */
        public static ProfileView from(ParserProfile source) {
            return profile(
                source.id().asString(), source.organizationId(), source.projectId(), source.key(),
                source.versionNumber(), source.configJson(), null, source.contentHash(),
                source.status(), source.createdAt());
        }

        /**
         * 将 Chunk Profile 转换为 Wire 视图。
         */
        public static ProfileView from(ChunkProfile source) {
            return profile(
                source.id().asString(), source.organizationId(), source.projectId(), source.key(),
                source.versionNumber(), source.configJson(), null, source.contentHash(),
                source.status(), source.createdAt());
        }

        /**
         * 将 Embedding Profile 转换为 Wire 视图，仅传递 SecretRef。
         */
        public static ProfileView from(EmbeddingProfile source) {
            return profile(
                source.id().asString(), source.organizationId(), source.projectId(), source.key(),
                source.versionNumber(), source.configJson(),
                source.credentialRef().map(SecretRef::asString).orElse(null),
                source.contentHash(), source.status(), source.createdAt());
        }

        /**
         * 将 Retrieval Profile 转换为 Wire 视图。
         */
        public static ProfileView from(RetrievalProfile source) {
            return profile(
                source.id().asString(), source.organizationId(), source.projectId(), source.key(),
                source.versionNumber(), source.configJson(), null, source.contentHash(),
                source.status(), source.createdAt());
        }

        /**
         * 使用字符串化公共字段创建 Profile Wire 视图。
         *
         * @param id                  Profile UUIDv7
         * @param organizationId      组织标识
         * @param projectId           项目标识
         * @param key                 稳定 Key
         * @param versionNumber       版本号
         * @param configJson          配置 JSON
         * @param credentialSecretRef 可选 SecretRef
         * @param contentHash         内容摘要
         * @param status              Profile 状态
         * @param createdAt           创建时间
         * @return Profile Wire 视图
         */
        private static ProfileView profile(
            String id,
            OrganizationId organizationId,
            ProjectId projectId,
            String key,
            long versionNumber,
            String configJson,
            String credentialSecretRef,
            Checksum contentHash,
            KnowledgeProfileStatus status,
            Instant createdAt) {
            return new ProfileView(
                id, organizationId.asString(), projectId.asString(), key, versionNumber,
                configJson, credentialSecretRef, contentHash.value(), status.name(), createdAt);
        }

        /**
         * 将 Wire Profile 还原为 Parser Profile。
         */
        public ParserProfile toParserProfile() {
            return new ParserProfile(
                ParserProfileId.parse(id), OrganizationId.parse(organizationId),
                ProjectId.parse(projectId), key, versionNumber, configJson,
                new Checksum(contentHash), KnowledgeProfileStatus.valueOf(status), createdAt);
        }

        /**
         * 将 Wire Profile 还原为 Chunk Profile。
         */
        public ChunkProfile toChunkProfile() {
            return new ChunkProfile(
                ChunkProfileId.parse(id), OrganizationId.parse(organizationId),
                ProjectId.parse(projectId), key, versionNumber, configJson,
                new Checksum(contentHash), KnowledgeProfileStatus.valueOf(status), createdAt);
        }

        /**
         * 将 Wire Profile 还原为 Embedding Profile。
         */
        public EmbeddingProfile toEmbeddingProfile() {
            return new EmbeddingProfile(
                EmbeddingProfileId.parse(id), OrganizationId.parse(organizationId),
                ProjectId.parse(projectId), key, versionNumber, configJson,
                Optional.ofNullable(credentialSecretRef).map(SecretRef::parse),
                new Checksum(contentHash), KnowledgeProfileStatus.valueOf(status), createdAt);
        }

        /**
         * 将 Wire Profile 还原为 Retrieval Profile。
         */
        public RetrievalProfile toRetrievalProfile() {
            return new RetrievalProfile(
                RetrievalProfileId.parse(id), OrganizationId.parse(organizationId),
                ProjectId.parse(projectId), key, versionNumber, configJson,
                new Checksum(contentHash), KnowledgeProfileStatus.valueOf(status), createdAt);
        }
    }

    /**
     * @param requestId        摄取请求 UUIDv7
     * @param organizationId   组织 UUIDv7
     * @param projectId        项目 UUIDv7
     * @param revision         固定 Knowledge Revision
     * @param documents        固定文档修订集合
     * @param parserProfile    固定 Parser Profile
     * @param chunkProfile     固定 Chunk Profile
     * @param embeddingProfile 固定 Embedding Profile
     * @param retrievalProfile 固定 Retrieval Profile
     * @author refinex
     */
    public record IngestionPlanView(
        String requestId,
        String organizationId,
        String projectId,
        RevisionView revision,
        List<DocumentRevisionView> documents,
        ProfileView parserProfile,
        ProfileView chunkProfile,
        ProfileView embeddingProfile,
        ProfileView retrievalProfile) {

        /**
         * 将完整摄取计划转换为 Internal API 响应。
         *
         * @param source 固定摄取计划
         * @return 语言中立计划响应
         */
        public static IngestionPlanView from(IngestionPlan source) {
            return new IngestionPlanView(
                source.requestId().asString(), source.organizationId().asString(),
                source.projectId().asString(), RevisionView.from(source.revision()),
                source.documents().stream().map(DocumentRevisionView::from).toList(),
                ProfileView.from(source.parserProfile()), ProfileView.from(source.chunkProfile()),
                ProfileView.from(source.embeddingProfile()),
                ProfileView.from(source.retrievalProfile()));
        }

        /**
         * 将 Internal API 响应还原为强校验摄取计划。
         *
         * @return 固定摄取计划
         */
        public IngestionPlan toDomain() {
            return new IngestionPlan(
                IngestionRequestId.parse(requestId), OrganizationId.parse(organizationId),
                ProjectId.parse(projectId), revision.toDomain(),
                documents.stream().map(DocumentRevisionView::toDomain).toList(),
                parserProfile.toParserProfile(), chunkProfile.toChunkProfile(),
                embeddingProfile.toEmbeddingProfile(), retrievalProfile.toRetrievalProfile());
        }
    }

    /**
     * @param kind 对象制品类别
     * @param ref  不可变对象引用
     * @author refinex
     */
    public record ArtifactView(String kind, ObjectRefView ref) {

        /**
         * 将制品引用转换为 Wire 视图。
         */
        public static ArtifactView from(ArtifactReference source) {
            return new ArtifactView(source.kind().name(), ObjectRefView.from(source.ref()));
        }

        /**
         * 将 Wire 制品引用还原为领域值。
         */
        public ArtifactReference toDomain() {
            return new ArtifactReference(ArtifactKind.valueOf(kind), ref.toDomain());
        }
    }

    /**
     * @param resultId       结果 UUIDv7
     * @param requestId      摄取请求 UUIDv7
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param revisionId     Knowledge Revision UUIDv7 标识
     * @param schedulerJobId Scheduler Job UUIDv7 标识
     * @param attemptId      Attempt UUIDv7 标识
     * @param idempotencyKey Internal Command 幂等键
     * @param documentCount  已处理文档数
     * @param chunkCount     已验证 Chunk 数
     * @param checksum       Chunk 与向量清单 SHA-256
     * @param artifacts      不可变制品引用
     * @param status         完整结果状态枚举：SUCCEEDED、FAILED
     * @param failureCode    失败代码，成功时为空
     * @param completedAt    Worker 完成时间
     * @author refinex
     */
    public record IngestionResultView(
        String resultId,
        String requestId,
        String organizationId,
        String projectId,
        String revisionId,
        String schedulerJobId,
        String attemptId,
        String idempotencyKey,
        int documentCount,
        int chunkCount,
        String checksum,
        List<ArtifactView> artifacts,
        String status,
        String failureCode,
        Instant completedAt) {

        /**
         * 将摄取结果转换为幂等 Internal Command 或响应。
         *
         * @param source 摄取结果
         * @return 语言中立结果视图
         */
        public static IngestionResultView from(IngestionResult source) {
            return new IngestionResultView(
                source.resultId().toString(), source.requestId().asString(),
                source.organizationId().asString(), source.projectId().asString(),
                source.revisionId().asString(), source.schedulerJobId().asString(),
                source.attemptId().toString(), source.idempotencyKey(), source.documentCount(),
                source.chunkCount(), source.checksum().value(),
                source.artifacts().stream().map(ArtifactView::from).toList(),
                source.status().name(), source.failureCode(), source.completedAt());
        }

        /**
         * 将结果 Wire DTO 还原为带完整不变量校验的领域值。
         *
         * @return 摄取结果
         */
        public IngestionResult toDomain() {
            return new IngestionResult(
                UUID.fromString(resultId), IngestionRequestId.parse(requestId),
                OrganizationId.parse(organizationId), ProjectId.parse(projectId),
                KnowledgeRevisionId.parse(revisionId), JobId.parse(schedulerJobId),
                UUID.fromString(attemptId), idempotencyKey, documentCount, chunkCount,
                new Checksum(checksum), artifacts.stream().map(ArtifactView::toDomain).toList(),
                ResultStatus.valueOf(status), failureCode, completedAt);
        }
    }
}
