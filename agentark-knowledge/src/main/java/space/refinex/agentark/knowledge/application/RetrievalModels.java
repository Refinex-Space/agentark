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
import space.refinex.agentark.knowledge.domain.*;

import java.time.Instant;
import java.util.*;

/**
 * 集中定义固定 Knowledge Revision 检索、Citation 与 Trace 契约。
 *
 * @author refinex
 */
public final class RetrievalModels {

    /**
     * 禁止实例化检索契约容器。
     */
    private RetrievalModels() {
    }

    /**
     * @param organizationId      可信组织标识
     * @param projectId           可信项目标识
     * @param revision            固定且 READY 的 Knowledge Revision
     * @param embeddingProfile    Revision 固定的 Embedding Profile
     * @param retrievalProfile    Revision 固定的 Retrieval Profile
     * @param allowedDocumentIds  已经应用授权解析的文档白名单
     * @param documentTitles      文档标题映射，用于 Citation 展示
     * @param query               查询文本
     * @param candidateLimit      召回候选上限
     * @param resultLimit         最终结果上限
     * @param contextBudgetChars  上下文字符预算
     * @param scoreThreshold      最低相关性分数
     * @param hybridEnabled       是否启用已配置的 Hybrid Port
     * @author refinex
     */
    public record RetrievalRequest(
        OrganizationId organizationId,
        ProjectId projectId,
        KnowledgeRevision revision,
        EmbeddingProfile embeddingProfile,
        RetrievalProfile retrievalProfile,
        Set<DocumentId> allowedDocumentIds,
        Map<DocumentId, String> documentTitles,
        String query,
        int candidateLimit,
        int resultLimit,
        int contextBudgetChars,
        double scoreThreshold,
        boolean hybridEnabled) {

        /**
         * 校验 READY 状态、不可变 Profile 绑定、ACL 白名单和检索预算。
         *
         * @param organizationId     组织标识
         * @param projectId          项目标识
         * @param revision           Knowledge Revision
         * @param embeddingProfile   Embedding Profile
         * @param retrievalProfile   Retrieval Profile
         * @param allowedDocumentIds 文档白名单
         * @param documentTitles     文档标题
         * @param query              查询文本
         * @param candidateLimit     候选上限
         * @param resultLimit        结果上限
         * @param contextBudgetChars 字符预算
         * @param scoreThreshold     分数阈值
         * @param hybridEnabled      Hybrid 开关
         */
        public RetrievalRequest {
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(revision, "revision must not be null");
            Objects.requireNonNull(embeddingProfile, "embeddingProfile must not be null");
            Objects.requireNonNull(retrievalProfile, "retrievalProfile must not be null");
            allowedDocumentIds = Set.copyOf(Objects.requireNonNull(
                allowedDocumentIds, "allowedDocumentIds must not be null"));
            documentTitles = Map.copyOf(Objects.requireNonNull(
                documentTitles, "documentTitles must not be null"));
            Map<DocumentId, String> checkedDocumentTitles = documentTitles;
            boolean ownerMismatch = !revision.organizationId().equals(organizationId)
                || !revision.projectId().equals(projectId)
                || !embeddingProfile.organizationId().equals(organizationId)
                || !embeddingProfile.projectId().equals(projectId)
                || !retrievalProfile.organizationId().equals(organizationId)
                || !retrievalProfile.projectId().equals(projectId);
            boolean bindingMismatch = !revision.embeddingProfileId().equals(embeddingProfile.id())
                || !revision.retrievalProfileId().equals(retrievalProfile.id());
            if (revision.status() != KnowledgeRevisionStatus.READY
                || embeddingProfile.status() != KnowledgeProfileStatus.PUBLISHED
                || retrievalProfile.status() != KnowledgeProfileStatus.PUBLISHED
                || ownerMismatch
                || bindingMismatch
                || allowedDocumentIds.stream().anyMatch(id -> !checkedDocumentTitles.containsKey(id))
                || query == null
                || query.isBlank()
                || query.length() > 8000
                || candidateLimit < 1
                || candidateLimit > 1000
                || resultLimit < 1
                || resultLimit > candidateLimit
                || contextBudgetChars < 1
                || contextBudgetChars > 1_000_000
                || !Double.isFinite(scoreThreshold)
                || scoreThreshold < 0
                || scoreThreshold > 1) {
                throw new IllegalArgumentException("retrieval request violates the fixed revision contract");
            }
        }
    }

