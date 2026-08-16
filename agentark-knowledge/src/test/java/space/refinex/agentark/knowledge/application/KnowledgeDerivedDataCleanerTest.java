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
import space.refinex.agentark.knowledge.port.ChunkArtifactStore;
import space.refinex.agentark.knowledge.port.KnowledgeChunk;
import space.refinex.agentark.knowledge.port.KnowledgeVectorStore;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorScope;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchHit;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorVerificationRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorWriteRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证删除传播先按可信 Revision 清理向量，再删除结果登记的派生对象。
 *
 * @author refinex
 */
class KnowledgeDerivedDataCleanerTest {

    /** 验证向量和制品删除顺序以及完整对象集合。 */
    @Test
    void deletesRevisionVectorsBeforeDerivedArtifacts() {
        Phase14Fixtures.Fixture fixture = Phase14Fixtures.create(
            space.refinex.agentark.knowledge.domain.KnowledgeRevisionStatus.READY);
        VectorScope scope = new VectorScope(
            fixture.organizationId(), fixture.projectId(), fixture.revision().id());
        ObjectRef first = ObjectRef.of(
            "object://knowledge/chunks-1.ndjson", Checksum.sha256("one"), 3,
            "application/x-ndjson");
        ObjectRef second = ObjectRef.of(
            "object://knowledge/chunks-2.ndjson", Checksum.sha256("two"), 3,
            "application/x-ndjson");
        List<String> order = new ArrayList<>();
        KnowledgeDerivedDataCleaner cleaner = new KnowledgeDerivedDataCleaner(
            new DeleteOnlyVectorStore(order), new DeleteOnlyArtifactStore(order));

        cleaner.delete(scope, List.of(first, second)).toCompletableFuture().join();

        assertThat(order).containsExactly(
            "vector:" + fixture.revision().id().asString(),
            "artifact:" + first.uri(), "artifact:" + second.uri());
    }

    /**
     * 只实现删除路径的向量存储测试替身。
     *
     * @author refinex
     */
    private static final class DeleteOnlyVectorStore implements KnowledgeVectorStore {

        /** 调用顺序记录。 */
        private final List<String> order;

        /**
         * 创建删除记录型向量存储。
         *
         * @param order 调用顺序记录
         */
        private DeleteOnlyVectorStore(List<String> order) {
            this.order = order;
        }

        /** 当前测试不执行写入。 */
        @Override
        public CompletionStage<Void> upsert(VectorWriteRequest request) {
            return CompletableFuture.failedFuture(new AssertionError("upsert must not be called"));
        }

        /** 当前测试不执行校验。 */
        @Override
        public CompletionStage<Boolean> verify(VectorVerificationRequest request) {
            return CompletableFuture.failedFuture(new AssertionError("verify must not be called"));
        }

        /** 当前测试不执行检索。 */
        @Override
        public CompletionStage<List<VectorSearchHit>> search(VectorSearchRequest request) {
            return CompletableFuture.failedFuture(new AssertionError("search must not be called"));
        }

        /** 记录固定 Revision 向量删除。 */
        @Override
        public CompletionStage<Void> delete(VectorScope scope) {
            order.add("vector:" + scope.knowledgeRevisionId().asString());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 只实现删除路径的制品存储测试替身。
     *
     * @author refinex
     */
    private static final class DeleteOnlyArtifactStore implements ChunkArtifactStore {

        /** 调用顺序记录。 */
        private final List<String> order;

        /**
         * 创建删除记录型制品存储。
         *
         * @param order 调用顺序记录
         */
        private DeleteOnlyArtifactStore(List<String> order) {
            this.order = order;
        }

        /** 当前测试不执行制品写入。 */
        @Override
        public CompletionStage<ObjectRef> put(
            space.refinex.agentark.kernel.id.KnowledgeRevisionId revisionId,
            List<KnowledgeChunk> chunks) {
            return CompletableFuture.failedFuture(new AssertionError("put must not be called"));
        }

        /** 记录派生制品删除。 */
        @Override
        public CompletionStage<Void> delete(ObjectRef ref) {
            order.add("artifact:" + ref.uri());
            return CompletableFuture.completedFuture(null);
        }
    }
}
