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

import space.refinex.agentark.kernel.id.DocumentId;
import space.refinex.agentark.kernel.id.DocumentRevisionId;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.knowledge.application.IngestionModels.ArtifactKind;
import space.refinex.agentark.knowledge.application.IngestionModels.ArtifactReference;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionCommand;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionPlan;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionResult;
import space.refinex.agentark.knowledge.application.IngestionModels.ResultStatus;
import space.refinex.agentark.knowledge.domain.DocumentRevision;
import space.refinex.agentark.knowledge.port.ChunkArtifactStore;
import space.refinex.agentark.knowledge.port.ChunkingStrategy;
import space.refinex.agentark.knowledge.port.DocumentContentResolver;
import space.refinex.agentark.knowledge.port.DocumentSecurityScanner;
import space.refinex.agentark.knowledge.port.EmbeddedChunk;
import space.refinex.agentark.knowledge.port.EmbeddingProvider;
import space.refinex.agentark.knowledge.port.IngestionPlanSource;
import space.refinex.agentark.knowledge.port.IngestionResultSink;
import space.refinex.agentark.knowledge.port.KnowledgeChunk;
import space.refinex.agentark.knowledge.port.KnowledgeVectorStore;
import space.refinex.agentark.knowledge.port.ParsedDocument;
import space.refinex.agentark.knowledge.port.ParserSandbox;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorScope;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorVerificationRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorWriteRequest;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;

/**
 * 在专用 Worker 执行器中编排安全扫描、隔离解析、切分、Embedding、Qdrant 写入和结果提交。
 *
 * <p>该应用服务只通过 Internal Port 读取计划和提交结果，绝不访问 Control DataSource。
 *
 * @author refinex
 */
public final class KnowledgeIngestionWorker {

    /**
     * Worker 加载固定摄取计划的 Internal Port。
     */
    private final IngestionPlanSource planSource;

    /**
     * Worker 提交幂等摄取结果的 Internal Port。
     */
    private final IngestionResultSink resultSink;

    /**
     * 原文受控读取端口。
     */
    private final DocumentContentResolver contentResolver;

    /**
     * 文档安全扫描端口。
     */
    private final DocumentSecurityScanner securityScanner;

    /**
     * 受限解析 Sandbox。
     */
    private final ParserSandbox parserSandbox;

    /**
     * 版本化切分策略。
     */
    private final ChunkingStrategy chunkingStrategy;

    /**
     * Chunk 不可变制品存储。
     */
    private final ChunkArtifactStore artifactStore;

    /**
     * 批次 Embedding Provider。
     */
    private final EmbeddingProvider embeddingProvider;

    /**
     * 强制租户过滤的向量存储。
     */
    private final KnowledgeVectorStore vectorStore;

    /**
     * 阻塞编排专用执行器。
     */
    private final Executor workerExecutor;

    /**
     * 完成时间来源。
     */
    private final Clock clock;

    /**
     * 单次 Embedding 最大 Chunk 数。
     */
    private final int embeddingBatchSize;

    /**
     * Embedding 最大尝试次数。
     */
    private final int embeddingAttempts;

    /**
     * Embedding 重试基础退避。
     */
    private final Duration retryBackoff;

    /**
     * 创建异步摄取 Worker。
     *
     * @param planSource         摄取计划来源
     * @param resultSink         摄取结果接收端
     * @param contentResolver    原文读取端口
     * @param securityScanner    安全扫描端口
     * @param parserSandbox      解析 Sandbox
     * @param chunkingStrategy   切分策略
     * @param artifactStore      Chunk 制品存储
     * @param embeddingProvider  Embedding Provider
     * @param vectorStore        向量存储
     * @param workerExecutor     Worker 专用执行器
     * @param clock              时钟
     * @param embeddingBatchSize Embedding 批次大小
     * @param embeddingAttempts  Embedding 最大尝试次数
     * @param retryBackoff       重试基础退避
     */
    public KnowledgeIngestionWorker(
        IngestionPlanSource planSource,
        IngestionResultSink resultSink,
        DocumentContentResolver contentResolver,
        DocumentSecurityScanner securityScanner,
        ParserSandbox parserSandbox,
        ChunkingStrategy chunkingStrategy,
        ChunkArtifactStore artifactStore,
        EmbeddingProvider embeddingProvider,
        KnowledgeVectorStore vectorStore,
        Executor workerExecutor,
        Clock clock,
        int embeddingBatchSize,
        int embeddingAttempts,
        Duration retryBackoff) {
        this.planSource = Objects.requireNonNull(planSource, "planSource must not be null");
        this.resultSink = Objects.requireNonNull(resultSink, "resultSink must not be null");
        this.contentResolver = Objects.requireNonNull(
            contentResolver, "contentResolver must not be null");
        this.securityScanner = Objects.requireNonNull(
            securityScanner, "securityScanner must not be null");
        this.parserSandbox = Objects.requireNonNull(parserSandbox, "parserSandbox must not be null");
        this.chunkingStrategy = Objects.requireNonNull(
            chunkingStrategy, "chunkingStrategy must not be null");
        this.artifactStore = Objects.requireNonNull(artifactStore, "artifactStore must not be null");
        this.embeddingProvider = Objects.requireNonNull(
            embeddingProvider, "embeddingProvider must not be null");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
        this.workerExecutor = Objects.requireNonNull(
            workerExecutor, "workerExecutor must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (embeddingBatchSize < 1 || embeddingBatchSize > 1024
            || embeddingAttempts < 1 || embeddingAttempts > 10
            || retryBackoff == null || retryBackoff.isNegative()
            || retryBackoff.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("ingestion worker retry configuration is invalid");
        }
        this.embeddingBatchSize = embeddingBatchSize;
        this.embeddingAttempts = embeddingAttempts;
        this.retryBackoff = retryBackoff;
    }

