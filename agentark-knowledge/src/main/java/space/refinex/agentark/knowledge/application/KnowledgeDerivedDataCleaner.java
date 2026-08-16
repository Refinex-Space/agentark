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

import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.knowledge.port.ChunkArtifactStore;
import space.refinex.agentark.knowledge.port.KnowledgeVectorStore;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorScope;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 按固定租户 Revision 删除向量与派生 Chunk 制品，供 DELETING 状态的异步任务调用。
 *
 * @author refinex
 */
public final class KnowledgeDerivedDataCleaner {

    /**
     * 向量存储端口。
     */
    private final KnowledgeVectorStore vectorStore;

    /**
     * Chunk 制品存储端口。
     */
    private final ChunkArtifactStore artifactStore;

    /**
     * 创建派生数据清理器。
     *
     * @param vectorStore  向量存储
     * @param artifactStore Chunk 制品存储
     */
    public KnowledgeDerivedDataCleaner(
        KnowledgeVectorStore vectorStore, ChunkArtifactStore artifactStore) {
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
        this.artifactStore = Objects.requireNonNull(artifactStore, "artifactStore must not be null");
    }

    /**
     * 先删除固定 Revision 向量，再并行删除当前结果登记的派生对象。
     *
     * @param scope     可信租户 Revision 范围
     * @param artifacts 待删除派生对象
     * @return 全部删除完成信号
     */
    public CompletionStage<Void> delete(VectorScope scope, List<ObjectRef> artifacts) {
        Objects.requireNonNull(scope, "scope must not be null");
        List<ObjectRef> refs = List.copyOf(
            Objects.requireNonNull(artifacts, "artifacts must not be null"));
        return vectorStore.delete(scope).thenCompose(ignored -> CompletableFuture.allOf(
            refs.stream()
                .map(artifactStore::delete)
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new)));
    }
}
