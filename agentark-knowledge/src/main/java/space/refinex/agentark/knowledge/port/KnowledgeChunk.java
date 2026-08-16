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

import space.refinex.agentark.kernel.id.DocumentRevisionId;

import java.util.Map;
import java.util.Objects;

/**
 * 表示切分后的语言中立文本块，稳定 Key 不依赖向量数据库 Point ID。
 *
 * @param key                文档修订内稳定 Chunk Key
 * @param documentRevisionId 来源文档修订标识
 * @param text               Chunk 文本
 * @param metadata           非敏感检索元数据
 * @author refinex
 */
public record KnowledgeChunk(
    String key,
    DocumentRevisionId documentRevisionId,
    String text,
    Map<String, String> metadata) {

    /**
     * 校验 Chunk Key、来源、文本并防御性复制元数据。
     *
     * @param key                Chunk Key
     * @param documentRevisionId 来源文档修订标识
     * @param text               Chunk 文本
     * @param metadata           检索元数据
     */
    public KnowledgeChunk {
        if (key == null || !key.matches("[a-z0-9][a-z0-9_.:-]{0,127}")) {
            throw new IllegalArgumentException("chunk key is invalid");
        }
        Objects.requireNonNull(documentRevisionId, "documentRevisionId must not be null");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("chunk text must not be blank");
        }
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
    }
}
