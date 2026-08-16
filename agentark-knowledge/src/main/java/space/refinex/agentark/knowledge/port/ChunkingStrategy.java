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

import space.refinex.agentark.knowledge.domain.ChunkProfile;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 定义把解析文本异步切分为稳定 Chunk 的 Provider Port。
 *
 * @author refinex
 */
public interface ChunkingStrategy {

    /**
     * 按不可变 Profile 异步切分文档。
     *
     * @param document 解析结果
     * @param profile  已发布 Chunk Profile
     * @return 有序且非空的 Chunk 列表
     */
    CompletionStage<List<KnowledgeChunk>> chunk(ParsedDocument document, ChunkProfile profile);
}
