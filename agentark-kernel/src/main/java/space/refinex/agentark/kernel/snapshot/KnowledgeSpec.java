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

package space.refinex.agentark.kernel.snapshot;

import java.util.Objects;

import space.refinex.agentark.kernel.id.KnowledgeRevisionId;

/**
 * 表示 Snapshot 固定的知识修订版本及其检索行为。
 *
 * @param knowledgeRevisionId 必须在发布前处于 READY 的知识修订版本标识
 * @param retrievalProfile    Provider 中立检索参数
 * @author refinex
 */
public record KnowledgeSpec(
    KnowledgeRevisionId knowledgeRevisionId, RetrievalSpec retrievalProfile) {

    /**
     * 校验并创建知识绑定。
     *
     * @param knowledgeRevisionId 知识修订版本标识
     * @param retrievalProfile    检索参数
     * @throws NullPointerException 当任一字段为 {@code null} 时抛出
     */
    public KnowledgeSpec {
        Objects.requireNonNull(
            knowledgeRevisionId, "KnowledgeSpec knowledgeRevisionId must not be null");
        Objects.requireNonNull(retrievalProfile, "KnowledgeSpec retrievalProfile must not be null");
    }
}
