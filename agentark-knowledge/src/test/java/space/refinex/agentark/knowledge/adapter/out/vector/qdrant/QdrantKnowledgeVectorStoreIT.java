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

package space.refinex.agentark.knowledge.adapter.out.vector.qdrant;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;
import space.refinex.agentark.kernel.id.DocumentId;
import space.refinex.agentark.kernel.id.DocumentRevisionId;
import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.knowledge.port.EmbeddedChunk;
import space.refinex.agentark.knowledge.port.KnowledgeChunk;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorScope;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorSearchRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorVerificationRequest;
import space.refinex.agentark.knowledge.port.VectorStoreModels.VectorWriteRequest;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用 Qdrant 1.18.3 容器验证租户、Revision、文档 ACL、校验和删除隔离。
 *
 * @author refinex
 */
@Testcontainers(disabledWithoutDocker = true)
class QdrantKnowledgeVectorStoreIT {

    /**
     * 固定 Qdrant 1.18.3 测试容器。
     */
    @Container
    private static final GenericContainer<?> QDRANT = new GenericContainer<>(
        DockerImageName.parse("qdrant/qdrant:v1.18.3"))
        .withExposedPorts(6333)
        .waitingFor(Wait.forHttp("/readyz").forPort(6333));

    /**
     * 验证 Adapter 强制注入三维租户范围和文档白名单，并可清理固定 Revision。
     */
    @Test
    void enforcesScopeAclVerificationAndDeletion() {
        QdrantKnowledgeVectorStore store = store();
        OrganizationId organizationId = OrganizationId.generate();
        ProjectId projectId = ProjectId.generate();
        KnowledgeRevisionId revisionId = KnowledgeRevisionId.generate();
        VectorScope scope = new VectorScope(organizationId, projectId, revisionId);
        DocumentId allowedDocument = DocumentId.generate();
        DocumentId deniedDocument = DocumentId.generate();
        DocumentRevisionId allowedRevision = DocumentRevisionId.generate();
        DocumentRevisionId deniedRevision = DocumentRevisionId.generate();
        EmbeddedChunk allowed = embedded(allowedRevision, "allowed:c000001", "允许内容",
            new float[]{1, 0, 0});
        EmbeddedChunk denied = embedded(deniedRevision, "denied:c000001", "拒绝内容",
            new float[]{0.9f, 0.1f, 0});
        Checksum checksum = Checksum.sha256("qdrant-manifest");

        store.upsert(new VectorWriteRequest(
            scope, List.of(allowed, denied),
            Map.of(allowedRevision, allowedDocument, deniedRevision, deniedDocument), checksum))
            .toCompletableFuture().join();

        assertThat(store.verify(new VectorVerificationRequest(scope, 2, checksum))
            .toCompletableFuture().join()).isTrue();
        var hits = store.search(new VectorSearchRequest(
            scope, Set.of(allowedDocument), new float[]{1, 0, 0}, 10, 0))
            .toCompletableFuture().join();
        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().documentId()).isEqualTo(allowedDocument);
        assertThat(hits.getFirst().chunk().metadata())
            .containsEntry("source_trust", "UNTRUSTED_EXTERNAL");

        QdrantKnowledgeVectorStore restartedStore = store();
        assertThat(restartedStore.search(new VectorSearchRequest(
            scope, Set.of(allowedDocument), new float[]{1, 0, 0}, 10, 0))
            .toCompletableFuture().join()).hasSize(1);

        VectorScope otherTenant = new VectorScope(
            organizationId, ProjectId.generate(), revisionId);
        assertThat(store.search(new VectorSearchRequest(
            otherTenant, Set.of(allowedDocument), new float[]{1, 0, 0}, 10, 0))
            .toCompletableFuture().join()).isEmpty();

        store.delete(scope).toCompletableFuture().join();
        assertThat(store.search(new VectorSearchRequest(
            scope, Set.of(allowedDocument), new float[]{1, 0, 0}, 10, 0))
            .toCompletableFuture().join()).isEmpty();
    }

    /**
     * 创建指向当前容器的 REST Adapter。
     *
     * @return Qdrant 向量存储
     */
    private QdrantKnowledgeVectorStore store() {
        URI endpoint = URI.create("http://127.0.0.1:" + QDRANT.getMappedPort(6333));
        return new QdrantKnowledgeVectorStore(
            new QdrantProperties(endpoint, "agentark_knowledge_it", 3, Duration.ofSeconds(10)),
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
            JsonMapper.builder().build(), Optional::<char[]>empty);
    }

    /**
     * 创建固定向量 Chunk。
     *
     * @param revisionId 文档修订标识
     * @param key        Chunk Key
     * @param text       Chunk 文本
     * @param vector     三维向量
     * @return 带向量 Chunk
     */
    private EmbeddedChunk embedded(
        DocumentRevisionId revisionId, String key, String text, float[] vector) {
        return new EmbeddedChunk(new KnowledgeChunk(
            key, revisionId, text, Map.of("section", "body")), vector);
    }
}
