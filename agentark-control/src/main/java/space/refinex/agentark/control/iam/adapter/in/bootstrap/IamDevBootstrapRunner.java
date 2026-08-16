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

package space.refinex.agentark.control.iam.adapter.in.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import space.refinex.agentark.control.iam.IamDevBootstrapProperties;
import space.refinex.agentark.control.iam.application.IamApplicationService;
import space.refinex.agentark.control.iam.application.PermissionRegistry;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.iam.domain.Environment;
import space.refinex.agentark.control.iam.domain.Organization;
import space.refinex.agentark.control.iam.domain.Project;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;

import java.util.Optional;
import java.util.Set;

/**
 * 在显式启用的 local Profile 中幂等创建最小组织、项目和环境，不创建密码或 API Key。
 *
 * @author refinex
 */
public final class IamDevBootstrapRunner implements ApplicationRunner {

    /**
     * 本地引导专用非敏感日志。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(IamDevBootstrapRunner.class);

    /**
     * 本地引导配置。
     */
    private final IamDevBootstrapProperties properties;

    /**
     * IAM 应用服务。
     */
    private final IamApplicationService iamService;

    /**
     * 用于幂等查找资源的租户目录端口。
     */
    private final TenantCatalogRepository tenantRepository;

    /**
     * 创建本地 IAM 引导执行器。
     *
     * @param properties       本地引导配置
     * @param iamService       IAM 应用服务
     * @param tenantRepository 租户目录端口
     */
    public IamDevBootstrapRunner(
        IamDevBootstrapProperties properties,
        IamApplicationService iamService,
        TenantCatalogRepository tenantRepository) {
        this.properties = java.util.Objects.requireNonNull(properties, "properties must not be null");
        this.iamService = java.util.Objects.requireNonNull(iamService, "iamService must not be null");
        this.tenantRepository = java.util.Objects.requireNonNull(
            tenantRepository, "tenantRepository must not be null");
    }

    /**
     * 幂等创建本地资源；已有资源仍必须属于配置主体，否则授权检查失败关闭。
     *
     * @param arguments 未使用的 Spring 启动参数
     */
    @Override
    public void run(ApplicationArguments arguments) {
        AgentArkPrincipal principal = new AgentArkPrincipal(
            properties.getIssuer(),
            properties.getSubject(),
            PrincipalType.USER,
            Set.of(PermissionRegistry.ORGANIZATION_CREATE),
            Optional.empty(),
            Optional.empty());

        Organization organization = tenantRepository
            .findOrganizationBySlug(properties.getOrganizationSlug())
            .orElseGet(() -> iamService.createOrganization(
                principal, properties.getOrganizationSlug(), properties.getOrganizationName()));
        Project project = tenantRepository
            .findProjectBySlug(organization.id(), properties.getProjectSlug())
            .orElseGet(() -> iamService.createProject(
                principal, organization.id(), properties.getProjectSlug(), properties.getProjectName()));
        Environment environment = tenantRepository
            .findEnvironmentByKey(project.id(), properties.getEnvironmentKey())
            .orElseGet(() -> iamService.createEnvironment(
                principal, project.id(), properties.getEnvironmentKey(), properties.getEnvironmentName()));
        LOGGER.info(
            "IAM local bootstrap ready organizationId={} projectId={} environmentId={}",
            organization.id().asString(),
            project.id().asString(),
            environment.id().asString());
    }
}