    /**
     * @param documentId         稳定文档标识
     * @param documentRevisionId 不可变文档修订标识
     * @param chunkKey           文档修订内稳定 Chunk Key
     * @param title              文档标题
     * @param sourceTrust        固定信任标记，不得由文档正文提升
     * @author refinex
     */
    public record Citation(
        DocumentId documentId,
        DocumentRevisionId documentRevisionId,
        String chunkKey,
        String title,
        String sourceTrust) {

        /**
         * 校验 Citation 来源和固定外部内容信任标记。
         *
         * @param documentId         文档标识
         * @param documentRevisionId 文档修订标识
         * @param chunkKey           Chunk Key
         * @param title              文档标题
         * @param sourceTrust        信任标记
         */
        public Citation {
            Objects.requireNonNull(documentId, "documentId must not be null");
            Objects.requireNonNull(documentRevisionId, "documentRevisionId must not be null");
            if (chunkKey == null || chunkKey.isBlank() || title == null || title.isBlank()
                || !"UNTRUSTED_EXTERNAL".equals(sourceTrust)) {
                throw new IllegalArgumentException("citation source is invalid");
            }
        }
    }

    /**
     * @param text     受上下文预算限制的 Chunk 文本
     * @param score    最终相关性分数
     * @param citation 可追踪引用
     * @author refinex
     */
    public record RetrievalItem(String text, double score, Citation citation) {

        /**
         * 校验结果文本、分数和 Citation。
         *
         * @param text     Chunk 文本
         * @param score    相关性分数
         * @param citation Citation
         */
        public RetrievalItem {
            if (text == null || text.isBlank() || !Double.isFinite(score) || score < 0 || score > 1) {
                throw new IllegalArgumentException("retrieval item is invalid");
            }
            Objects.requireNonNull(citation, "citation must not be null");
        }
    }

    /**
     * @param traceId           全局 Trace UUIDv7
     * @param knowledgeRevisionId 固定 Knowledge Revision 标识
     * @param vectorCandidates  向量候选数
     * @param hybridCandidates  Hybrid 候选数
     * @param deduplicatedCount 去重后候选数
     * @param returnedCount     最终返回数
     * @param queryCharacters   查询字符数
     * @param contextCharacters 上下文字符数
     * @param durationMillis    检索耗时毫秒
     * @param completedAt       完成时间
     * @author refinex
     */
    public record RetrievalTrace(
        UUID traceId,
        KnowledgeRevisionId knowledgeRevisionId,
        int vectorCandidates,
        int hybridCandidates,
        int deduplicatedCount,
        int returnedCount,
        int queryCharacters,
        int contextCharacters,
        long durationMillis,
        Instant completedAt) {

        /**
         * 校验 Trace 标识、计数、单位和时间。
         *
         * @param traceId            Trace UUIDv7
         * @param knowledgeRevisionId Knowledge Revision 标识
         * @param vectorCandidates   向量候选数
         * @param hybridCandidates   Hybrid 候选数
         * @param deduplicatedCount  去重候选数
         * @param returnedCount      返回数
         * @param queryCharacters    查询字符数
         * @param contextCharacters  上下文字符数
         * @param durationMillis     耗时毫秒
         * @param completedAt        完成时间
         */
        public RetrievalTrace {
            Objects.requireNonNull(traceId, "traceId must not be null");
            Objects.requireNonNull(knowledgeRevisionId, "knowledgeRevisionId must not be null");
            Objects.requireNonNull(completedAt, "completedAt must not be null");
            if (traceId.version() != 7
                || traceId.variant() != 2
                || vectorCandidates < 0
                || hybridCandidates < 0
                || deduplicatedCount < 0
                || returnedCount < 0
                || queryCharacters < 0
                || contextCharacters < 0
                || durationMillis < 0) {
                throw new IllegalArgumentException("retrieval trace is invalid");
            }
        }
    }

    /**
     * @param items 最终检索结果
     * @param trace 可审计 Trace 与原始 Usage
     * @author refinex
     */
    public record RetrievalResult(List<RetrievalItem> items, RetrievalTrace trace) {

        /**
         * 防御性复制结果并校验 Trace。
         *
         * @param items 结果列表
         * @param trace 检索 Trace
         */
        public RetrievalResult {
            items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
            Objects.requireNonNull(trace, "trace must not be null");
        }
    }
}
