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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import space.refinex.agentark.control.catalog.application.port.CatalogRepository;
import space.refinex.agentark.control.iam.application.IamAuditPublisher;
import space.refinex.agentark.control.iam.application.IamAuthorizationService;
import space.refinex.agentark.control.iam.application.PermissionRegistry;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.iam.domain.Environment;
import space.refinex.agentark.control.iam.domain.IamStatus;
import space.refinex.agentark.control.iam.domain.Project;
import space.refinex.agentark.control.release.application.ReleaseApplicationService;
import space.refinex.agentark.control.release.application.port.ReleaseRepository;
import space.refinex.agentark.control.release.domain.ReleaseModels.Deployment;
import space.refinex.agentark.control.release.domain.ReleaseModels.DeploymentStatus;
import space.refinex.agentark.control.release.domain.ReleaseModels.TrafficPolicy;
import space.refinex.agentark.control.secret.application.port.SecretRepository;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.kernel.id.AgentId;
import space.refinex.agentark.kernel.id.DeploymentId;
import space.refinex.agentark.kernel.id.EnvironmentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.RevisionId;
import tools.jackson.databind.json.JsonMapper;

/**
 * 验证 Deployment 授权使用精确 Environment Scope，不要求重复的 Project Scope 绑定。
 *
 * @author refinex
 */
class ReleaseApplicationServiceTest {

    /** 验证 Environment 级角色可以通过单次精确 Scope 授权读取 Deployment。 */
    @Test
    void authorizesDeploymentAtEnvironmentScopeOnly() {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        OrganizationId organizationId = OrganizationId.generate();
        ProjectId projectId = ProjectId.generate();
        EnvironmentId environmentId = EnvironmentId.generate();
        DeploymentId deploymentId = DeploymentId.generate();
        TenantCatalogRepository tenantRepository = mock(TenantCatalogRepository.class);
        IamAuthorizationService authorizationService = mock(IamAuthorizationService.class);
        ReleaseRepository repository = mock(ReleaseRepository.class);
        Project project = new Project(
            projectId, organizationId, "release", "Release", IamStatus.ACTIVE, 0, now, now);
        Environment environment = new Environment(
            environmentId, organizationId, projectId, "production", "Production",
            IamStatus.ACTIVE, 0, now, now);
        Deployment deployment = new Deployment(
            deploymentId, organizationId, projectId, environmentId, AgentId.generate(),
            RevisionId.generate(), DeploymentStatus.ENABLED, TrafficPolicy.full(), 0, now, now);
        when(tenantRepository.findProject(projectId)).thenReturn(Optional.of(project));
        when(tenantRepository.findEnvironment(environmentId)).thenReturn(Optional.of(environment));
        when(repository.findDeployment(projectId, environmentId, deploymentId))
            .thenReturn(Optional.of(deployment));
        ReleaseApplicationService service = new ReleaseApplicationService(
            repository, mock(CatalogRepository.class), tenantRepository, authorizationService,
            mock(SecretRepository.class), mock(IamAuditPublisher.class),
            Clock.fixed(now, ZoneOffset.UTC), JsonMapper.builder().build());
        AgentArkPrincipal principal = new AgentArkPrincipal(
            "https://issuer.example.test", "operator", PrincipalType.USER,
            Set.of(), Optional.empty(), Optional.empty());

        Deployment found = service.getDeployment(
            principal, projectId, environmentId, deploymentId);

        assertThat(found).isEqualTo(deployment);
        verify(authorizationService).requirePermission(
            principal, organizationId, Optional.of(projectId), Optional.of(environmentId),
            PermissionRegistry.DEPLOYMENT_READ);
        verifyNoMoreInteractions(authorizationService);
    }
}
