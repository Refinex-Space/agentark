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

package space.refinex.agentark.knowledge.application;

import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.knowledge.application.RetrievalModels.*;
import space.refinex.agentark.knowledge.port.*;
import space.refinex.agentark.knowledge.port.VectorStoreModels.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 编排固定 READY Revision 的 Query Embedding、向量/Hybrid 召回、重排、去重、预算和 Citation。
 *
 * @author refinex
 */
public final class KnowledgeRetrievalService {

    /**
     * 查询 Embedding Provider。
     */
    private final QueryEmbeddingProvider embeddingProvider;

    /**
     * 强制租户过滤的向量后端。
     */
    private final KnowledgeVectorStore vectorStore;

    /**
     * 可选 Hybrid 召回 Port。
     */
    private final Optional<HybridRetriever> hybridRetriever;

    /**
     * Provider 中立重排器。
     */
    private final Reranker reranker;

    /**
     * 不可静默丢弃的 Trace 发布 Port。
     */
    private final RetrievalTelemetryPort telemetry;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 创建检索应用服务。
     *
     * @param embeddingProvider 查询 Embedding Provider
     * @param vectorStore       向量后端
     * @param hybridRetriever   可选 Hybrid Port
     * @param reranker          重排器
     * @param telemetry         Trace 发布 Port
     * @param clock             UTC 时钟
     */
    public KnowledgeRetrievalService(
        QueryEmbeddingProvider embeddingProvider,
        KnowledgeVectorStore vectorStore,
        Optional<HybridRetriever> hybridRetriever,
        Reranker reranker,
        RetrievalTelemetryPort telemetry,
        Clock clock) {
        this.embeddingProvider = Objects.requireNonNull(
            embeddingProvider, "embeddingProvider must not be null");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
        this.hybridRetriever = Objects.requireNonNull(
            hybridRetriever, "hybridRetriever must not be null");
        this.reranker = Objects.requireNonNull(reranker, "reranker must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 执行一次固定 Revision 检索；无结果返回空列表，Provider 故障显式失败而不伪造空结果。
     *
     * @param request 已完成租户、READY、Profile 与文档 ACL 校验的请求
     * @return 带 Citation 和 Trace 的异步结果
     */
    public CompletionStage<RetrievalResult> retrieve(RetrievalRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Instant startedAt = Instant.now(clock);
        VectorScope scope = new VectorScope(
            request.organizationId(), request.projectId(), request.revision().id());
        if (request.allowedDocumentIds().isEmpty()) {
            return CompletableFuture.completedFuture(finish(
                request, startedAt, List.of(), 0, 0, 0));
        }
        return embeddingProvider.embedQuery(request.query(), request.embeddingProfile())
            .thenCompose(vector -> {
                VectorSearchRequest search = new VectorSearchRequest(
                    scope, request.allowedDocumentIds(), vector, request.candidateLimit(),
                    request.scoreThreshold());
                CompletionStage<List<VectorSearchHit>> vectorHits = vectorStore.search(search);
                CompletionStage<List<VectorSearchHit>> hybridHits = request.hybridEnabled()
                    ? hybridRetriever.orElseThrow(() ->
                        new IllegalStateException("hybrid retrieval is enabled without a provider"))
                    .retrieve(request.query(), search)
                    : CompletableFuture.completedFuture(List.of());
                return vectorHits.thenCombine(hybridHits, SearchCandidates::new);
            })
            .thenCompose(candidates -> {
                List<VectorSearchHit> deduplicated = deduplicate(
                    candidates.vector(), candidates.hybrid());
                List<RetrievalCandidate> neutral = deduplicated.stream()
                    .map(hit -> new RetrievalCandidate(hit.chunk(), hit.score()))
                    .toList();
                return reranker.rerank(
                        request.query(), neutral, request.retrievalProfile(), request.resultLimit())
                    .thenApply(reranked -> finish(
                        request, startedAt,
                        items(request, reranked, deduplicated),
                        candidates.vector().size(), candidates.hybrid().size(), deduplicated.size()));
            });
    }

    /**
     * 合并候选并按文档修订与 Chunk Key 去重，保留最高分结果。
     *
     * @param vector 向量候选
     * @param hybrid Hybrid 候选
     * @return 分数降序候选
     */
    private List<VectorSearchHit> deduplicate(
        List<VectorSearchHit> vector, List<VectorSearchHit> hybrid) {
        Map<String, VectorSearchHit> unique = new LinkedHashMap<>();
        java.util.stream.Stream.concat(vector.stream(), hybrid.stream()).forEach(hit -> {
            String key = hit.chunk().documentRevisionId().asString() + ":" + hit.chunk().key();
            unique.merge(key, hit, (left, right) -> left.score() >= right.score() ? left : right);
        });
        return unique.values().stream()
            .sorted(Comparator.comparingDouble(VectorSearchHit::score).reversed())
            .toList();
    }

    /**
     * 将重排结果绑定回文档身份，并执行严格上下文字符预算。
     *
     * @param request      检索请求
     * @param reranked     重排结果
     * @param sourceHits   带文档身份的去重候选
     * @return 最终检索项
     */
    private List<RetrievalItem> items(
        RetrievalRequest request,
        List<RetrievalCandidate> reranked,
        List<VectorSearchHit> sourceHits) {
        Map<String, VectorSearchHit> byChunk = new HashMap<>();
        sourceHits.forEach(hit -> byChunk.put(
            hit.chunk().documentRevisionId().asString() + ":" + hit.chunk().key(), hit));
        List<RetrievalItem> items = new ArrayList<>();
        int remaining = request.contextBudgetChars();
        for (RetrievalCandidate candidate : reranked) {
            if (remaining == 0 || items.size() == request.resultLimit()) {
                break;
            }
            String key = candidate.chunk().documentRevisionId().asString()
                + ":" + candidate.chunk().key();
            VectorSearchHit source = Objects.requireNonNull(
                byChunk.get(key), "reranker returned an unknown candidate");
            String text = candidate.chunk().text();
            String excerpt = text.length() <= remaining ? text : text.substring(0, remaining);
            if (!excerpt.isBlank()) {
                items.add(new RetrievalItem(
                    excerpt, candidate.score(), new Citation(
                        source.documentId(), candidate.chunk().documentRevisionId(),
                        candidate.chunk().key(), request.documentTitles().get(source.documentId()),
                        "UNTRUSTED_EXTERNAL")));
                remaining -= excerpt.length();
            }
        }
        return List.copyOf(items);
    }

    /**
     * 构造并发布不含查询正文和 Chunk 正文的 Trace。
     *
     * @param request             检索请求
     * @param startedAt           开始时刻
     * @param items               最终结果
     * @param vectorCandidates    向量候选数
     * @param hybridCandidates    Hybrid 候选数
     * @param deduplicatedCount   去重候选数
     * @return 带 Trace 的结果
     */
    private RetrievalResult finish(
        RetrievalRequest request,
        Instant startedAt,
        List<RetrievalItem> items,
        int vectorCandidates,
        int hybridCandidates,
        int deduplicatedCount) {
        Instant completedAt = Instant.now(clock);
        RetrievalTrace trace = new RetrievalTrace(
            JobId.generate().value(), request.revision().id(), vectorCandidates,
            hybridCandidates, deduplicatedCount, items.size(), request.query().length(),
            items.stream().mapToInt(item -> item.text().length()).sum(),
            Math.max(0, Duration.between(startedAt, completedAt).toMillis()), completedAt);
        telemetry.record(trace);
        return new RetrievalResult(items, trace);
    }

    /**
     * @param vector 向量候选
     * @param hybrid Hybrid 候选
     * @author refinex
     */
    private record SearchCandidates(
        List<VectorSearchHit> vector, List<VectorSearchHit> hybrid) {

        /**
         * 防御性复制两类候选。
         *
         * @param vector 向量候选
         * @param hybrid Hybrid 候选
         */
        private SearchCandidates {
            vector = List.copyOf(vector);
            hybrid = List.copyOf(hybrid);
        }
    }
}
