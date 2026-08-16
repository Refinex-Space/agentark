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

package space.refinex.agentark.knowledge.adapter.out.vector.agentscope;

import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;
import space.refinex.agentark.kernel.id.DocumentId;
import space.refinex.agentark.knowledge.application.KnowledgeRetrievalService;
import space.refinex.agentark.knowledge.application.Phase14Fixtures;
import space.refinex.agentark.knowledge.application.RetrievalModels.RetrievalRequest;
import space.refinex.agentark.knowledge.domain.KnowledgeRevisionStatus;
import space.refinex.agentark.knowledge.port.KnowledgeVectorStore;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorScope;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchHit;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorVerificationRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorWriteRequest;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 AgentScope 防腐层只注册只读 Tool，且模型查询不能替换固定 Revision。
 *
 * @author refinex
 */
class AgentScopeKnowledgeAdapterTest {

    /**
     * 验证 Toolkit 注册与固定 Revision 请求映射。
     */
    @Test
    void registersReadOnlyRetrievalToolForFixedRevision() {
        Phase14Fixtures.Fixture fixture = Phase14Fixtures.create(KnowledgeRevisionStatus.READY);
        CapturingVectorStore vectorStore = new CapturingVectorStore();
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
            (query, profile) -> CompletableFuture.completedFuture(new float[]{1, 0, 0}),
            vectorStore, Optional.empty(),
            (query, candidates, profile, limit) -> CompletableFuture.completedFuture(candidates),
            trace -> { }, Clock.fixed(fixture.now(), ZoneOffset.UTC));
        AgentScopeKnowledgeAdapter adapter = new AgentScopeKnowledgeAdapter(
            service, request(fixture, Set.of(fixture.document().id()), 256),
            JsonMapper.builder().build());
        Toolkit toolkit = new Toolkit();

        adapter.register(toolkit);
        String json = adapter.retrieve("新的模型查询").block();

        assertThat(toolkit.getToolNames()).containsExactly("knowledge_retrieve");
        assertThat(json).contains("knowledgeRevisionId");
        assertThat(vectorStore.request.scope().knowledgeRevisionId())
            .isEqualTo(fixture.revision().id());
    }

    /**
     * 创建固定 READY Revision 检索请求。
     *
     * @param fixture 测试夹具
     * @param allowed 已授权文档集合
     * @param budget  上下文字符预算
     * @return 检索请求
     */
    private static RetrievalRequest request(
        Phase14Fixtures.Fixture fixture,
        Set<DocumentId> allowed,
        int budget) {
        Map<DocumentId, String> titles = allowed.isEmpty()
            ? Map.of() : Map.of(fixture.document().id(), fixture.document().title());
        return new RetrievalRequest(
            fixture.organizationId(), fixture.projectId(), fixture.revision(),
            fixture.embedding(), fixture.retrieval(), allowed, titles, "AgentArk 是什么？",
            10, 5, budget, 0, false);
    }

    /**
     * 捕获 AgentScope Tool 最终发往 Provider 中立向量端口的请求。
     *
     * @author refinex
     */
    private static final class CapturingVectorStore implements KnowledgeVectorStore {

        /**
         * 捕获的检索请求。
         */
        private VectorSearchRequest request;

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
         * 捕获请求并返回空结果。
         */
        @Override
        public CompletionStage<List<VectorSearchHit>> search(VectorSearchRequest request) {
            this.request = request;
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
