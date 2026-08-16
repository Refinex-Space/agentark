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

package space.refinex.agentark.control.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import space.refinex.agentark.control.catalog.application.port.CatalogRepository;
import space.refinex.agentark.control.catalog.domain.CatalogAsset;
import space.refinex.agentark.control.catalog.domain.CatalogAssetKind;
import space.refinex.agentark.control.catalog.domain.CatalogAssetStatus;
import space.refinex.agentark.control.iam.application.IamAuditPublisher;
import space.refinex.agentark.control.iam.application.IamAuthorizationService;
import space.refinex.agentark.control.iam.application.IamConflictException;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.iam.domain.IamStatus;
import space.refinex.agentark.control.iam.domain.Project;
import space.refinex.agentark.control.release.application.AgentPublisher;
import space.refinex.agentark.control.release.application.CanonicalSnapshotSerializer;
import space.refinex.agentark.control.release.application.CanonicalSnapshotSerializer.SerializedSnapshot;
import space.refinex.agentark.control.release.application.SnapshotAssetResolver;
import space.refinex.agentark.control.release.application.port.ReleaseRepository;
import space.refinex.agentark.control.release.domain.AgentDraftSpec;
import space.refinex.agentark.control.release.domain.AgentDraftSpec.LimitSpec;
import space.refinex.agentark.control.release.domain.AgentDraftSpec.ModelBinding;
import space.refinex.agentark.control.release.domain.AgentDraftSpec.PermissionBinding;
import space.refinex.agentark.control.release.domain.AgentDraftSpec.ProfileBindings;
import space.refinex.agentark.control.release.domain.ReleaseModels.AgentDraft;
import space.refinex.agentark.control.release.domain.ReleaseModels.AgentRevision;
import space.refinex.agentark.control.release.domain.ReleaseModels.OutboxEvent;
import space.refinex.agentark.control.release.domain.ReleaseModels.PublishOperation;
import space.refinex.agentark.control.release.domain.ReleaseModels.PublishStatus;
import space.refinex.agentark.control.release.domain.ReleaseModels.StoredSnapshot;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.kernel.id.AgentId;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.MemoryProfileId;
import space.refinex.agentark.kernel.id.MemoryProfileVersionId;
import space.refinex.agentark.kernel.id.ModelProfileId;
import space.refinex.agentark.kernel.id.ModelProviderId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.PermissionPolicyId;
import space.refinex.agentark.kernel.id.PermissionPolicyVersionId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.RevisionId;
import space.refinex.agentark.kernel.id.SandboxProfileId;
import space.refinex.agentark.kernel.id.SandboxProfileVersionId;
import space.refinex.agentark.kernel.id.SnapshotId;
import space.refinex.agentark.kernel.id.WorkspaceProfileId;
import space.refinex.agentark.kernel.id.WorkspaceProfileVersionId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.snapshot.AgentRevisionSnapshot;
import tools.jackson.databind.json.JsonMapper;

/**
 * 验证发布幂等键绑定 Draft 版本，并且重放不会再次解析资产或写入发布事务。
 *
 * @author refinex
 */
class AgentPublisherTest {

    /** 固定测试时刻。 */
    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

    /** Release 持久化端口替身。 */
    private ReleaseRepository repository;

    /** 资产解析器替身。 */
    private SnapshotAssetResolver assetResolver;

    /** Canonical Snapshot 序列化器替身。 */
    private CanonicalSnapshotSerializer serializer;

    /** AI 资产目录端口替身。 */
    private CatalogRepository catalogRepository;

    /** 租户目录端口替身。 */
    private TenantCatalogRepository tenantRepository;

    /** 发布器被测对象。 */
    private AgentPublisher publisher;

    /** 项目标识。 */
    private ProjectId projectId;

    /** Agent 标识。 */
    private AgentId agentId;

    /** 已认证用户主体。 */
    private AgentArkPrincipal principal;

    /**
     * 为每个测试创建隔离的端口替身和固定租户对象。
     */
    @BeforeEach
    void setUp() {
        repository = mock(ReleaseRepository.class);
        assetResolver = mock(SnapshotAssetResolver.class);
        serializer = mock(CanonicalSnapshotSerializer.class);
        catalogRepository = mock(CatalogRepository.class);
        tenantRepository = mock(TenantCatalogRepository.class);
        IamAuthorizationService authorizationService = mock(IamAuthorizationService.class);
        projectId = ProjectId.generate();
        agentId = AgentId.generate();
        OrganizationId organizationId = OrganizationId.generate();
        Project project = new Project(
            projectId, organizationId, "release", "Release", IamStatus.ACTIVE, 0, NOW, NOW);
        when(tenantRepository.findProject(projectId)).thenReturn(Optional.of(project));
        principal = new AgentArkPrincipal(
            "https://issuer.example.test", "publisher", PrincipalType.USER,
            Set.of(), Optional.empty(), Optional.empty());
        publisher = new AgentPublisher(
            repository,
            assetResolver,
            serializer,
            catalogRepository,
            tenantRepository,
            authorizationService,
            mock(IamAuditPublisher.class),
            Clock.fixed(NOW, ZoneOffset.UTC),
            JsonMapper.builder().build());
    }