    /**
     * 在专用 Worker 执行器异步执行一次 Attempt，并始终向 Control 提交明确结果。
     *
     * @param command Scheduler 发出的固定 Attempt 命令
     * @return Control 接受后的持久摄取结果
     */
    public CompletionStage<IngestionResult> execute(IngestionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return CompletableFuture.supplyAsync(() -> run(command), workerExecutor)
            .thenCompose(resultSink::submit);
    }

    /**
     * 同步编排当前 Worker 线程中的全部阶段。
     *
     * @param command 摄取命令
     * @return 待提交结果
     */
    private IngestionResult run(IngestionCommand command) {
        try {
            IngestionPlan plan = join(planSource.load(command.requestId()), "PLAN_LOAD_FAILED");
            requireMatchingCommand(command, plan);
            List<KnowledgeChunk> chunks = parseAndChunk(plan);
            ObjectRef chunkArtifact = join(
                artifactStore.put(command.revisionId(), chunks), "ARTIFACT_WRITE_FAILED");
            List<EmbeddedChunk> embedded = embed(chunks, plan);
            Checksum checksum = manifestChecksum(embedded);
            VectorScope scope = new VectorScope(
                command.organizationId(), command.projectId(), command.revisionId());
            Map<DocumentRevisionId, DocumentId> documentIds = new LinkedHashMap<>();
            for (DocumentRevision document : plan.documents()) {
                documentIds.put(document.id(), document.documentId());
            }
            join(vectorStore.upsert(new VectorWriteRequest(
                scope, embedded, documentIds, checksum)), "VECTOR_UPSERT_FAILED");
            boolean verified = join(vectorStore.verify(new VectorVerificationRequest(
                scope, embedded.size(), checksum)), "VECTOR_VERIFY_FAILED");
            if (!verified) {
                throw new IngestionStageException("VECTOR_VERIFY_MISMATCH", null);
            }
            return result(command, plan.documents().size(), chunks.size(), checksum,
                List.of(new ArtifactReference(ArtifactKind.CHUNKS, chunkArtifact)),
                ResultStatus.SUCCEEDED, "");
        } catch (IngestionStageException exception) {
            return result(command, 0, 0,
                Checksum.sha256(command.attemptId() + ":" + exception.code()),
                List.of(), ResultStatus.FAILED, exception.code());
        } catch (RuntimeException exception) {
            return result(command, 0, 0,
                Checksum.sha256(command.attemptId() + ":INGESTION_FAILED"),
                List.of(), ResultStatus.FAILED, "INGESTION_FAILED");
        }
    }

    /**
     * 校验 Scheduler 命令与 Control 返回计划完全一致。
     *
     * @param command 摄取命令
     * @param plan    摄取计划
     */
    private void requireMatchingCommand(IngestionCommand command, IngestionPlan plan) {
        if (!command.requestId().equals(plan.requestId())
            || !command.organizationId().equals(plan.organizationId())
            || !command.projectId().equals(plan.projectId())
            || !command.revisionId().equals(plan.revision().id())) {
            throw new IngestionStageException("PLAN_SCOPE_MISMATCH", null);
        }
    }

