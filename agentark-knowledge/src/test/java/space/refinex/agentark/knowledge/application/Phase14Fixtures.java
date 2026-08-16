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

import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.foundation.security.ServiceIdentity;
import space.refinex.agentark.kernel.id.ChunkProfileId;
import space.refinex.agentark.kernel.id.DataSourceId;
import space.refinex.agentark.kernel.id.DocumentId;
import space.refinex.agentark.kernel.id.DocumentRevisionId;
import space.refinex.agentark.kernel.id.EmbeddingProfileId;
import space.refinex.agentark.kernel.id.IngestionRequestId;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.KnowledgeBaseId;
import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ParserProfileId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.RetrievalProfileId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.knowledge.adapter.out.fake.InMemoryKnowledgeIngestionResultRepository;
import space.refinex.agentark.knowledge.adapter.out.fake.InMemoryKnowledgeRepository;
import space.refinex.agentark.knowledge.application.IngestionModels.ArtifactKind;
import space.refinex.agentark.knowledge.application.IngestionModels.ArtifactReference;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionCommand;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionPlan;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionResult;
import space.refinex.agentark.knowledge.application.IngestionModels.ResultStatus;
import space.refinex.agentark.knowledge.domain.ChunkProfile;
import space.refinex.agentark.knowledge.domain.Document;
import space.refinex.agentark.knowledge.domain.DocumentAcl;
import space.refinex.agentark.knowledge.domain.DocumentRevision;
import space.refinex.agentark.knowledge.domain.DocumentStatus;
import space.refinex.agentark.knowledge.domain.EmbeddingProfile;
import space.refinex.agentark.knowledge.domain.IngestionJobDescriptor;
import space.refinex.agentark.knowledge.domain.IngestionRequestStatus;
import space.refinex.agentark.knowledge.domain.KnowledgeProfileStatus;
import space.refinex.agentark.knowledge.domain.KnowledgeRevision;
import space.refinex.agentark.knowledge.domain.KnowledgeRevisionStatus;
import space.refinex.agentark.knowledge.domain.ParserProfile;
import space.refinex.agentark.knowledge.domain.RetrievalProfile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 为 Phase 14 摄取、检索和防腐层测试构造一致的固定租户与 Revision。
 *
 * @author refinex
 */
public final class Phase14Fixtures {

    /**
     * 禁止实例化测试夹具容器。
     */
    private Phase14Fixtures() {
    }

    /**
     * 创建并持久化指定状态的单文档 Knowledge Revision 夹具。
     *
     * @param status Knowledge Revision 状态
     * @return 完整测试夹具
     */
    public static Fixture create(KnowledgeRevisionStatus status) {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        OrganizationId organizationId = OrganizationId.generate();
        ProjectId projectId = ProjectId.generate();
        KnowledgeBaseId baseId = KnowledgeBaseId.generate();
        DocumentId documentId = DocumentId.generate();
        DocumentRevision documentRevision = new DocumentRevision(
            DocumentRevisionId.generate(), organizationId, projectId, baseId, documentId, 1,
            "manual.md", ObjectRef.of("object://knowledge/manual.md",
                Checksum.sha256("AgentArk manual"), 15, "text/markdown"), now);
        Document document = new Document(
            documentId, organizationId, projectId, baseId, DataSourceId.generate(),
            "AgentArk 手册", Map.of("category", "manual"), List.of(new DocumentAcl(
                DocumentAcl.SubjectType.PROJECT, projectId.asString(),
                DocumentAcl.AccessLevel.READ)), DocumentStatus.ACTIVE, 0, now, now);
        ParserProfile parser = new ParserProfile(
            ParserProfileId.generate(), organizationId, projectId, "text-parser", 1,
            "{\"format\":\"markdown\"}", Checksum.sha256("parser"),
            KnowledgeProfileStatus.PUBLISHED, now);
        ChunkProfile chunk = new ChunkProfile(
            ChunkProfileId.generate(), organizationId, projectId, "paragraph", 1,
            "{\"maxCharacters\":512,\"overlapCharacters\":32}", Checksum.sha256("chunk"),
            KnowledgeProfileStatus.PUBLISHED, now);
        EmbeddingProfile embedding = new EmbeddingProfile(
            EmbeddingProfileId.generate(), organizationId, projectId, "embedding", 1,
            "{\"dimension\":3}", Optional.empty(), Checksum.sha256("embedding"),
            KnowledgeProfileStatus.PUBLISHED, now);
        RetrievalProfile retrieval = new RetrievalProfile(
            RetrievalProfileId.generate(), organizationId, projectId, "retrieval", 1,
            "{\"topK\":10}", Checksum.sha256("retrieval"),
            KnowledgeProfileStatus.PUBLISHED, now);
        KnowledgeRevision revision = new KnowledgeRevision(
            KnowledgeRevisionId.generate(), organizationId, projectId, baseId, 1,
            List.of(documentRevision.id()), parser.id(), chunk.id(), embedding.id(), retrieval.id(),
            Checksum.sha256("revision"), status, "", 0, now, now);
        IngestionJobDescriptor request = new IngestionJobDescriptor(
            IngestionRequestId.generate(), organizationId, projectId, revision.id(),
            "ingestion:phase14:0001", IngestionRequestStatus.DESCRIBED, now);
        InMemoryKnowledgeRepository repository = new InMemoryKnowledgeRepository();
        repository.insertDocument(document, documentRevision, "fixture");
        repository.insertParserProfile(parser, "fixture");
        repository.insertChunkProfile(chunk, "fixture");
        repository.insertEmbeddingProfile(embedding, "fixture");
        repository.insertRetrievalProfile(retrieval, "fixture");
        repository.insertKnowledgeRevision(revision, "fixture");
        repository.insertOrFindIngestionRequest(request, "fixture");
        return new Fixture(
            now, organizationId, projectId, document, documentRevision, parser, chunk,
            embedding, retrieval, revision, request, repository,
            new InMemoryKnowledgeIngestionResultRepository());
    }

