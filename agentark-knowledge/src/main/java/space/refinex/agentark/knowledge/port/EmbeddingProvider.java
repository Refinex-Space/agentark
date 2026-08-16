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

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 定义批次受控且异步执行的 Embedding Provider Port。
 *
 * @author refinex
 */
public interface EmbeddingProvider {

    /**
     * 异步生成 Chunk 向量，输出顺序和数量必须与输入一致。
     *
     * @param chunks  单次受控批次
     * @param profile 已发布 Embedding Profile
     * @return 带向量的 Chunk 列表
     */
    CompletionStage<List<EmbeddedChunk>> embed(
        List<KnowledgeChunk> chunks, EmbeddingProfile profile);
}
