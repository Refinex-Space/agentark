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

import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchHit;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchRequest;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 定义可选关键词或混合召回 Port；未配置时 Retrieval 仍可使用纯向量路径。
 *
 * @author refinex
 */
@FunctionalInterface
public interface HybridRetriever {

    /**
     * 在与向量检索相同的租户和 ACL 范围内召回候选。
     *
     * @param query   原始查询文本
     * @param request 已固定租户、Revision 和 ACL 的检索请求
     * @return 异步混合候选
     */
    CompletionStage<List<VectorSearchHit>> retrieve(
        String query, VectorSearchRequest request);
}
