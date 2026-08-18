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

import org.junit.jupiter.api.Test;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionCommand;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionResult;
import space.refinex.agentark.knowledge.application.IngestionModels.ResultStatus;
import space.refinex.agentark.knowledge.domain.KnowledgeRevisionStatus;
import space.refinex.agentark.knowledge.port.ChunkArtifactStore;
import space.refinex.agentark.knowledge.port.EmbeddedChunk;
import space.refinex.agentark.knowledge.port.KnowledgeChunk;
import space.refinex.agentark.knowledge.port.KnowledgeVectorStore;
import space.refinex.agentark.knowledge.port.ParsedDocument;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorScope;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchHit;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorVerificationRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorWriteRequest;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证异步摄取 Worker 的成功校验、失败结果和 Control 提交顺序。
 *
 * @author refinex
 */
class KnowledgeIngestionWorkerTest {

    /**
     * 验证成功结果只在向量写入和数量摘要校验完成后提交。
     */
    @Test
    void submitsSuccessOnlyAfterVectorVerification() {
        Phase14Fixtures.Fixture fixture = Phase14Fixtures.create(
            KnowledgeRevisionStatus.INGESTING);
        IngestionCommand command = Phase14Fixtures.command(fixture);
        RecordingVectorStore vectorStore = new RecordingVectorStore(true);
        KnowledgeIngestionWorker worker = worker(fixture, vectorStore, false);

        IngestionResult result = worker.execute(command).toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(ResultStatus.SUCCEEDED);
        assertThat(result.documentCount()).isEqualTo(1);
        assertThat(result.chunkCount()).isEqualTo(1);
        assertThat(vectorStore.verified).isTrue();
        assertThat(result.artifacts()).hasSize(1);
    }

