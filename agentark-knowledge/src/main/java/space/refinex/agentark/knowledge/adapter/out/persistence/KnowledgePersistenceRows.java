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

import java.time.Instant;
import java.util.UUID;

/**
 * 集中定义 Knowledge MyBatis 边界行对象，不向 Domain 或 Public API 暴露。
 *
 * @author refinex
 */
final class KnowledgePersistenceRows {

    /**
     * 禁止实例化行对象容器。
     */
    private KnowledgePersistenceRows() {
    }

    /**
     * @param id             主键 UUID
     * @param organizationId 组织 UUID
     * @param projectId      项目 UUID
     * @param knowledgeKey   稳定 Key
     * @param name           显示名称
     * @param description    用途说明
     * @param status         生命周期状态
     * @param version        乐观锁版本
     * @param createdAt      创建时间
     * @param createdBy      创建主体
     * @param updatedAt      更新时间
     * @param updatedBy      更新主体
     * @author refinex
     */
    record BaseRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String knowledgeKey,
        String name,
        String description,
        String status,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {
    }

    /**
     * @param id              主键 UUID
     * @param organizationId  组织 UUID
     * @param projectId       项目 UUID
     * @param knowledgeBaseId 所属知识库 UUID
     * @param sourceType      来源类型
     * @param name            显示名称
     * @param descriptorJson  来源 JSON
     * @param createdAt       创建时间
     * @param createdBy       创建主体
     * @author refinex
     */
    record DataSourceRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        UUID knowledgeBaseId,
        String sourceType,
        String name,
        String descriptorJson,
        Instant createdAt,
        String createdBy) {
    }

    /**
     * @param id              主键 UUID
     * @param organizationId  组织 UUID
     * @param projectId       项目 UUID
     * @param knowledgeBaseId 所属知识库 UUID
     * @param dataSourceId    数据源 UUID
     * @param title           标题
     * @param metadataJson    元数据 JSON
     * @param status          生命周期状态
     * @param version         乐观锁版本
     * @param createdAt       创建时间
     * @param createdBy       创建主体
     * @param updatedAt       更新时间
     * @param updatedBy       更新主体
     * @author refinex
     */
    record DocumentRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        UUID knowledgeBaseId,
        UUID dataSourceId,
        String title,
        String metadataJson,
        String status,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {
    }

    /**
     * @param documentId     文档 UUID
     * @param organizationId 组织 UUID
     * @param projectId      项目 UUID
     * @param subjectType    ACL 主体类型
     * @param subjectId      ACL 主体 UUID
     * @param accessLevel    访问级别
     * @param createdAt      创建时间
     * @param createdBy      创建主体
     * @author refinex
     */
    record AclRow(
        UUID documentId,
        UUID organizationId,
        UUID projectId,
        String subjectType,
        UUID subjectId,
        String accessLevel,
        Instant createdAt,
        String createdBy) {
    }

    /**
     * @param id               主键 UUID
     * @param organizationId   组织 UUID
     * @param projectId        项目 UUID
     * @param knowledgeBaseId  所属知识库 UUID
     * @param documentId       文档 UUID
     * @param revisionNumber   版本号
     * @param originalFileName 原始文件名
     * @param objectUri        对象 URI
     * @param contentHash      SHA-256 原始字节
     * @param contentSize      字节数
     * @param contentType      媒体类型
     * @param createdAt        创建时间
     * @param createdBy        创建主体
     * @author refinex
     */
    record DocumentRevisionRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        UUID knowledgeBaseId,
        UUID documentId,
        long revisionNumber,
        String originalFileName,
        String objectUri,
        byte[] contentHash,
        long contentSize,
        String contentType,
        Instant createdAt,
        String createdBy) {

        /**
         * 防御性复制摘要字节。
         */
        DocumentRevisionRow {
            contentHash = contentHash.clone();
        }

        /**
         * @return SHA-256 原始字节副本
         */
        @Override
        public byte[] contentHash() {
            return contentHash.clone();
        }
    }

    /**
     * @param id                  配置版本 UUID
     * @param organizationId      组织 UUID
     * @param projectId           项目 UUID
     * @param profileKey          稳定 Key
     * @param versionNumber       版本号
     * @param configJson          配置 JSON
     * @param credentialSecretRef 可选 SecretRef
     * @param contentHash         SHA-256 原始字节
     * @param status              发布状态
     * @param createdAt           创建时间
     * @param createdBy           创建主体
     * @author refinex
     */
    record ProfileRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String profileKey,
        long versionNumber,
        String configJson,
        String credentialSecretRef,
        byte[] contentHash,
        String status,
        Instant createdAt,
        String createdBy) {

        /**
         * 防御性复制摘要字节。
         */
        ProfileRow {
            contentHash = contentHash.clone();
        }

        /**
         * @return SHA-256 原始字节副本
         */
        @Override
        public byte[] contentHash() {
            return contentHash.clone();
        }
    }

    /**
     * @param id                 知识修订 UUID
     * @param organizationId     组织 UUID
     * @param projectId          项目 UUID
     * @param knowledgeBaseId    所属知识库 UUID
     * @param revisionNumber     版本号
     * @param parserProfileId    文档解析 Profile UUID
     * @param chunkProfileId     文档切分 Profile UUID
     * @param embeddingProfileId 向量生成 Profile UUID
     * @param retrievalProfileId 检索策略 Profile UUID
     * @param contentHash        SHA-256 原始字节
     * @param status             生命周期状态
     * @param failureCode        可选失败代码
     * @param version            乐观锁版本
     * @param createdAt          创建时间
     * @param createdBy          创建主体
     * @param updatedAt          更新时间
     * @param updatedBy          更新主体
     * @author refinex
     */
    record RevisionRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        UUID knowledgeBaseId,
        long revisionNumber,
        UUID parserProfileId,
        UUID chunkProfileId,
        UUID embeddingProfileId,
        UUID retrievalProfileId,
        byte[] contentHash,
        String status,
        String failureCode,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

        /**
         * 防御性复制摘要字节。
         */
        RevisionRow {
            contentHash = contentHash.clone();
        }

        /**
         * @return SHA-256 原始字节副本
         */
        @Override
        public byte[] contentHash() {
            return contentHash.clone();
        }
    }

    /**
     * @param id                  请求 UUID
     * @param organizationId      组织 UUID
     * @param projectId           项目 UUID
     * @param knowledgeRevisionId 知识修订 UUID
     * @param idempotencyKey      幂等键
     * @param status              请求状态
     * @param requestedAt         请求时间
     * @param requestedBy         请求主体
     * @author refinex
     */
    record IngestionRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        UUID knowledgeRevisionId,
        String idempotencyKey,
        String status,
        Instant requestedAt,
        String requestedBy) {
    }
}
