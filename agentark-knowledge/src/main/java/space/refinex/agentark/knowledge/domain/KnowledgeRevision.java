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

package space.refinex.agentark.knowledge.domain;

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 表示文档修订集合与四类 Profile 绑定形成的不可变 Knowledge Revision。
 *
 * @param id                  Knowledge Revision 标识
 * @param organizationId      组织标识
 * @param projectId           项目标识
 * @param knowledgeBaseId     Knowledge Base 标识
 * @param revisionNumber      Knowledge Base 内单调递增版本号
 * @param documentRevisionIds 不可变文档修订集合
 * @param parserProfileId     Parser Profile 版本标识
 * @param chunkProfileId      Chunk Profile 版本标识
 * @param embeddingProfileId  Embedding Profile 版本标识
 * @param retrievalProfileId  Retrieval Profile 版本标识
 * @param contentHash         全部绑定内容的 SHA-256
 * @param status              生命周期状态
 * @param failureCode         可选稳定失败代码
 * @param version             状态乐观锁版本
 * @param createdAt           创建时间
 * @param updatedAt           状态更新时间
 * @author refinex
 */
public record KnowledgeRevision(
    KnowledgeRevisionId id,
    OrganizationId organizationId,
    ProjectId projectId,
    KnowledgeBaseId knowledgeBaseId,
    long revisionNumber,
    List<DocumentRevisionId> documentRevisionIds,
    ParserProfileId parserProfileId,
    ChunkProfileId chunkProfileId,
    EmbeddingProfileId embeddingProfileId,
    RetrievalProfileId retrievalProfileId,
    Checksum contentHash,
    KnowledgeRevisionStatus status,
    String failureCode,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验不可变内容绑定、状态附加字段和时间不变量。
     *
     * @param id                  Knowledge Revision 标识
     * @param organizationId      组织标识
     * @param projectId           项目标识
     * @param knowledgeBaseId     Knowledge Base 标识
     * @param revisionNumber      版本号
     * @param documentRevisionIds 文档修订集合
     * @param parserProfileId     Parser Profile 标识
     * @param chunkProfileId      Chunk Profile 标识
     * @param embeddingProfileId  Embedding Profile 标识
     * @param retrievalProfileId  Retrieval Profile 标识
     * @param contentHash         内容摘要
     * @param status              生命周期状态
     * @param failureCode         失败代码
     * @param version             乐观锁版本
     * @param createdAt           创建时间
     * @param updatedAt           更新时间
     */
    public KnowledgeRevision {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(knowledgeBaseId, "knowledgeBaseId must not be null");
        documentRevisionIds = List.copyOf(
            Objects.requireNonNull(documentRevisionIds, "documentRevisionIds must not be null"));
        Objects.requireNonNull(parserProfileId, "parserProfileId must not be null");
        Objects.requireNonNull(chunkProfileId, "chunkProfileId must not be null");
        Objects.requireNonNull(embeddingProfileId, "embeddingProfileId must not be null");
        Objects.requireNonNull(retrievalProfileId, "retrievalProfileId must not be null");
        Objects.requireNonNull(contentHash, "contentHash must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        failureCode = failureCode == null ? "" : failureCode;
        if (revisionNumber <= 0
            || documentRevisionIds.isEmpty()
            || documentRevisionIds.stream().distinct().count() != documentRevisionIds.size()
            || version < 0
            || updatedAt.isBefore(createdAt)
            || (status == KnowledgeRevisionStatus.FAILED) != !failureCode.isBlank()
            || (!failureCode.isBlank() && !failureCode.matches("[A-Z][A-Z0-9_]{0,63}"))) {
            throw new IllegalArgumentException("knowledge revision fields are invalid");
        }
    }

    /**
     * 执行一次受状态机保护的转换并保留全部不可变内容绑定。
     *
     * @param target            目标状态
     * @param targetFailureCode FAILED 状态的稳定失败代码，其他状态必须为空
     * @param changedAt         转换时刻
     * @return 状态和乐观锁版本更新后的新聚合值
     */
    public KnowledgeRevision transitionTo(
        KnowledgeRevisionStatus target, String targetFailureCode, Instant changedAt) {
        KnowledgeRevisionStatus checkedTarget = status.requireTransitionTo(target);
        return new KnowledgeRevision(
            id,
            organizationId,
            projectId,
            knowledgeBaseId,
            revisionNumber,
            documentRevisionIds,
            parserProfileId,
            chunkProfileId,
            embeddingProfileId,
            retrievalProfileId,
            contentHash,
            checkedTarget,
            targetFailureCode,
            version + 1,
            createdAt,
            Objects.requireNonNull(changedAt, "changedAt must not be null"));
    }

    /**
     * 判断当前版本是否可供 Agent Revision Resolver 引用。
     *
     * @return 仅 READY 状态返回 {@code true}
     */
    public boolean isReferenceable() {
        return status == KnowledgeRevisionStatus.READY;
    }
}