    /**
     * 创建固定摄取计划。
     *
     * @param fixture 测试夹具
     * @return 摄取计划
     */
    static IngestionPlan plan(Fixture fixture) {
        return new IngestionPlan(
            fixture.request().id(), fixture.organizationId(), fixture.projectId(),
            fixture.revision(), List.of(fixture.documentRevision()), fixture.parser(),
            fixture.chunk(), fixture.embedding(), fixture.retrieval());
    }

    /**
     * 创建当前夹具的 Scheduler 命令。
     *
     * @param fixture 测试夹具
     * @return 摄取命令
     */
    static IngestionCommand command(Fixture fixture) {
        return new IngestionCommand(
            fixture.request().id(), fixture.organizationId(), fixture.projectId(),
            fixture.revision().id(), JobId.generate(), JobId.generate().value(),
            "internal:phase14:0001");
    }

    /**
     * 创建通过数量和制品校验的成功结果。
     *
     * @param fixture 测试夹具
     * @param command 当前命令
     * @return 成功摄取结果
     */
    static IngestionResult success(Fixture fixture, IngestionCommand command) {
        return new IngestionResult(
            JobId.generate().value(), command.requestId(), fixture.organizationId(),
            fixture.projectId(), fixture.revision().id(), command.schedulerJobId(),
            command.attemptId(), command.idempotencyKey(), 1, 1, Checksum.sha256("manifest"),
            List.of(new ArtifactReference(ArtifactKind.CHUNKS, ObjectRef.of(
                "object://knowledge/chunks.ndjson", Checksum.sha256("chunk"), 5,
                "application/x-ndjson"))), ResultStatus.SUCCEEDED, "", fixture.now());
    }

    /**
     * 创建具备 Control Audience 的 Scheduler 服务主体。
     *
     * @return 内部服务主体
     */
    static AgentArkPrincipal servicePrincipal() {
        return new AgentArkPrincipal(
            "https://issuer.agentark.test", "scheduler-worker", PrincipalType.SERVICE,
            Set.of(), Optional.empty(), Optional.of(new ServiceIdentity(
                "agentark-scheduler", Set.of(KnowledgeIngestionControlService.CONTROL_AUDIENCE))));
    }

    /**
     * @param now              固定时刻
     * @param organizationId   组织标识
     * @param projectId        项目标识
     * @param document         文档稳定身份
     * @param documentRevision 文档修订
     * @param parser           Parser Profile 配置
     * @param chunk            Chunk Profile 配置
     * @param embedding        Embedding Profile 配置
     * @param retrieval        Retrieval Profile 配置
     * @param revision         Knowledge Revision 版本
     * @param request          摄取请求
     * @param repository       元数据仓储
     * @param resultRepository 结果仓储
     * @author refinex
     */
    public record Fixture(
        Instant now,
        OrganizationId organizationId,
        ProjectId projectId,
        Document document,
        DocumentRevision documentRevision,
        ParserProfile parser,
        ChunkProfile chunk,
        EmbeddingProfile embedding,
        RetrievalProfile retrieval,
        KnowledgeRevision revision,
        IngestionJobDescriptor request,
        InMemoryKnowledgeRepository repository,
        InMemoryKnowledgeIngestionResultRepository resultRepository) {
    }
}
