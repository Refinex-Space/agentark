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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.knowledge.adapter.out.fake.InMemoryKnowledgeRepository;
import space.refinex.agentark.knowledge.domain.ChunkProfile;
import space.refinex.agentark.knowledge.domain.DataSource;
import space.refinex.agentark.knowledge.domain.DataSourceType;
import space.refinex.agentark.knowledge.domain.Document;
import space.refinex.agentark.knowledge.domain.DocumentAcl;
import space.refinex.agentark.knowledge.domain.DocumentRevision;
import space.refinex.agentark.knowledge.domain.DocumentStatus;
import space.refinex.agentark.knowledge.domain.IngestionJobDescriptor;
import space.refinex.agentark.knowledge.domain.KnowledgeBase;
import space.refinex.agentark.knowledge.domain.KnowledgeProfileStatus;
import space.refinex.agentark.knowledge.domain.KnowledgeRevision;
import space.refinex.agentark.knowledge.domain.KnowledgeRevisionStatus;
import space.refinex.agentark.kernel.id.DataSourceId;
import space.refinex.agentark.kernel.id.DocumentId;
import space.refinex.agentark.kernel.id.DocumentRevisionId;
import space.refinex.agentark.kernel.id.KnowledgeBaseId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;

/**
 * 验证应用服务的 Profile 变更、新 Revision、READY Resolver、幂等描述和项目隔离。
 *
 * @author refinex
 */
class KnowledgeApplicationServiceTest {

    /** 固定测试时钟。 */
    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

    /** 创建应用服务测试实例。 */
    KnowledgeApplicationServiceTest() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /** 证明修改 Chunk Profile 会创建新 Knowledge Revision，旧 Revision 内容保持不变。 */
    @Test
    void createsNewRevisionWhenAProfileChangesAndResolvesOnlyReady() {
        Fixture fixture = fixture();
        KnowledgeApplicationService service = fixture.service();
        AgentArkPrincipal principal = principal();
        KnowledgeBase knowledgeBase = service.createKnowledgeBase(
            principal, fixture.projectId(), "manuals", "产品手册", "测试知识库");
        DataSource source = service.createDataSource(
            principal, fixture.projectId(), knowledgeBase.id(), DataSourceType.UPLOAD,
            "受控上传", "{}");
        DocumentRevision documentRevision = seedDocument(fixture, knowledgeBase, source);

        var parser = service.createParserProfile(
            principal, fixture.projectId(), "text-parser", "{\"format\":\"text\"}",
            KnowledgeProfileStatus.PUBLISHED);
        ChunkProfile chunkV1 = service.createChunkProfile(
            principal, fixture.projectId(), "paragraph", "{\"size\":512}",
            KnowledgeProfileStatus.PUBLISHED);
        var embedding = service.createEmbeddingProfile(
            principal, fixture.projectId(), "embedding", "{\"dimension\":3}", Optional.empty(),
            KnowledgeProfileStatus.PUBLISHED);
        var retrieval = service.createRetrievalProfile(
            principal, fixture.projectId(), "hybrid", "{\"topK\":10}",
            KnowledgeProfileStatus.PUBLISHED);

        KnowledgeRevision first = service.createKnowledgeRevision(
            principal, fixture.projectId(), knowledgeBase.id(), List.of(documentRevision.id()),
            parser.id(), chunkV1.id(), embedding.id(), retrieval.id());
        ChunkProfile chunkV2 = service.createChunkProfile(
            principal, fixture.projectId(), "paragraph", "{\"size\":256}",
            KnowledgeProfileStatus.PUBLISHED);
        KnowledgeRevision second = service.createKnowledgeRevision(
            principal, fixture.projectId(), knowledgeBase.id(), List.of(documentRevision.id()),
            parser.id(), chunkV2.id(), embedding.id(), retrieval.id());

        assertThat(first.revisionNumber()).isEqualTo(1);
        assertThat(second.revisionNumber()).isEqualTo(2);
        assertThat(second.contentHash()).isNotEqualTo(first.contentHash());
        assertThat(fixture.repository().findKnowledgeRevision(fixture.projectId(), first.id()))
            .contains(first);
        KnowledgeRevisionResolver resolver = new KnowledgeRevisionResolver(fixture.repository());
        assertThatThrownBy(() -> resolver.resolveReady(fixture.projectId(), first.id()))
            .isInstanceOf(KnowledgeConflictException.class);

        IngestionJobDescriptor request = service.requestIngestion(
            principal, fixture.projectId(), first.id(), "ingestion:first:0001");
        assertThat(service.requestIngestion(
            principal, fixture.projectId(), first.id(), "ingestion:first:0001")).isEqualTo(request);
        service.transitionRevision(
            principal, fixture.projectId(), first.id(), KnowledgeRevisionStatus.VERIFYING, "");
        service.transitionRevision(
            principal, fixture.projectId(), first.id(), KnowledgeRevisionStatus.READY, "");

        assertThat(resolver.resolveReady(fixture.projectId(), first.id()).status())
            .isEqualTo(KnowledgeRevisionStatus.READY);
        assertThat(fixture.audits()).isNotEmpty();
    }

