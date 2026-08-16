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
 * 表示 Provider 中立的解析结果，仅在异步摄取流水线内部传递。
 *
 * @param documentRevisionId 原始文档修订标识
 * @param text               解析后的文本
 * @param metadata           解析器产生的非敏感元数据
 * @author refinex
 */
public record ParsedDocument(
    DocumentRevisionId documentRevisionId, String text, Map<String, String> metadata) {

    /**
     * 校验解析结果不为空并防御性复制元数据。
     *
     * @param documentRevisionId 原始文档修订标识
     * @param text               解析文本
     * @param metadata           解析元数据
     */
    public ParsedDocument {
        Objects.requireNonNull(documentRevisionId, "documentRevisionId must not be null");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("parsed text must not be blank");
        }
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
    }
}
