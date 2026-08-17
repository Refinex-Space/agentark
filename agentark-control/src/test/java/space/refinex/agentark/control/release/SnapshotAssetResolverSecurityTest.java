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

import org.junit.jupiter.api.Test;
import space.refinex.agentark.control.catalog.application.port.CatalogRepository;
import space.refinex.agentark.control.catalog.domain.CatalogAsset;
import space.refinex.agentark.control.catalog.domain.CatalogAssetKind;
import space.refinex.agentark.control.catalog.domain.CatalogAssetStatus;
import space.refinex.agentark.control.catalog.domain.CatalogVersion;
import space.refinex.agentark.control.catalog.domain.CatalogVersionStatus;
import space.refinex.agentark.control.iam.application.IamConflictException;
import space.refinex.agentark.control.release.application.SnapshotAssetResolver;
import space.refinex.agentark.control.release.application.port.KnowledgeSnapshotLookup;
import space.refinex.agentark.control.release.domain.AgentDraftSpec;
import space.refinex.agentark.control.secret.application.port.SecretRepository;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 证明敏感项目发布策略拒绝不在允许区域内的 Model/MCP 资产。
 *
 * @author refinex
 */
class SnapshotAssetResolverSecurityTest {

    /**
     * 证明 SENSITIVE Permission Policy 会在 Snapshot 生成前拒绝越区 Model。
     */
    @Test
    void shouldRejectModelOutsideSensitiveDataBoundary() {
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        OrganizationId organizationId = OrganizationId.generate();
        ProjectId projectId = ProjectId.generate();
        ModelProviderId modelId = ModelProviderId.generate();
        ModelProfileId modelVersionId = ModelProfileId.generate();
        PermissionPolicyId policyId = PermissionPolicyId.generate();
        PermissionPolicyVersionId policyVersionId = PermissionPolicyVersionId.generate();
        CatalogRepository repository = mock(CatalogRepository.class);
        SecretRepository secretRepository = mock(SecretRepository.class);
        when(secretRepository.existsReference(any(), any())).thenReturn(true);
        when(repository.findAsset(CatalogAssetKind.PERMISSION_POLICY, projectId, policyId))
            .thenReturn(Optional.of(asset(
                policyId, CatalogAssetKind.PERMISSION_POLICY, organizationId, projectId, "{}", now)));
        when(repository.findVersion(
            CatalogAssetKind.PERMISSION_POLICY, projectId, policyId, policyVersionId))
            .thenReturn(Optional.of(version(
                policyVersionId, CatalogAssetKind.PERMISSION_POLICY, organizationId, projectId,
                policyId, "{\"defaultDecision\":\"DENY\",\"rules\":[],"
                    + "\"dataBoundary\":{\"classification\":\"SENSITIVE\","
                    + "\"allowedRegions\":[\"cn-north\"]}}", now)));
        when(repository.findAsset(CatalogAssetKind.MODEL_PROVIDER, projectId, modelId))
            .thenReturn(Optional.of(asset(
                modelId, CatalogAssetKind.MODEL_PROVIDER, organizationId, projectId,
                "{\"providerType\":\"OPENAI_COMPATIBLE\",\"descriptor\":{}}", now)));
        when(repository.findVersion(
            CatalogAssetKind.MODEL_PROVIDER, projectId, modelId, modelVersionId))
            .thenReturn(Optional.of(version(
                modelVersionId, CatalogAssetKind.MODEL_PROVIDER, organizationId, projectId,
                modelId, "{\"modelName\":\"model-a\",\"capabilities\":[\"STREAMING\"],"
                    + "\"parameters\":{\"temperature\":0.1,\"maxTokens\":128},"
                    + "\"credentialSecretRef\":\"secret://project/credential\","
                    + "\"dataRegion\":\"us-west\"}", now)));
        SnapshotAssetResolver resolver = new SnapshotAssetResolver(
            repository, secretRepository, mock(KnowledgeSnapshotLookup.class),
            JsonMapper.builder().build());
        AgentDraftSpec draft = draft(modelId, modelVersionId, policyId, policyVersionId);

        assertThatThrownBy(() -> resolver.resolve(
            organizationId, projectId, AgentId.generate(), "sensitive-agent", draft,
            RevisionId.generate(), SnapshotId.generate(), 1, now))
            .isInstanceOf(IamConflictException.class)
            .hasMessageContaining("data region is not allowed");
    }

    /**
     * @param id 资产稳定标识
     * @param kind 资产类型
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param metadataJson 非敏感元数据 JSON
     * @param now 固定时刻
     * @return 活动资产
     */
    private CatalogAsset asset(
        StrongId id,
        CatalogAssetKind kind,
        OrganizationId organizationId,
        ProjectId projectId,
        String metadataJson,
        Instant now) {
        return new CatalogAsset(
            id, kind, organizationId, projectId, "security-asset", "安全资产", "",
            metadataJson, CatalogAssetStatus.ACTIVE, 0, now, now);
    }

    /**
     * @param id 资产版本标识
     * @param kind 资产类型
     * @param organizationId 组织标识
     * @param projectId 项目标识
     * @param ownerId 资产稳定标识
     * @param payloadJson 版本载荷 JSON
     * @param now 固定时刻
     * @return 已发布不可变版本
     */
    private CatalogVersion version(
        StrongId id,
        CatalogAssetKind kind,
        OrganizationId organizationId,
        ProjectId projectId,
        StrongId ownerId,
        String payloadJson,
        Instant now) {
        return new CatalogVersion(
            id, kind, organizationId, projectId, ownerId, 1, payloadJson,
            Checksum.sha256(payloadJson), CatalogVersionStatus.PUBLISHED, now);
    }

    /**
     * @param modelId Model Provider 标识
     * @param modelVersionId Model Profile 版本标识
     * @param policyId Permission Policy 标识
     * @param policyVersionId Permission Policy 版本标识
     * @return 指向敏感发布策略的最小 Draft
     */
    private AgentDraftSpec draft(
        ModelProviderId modelId,
        ModelProfileId modelVersionId,
        PermissionPolicyId policyId,
        PermissionPolicyVersionId policyVersionId) {
        return new AgentDraftSpec(
            "agentscope-java-2", List.of(),
            new AgentDraftSpec.ModelBinding(modelId, modelVersionId),
            List.of(), List.of(), List.of(), List.of(),
            new AgentDraftSpec.ProfileBindings(
                MemoryProfileId.generate(), MemoryProfileVersionId.generate(),
                WorkspaceProfileId.generate(), WorkspaceProfileVersionId.generate(),
                SandboxProfileId.generate(), SandboxProfileVersionId.generate()),
            new AgentDraftSpec.PermissionBinding(policyId, policyVersionId),
            new AgentDraftSpec.LimitSpec(60, 1, 0));
    }
}
