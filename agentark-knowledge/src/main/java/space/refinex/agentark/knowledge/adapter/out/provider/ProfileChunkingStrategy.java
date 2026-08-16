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

package space.refinex.agentark.knowledge.adapter.out.provider;

import space.refinex.agentark.knowledge.domain.ChunkProfile;
import space.refinex.agentark.knowledge.port.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.concurrent.*;

/**
 * 按不可变 Chunk Profile 的字符上限与重叠量生成稳定、有序且带信任标记的 Chunk。
 *
 * @author refinex
 */
public final class ProfileChunkingStrategy implements ChunkingStrategy {

    /**
     * 统一 JSON Mapper。
     */
    private final JsonMapper jsonMapper;

    /**
     * CPU 切分执行器。
     */
    private final Executor executor;

    /**
     * 创建版本化 Chunk 策略。
     *
     * @param jsonMapper JSON Mapper
     * @param executor   CPU 执行器
     */
    public ProfileChunkingStrategy(JsonMapper jsonMapper, Executor executor) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * 使用 Profile 中的 {@code maxCharacters} 和 {@code overlapCharacters} 执行确定性切分。
     *
     * @param document 解析结果
     * @param profile  固定 Chunk Profile
     * @return 异步有序 Chunk
     */
    @Override
    public CompletionStage<List<KnowledgeChunk>> chunk(
        ParsedDocument document, ChunkProfile profile) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        return CompletableFuture.supplyAsync(() -> split(document, profile), executor);
    }

    /**
     * 解析受限 Profile 配置并执行字符窗口切分。
     *
     * @param document 解析结果
     * @param profile  Chunk Profile
     * @return 不可变 Chunk 列表
     */
    private List<KnowledgeChunk> split(ParsedDocument document, ChunkProfile profile) {
        JsonNode root = jsonMapper.readTree(profile.configJson());
        int maximum = integer(root.get("maxCharacters"), 1000);
        int overlap = integer(root.get("overlapCharacters"), 100);
        if (maximum < 128 || maximum > 32_768 || overlap < 0 || overlap >= maximum) {
            throw new IllegalArgumentException("chunk profile character limits are invalid");
        }
        List<KnowledgeChunk> chunks = new ArrayList<>();
        String text = document.text();
        int start = 0;
        int ordinal = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + maximum);
            if (end < text.length()) {
                int boundary = text.lastIndexOf('\n', end);
                if (boundary > start + maximum / 2) {
                    end = boundary;
                }
            }
            String value = text.substring(start, end).strip();
            if (!value.isEmpty()) {
                Map<String, String> metadata = new LinkedHashMap<>(document.metadata());
                metadata.put("chunk_ordinal", Integer.toString(ordinal));
                metadata.put("source_trust", "UNTRUSTED_EXTERNAL");
                chunks.add(new KnowledgeChunk(
                    document.documentRevisionId().asString() + ":c" + String.format("%06d", ordinal),
                    document.documentRevisionId(), value, metadata));
                ordinal++;
            }
            if (end == text.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("chunk strategy produced no content");
        }
        return List.copyOf(chunks);
    }

    /**
     * 从 JSON 整数字段读取配置，缺失时返回安全默认值。
     *
     * @param node         JSON 字段
     * @param defaultValue 安全默认值
     * @return 配置整数
     */
    private int integer(JsonNode node, int defaultValue) {
        if (node == null) {
            return defaultValue;
        }
        if (!node.canConvertToInt()) {
            throw new IllegalArgumentException("chunk profile integer field is invalid");
        }
        return node.intValue();
    }
}
