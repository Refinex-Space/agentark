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

import java.util.Objects;

/**
 * 表示 Provider 中立的召回候选与归一化分数。
 *
 * @param chunk 候选 Chunk
 * @param score 零到一之间的相关性分数
 * @author refinex
 */
public record RetrievalCandidate(KnowledgeChunk chunk, double score) {

    /**
     * 校验候选与有限分数范围。
     *
     * @param chunk 候选 Chunk
     * @param score 相关性分数
     */
    public RetrievalCandidate {
        Objects.requireNonNull(chunk, "chunk must not be null");
        if (!Double.isFinite(score) || score < 0 || score > 1) {
            throw new IllegalArgumentException("score must be between zero and one");
        }
    }
}
