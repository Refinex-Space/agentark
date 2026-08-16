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

package space.refinex.agentark.knowledge.adapter.out.fake;

import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.knowledge.domain.*;
import space.refinex.agentark.knowledge.port.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提供确定性的内存 Parser、Chunker、Embedding、Vector、Retriever 与 Reranker 测试替身。
 *
 * @author refinex
 */
public final class FakeKnowledgeProviders
    implements DocumentParser, ChunkingStrategy, EmbeddingProvider, VectorIndex, Retriever, Reranker {

    /**
     * 按项目和 Revision 保存测试向量，键不冒充生产 Collection 名。
     */
    private final Map<IndexKey, List<EmbeddedChunk>> index = new ConcurrentHashMap<>();

    /**
     * 创建空的确定性 Provider 集合。
     */
    public FakeKnowledgeProviders() {
        // 测试向量索引按调用逐步填充。
    }

    /**
     * 按确定性测试替身契约执行异步 Provider 操作。
     */
    @Override
    public CompletionStage<ParsedDocument> parse(
        DocumentRevision revision, ParserProfile profile, DocumentContentSource contentSource) {
        try (var input = contentSource.open()) {
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return CompletableFuture.completedFuture(
                new ParsedDocument(revision.id(), text, Map.of("parser", profile.key())));
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /**
     * 按确定性测试替身契约执行异步 Provider 操作。
     */
    @Override
    public CompletionStage<List<KnowledgeChunk>> chunk(
        ParsedDocument document, ChunkProfile profile) {
        String[] paragraphs = document.text().split("(?:\\R\\s*){2,}");
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int indexValue = 0; indexValue < paragraphs.length; indexValue++) {
            if (!paragraphs[indexValue].isBlank()) {
                chunks.add(new KnowledgeChunk(
                    "chunk-" + indexValue,
                    document.documentRevisionId(),
                    paragraphs[indexValue].strip(),
                    Map.of("chunkProfile", profile.key())));
            }
        }
        if (chunks.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("fake chunker requires non-empty text"));
        }
        return CompletableFuture.completedFuture(List.copyOf(chunks));
    }

    /**
     * 按确定性测试替身契约执行异步 Provider 操作。
     */
    @Override
    public CompletionStage<List<EmbeddedChunk>> embed(
        List<KnowledgeChunk> chunks, EmbeddingProfile profile) {
        List<EmbeddedChunk> embedded = chunks.stream()
            .map(chunk -> new EmbeddedChunk(
                chunk,
                new float[]{
                    normalized(chunk.text().hashCode()),
                    normalized(chunk.text().length()),
                    normalized(profile.key().hashCode())
                }))
            .toList();
        return CompletableFuture.completedFuture(embedded);
    }

    /**
     * 按确定性测试替身契约执行异步 Provider 操作。
     */
    @Override
    public CompletionStage<Void> upsert(
        ProjectId projectId, KnowledgeRevisionId revisionId, List<EmbeddedChunk> chunks) {
        index.put(new IndexKey(projectId, revisionId), List.copyOf(chunks));
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 按确定性测试替身契约执行异步 Provider 操作。
     */
    @Override
    public CompletionStage<Void> delete(ProjectId projectId, KnowledgeRevisionId revisionId) {
        index.remove(new IndexKey(projectId, revisionId));
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 按确定性测试替身契约执行异步 Provider 操作。
     */
    @Override
    public CompletionStage<List<RetrievalCandidate>> retrieve(
        ProjectId projectId,
        KnowledgeRevisionId revisionId,
        String query,
        RetrievalProfile profile,
        int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("query and limit are invalid"));
        }
        List<RetrievalCandidate> candidates = index
            .getOrDefault(new IndexKey(projectId, revisionId), List.of())
            .stream()
            .map(value -> new RetrievalCandidate(
                value.chunk(), value.chunk().text().contains(query) ? 1.0 : 0.25))
            .limit(limit)
            .toList();
        return CompletableFuture.completedFuture(candidates);
    }

    /**
     * 按确定性测试替身契约执行异步 Provider 操作。
     */
    @Override
    public CompletionStage<List<RetrievalCandidate>> rerank(
        String query,
        List<RetrievalCandidate> candidates,
        RetrievalProfile profile,
        int limit) {
        if (query == null || query.isBlank() || candidates == null || limit <= 0) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("rerank input is invalid"));
        }
        List<RetrievalCandidate> sorted = candidates.stream()
            .sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed())
            .limit(limit)
            .toList();
        return CompletableFuture.completedFuture(sorted);
    }

    /**
     * 把任意整数稳定归一化为零到一之间的测试浮点数。
     *
     * @param value 任意整数
     * @return 确定性归一化值
     */
    private static float normalized(int value) {
        return (Math.abs((long) value) % 10_000L) / 10_000.0F;
    }

    /**
     * 表示测试索引的显式租户键。
     *
     * @param projectId  项目标识
     * @param revisionId Knowledge Revision 标识
     * @author refinex
     */
    private record IndexKey(ProjectId projectId, KnowledgeRevisionId revisionId) {

        /**
         * 校验测试索引键。
         *
         * @param projectId  项目标识
         * @param revisionId Knowledge Revision 标识
         */
        private IndexKey {
            java.util.Objects.requireNonNull(projectId, "projectId must not be null");
            java.util.Objects.requireNonNull(revisionId, "revisionId must not be null");
        }
    }
}
