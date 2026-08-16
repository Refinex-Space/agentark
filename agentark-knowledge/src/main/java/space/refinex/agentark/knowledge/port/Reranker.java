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

import space.refinex.agentark.knowledge.domain.RetrievalProfile;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 定义对已召回候选执行异步重排的 Provider Port。
 *
 * @author refinex
 */
public interface Reranker {

    /**
     * 按查询和不可变 Profile 重排候选，不修改输入集合。
     *
     * @param query      查询文本
     * @param candidates 原始候选
     * @param profile    已发布 Retrieval Profile
     * @param limit      最大返回数
     * @return 重排后的候选
     */
    CompletionStage<List<RetrievalCandidate>> rerank(
        String query,
        List<RetrievalCandidate> candidates,
        RetrievalProfile profile,
        int limit);
}