    /** 证明内存 Repository 也必须显式匹配 Project，错误项目不能读取或猜测资源。 */
    @Test
    void hidesEveryResourceFromAnotherProject() {
        Fixture fixture = fixture();
        KnowledgeBase knowledgeBase = fixture.service().createKnowledgeBase(
            principal(), fixture.projectId(), "private", "私有知识库", "");

        ProjectId anotherProject = ProjectId.generate();

        assertThat(fixture.repository().findKnowledgeBase(anotherProject, knowledgeBase.id()))
            .isEmpty();
        assertThat(fixture.repository().listKnowledgeBases(
            anotherProject, Optional.empty(), 50)).isEmpty();
    }

    /** 证明列表游标不透明、无重复且非法游标被稳定拒绝。 */
    @Test
    void paginatesKnowledgeBasesWithOpaqueCursor() {
        Fixture fixture = fixture();
        AgentArkPrincipal principal = principal();
        fixture.service().createKnowledgeBase(
            principal, fixture.projectId(), "alpha", "知识库甲", "");
        fixture.service().createKnowledgeBase(
            principal, fixture.projectId(), "beta", "知识库乙", "");
        fixture.service().createKnowledgeBase(
            principal, fixture.projectId(), "gamma", "知识库丙", "");

        var first = fixture.service().listKnowledgeBases(
            principal, fixture.projectId(), null, 2);
        var second = fixture.service().listKnowledgeBases(
            principal, fixture.projectId(), first.nextCursor().orElseThrow(), 2);

        assertThat(first.items()).hasSize(2);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.items()).hasSize(1);
        assertThat(second.hasMore()).isFalse();
        assertThat(java.util.stream.Stream.concat(first.items().stream(), second.items().stream())
            .map(KnowledgeBase::id)).doesNotHaveDuplicates();
        assertThatThrownBy(() -> fixture.service().listKnowledgeBases(
            principal, fixture.projectId(), "not-a-cursor", 2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 创建带内存 Repository、授权 Port、审计 Port 和固定时钟的 Fixture。
     *
     * @return 测试 Fixture
     */
    private static Fixture fixture() {
        InMemoryKnowledgeRepository repository = new InMemoryKnowledgeRepository();
        OrganizationId organizationId = OrganizationId.generate();
        ProjectId projectId = ProjectId.generate();
        List<KnowledgeAuditRecord> audits = new ArrayList<>();
        KnowledgeApplicationService service = new KnowledgeApplicationService(
            repository,
            (principal, requestedProjectId, permission) -> {
                if (!requestedProjectId.equals(projectId) || !KnowledgePermissions.ALL.contains(permission)) {
                    throw new org.springframework.security.access.AccessDeniedException("denied");
                }
                return new KnowledgeProjectContext(
                    organizationId, projectId, "USER", OrganizationId.generate().value());
            },
            audits::add,
            Optional.empty(),
            Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(repository, service, projectId, audits);
    }

    /**
     * 直接向内存 Repository 写入一个已完成 Object Store 校验的文档 Fixture。
     *
     * @param fixture 测试 Fixture
     * @param knowledgeBase Knowledge Base
     * @param source UPLOAD 数据源
     * @return 首个 Document Revision
     */
    private static DocumentRevision seedDocument(
        Fixture fixture, KnowledgeBase knowledgeBase, DataSource source) {
        DocumentId documentId = DocumentId.generate();
        Document document = new Document(
            documentId, knowledgeBase.organizationId(), fixture.projectId(), knowledgeBase.id(),
            source.id(), "手册", Map.of("language", "zh-CN"),
            List.of(new DocumentAcl(DocumentAcl.SubjectType.PROJECT,
                fixture.projectId().value().toString(), DocumentAcl.AccessLevel.MANAGE)),
            DocumentStatus.ACTIVE, 0, NOW, NOW);
        DocumentRevision revision = new DocumentRevision(
            DocumentRevisionId.generate(), knowledgeBase.organizationId(), fixture.projectId(),
            knowledgeBase.id(), documentId, 1, "manual.txt",
            new ObjectRef(URI.create("object://test/documents/manual"),
                Checksum.sha256("manual"), 6, "text/plain"), NOW);
        fixture.repository().insertDocument(document, revision, "USER:test");
        return revision;
    }

    /**
     * 创建最小用户协议主体。
     *
     * @return 已认证用户主体
     */
    private static AgentArkPrincipal principal() {
        return new AgentArkPrincipal(
            "https://issuer.example", "user-1", PrincipalType.USER, Set.of(), Optional.empty(),
            Optional.empty());
    }

    /**
     * @param repository 内存 Repository
     * @param service 应用服务
     * @param projectId 授权项目
     * @param audits 审计记录
     * @author refinex
     */
    private record Fixture(
        InMemoryKnowledgeRepository repository,
        KnowledgeApplicationService service,
        ProjectId projectId,
        List<KnowledgeAuditRecord> audits) {

        /**
         * 校验 Fixture 依赖。
         *
         * @param repository 内存 Repository
         * @param service 应用服务
         * @param projectId 项目标识
         * @param audits 审计记录
         */
        private Fixture {
            java.util.Objects.requireNonNull(repository, "repository must not be null");
            java.util.Objects.requireNonNull(service, "service must not be null");
            java.util.Objects.requireNonNull(projectId, "projectId must not be null");
            java.util.Objects.requireNonNull(audits, "audits must not be null");
        }
    }
}
