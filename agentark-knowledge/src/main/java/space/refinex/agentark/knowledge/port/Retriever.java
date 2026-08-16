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

import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.knowledge.domain.RetrievalProfile;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 定义只对已授权且 READY 的 Knowledge Revision 执行召回的 Provider Port。
 *
 * @author refinex
 */
public interface Retriever {

    /**
     * 异步召回候选，项目和 Revision 是独立参数，不能依赖 Collection 名授权。
     *
     * @param projectId  显式项目边界
     * @param revisionId READY Knowledge Revision 标识
     * @param query      查询文本
     * @param profile    已发布 Retrieval Profile
     * @param limit      最大候选数
     * @return 按 Provider 原始相关性排序的候选
     */
    CompletionStage<List<RetrievalCandidate>> retrieve(
        ProjectId projectId,
        KnowledgeRevisionId revisionId,
        String query,
        RetrievalProfile profile,
        int limit);
}