    /** 验证同一幂等键和 Draft 版本直接返回首次发布结果，不产生第二次写入。 */
    @Test
    void replaysSuccessfulPublishWithoutCreatingAnotherRevision() {
        AgentDraft draft = draft(3);
        AgentRevision revision = revision();
        PublishOperation operation = new PublishOperation(
            JobId.generate(), projectId, agentId, "publish-001", 3,
            PublishStatus.SUCCEEDED, Optional.of(revision.id()), NOW);
        when(repository.lockDraft(projectId, agentId)).thenReturn(Optional.of(draft));
        when(repository.findPublishOperation(projectId, agentId, "publish-001"))
            .thenReturn(Optional.of(operation));
        when(repository.findSnapshot(projectId, revision.id()))
            .thenReturn(Optional.of(new StoredSnapshot(revision, "{}")));

        AgentRevision replayed = publisher.publish(
            principal, projectId, agentId, "publish-001", 3);

        assertThat(replayed).isEqualTo(revision);
        verify(assetResolver, never()).resolve(
            any(), any(), any(), any(), any(), any(), any(), anyLong(), any());
        verify(repository, never()).insertPublished(any(), any(), any(), any(), any());
    }

    /** 验证同一幂等键不能绑定另一个 Draft 版本。 */
    @Test
    void rejectsIdempotencyKeyReusedForAnotherDraftVersion() {
        AgentRevision revision = revision();
        PublishOperation operation = new PublishOperation(
            JobId.generate(), projectId, agentId, "publish-001", 2,
            PublishStatus.SUCCEEDED, Optional.of(revision.id()), NOW);
        when(repository.lockDraft(projectId, agentId)).thenReturn(Optional.of(draft(3)));
        when(repository.findPublishOperation(projectId, agentId, "publish-001"))
            .thenReturn(Optional.of(operation));

        assertThatThrownBy(() -> publisher.publish(
            principal, projectId, agentId, "publish-001", 3))
            .isInstanceOf(IamConflictException.class)
            .hasMessage("idempotency key is bound to another draft version");

        verify(repository, never()).findSnapshot(any(), any());
        verify(repository, never()).insertPublished(any(), any(), any(), any(), any());
    }

    /** 验证首次发布把非秘密区段差异摘要写入同一 Outbox 载荷。 */
    @Test
    void recordsNonSecretDiffSummaryInPublishedOutbox() {
        AgentDraft draft = draft(0);
        AgentRevisionSnapshot provisional = mock(AgentRevisionSnapshot.class);
        AgentRevisionSnapshot sealed = mock(AgentRevisionSnapshot.class);
        when(sealed.contentHash()).thenReturn(Checksum.sha256("snapshot"));
        when(repository.lockDraft(projectId, agentId)).thenReturn(Optional.of(draft));
        when(repository.findPublishOperation(projectId, agentId, "publish-002"))
            .thenReturn(Optional.empty());
        when(repository.nextRevisionNumber(projectId, agentId)).thenReturn(1L);
        when(repository.listRevisions(projectId, agentId)).thenReturn(List.of());
        when(catalogRepository.findAsset(CatalogAssetKind.AGENT, projectId, agentId))
            .thenReturn(Optional.of(new CatalogAsset(
                agentId, CatalogAssetKind.AGENT, OrganizationId.generate(), projectId,
                "review-agent", "Review Agent", "", "{}", CatalogAssetStatus.ACTIVE,
                0, NOW, NOW)));
        when(assetResolver.resolve(
            any(), any(), any(), any(), any(), any(), any(), anyLong(), any()))
            .thenReturn(provisional);
        when(serializer.serialize(provisional))
            .thenReturn(new SerializedSnapshot(sealed, "{}"));

        publisher.publish(principal, projectId, agentId, "publish-002", 0);

        ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).insertPublished(any(), any(), any(), outbox.capture(), any());
        assertThat(outbox.getValue().payloadJson())
            .contains("\"diffSummary\"")
            .contains("\"previousRevisionId\":\"\"")
            .contains("\"changedSections\"")
            .contains("\"model\"")
            .doesNotContain("secretValue")
            .doesNotContain("plaintext");
    }

    /**
     * 创建具备全部必需引用的最小 Draft。
     *
     * @param version Draft 乐观锁版本
     * @return 测试 Draft
     */
    private AgentDraft draft(long version) {
        AgentDraftSpec spec = new AgentDraftSpec(
            "agentscope-java-2", List.of("tool-calling"),
            new ModelBinding(ModelProviderId.generate(), ModelProfileId.generate()),
            List.of(), List.of(), List.of(), List.of(),
            new ProfileBindings(
                MemoryProfileId.generate(), MemoryProfileVersionId.generate(),
                WorkspaceProfileId.generate(), WorkspaceProfileVersionId.generate(),
                SandboxProfileId.generate(), SandboxProfileVersionId.generate()),
            new PermissionBinding(
                PermissionPolicyId.generate(), PermissionPolicyVersionId.generate()),
            new LimitSpec(300, 10, 2));
        return new AgentDraft(
            agentId, OrganizationId.generate(), projectId, spec, version, NOW);
    }

    /**
     * 创建已发布 Revision 元数据。
     *
     * @return 测试 Revision
     */
    private AgentRevision revision() {
        return new AgentRevision(
            RevisionId.generate(), OrganizationId.generate(), projectId, agentId,
            SnapshotId.generate(), 1, 1, "agentscope-java-2",
            Checksum.sha256("snapshot"), List.of("tool-calling"), NOW);
    }
}
