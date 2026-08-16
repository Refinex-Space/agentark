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

package space.refinex.agentark.control.release.application;

import space.refinex.agentark.control.iam.application.IamAccessDeniedException;
import space.refinex.agentark.control.iam.application.IamConflictException;
import space.refinex.agentark.control.iam.application.IamNotFoundException;
import space.refinex.agentark.control.release.application.port.ReleaseRepository;
import space.refinex.agentark.control.release.domain.ReleaseModels.Deployment;
import space.refinex.agentark.control.release.domain.ReleaseModels.StoredSnapshot;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.kernel.id.DeploymentId;
import space.refinex.agentark.kernel.id.RevisionId;

import java.util.Objects;
import java.util.Set;

/**
 * 为 Runtime 提供只读 Snapshot/Deployment Contract，并在返回前校验服务身份与能力声明。
 *
 * @author refinex
 */
public final class RuntimeInternalContractService {

    /**
     * Internal API 受众。
     */
    public static final String CONTROL_AUDIENCE = "agentark-control";

    /**
     * Release 持久化端口。
     */
    private final ReleaseRepository repository;

    /**
     * @param repository Release 持久化端口
     */
    public RuntimeInternalContractService(ReleaseRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * @param principal       已认证服务主体
     * @param revisionId      Revision 标识
     * @param runtimeProvider Runtime Provider 声明
     * @param schemaVersions  支持的 Snapshot Schema 版本
     * @param capabilities    Runtime 能力声明
     * @return 完整 Canonical Snapshot
     */
    public StoredSnapshot snapshot(
        AgentArkPrincipal principal,
        RevisionId revisionId,
        String runtimeProvider,
        Set<Integer> schemaVersions,
        Set<String> capabilities) {
        requireService(principal);
        StoredSnapshot snapshot = repository.findSnapshotInternal(revisionId)
            .orElseThrow(() -> new IamNotFoundException("agent revision is not visible"));
        if (!snapshot.revision().runtimeProvider().equals(runtimeProvider)
            || !schemaVersions.contains(snapshot.revision().schemaVersion())
            || !capabilities.containsAll(snapshot.revision().requiredCapabilities())) {
            throw new IamConflictException("runtime provider or snapshot capability is incompatible");
        }
        return snapshot;
    }

    /**
     * @param principal    已认证服务主体
     * @param deploymentId Deployment 标识
     * @return 不暴露 Control Entity 的语言中立 Deployment 描述
     */
    public DeploymentDescriptor deployment(
        AgentArkPrincipal principal, DeploymentId deploymentId) {
        requireService(principal);
        Deployment deployment = repository.findDeploymentInternal(deploymentId)
            .orElseThrow(() -> new IamNotFoundException("deployment is not visible"));
        StoredSnapshot snapshot = repository.findSnapshotInternal(deployment.desiredRevisionId())
            .orElseThrow(() -> new IllegalStateException("deployment revision snapshot is missing"));
        return new DeploymentDescriptor(
            deployment.id().asString(), deployment.organizationId().asString(),
            deployment.projectId().asString(), deployment.environmentId().asString(),
            deployment.agentId().asString(), deployment.desiredRevisionId().asString(),
            deployment.status().name(), deployment.trafficPolicy().type().name(),
            deployment.trafficPolicy().canaryPercent(), deployment.version(),
            snapshot.revision().schemaVersion(), snapshot.revision().runtimeProvider(),
            snapshot.revision().requiredCapabilities());
    }

    /**
     * @param principal 候选主体
     */
    private void requireService(AgentArkPrincipal principal) {
        if (principal == null || principal.type() != PrincipalType.SERVICE
            || principal.serviceIdentity().isEmpty()
            || !principal.serviceIdentity().orElseThrow().audiences().contains(CONTROL_AUDIENCE)) {
            throw new IamAccessDeniedException("internal service audience is required");
        }
    }

    /**
     * @param deploymentId         Deployment 的 UUIDv7 标识
     * @param organizationId       组织 UUIDv7
     * @param projectId            项目 UUIDv7
     * @param environmentId        环境 UUIDv7
     * @param agentId              Agent 的 UUIDv7 标识
     * @param desiredRevisionId    期望 Revision UUIDv7
     * @param desiredStatus        期望状态：ENABLED、DISABLED
     * @param trafficPolicy        流量策略：FULL、CANARY
     * @param canaryPercent        Canary 百分比
     * @param version              Deployment 乐观锁版本
     * @param schemaVersion        Snapshot Schema 版本
     * @param runtimeProvider      Runtime Provider 标识
     * @param requiredCapabilities Runtime 必需能力
     * @author refinex
     */
    public record DeploymentDescriptor(
        String deploymentId,
        String organizationId,
        String projectId,
        String environmentId,
        String agentId,
        String desiredRevisionId,
        String desiredStatus,
        String trafficPolicy,
        int canaryPercent,
        long version,
        int schemaVersion,
        String runtimeProvider,
        java.util.List<String> requiredCapabilities) {
        /**
         * 防御性复制 Runtime 能力列表。
         */
        public DeploymentDescriptor {
            requiredCapabilities = java.util.List.copyOf(requiredCapabilities);
        }
    }
}