    /**
     * 验证安全扫描失败不会执行 Parser、Embedding 或向量写入，并提交稳定失败码。
     */
    @Test
    void submitsStableFailureWhenSecurityScanRejectsDocument() {
        Phase14Fixtures.Fixture fixture = Phase14Fixtures.create(
            KnowledgeRevisionStatus.INGESTING);
        RecordingVectorStore vectorStore = new RecordingVectorStore(true);
        KnowledgeIngestionWorker worker = worker(fixture, vectorStore, true);

        IngestionResult result = worker.execute(Phase14Fixtures.command(fixture))
            .toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(ResultStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo("SECURITY_SCAN_FAILED");
        assertThat(vectorStore.upserted).isFalse();
    }

    /**
     * 验证 Qdrant 写入不可用时不会伪造成功，并提交可供 Control Outbox 传播的稳定失败结果。
     */
    @Test
    void submitsStableFailureWhenVectorStoreIsUnavailable() {
        Phase14Fixtures.Fixture fixture = Phase14Fixtures.create(
            KnowledgeRevisionStatus.INGESTING);
        RecordingVectorStore vectorStore = new RecordingVectorStore(true, true);
        KnowledgeIngestionWorker worker = worker(fixture, vectorStore, false);

        IngestionResult result = worker.execute(Phase14Fixtures.command(fixture))
            .toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(ResultStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo("VECTOR_UPSERT_FAILED");
        assertThat(vectorStore.verified).isFalse();
    }

    /**
     * 验证大文档形成四千个 Chunk 时仍严格按固定批次 Embedding，不生成无界 Provider 请求。
     */
    @Test
    void batchesLargeDocumentChunkSet() {
        Phase14Fixtures.Fixture fixture = Phase14Fixtures.create(
            KnowledgeRevisionStatus.INGESTING);
        IngestionCommand command = Phase14Fixtures.command(fixture);
        RecordingVectorStore vectorStore = new RecordingVectorStore(true);
        AtomicInteger batchCount = new AtomicInteger();
        AtomicInteger largestBatch = new AtomicInteger();
        int chunkCount = 4096;
        int batchSize = 64;
        KnowledgeIngestionWorker worker = new KnowledgeIngestionWorker(
            requestId -> CompletableFuture.completedFuture(Phase14Fixtures.plan(fixture)),
            CompletableFuture::completedFuture,
            revision -> new ByteArrayInputStream("large document".getBytes()),
            (revision, resolver) -> CompletableFuture.completedFuture(null),
            (revision, profile, resolver) -> CompletableFuture.completedFuture(
                new ParsedDocument(revision.id(), "large document", Map.of(
                    "source_trust", "UNTRUSTED_EXTERNAL"))),
            (document, profile) -> CompletableFuture.completedFuture(
                IntStream.range(0, chunkCount)
                    .mapToObj(index -> new KnowledgeChunk(
                        document.documentRevisionId().asString() + ":c" + index,
                        document.documentRevisionId(), "chunk-" + index, document.metadata()))
                    .toList()),
            new ChunkArtifactStore() {
                /**
                 * 返回代表大 Chunk 清单的固定内容寻址制品。
                 */
                @Override
                public CompletionStage<ObjectRef> put(
                    space.refinex.agentark.kernel.id.KnowledgeRevisionId revisionId,
                    List<KnowledgeChunk> chunks) {
                    return CompletableFuture.completedFuture(ObjectRef.of(
                        "object://knowledge/large-chunks.ndjson",
                        Checksum.sha256("large-chunks"), chunks.size(),
                        "application/x-ndjson"));
                }

                /**
                 * 当前批次测试不执行制品删除。
                 */
                @Override
                public CompletionStage<Void> delete(ObjectRef ref) {
                    return CompletableFuture.completedFuture(null);
                }
            },
            (chunks, profile) -> {
                batchCount.incrementAndGet();
                largestBatch.accumulateAndGet(chunks.size(), Math::max);
                return CompletableFuture.completedFuture(chunks.stream()
                    .map(chunk -> new EmbeddedChunk(chunk, new float[]{1, 0, 0})).toList());
            },
            vectorStore, Runnable::run, Clock.fixed(fixture.now(), ZoneOffset.UTC),
            batchSize, 3, Duration.ZERO);

        IngestionResult result = worker.execute(command).toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(ResultStatus.SUCCEEDED);
        assertThat(result.chunkCount()).isEqualTo(chunkCount);
        assertThat(batchCount).hasValue(chunkCount / batchSize);
        assertThat(largestBatch).hasValue(batchSize);
    }

    /**
     * 创建使用确定性 Fake Provider 的 Worker。
     *
     * @param fixture     测试夹具
     * @param vectorStore 记录型向量存储
     * @param rejectScan  是否拒绝安全扫描
     * @return 摄取 Worker
     */
    private KnowledgeIngestionWorker worker(
        Phase14Fixtures.Fixture fixture,
        RecordingVectorStore vectorStore,
        boolean rejectScan) {
        return new KnowledgeIngestionWorker(
            requestId -> CompletableFuture.completedFuture(Phase14Fixtures.plan(fixture)),
            CompletableFuture::completedFuture,
            revision -> new ByteArrayInputStream("AgentArk manual".getBytes()),
            (revision, resolver) -> rejectScan
                ? CompletableFuture.failedFuture(new IllegalArgumentException("malware"))
                : CompletableFuture.completedFuture(null),
            (revision, profile, resolver) -> CompletableFuture.completedFuture(
                new ParsedDocument(revision.id(), "AgentArk manual", Map.of(
                    "source_trust", "UNTRUSTED_EXTERNAL"))),
            (document, profile) -> CompletableFuture.completedFuture(List.of(
                new KnowledgeChunk(document.documentRevisionId().asString() + ":c000000",
                    document.documentRevisionId(), document.text(), document.metadata()))),
            new ChunkArtifactStore() {
                /**
                 * 返回固定 Chunk 制品引用。
                 */
                @Override
                public CompletionStage<ObjectRef> put(
                    space.refinex.agentark.kernel.id.KnowledgeRevisionId revisionId,
                    List<KnowledgeChunk> chunks) {
                    return CompletableFuture.completedFuture(ObjectRef.of(
                        "object://knowledge/chunks.ndjson", Checksum.sha256("chunks"), 6,
                        "application/x-ndjson"));
                }

                /**
                 * 当前测试无需删除制品。
                 */
                @Override
                public CompletionStage<Void> delete(ObjectRef ref) {
                    return CompletableFuture.completedFuture(null);
                }
            },
            (chunks, profile) -> CompletableFuture.completedFuture(chunks.stream()
                .map(chunk -> new EmbeddedChunk(chunk, new float[]{1, 0, 0})).toList()),
            vectorStore, Runnable::run, Clock.fixed(fixture.now(), ZoneOffset.UTC),
            32, 3, Duration.ZERO);
    }

    /**
     * 记录向量阶段调用并提供可控校验结果。
     *
     * @author refinex
     */
    private static final class RecordingVectorStore implements KnowledgeVectorStore {

        /**
         * 固定校验结果。
         */
        private final boolean verificationResult;

        /**
         * 是否模拟向量存储写入不可用。
         */
        private final boolean upsertFailure;

        /**
         * 是否执行过 Upsert。
         */
        private boolean upserted;

        /**
         * 是否执行过 Verify。
         */
        private boolean verified;

        /**
         * 创建记录型向量存储。
         *
         * @param verificationResult 固定校验结果
         */
        private RecordingVectorStore(boolean verificationResult) {
            this(verificationResult, false);
        }

        /**
         * 创建可控制写入失败的记录型向量存储。
         *
         * @param verificationResult 固定校验结果
         * @param upsertFailure      是否模拟写入失败
         */
        private RecordingVectorStore(boolean verificationResult, boolean upsertFailure) {
            this.verificationResult = verificationResult;
            this.upsertFailure = upsertFailure;
        }

        /**
         * 记录向量写入。
         */
        @Override
        public CompletionStage<Void> upsert(VectorWriteRequest request) {
            upserted = true;
            if (upsertFailure) {
                return CompletableFuture.failedFuture(
                    new IllegalStateException("qdrant unavailable"));
            }
            return CompletableFuture.completedFuture(null);
        }

        /**
         * 记录数量摘要校验。
         */
        @Override
        public CompletionStage<Boolean> verify(VectorVerificationRequest request) {
            verified = true;
            return CompletableFuture.completedFuture(verificationResult);
        }

        /**
         * 当前测试不执行检索。
         */
        @Override
        public CompletionStage<List<VectorSearchHit>> search(VectorSearchRequest request) {
            return CompletableFuture.completedFuture(List.of());
        }

        /**
         * 当前测试不执行删除。
         */
        @Override
        public CompletionStage<Void> delete(VectorScope scope) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
