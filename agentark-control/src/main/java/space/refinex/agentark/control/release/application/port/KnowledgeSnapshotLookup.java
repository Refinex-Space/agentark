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

package space.refinex.agentark.control.release.application.port;

import space.refinex.agentark.kernel.id.KnowledgeBaseId;
import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 隔离 Control Release 与 Knowledge 实现模块的中立 READY Revision 查询端口。
 *
 * @author refinex
 */
public interface KnowledgeSnapshotLookup {

    /**
     * @param projectId       项目标识
     * @param knowledgeBaseId Knowledge Base 稳定身份
     * @param revisionId      Knowledge Revision 标识
     * @return 同项目 READY Revision 的检索参数，不可见或未 READY 时为空
     */
    Optional<ResolvedKnowledge> findReady(
        ProjectId projectId,
        KnowledgeBaseId knowledgeBaseId,
        KnowledgeRevisionId revisionId);

    /**
     * @param revisionId     Knowledge Revision 标识
     * @param topK           最大候选数量
     * @param scoreThreshold 相关性阈值
     * @param reranker       重排器稳定名称
     * @author refinex
     */
    record ResolvedKnowledge(
        KnowledgeRevisionId revisionId,
        int topK,
        BigDecimal scoreThreshold,
        String reranker) {
        /**
         * 校验中立检索参数。
         */
        public ResolvedKnowledge {
            java.util.Objects.requireNonNull(revisionId, "revisionId must not be null");
            java.util.Objects.requireNonNull(scoreThreshold, "scoreThreshold must not be null");
            if (reranker == null || reranker.isBlank()) {
                throw new IllegalArgumentException("reranker must not be blank");
            }
        }
    }
}
