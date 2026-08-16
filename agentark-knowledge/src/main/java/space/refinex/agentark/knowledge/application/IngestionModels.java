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

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.knowledge.domain.*;

import java.time.Instant;
import java.util.*;

/**
 * 集中定义 Scheduler Worker 与 Control Internal Command 之间的语言中立摄取契约。
 *
 * @author refinex
 */
public final class IngestionModels {

    /**
     * 禁止实例化摄取契约容器。
     */
    private IngestionModels() {
    }

    /**
     * 定义摄取结果状态。
     *
     * @author refinex
     */
    public enum ResultStatus {

        /**
         * 解析、切分、向量写入及数量摘要校验均成功。
         */
        SUCCEEDED,

        /**
         * 当前 Attempt 明确失败，可由 Scheduler 创建新 Attempt 重试。
         */
        FAILED
    }

    /**
     * 定义摄取制品类别。
     *
     * @author refinex
     */
    public enum ArtifactKind {

        /**
         * 规范化 Section 与 Chunk 的不可变制品。
         */
        CHUNKS,

        /**
         * 向量写入清单及校验摘要。
         */
        VECTOR_MANIFEST
    }

    /**
     * @param kind 制品类别
     * @param ref  不含授权参数的对象引用
     * @author refinex
     */
    public record ArtifactReference(ArtifactKind kind, ObjectRef ref) {

        /**
         * 校验制品类别和不可变对象引用。
         *
         * @param kind 制品类别
         * @param ref  对象引用
         */
        public ArtifactReference {
            Objects.requireNonNull(kind, "kind must not be null");
            Objects.requireNonNull(ref, "ref must not be null");
        }
    }

    /**
     * @param requestId       Control 摄取请求标识
     * @param organizationId  可信组织标识
     * @param projectId       可信项目标识
     * @param revision        固定且处于 INGESTING 的 Knowledge Revision
     * @param documents       按 Revision 绑定加载的不可变文档修订
     * @param parserProfile   固定 Parser Profile
     * @param chunkProfile    固定 Chunk Profile
     * @param embeddingProfile 固定 Embedding Profile
     * @param retrievalProfile 固定 Retrieval Profile
     * @author refinex
     */
    public record IngestionPlan(
        IngestionRequestId requestId,
        OrganizationId organizationId,
        ProjectId projectId,
        KnowledgeRevision revision,
        List<DocumentRevision> documents,
        ParserProfile parserProfile,
        ChunkProfile chunkProfile,
        EmbeddingProfile embeddingProfile,
        RetrievalProfile retrievalProfile) {

        /**
         * 校验租户链、Revision 状态、文档顺序和四类不可变 Profile 绑定。
         *
         * @param requestId        摄取请求标识
         * @param organizationId   组织标识
         * @param projectId        项目标识
         * @param revision         Knowledge Revision
         * @param documents        文档修订列表
         * @param parserProfile    Parser Profile
         * @param chunkProfile     Chunk Profile
         * @param embeddingProfile Embedding Profile
         * @param retrievalProfile Retrieval Profile
         */
        public IngestionPlan {
            Objects.requireNonNull(requestId, "requestId must not be null");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(revision, "revision must not be null");
            documents = List.copyOf(Objects.requireNonNull(documents, "documents must not be null"));
            Objects.requireNonNull(parserProfile, "parserProfile must not be null");
            Objects.requireNonNull(chunkProfile, "chunkProfile must not be null");
            Objects.requireNonNull(embeddingProfile, "embeddingProfile must not be null");
            Objects.requireNonNull(retrievalProfile, "retrievalProfile must not be null");
            List<DocumentRevisionId> actualDocuments = documents.stream()
                .map(DocumentRevision::id).toList();
            boolean ownerMismatch = !revision.organizationId().equals(organizationId)
                || !revision.projectId().equals(projectId)
                || documents.stream().anyMatch(document ->
                    !document.organizationId().equals(organizationId)
                        || !document.projectId().equals(projectId))
                || !parserProfile.organizationId().equals(organizationId)
                || !chunkProfile.organizationId().equals(organizationId)
                || !embeddingProfile.organizationId().equals(organizationId)
                || !retrievalProfile.organizationId().equals(organizationId)
                || !parserProfile.projectId().equals(projectId)
                || !chunkProfile.projectId().equals(projectId)
                || !embeddingProfile.projectId().equals(projectId)
                || !retrievalProfile.projectId().equals(projectId);
            boolean bindingMismatch = !revision.documentRevisionIds().equals(actualDocuments)
                || !revision.parserProfileId().equals(parserProfile.id())
                || !revision.chunkProfileId().equals(chunkProfile.id())
                || !revision.embeddingProfileId().equals(embeddingProfile.id())
                || !revision.retrievalProfileId().equals(retrievalProfile.id());
            if (documents.isEmpty()
                || revision.status() != KnowledgeRevisionStatus.INGESTING
                || ownerMismatch
                || bindingMismatch) {
                throw new IllegalArgumentException("ingestion plan does not match the immutable revision");
            }
        }
    }

