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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import space.refinex.agentark.knowledge.domain.ChunkProfile;
import space.refinex.agentark.knowledge.domain.DocumentRevision;
import space.refinex.agentark.knowledge.domain.EmbeddingProfile;
import space.refinex.agentark.knowledge.domain.KnowledgeProfileStatus;
import space.refinex.agentark.knowledge.domain.ParserProfile;
import space.refinex.agentark.knowledge.domain.RetrievalProfile;
import space.refinex.agentark.kernel.id.ChunkProfileId;
import space.refinex.agentark.kernel.id.DocumentId;
import space.refinex.agentark.kernel.id.DocumentRevisionId;
import space.refinex.agentark.kernel.id.EmbeddingProfileId;
import space.refinex.agentark.kernel.id.KnowledgeBaseId;
import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ParserProfileId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.RetrievalProfileId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;

/**
 * 验证 Fake Provider 可复现完整异步流水线，且项目标识参与向量索引键。
 *
 * @author refinex
 */
class FakeKnowledgeProvidersTest {

    /** 创建 Fake Provider 测试实例。 */
    FakeKnowledgeProvidersTest() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /** 证明解析、切分、Embedding、写入、召回、重排和删除均可确定性复现。 */
    @Test
    void completesDeterministicInMemoryPipeline() {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        OrganizationId organizationId = OrganizationId.generate();
        ProjectId projectId = ProjectId.generate();
        DocumentRevision revision = new DocumentRevision(
            DocumentRevisionId.generate(), organizationId, projectId, KnowledgeBaseId.generate(),
            DocumentId.generate(), 1, "manual.txt",
            new ObjectRef(URI.create("object://test/manual"), Checksum.sha256("AgentArk\n\nKnowledge"),
                20, "text/plain"), now);
        ParserProfile parser = new ParserProfile(
            ParserProfileId.generate(), organizationId, projectId, "text", 1, "{}",
            Checksum.sha256("{}"), KnowledgeProfileStatus.PUBLISHED, now);
        ChunkProfile chunk = new ChunkProfile(
            ChunkProfileId.generate(), organizationId, projectId, "paragraph", 1, "{}",
            Checksum.sha256("{}"), KnowledgeProfileStatus.PUBLISHED, now);
        EmbeddingProfile embedding = new EmbeddingProfile(
            EmbeddingProfileId.generate(), organizationId, projectId, "fake", 1, "{}",
            Optional.empty(), Checksum.sha256("{}"), KnowledgeProfileStatus.PUBLISHED, now);
        RetrievalProfile retrieval = new RetrievalProfile(
            RetrievalProfileId.generate(), organizationId, projectId, "fake", 1, "{}",
            Checksum.sha256("{}"), KnowledgeProfileStatus.PUBLISHED, now);
        KnowledgeRevisionId knowledgeRevisionId = KnowledgeRevisionId.generate();
        FakeKnowledgeProviders providers = new FakeKnowledgeProviders();

        var parsed = providers.parse(
            revision, parser,
            () -> new ByteArrayInputStream("AgentArk\n\nKnowledge".getBytes(StandardCharsets.UTF_8)))
            .toCompletableFuture().join();
        var chunks = providers.chunk(parsed, chunk).toCompletableFuture().join();
        var embedded = providers.embed(chunks, embedding).toCompletableFuture().join();
        providers.upsert(projectId, knowledgeRevisionId, embedded).toCompletableFuture().join();
        var candidates = providers.retrieve(
            projectId, knowledgeRevisionId, "Knowledge", retrieval, 10)
            .toCompletableFuture().join();
        var reranked = providers.rerank("Knowledge", candidates, retrieval, 1)
            .toCompletableFuture().join();

        assertThat(chunks).hasSize(2);
        assertThat(embedded).allSatisfy(value -> assertThat(value.vector()).hasSize(3));
        assertThat(reranked).hasSize(1);
        assertThat(reranked.get(0).score()).isEqualTo(1.0);

        providers.delete(projectId, knowledgeRevisionId).toCompletableFuture().join();
        assertThat(providers.retrieve(projectId, knowledgeRevisionId, "Knowledge", retrieval, 10)
            .toCompletableFuture().join()).isEmpty();
    }
}
