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

import space.refinex.agentark.knowledge.domain.EmbeddingProfile;

import java.util.concurrent.CompletionStage;

/**
 * 定义使用固定 Embedding Profile 生成查询向量的 Provider Port。
 *
 * @author refinex
 */
@FunctionalInterface
public interface QueryEmbeddingProvider {

    /**
     * 生成查询向量，返回数组所有权转移给调用方。
     *
     * @param query   已限制长度的查询文本
     * @param profile 固定 Embedding Profile
     * @return 异步查询向量
     */
    CompletionStage<float[]> embedQuery(String query, EmbeddingProfile profile);
}