    /**
     * @param requestId      Control 摄取请求标识
     * @param organizationId 可信组织标识
     * @param projectId      可信项目标识
     * @param revisionId     固定 Knowledge Revision 标识
     * @param schedulerJobId Scheduler Job 标识
     * @param attemptId      当前摄取 Attempt UUIDv7
     * @param idempotencyKey Internal Command 幂等键
     * @author refinex
     */
    public record IngestionCommand(
        IngestionRequestId requestId,
        OrganizationId organizationId,
        ProjectId projectId,
        KnowledgeRevisionId revisionId,
        JobId schedulerJobId,
        UUID attemptId,
        String idempotencyKey) {

        /**
         * 校验 Scheduler 命令的身份、Attempt 和幂等键。
         *
         * @param requestId      摄取请求标识
         * @param organizationId 组织标识
         * @param projectId      项目标识
         * @param revisionId     Knowledge Revision 标识
         * @param schedulerJobId Scheduler Job 标识
         * @param attemptId      Attempt UUIDv7
         * @param idempotencyKey 幂等键
         */
        public IngestionCommand {
            Objects.requireNonNull(requestId, "requestId must not be null");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(revisionId, "revisionId must not be null");
            Objects.requireNonNull(schedulerJobId, "schedulerJobId must not be null");
            requireUuidV7(attemptId, "attemptId");
            if (idempotencyKey == null
                || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{7,127}")) {
                throw new IllegalArgumentException("idempotencyKey must contain 8 to 128 safe characters");
            }
        }
    }

    /**
     * @param resultId         摄取结果 UUIDv7
     * @param requestId        Control 摄取请求标识
     * @param organizationId   可信组织标识
     * @param projectId        可信项目标识
     * @param revisionId       固定 Knowledge Revision 标识
     * @param schedulerJobId   Scheduler Job 标识
     * @param attemptId        当前 Attempt UUIDv7
     * @param idempotencyKey   Internal Command 幂等键
     * @param documentCount    已处理文档修订数量
     * @param chunkCount       已验证向量 Chunk 数量
     * @param checksum         Chunk 与向量清单 SHA-256
     * @param artifacts        不可变 Chunk/Manifest 制品引用
     * @param status           当前 Attempt 结果状态
     * @param failureCode      失败时稳定代码，成功时为空
     * @param completedAt      Worker 完成时间
     * @author refinex
     */
    public record IngestionResult(
        UUID resultId,
        IngestionRequestId requestId,
        OrganizationId organizationId,
        ProjectId projectId,
        KnowledgeRevisionId revisionId,
        JobId schedulerJobId,
        UUID attemptId,
        String idempotencyKey,
        int documentCount,
        int chunkCount,
        Checksum checksum,
        List<ArtifactReference> artifacts,
        ResultStatus status,
        String failureCode,
        Instant completedAt) {

        /**
         * 校验成功和失败结果的计数、摘要、制品及错误字段不变量。
         *
         * @param resultId       结果 UUIDv7
         * @param requestId      摄取请求标识
         * @param organizationId 组织标识
         * @param projectId      项目标识
         * @param revisionId     Knowledge Revision 标识
         * @param schedulerJobId Scheduler Job 标识
         * @param attemptId      Attempt UUIDv7
         * @param idempotencyKey 幂等键
         * @param documentCount  文档数
         * @param chunkCount     Chunk 数
         * @param checksum       清单摘要
         * @param artifacts      制品引用
         * @param status         结果状态
         * @param failureCode    失败代码
         * @param completedAt    完成时间
         */
        public IngestionResult {
            requireUuidV7(resultId, "resultId");
            Objects.requireNonNull(requestId, "requestId must not be null");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(revisionId, "revisionId must not be null");
            Objects.requireNonNull(schedulerJobId, "schedulerJobId must not be null");
            requireUuidV7(attemptId, "attemptId");
            Objects.requireNonNull(checksum, "checksum must not be null");
            artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts must not be null"));
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(completedAt, "completedAt must not be null");
            if (idempotencyKey == null
                || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{7,127}")) {
                throw new IllegalArgumentException("idempotencyKey must contain 8 to 128 safe characters");
            }
            String code = failureCode == null ? "" : failureCode;
            if (documentCount < 0
                || chunkCount < 0
                || (status == ResultStatus.SUCCEEDED
                    && (documentCount == 0 || chunkCount == 0 || artifacts.isEmpty() || !code.isEmpty()))
                || (status == ResultStatus.FAILED
                    && !code.matches("[A-Z][A-Z0-9_]{2,63}"))) {
                throw new IllegalArgumentException("ingestion result fields do not match status");
            }
            failureCode = code;
        }
    }

    /**
     * 校验 UUID 使用 RFC 9562 UUIDv7 版本和 RFC 4122 Variant。
     *
     * @param value UUID 值
     * @param name  参数名称
     */
    private static void requireUuidV7(UUID value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.version() != 7 || value.variant() != 2) {
            throw new IllegalArgumentException(name + " must be an RFC 9562 UUIDv7");
        }
    }
}
