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

package space.refinex.agentark.knowledge.adapter.in.web;

import jakarta.validation.constraints.*;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.knowledge.domain.*;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 集中定义 Knowledge Public API 的输入契约，响应使用不含厂商类型的领域只读模型。
 *
 * @author refinex
 */
public final class KnowledgeApiModels {

    /**
     * 禁止实例化 API 模型容器。
     */
    private KnowledgeApiModels() {
    }

    /**
     * @param key         项目内稳定 Key
     * @param name        显示名称
     * @param description 可选用途说明
     * @author refinex
     */
    public record CreateKnowledgeBaseRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,62}") String key,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description) {
    }

    /**
     * @param type       数据源类型：UPLOAD、URI 或 CONNECTOR
     * @param name       显示名称
     * @param descriptor 不含凭据的来源描述
     * @author refinex
     */
    public record CreateDataSourceRequest(
        @NotBlank @Pattern(regexp = "UPLOAD|URI|CONNECTOR") String type,
        @NotBlank @Size(max = 128) String name,
        @NotNull Map<String, Object> descriptor) {

        /**
         * 防御性复制来源描述。
         */
        public CreateDataSourceRequest {
            descriptor = descriptor == null ? null : Map.copyOf(descriptor);
        }
    }

    /**
     * @param key                 项目内稳定 Key
     * @param config              Provider 中立配置
     * @param credentialSecretRef Embedding Profile 可选 SecretRef，其他类型必须为空
     * @param status              Profile 状态：DRAFT、PUBLISHED 或 DEPRECATED
     * @author refinex
     */
    public record CreateProfileRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,62}") String key,
        @NotNull Map<String, Object> config,
        @Size(max = 512) String credentialSecretRef,
        @NotBlank @Pattern(regexp = "DRAFT|PUBLISHED|DEPRECATED") String status) {

        /**
         * 防御性复制配置。
         */
        public CreateProfileRequest {
            config = config == null ? null : Map.copyOf(config);
        }
    }

    /**
     * @param documentRevisionIds 不可变文档修订 UUIDv7 列表
     * @param parserProfileId     文档解析 Profile UUIDv7
     * @param chunkProfileId      文档切分 Profile UUIDv7
     * @param embeddingProfileId  向量生成 Profile UUIDv7
     * @param retrievalProfileId  检索策略 Profile UUIDv7
     * @author refinex
     */
    public record CreateKnowledgeRevisionRequest(
        @NotEmpty List<@NotBlank String> documentRevisionIds,
        @NotBlank String parserProfileId,
        @NotBlank String chunkProfileId,
        @NotBlank String embeddingProfileId,
        @NotBlank String retrievalProfileId) {

        /**
         * 防御性复制文档修订列表。
         */
        public CreateKnowledgeRevisionRequest {
            documentRevisionIds = documentRevisionIds == null
                ? null
                : List.copyOf(documentRevisionIds);
        }
    }

    /**
     * @param idempotencyKey 项目内摄取请求幂等键
     * @author refinex
     */
    public record CreateIngestionRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{7,127}")
        String idempotencyKey) {
    }

    /**
     * @param id             Knowledge Base 标识 UUIDv7
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param key            项目内稳定 Key
     * @param name           显示名称
     * @param description    用途说明
     * @param status         生命周期状态
     * @param version        乐观锁版本
     * @param createdAt      创建时间
     * @param updatedAt      更新时间
     * @author refinex
     */
    public record KnowledgeBaseView(
        String id,
        String organizationId,
        String projectId,
        String key,
        String name,
        String description,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

        /**
         * 将领域聚合转换为语言中立 Public API 视图。
         *
         * @param source Knowledge Base 领域聚合
         * @return Public API 视图
         */
        public static KnowledgeBaseView from(KnowledgeBase source) {
            return new KnowledgeBaseView(
                source.id().asString(), source.organizationId().asString(),
                source.projectId().asString(), source.key(), source.name(), source.description(),
                source.status().name(), source.version(), source.createdAt(), source.updatedAt());
        }
    }

    /**
     * @param id              数据源 UUIDv7
     * @param organizationId  组织 UUIDv7
     * @param projectId       项目 UUIDv7
     * @param knowledgeBaseId Knowledge Base 标识 UUIDv7
     * @param type            数据源类型：UPLOAD、URI 或 CONNECTOR
     * @param name            显示名称
     * @param descriptor      不含凭据的来源描述对象
     * @param createdAt       创建时间
     * @author refinex
     */
    public record DataSourceView(
        String id,
        String organizationId,
        String projectId,
        String knowledgeBaseId,
        String type,
        String name,
        Map<String, Object> descriptor,
        Instant createdAt) {

        /**
         * 防御性复制来源描述。
         */
        public DataSourceView {
            descriptor = Map.copyOf(descriptor);
        }

        /**
         * 将数据库 JSON 字符串还原为公共对象。
         *
         * @param source 数据源领域对象
         * @param mapper 应用 JSON 映射器
         * @return Public API 视图
         */
        public static DataSourceView from(DataSource source, JsonMapper mapper) {
            return new DataSourceView(
                source.id().asString(), source.organizationId().asString(),
                source.projectId().asString(), source.knowledgeBaseId().asString(),
                source.type().name(), source.name(), objectMap(source.descriptorJson(), mapper),
                source.createdAt());
        }
    }

    /**
     * @param subjectType ACL 主体类型：PROJECT、USER、SERVICE_ACCOUNT 或 ROLE
     * @param subjectId   ACL 主体 UUIDv7
     * @param accessLevel 访问级别：READ、WRITE 或 MANAGE
     * @author refinex
     */
    public record DocumentAclView(String subjectType, String subjectId, String accessLevel) {

        /**
         * @param source 文档 ACL 领域值
         * @return Public API 视图
         */
        public static DocumentAclView from(DocumentAcl source) {
            return new DocumentAclView(
                source.subjectType().name(), source.subjectId(), source.accessLevel().name());
        }
    }

    /**
     * @param id              文档 UUIDv7
     * @param organizationId  组织 UUIDv7
     * @param projectId       项目 UUIDv7
     * @param knowledgeBaseId Knowledge Base 标识 UUIDv7
     * @param dataSourceId    数据源 UUIDv7
     * @param title           文档标题
     * @param metadata        不含原文与 Secret 的元数据
     * @param acl             显式文档 ACL
     * @param status          文档状态：ACTIVE、ARCHIVED 或 DELETING
     * @param version         乐观锁版本
     * @param createdAt       创建时间
     * @param updatedAt       更新时间
     * @author refinex
     */
    public record DocumentView(
        String id,
        String organizationId,
        String projectId,
        String knowledgeBaseId,
        String dataSourceId,
        String title,
        Map<String, String> metadata,
        List<DocumentAclView> acl,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

        /**
         * 防御性复制文档元数据和 ACL。
         */
        public DocumentView {
            metadata = Map.copyOf(metadata);
            acl = List.copyOf(acl);
        }

        /**
         * @param source 文档领域聚合
         * @return Public API 视图
         */
        public static DocumentView from(Document source) {
            return new DocumentView(
                source.id().asString(), source.organizationId().asString(),
                source.projectId().asString(), source.knowledgeBaseId().asString(),
                source.dataSourceId().asString(), source.title(), source.metadata(),
                source.acl().stream().map(DocumentAclView::from).toList(), source.status().name(),
                source.version(), source.createdAt(), source.updatedAt());
        }
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
         * @param source Kernel 对象引用
         * @return Public API 视图
         */
        public static ObjectRefView from(ObjectRef source) {
            return new ObjectRefView(
                source.uri().toASCIIString(), source.checksum().toString(), source.size(),
                source.mediaType());
        }
    }

    /**
     * @param id               文档修订 UUIDv7
     * @param organizationId   组织 UUIDv7
     * @param projectId        项目 UUIDv7
     * @param knowledgeBaseId  Knowledge Base 标识 UUIDv7
     * @param documentId       文档 UUIDv7
     * @param revisionNumber   文档内版本号
     * @param originalFileName 原始文件名
     * @param objectRef        原文件持久对象引用
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
         * @param source 文档修订领域值
         * @return Public API 视图
         */
        public static DocumentRevisionView from(DocumentRevision source) {
            return new DocumentRevisionView(
                source.id().asString(), source.organizationId().asString(),
                source.projectId().asString(), source.knowledgeBaseId().asString(),
                source.documentId().asString(), source.revisionNumber(), source.originalFileName(),
                ObjectRefView.from(source.objectRef()), source.createdAt());
        }
    }

    /**
     * @param id                  Profile 标识 UUIDv7
     * @param profileKind         Profile 类型：PARSER、CHUNK、EMBEDDING 或 RETRIEVAL
     * @param organizationId      组织 UUIDv7
     * @param projectId           项目 UUIDv7
     * @param key                 项目内稳定 Key
     * @param versionNumber       Key 下不可变版本号
     * @param config              Provider 中立配置对象
     * @param credentialSecretRef 可选 SecretRef，仅 Embedding Profile 可使用
     * @param contentHash         配置内容 SHA-256
     * @param status              发布状态：DRAFT、PUBLISHED 或 DEPRECATED
     * @param createdAt           创建时间
     * @author refinex
     */
    public record KnowledgeProfileView(
        String id,
        String profileKind,
        String organizationId,
        String projectId,
        String key,
        long versionNumber,
        Map<String, Object> config,
        String credentialSecretRef,
        String contentHash,
        String status,
        Instant createdAt) {

        /**
         * 防御性复制 Profile 配置。
         */
        public KnowledgeProfileView {
            config = Map.copyOf(config);
        }

        /**
         * @param source Parser Profile @param mapper JSON 映射器 @return Public API 视图
         */
        public static KnowledgeProfileView from(ParserProfile source, JsonMapper mapper) {
            return profile(
                source.id().asString(), "PARSER", source.organizationId().asString(),
                source.projectId().asString(), source.key(), source.versionNumber(),
                source.configJson(), null, source.contentHash().toString(), source.status().name(),
                source.createdAt(), mapper);
        }

        /**
         * @param source Chunk Profile @param mapper JSON 映射器 @return Public API 视图
         */
        public static KnowledgeProfileView from(ChunkProfile source, JsonMapper mapper) {
            return profile(
                source.id().asString(), "CHUNK", source.organizationId().asString(),
                source.projectId().asString(), source.key(), source.versionNumber(),
                source.configJson(), null, source.contentHash().toString(), source.status().name(),
                source.createdAt(), mapper);
        }

        /**
         * @param source Embedding Profile @param mapper JSON 映射器 @return Public API 视图
         */
        public static KnowledgeProfileView from(EmbeddingProfile source, JsonMapper mapper) {
            return profile(
                source.id().asString(), "EMBEDDING", source.organizationId().asString(),
                source.projectId().asString(), source.key(), source.versionNumber(),
                source.configJson(), source.credentialRef().map(value -> value.asString()).orElse(null),
                source.contentHash().toString(), source.status().name(), source.createdAt(), mapper);
        }

        /**
         * @param source Retrieval Profile @param mapper JSON 映射器 @return Public API 视图
         */
        public static KnowledgeProfileView from(RetrievalProfile source, JsonMapper mapper) {
            return profile(
                source.id().asString(), "RETRIEVAL", source.organizationId().asString(),
                source.projectId().asString(), source.key(), source.versionNumber(),
                source.configJson(), null, source.contentHash().toString(), source.status().name(),
                source.createdAt(), mapper);
        }

        /**
         * 组装四类 Profile 共用的公共视图。
         *
         * @param id                  Profile 标识
         * @param kind                Profile 类型
         * @param organizationId      组织标识
         * @param projectId           项目标识
         * @param key                 稳定 Key
         * @param versionNumber       版本号
         * @param configJson          配置 JSON
         * @param credentialSecretRef 可选凭据引用
         * @param contentHash         内容摘要
         * @param status              发布状态
         * @param createdAt           创建时间
         * @param mapper              JSON 映射器
         * @return Public API 视图
         */
        private static KnowledgeProfileView profile(
            String id,
            String kind,
            String organizationId,
            String projectId,
            String key,
            long versionNumber,
            String configJson,
            String credentialSecretRef,
            String contentHash,
            String status,
            Instant createdAt,
            JsonMapper mapper) {
            return new KnowledgeProfileView(
                id, kind, organizationId, projectId, key, versionNumber,
                objectMap(configJson, mapper), credentialSecretRef, contentHash, status, createdAt);
        }
    }

    /**
     * @param id                  Knowledge Revision 标识 UUIDv7
     * @param organizationId      组织 UUIDv7
     * @param projectId           项目 UUIDv7
     * @param knowledgeBaseId     Knowledge Base 标识 UUIDv7
     * @param revisionNumber      Knowledge Base 内版本号
     * @param documentRevisionIds 不可变文档修订 UUIDv7 列表
     * @param parserProfileId     Parser Profile 标识 UUIDv7
     * @param chunkProfileId      Chunk Profile 标识 UUIDv7
     * @param embeddingProfileId  Embedding Profile 标识 UUIDv7
     * @param retrievalProfileId  Retrieval Profile 标识 UUIDv7
     * @param contentHash         全部绑定内容 SHA-256
     * @param status              Revision 状态
     * @param failureCode         可选稳定失败代码
     * @param version             状态乐观锁版本
     * @param createdAt           创建时间
     * @param updatedAt           状态更新时间
     * @author refinex
     */
    public record KnowledgeRevisionView(
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
         * 防御性复制文档修订标识列表。
         */
        public KnowledgeRevisionView {
            documentRevisionIds = List.copyOf(documentRevisionIds);
        }

        /**
         * @param source Knowledge Revision 领域值 @return Public API 视图
         */
        public static KnowledgeRevisionView from(KnowledgeRevision source) {
            return new KnowledgeRevisionView(
                source.id().asString(), source.organizationId().asString(),
                source.projectId().asString(), source.knowledgeBaseId().asString(),
                source.revisionNumber(),
                source.documentRevisionIds().stream().map(value -> value.asString()).toList(),
                source.parserProfileId().asString(), source.chunkProfileId().asString(),
                source.embeddingProfileId().asString(), source.retrievalProfileId().asString(),
                source.contentHash().toString(), source.status().name(), source.failureCode(),
                source.version(), source.createdAt(), source.updatedAt());
        }
    }

    /**
     * @param id                  摄取请求 UUIDv7
     * @param organizationId      组织 UUIDv7
     * @param projectId           项目 UUIDv7
     * @param knowledgeRevisionId Knowledge Revision 标识 UUIDv7
     * @param idempotencyKey      项目内幂等键
     * @param status              请求状态：DESCRIBED 或 CANCELLED
     * @param requestedAt         请求时间
     * @author refinex
     */
    public record IngestionRequestView(
        String id,
        String organizationId,
        String projectId,
        String knowledgeRevisionId,
        String idempotencyKey,
        String status,
        Instant requestedAt) {

        /**
         * @param source 摄取请求领域值 @return Public API 视图
         */
        public static IngestionRequestView from(IngestionJobDescriptor source) {
            return new IngestionRequestView(
                source.id().asString(), source.organizationId().asString(),
                source.projectId().asString(), source.knowledgeRevisionId().asString(),
                source.idempotencyKey(), source.status().name(), source.requestedAt());
        }
    }

    /**
     * 将持久化 JSON 对象解析为 Public API 映射。
     *
     * @param json   持久化 JSON
     * @param mapper 应用 JSON 映射器
     * @return 不会双重编码的对象映射
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(String json, JsonMapper mapper) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("stored knowledge JSON is invalid", exception);
        }
    }

    /**
     * 定义 Public API 支持创建的四类 Profile。
     *
     * @author refinex
     */
    public enum ProfileKind {

        /**
         * 文档解析 Profile。
         */
        PARSER,

        /**
         * 文档切分 Profile。
         */
        CHUNK,

        /**
         * 向量生成 Profile。
         */
        EMBEDDING,

        /**
         * 检索策略 Profile。
         */
        RETRIEVAL;

        /**
         * 解析小写 URL Segment。
         *
         * @param value URL Segment
         * @return Profile 类型
         */
        public static ProfileKind parse(String value) {
            if (value == null) {
                throw new IllegalArgumentException("profile kind is required");
            }
            return valueOf(value.toUpperCase(java.util.Locale.ROOT));
        }
    }
}