    /**
     * 串行扫描和解析文档，保持 Revision 内确定性顺序。
     *
     * @param plan 固定摄取计划
     * @return 有序 Chunk
     */
    private List<KnowledgeChunk> parseAndChunk(IngestionPlan plan) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (DocumentRevision document : plan.documents()) {
            join(securityScanner.scan(document, contentResolver), "SECURITY_SCAN_FAILED");
            ParsedDocument parsed = join(parserSandbox.parse(
                document, plan.parserProfile(), contentResolver), "PARSER_FAILED");
            chunks.addAll(join(chunkingStrategy.chunk(
                parsed, plan.chunkProfile()), "CHUNKING_FAILED"));
        }
        if (chunks.isEmpty()) {
            throw new IngestionStageException("EMPTY_CHUNK_SET", null);
        }
        return List.copyOf(chunks);
    }

    /**
     * 分批生成向量并对可重试 Provider 失败执行有界指数退避。
     *
     * @param chunks 待向量化 Chunk
     * @param plan   固定摄取计划
     * @return 保持输入顺序的带向量 Chunk
     */
    private List<EmbeddedChunk> embed(List<KnowledgeChunk> chunks, IngestionPlan plan) {
        List<EmbeddedChunk> embedded = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += embeddingBatchSize) {
            List<KnowledgeChunk> batch = chunks.subList(
                start, Math.min(chunks.size(), start + embeddingBatchSize));
            List<EmbeddedChunk> result = null;
            RuntimeException lastFailure = null;
            for (int attempt = 1; attempt <= embeddingAttempts; attempt++) {
                try {
                    result = embeddingProvider.embed(batch, plan.embeddingProfile())
                        .toCompletableFuture().join();
                    break;
                } catch (CompletionException exception) {
                    lastFailure = exception;
                    if (attempt < embeddingAttempts) {
                        LockSupport.parkNanos(retryBackoff.multipliedBy(attempt).toNanos());
                    }
                }
            }
            if (result == null) {
                throw new IngestionStageException("EMBEDDING_FAILED", lastFailure);
            }
            requireMatchingEmbeddingBatch(batch, result);
            embedded.addAll(result);
        }
        return List.copyOf(embedded);
    }

    /**
     * 确认 Provider 未改变 Chunk 数量或顺序。
     *
     * @param chunks   输入 Chunk
     * @param embedded Provider 输出
     */
    private void requireMatchingEmbeddingBatch(
        List<KnowledgeChunk> chunks, List<EmbeddedChunk> embedded) {
        if (embedded.size() != chunks.size()) {
            throw new IngestionStageException("EMBEDDING_COUNT_MISMATCH", null);
        }
        for (int index = 0; index < chunks.size(); index++) {
            if (!chunks.get(index).equals(embedded.get(index).chunk())) {
                throw new IngestionStageException("EMBEDDING_ORDER_MISMATCH", null);
            }
        }
    }

    /**
     * 对 Chunk 身份、文本和向量位模式生成确定性 SHA-256 清单摘要。
     *
     * @param chunks 带向量 Chunk
     * @return 清单摘要
     */
    private Checksum manifestChecksum(List<EmbeddedChunk> chunks) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                for (EmbeddedChunk embedded : chunks) {
                    KnowledgeChunk chunk = embedded.chunk();
                    output.writeUTF(chunk.key());
                    output.writeUTF(chunk.documentRevisionId().asString());
                    output.writeUTF(Checksum.sha256(chunk.text()).value());
                    for (float value : embedded.vector()) {
                        output.writeInt(Float.floatToIntBits(value));
                    }
                }
            }
            return Checksum.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IngestionStageException("MANIFEST_HASH_FAILED", exception);
        }
    }

    /**
     * 构造成功或失败摄取结果。
     *
     * @param command       摄取命令
     * @param documentCount 文档数量
     * @param chunkCount    Chunk 数量
     * @param checksum      清单摘要
     * @param artifacts     制品引用
     * @param status        结果状态
     * @param failureCode   稳定失败代码
     * @return 摄取结果
     */
    private IngestionResult result(
        IngestionCommand command,
        int documentCount,
        int chunkCount,
        Checksum checksum,
        List<ArtifactReference> artifacts,
        ResultStatus status,
        String failureCode) {
        return new IngestionResult(
            JobId.generate().value(), command.requestId(), command.organizationId(),
            command.projectId(), command.revisionId(), command.schedulerJobId(),
            command.attemptId(), command.idempotencyKey(), documentCount, chunkCount,
            checksum, artifacts, status, failureCode, clock.instant());
    }

    /**
     * 等待当前 Worker 阶段并把实现异常转换为稳定阶段代码。
     *
     * @param stage 异步阶段
     * @param code  稳定失败代码
     * @param <T>   结果类型
     * @return 阶段结果
     */
    private <T> T join(CompletionStage<T> stage, String code) {
        try {
            return stage.toCompletableFuture().join();
        } catch (CompletionException exception) {
            throw new IngestionStageException(code, exception.getCause());
        } catch (RuntimeException exception) {
            throw new IngestionStageException(code, exception);
        }
    }

    /**
     * 携带稳定失败代码且不向外暴露 Provider 响应正文的内部异常。
     *
     * @author refinex
     */
    private static final class IngestionStageException extends RuntimeException {

        /**
         * 稳定失败代码。
         */
        private final String code;

        /**
         * 创建阶段异常。
         *
         * @param code  稳定失败代码
         * @param cause 原始异常
         */
        private IngestionStageException(String code, Throwable cause) {
            super(code, cause);
            this.code = code;
        }

        /**
         * 返回稳定失败代码。
         *
         * @return 稳定失败代码
         */
        private String code() {
            return code;
        }
    }
}
