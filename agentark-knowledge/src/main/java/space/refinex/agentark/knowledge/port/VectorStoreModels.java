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

package space.refinex.agentark.knowledge.port;

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;

import java.util.*;

/**
 * 集中定义向量后端请求模型，所有操作均显式携带可信租户和固定 Revision。
 *
 * @author refinex
 */
public final class VectorStoreModels {

    /**
     * 禁止实例化向量请求模型容器。
     */
    private VectorStoreModels() {
    }

    /**
     * @param organizationId     可信组织标识
     * @param projectId          可信项目标识
     * @param knowledgeRevisionId 固定 Knowledge Revision 标识
     * @author refinex
     */
    public record VectorScope(
        OrganizationId organizationId,
        ProjectId projectId,
        KnowledgeRevisionId knowledgeRevisionId) {

        /**
         * 校验向量操作的三维租户范围。
         *
         * @param organizationId      组织标识
         * @param projectId           项目标识
         * @param knowledgeRevisionId Knowledge Revision 标识
         */
        public VectorScope {
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(knowledgeRevisionId, "knowledgeRevisionId must not be null");
        }
    }

    /**
     * @param scope             可信租户范围
     * @param chunks            带向量 Chunk
     * @param documentIds       文档修订到稳定文档的不可变映射
     * @param revisionChecksum  当前完整 Chunk/Vector 清单摘要
     * @author refinex
     */
    public record VectorWriteRequest(
        VectorScope scope,
        List<EmbeddedChunk> chunks,
        Map<DocumentRevisionId, DocumentId> documentIds,
        Checksum revisionChecksum) {

        /**
         * 校验向量批次非空、文档映射完整并防御性复制集合。
         *
         * @param scope            租户范围
         * @param chunks           带向量 Chunk
         * @param documentIds      文档映射
         * @param revisionChecksum 清单摘要
         */
        public VectorWriteRequest {
            Objects.requireNonNull(scope, "scope must not be null");
            chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks must not be null"));
            documentIds = Map.copyOf(Objects.requireNonNull(
                documentIds, "documentIds must not be null"));
            Map<DocumentRevisionId, DocumentId> checkedDocumentIds = documentIds;
            Objects.requireNonNull(revisionChecksum, "revisionChecksum must not be null");
            if (chunks.isEmpty() || chunks.stream().anyMatch(value ->
                !checkedDocumentIds.containsKey(value.chunk().documentRevisionId()))) {
                throw new IllegalArgumentException("vector write request has an incomplete document map");
            }
        }
    }

    /**
     * @param scope            可信租户范围
     * @param expectedCount    预期 Point 数量
     * @param expectedChecksum 预期完整清单摘要
     * @author refinex
     */
    public record VectorVerificationRequest(
        VectorScope scope, int expectedCount, Checksum expectedChecksum) {

        /**
         * 校验预期数量和摘要。
         *
         * @param scope            租户范围
         * @param expectedCount    预期数量
         * @param expectedChecksum 预期摘要
         */
        public VectorVerificationRequest {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(expectedChecksum, "expectedChecksum must not be null");
            if (expectedCount <= 0) {
                throw new IllegalArgumentException("expectedCount must be positive");
            }
        }
    }

    /**
     * @param scope              可信租户范围
     * @param allowedDocumentIds 已由应用授权解析的文档白名单
     * @param queryVector        查询向量
     * @param limit              最大候选数
     * @param scoreThreshold     零到一之间的最低相似度
     * @author refinex
     */
    public record VectorSearchRequest(
        VectorScope scope,
        Set<DocumentId> allowedDocumentIds,
        float[] queryVector,
        int limit,
        double scoreThreshold) {

        /**
         * 校验 ACL 白名单、查询向量和检索边界，并复制可变数组。
         *
         * @param scope              租户范围
         * @param allowedDocumentIds 文档白名单
         * @param queryVector        查询向量
         * @param limit              最大候选数
         * @param scoreThreshold     最低相似度
         */
        public VectorSearchRequest {
            Objects.requireNonNull(scope, "scope must not be null");
            allowedDocumentIds = Set.copyOf(Objects.requireNonNull(
                allowedDocumentIds, "allowedDocumentIds must not be null"));
            if (queryVector == null
                || queryVector.length == 0
                || limit < 1
                || limit > 1000
                || !Double.isFinite(scoreThreshold)
                || scoreThreshold < 0
                || scoreThreshold > 1) {
                throw new IllegalArgumentException("vector search request is invalid");
            }
            queryVector = queryVector.clone();
        }

        /**
         * 返回查询向量副本，防止调用方修改请求。
         *
         * @return 查询向量副本
         */
        @Override
        public float[] queryVector() {
            return queryVector.clone();
        }
    }

    /**
     * @param chunk      Provider 中立 Chunk
     * @param documentId 稳定文档标识
     * @param score      零到一之间的相似度
     * @author refinex
     */
    public record VectorSearchHit(KnowledgeChunk chunk, DocumentId documentId, double score) {

        /**
         * 校验命中来源和归一化分数。
         *
         * @param chunk      Chunk
         * @param documentId 文档标识
         * @param score      相关性分数
         */
        public VectorSearchHit {
            Objects.requireNonNull(chunk, "chunk must not be null");
            Objects.requireNonNull(documentId, "documentId must not be null");
            if (!Double.isFinite(score) || score < 0 || score > 1) {
                throw new IllegalArgumentException("score must be between zero and one");
            }
        }
    }
}
