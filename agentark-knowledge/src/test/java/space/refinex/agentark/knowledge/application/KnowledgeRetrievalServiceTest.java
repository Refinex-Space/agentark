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
import space.refinex.agentark.knowledge.application.RetrievalModels.RetrievalRequest;
import space.refinex.agentark.knowledge.application.RetrievalModels.RetrievalResult;
import space.refinex.agentark.knowledge.domain.KnowledgeRevisionStatus;
import space.refinex.agentark.knowledge.port.KnowledgeChunk;
import space.refinex.agentark.knowledge.port.KnowledgeVectorStore;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorScope;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchHit;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorVerificationRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorWriteRequest;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证固定 Revision 检索的 ACL、预算、Citation、Trace 和无结果策略。
 *
 * @author refinex
 */
class KnowledgeRetrievalServiceTest {

    /**
     * 验证检索强制使用固定 Revision 与文档白名单并产生可追踪 Citation。
     */
    @Test
    void retrievesCitedContextWithinFixedRevisionAndBudget() {
        Phase14Fixtures.Fixture fixture = Phase14Fixtures.create(KnowledgeRevisionStatus.READY);
        RecordingVectorStore vectorStore = new RecordingVectorStore(fixture);
        AtomicReference<RetrievalModels.RetrievalTrace> trace = new AtomicReference<>();
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
            (query, profile) -> CompletableFuture.completedFuture(new float[]{1, 0, 0}),
            vectorStore, Optional.empty(),
            (query, candidates, profile, limit) -> CompletableFuture.completedFuture(
                candidates.stream().limit(limit).toList()),
            trace::set, Clock.fixed(fixture.now(), ZoneOffset.UTC));
        RetrievalRequest request = request(fixture, Set.of(fixture.document().id()), 8);

        RetrievalResult result = service.retrieve(request).toCompletableFuture().join();

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().text()).hasSize(8);
        assertThat(result.items().getFirst().citation().documentId())
            .isEqualTo(fixture.document().id());
        assertThat(result.items().getFirst().citation().sourceTrust())
            .isEqualTo("UNTRUSTED_EXTERNAL");
        assertThat(vectorStore.lastRequest.scope().knowledgeRevisionId())
            .isEqualTo(fixture.revision().id());
        assertThat(vectorStore.lastRequest.allowedDocumentIds())
            .containsExactly(fixture.document().id());
        assertThat(trace.get()).isEqualTo(result.trace());
    }

    /**
     * 验证空文档 ACL 直接返回空结果且不调用向量后端。
     */
    @Test
    void returnsEmptyWithoutCallingProvidersWhenAclIsEmpty() {
        Phase14Fixtures.Fixture fixture = Phase14Fixtures.create(KnowledgeRevisionStatus.READY);
        RecordingVectorStore vectorStore = new RecordingVectorStore(fixture);
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
            (query, profile) -> CompletableFuture.failedFuture(
                new AssertionError("embedding must not be called")),
            vectorStore, Optional.empty(),
            (query, candidates, profile, limit) -> CompletableFuture.completedFuture(candidates),
            trace -> { }, Clock.fixed(fixture.now(), ZoneOffset.UTC));

        RetrievalResult result = service.retrieve(request(fixture, Set.of(), 100))
            .toCompletableFuture().join();

        assertThat(result.items()).isEmpty();
        assertThat(vectorStore.lastRequest).isNull();
    }

    /**
     * 创建固定 READY Revision 检索请求。
     *
     * @param fixture 测试夹具
     * @param allowed 已授权文档集合
     * @param budget  上下文字符预算
     * @return 检索请求
     */
    static RetrievalRequest request(
        Phase14Fixtures.Fixture fixture,
        Set<space.refinex.agentark.kernel.id.DocumentId> allowed,
        int budget) {
        Map<space.refinex.agentark.kernel.id.DocumentId, String> titles = allowed.isEmpty()
            ? Map.of() : Map.of(fixture.document().id(), fixture.document().title());
        return new RetrievalRequest(
            fixture.organizationId(), fixture.projectId(), fixture.revision(),
            fixture.embedding(), fixture.retrieval(), allowed, titles, "AgentArk 是什么？",
            10, 5, budget, 0, false);
    }

    /**
     * 记录 Adapter 接收的强制 Filter 请求并返回一个固定命中。
     *
     * @author refinex
     */
    private static final class RecordingVectorStore implements KnowledgeVectorStore {

        /**
         * 测试夹具。
         */
        private final Phase14Fixtures.Fixture fixture;

        /**
         * 最后一次检索请求。
         */
        private VectorSearchRequest lastRequest;

        /**
         * 创建记录型向量存储。
         *
         * @param fixture 测试夹具
         */
        private RecordingVectorStore(Phase14Fixtures.Fixture fixture) {
            this.fixture = fixture;
        }

        /**
         * 当前测试不执行写入。
         */
        @Override
        public CompletionStage<Void> upsert(VectorWriteRequest request) {
            return CompletableFuture.completedFuture(null);
        }

        /**
         * 当前测试不执行校验。
         */
        @Override
        public CompletionStage<Boolean> verify(VectorVerificationRequest request) {
            return CompletableFuture.completedFuture(true);
        }

        /**
         * 记录请求并返回带不受信标记的固定 Chunk。
         */
        @Override
        public CompletionStage<List<VectorSearchHit>> search(VectorSearchRequest request) {
            lastRequest = request;
            KnowledgeChunk chunk = new KnowledgeChunk(
                fixture.documentRevision().id().asString() + ":c000000",
                fixture.documentRevision().id(), "AgentArk 是托管 Agent 平台。",
                Map.of("source_trust", "UNTRUSTED_EXTERNAL"));
            return CompletableFuture.completedFuture(List.of(
                new VectorSearchHit(chunk, fixture.document().id(), 0.95)));
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
