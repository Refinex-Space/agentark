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

package space.refinex.agentark.control.iam.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import space.refinex.agentark.control.iam.application.port.AuthorizationRepository;
import space.refinex.agentark.control.iam.application.port.IdentityRepository;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.iam.domain.*;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.kernel.id.EnvironmentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.RoleId;

import java.time.Clock;
import java.util.*;

/**
 * 编排 IAM 租户目录、成员关系、角色和服务账号事务，不向 Controller 暴露 Mapper。
 *
 * @author refinex
 */
public class IamApplicationService {

    /**
     * API 列表的硬上限，避免无界授权查询。
     */
    private static final int LIST_LIMIT = 100;

    /**
     * 租户目录端口。
     */
    private final TenantCatalogRepository tenantRepository;

    /**
     * 身份与成员关系端口。
     */
    private final IdentityRepository identityRepository;

    /**
     * 角色和授权端口。
     */
    private final AuthorizationRepository authorizationRepository;

    /**
     * 应用授权服务。
     */
    private final IamAuthorizationService authorizationService;

    /**
     * 授权缓存失效事件发布器。
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 事务感知审计发布器。
     */
    private final IamAuditPublisher auditPublisher;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 创建 IAM 应用服务。
     *
     * @param tenantRepository        租户目录端口
     * @param identityRepository      身份端口
     * @param authorizationRepository 授权端口
     * @param authorizationService    应用授权服务
     * @param eventPublisher          领域事件发布器
     * @param auditPublisher          审计发布器
     * @param clock                   UTC 时钟
     */
    public IamApplicationService(TenantCatalogRepository tenantRepository, IdentityRepository identityRepository,
                                 AuthorizationRepository authorizationRepository, IamAuthorizationService authorizationService,
                                 ApplicationEventPublisher eventPublisher, IamAuditPublisher auditPublisher, Clock clock) {

        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository must not be null");
        this.identityRepository = Objects.requireNonNull(identityRepository, "identityRepository must not be null");
        this.authorizationRepository = Objects.requireNonNull(authorizationRepository, "authorizationRepository must not be null");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "auditPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建组织、组织所有者角色和创建者绑定。
     *
     * @param principal 已认证用户
     * @param slug      全局组织 Slug
     * @param name      展示名称
     * @return 新组织
     */
    @Transactional
    public Organization createOrganization(AgentArkPrincipal principal, String slug, String name) {
        ResolvedPrincipal actor = authorizationService.requirePlatformPermission(principal, PermissionRegistry.ORGANIZATION_CREATE);
        Organization organization = Organization.create(slug, name, clock.instant());
        tenantRepository.insertOrganization(organization);

        Role owner = Role.create(
            organization.id(),
            Optional.empty(),
            "organization-owner",
            "组织所有者",
            true,
            PermissionRegistry.organizationOwnerPermissions(),
            clock.instant());
        authorizationRepository.insertRole(owner);
        RoleBinding binding = RoleBinding.create(
            organization.id(),
            Optional.empty(),
            owner.id(),
            actor.kind(),
            actor.id(),
            IamScopeType.ORGANIZATION,
            organization.id().value(),
            clock.instant());
        authorizationRepository.insertRoleBinding(binding);
        changed(organization.id(), Optional.empty());
        audit(principal, "organization.create", "organization", organization.id().asString(),
            Optional.of(organization.id()), Optional.empty());
        return organization;
    }

    /**
     * 仅列出当前主体有角色绑定的组织。
     *
     * @param principal 已认证主体
     * @return 最多一百个可见组织
     */
    @Transactional(readOnly = true)
    public List<Organization> listOrganizations(AgentArkPrincipal principal) {
        ResolvedPrincipal resolved = authorizationService.resolve(principal);
        return tenantRepository.listOrganizationsForPrincipal(resolved.kind(), resolved.id(), LIST_LIMIT);
    }

    /**
     * 创建项目、项目内置角色、创建者成员关系和管理员绑定。
     *
     * @param principal      已认证主体
     * @param organizationId 所属组织
     * @param slug           组织内项目 Slug
     * @param name           展示名称
     * @return 新项目
     */
    @Transactional
    public Project createProject(AgentArkPrincipal principal, OrganizationId organizationId, String slug, String name) {
        requireOrganization(organizationId);
        ResolvedPrincipal actor = authorizationService.requirePermission(
            principal,
            organizationId,
            Optional.empty(),
            Optional.empty(),
            PermissionRegistry.PROJECT_CREATE);
        Project project = Project.create(organizationId, slug, name, clock.instant());
        tenantRepository.insertProject(project);
        identityRepository.insertMembership(Membership.create(organizationId, project.id(), actor.kind(), actor.id(), clock.instant()));

        Role admin = createBuiltInProjectRoles(project).stream()
            .filter(role -> role.key().equals("project-admin"))
            .findFirst()
            .orElseThrow();
        authorizationRepository.insertRoleBinding(RoleBinding.create(
            organizationId,
            Optional.of(project.id()),
            admin.id(),
            actor.kind(),
            actor.id(),
            IamScopeType.PROJECT,
            project.id().value(),
            clock.instant()));
        changed(organizationId, Optional.of(project.id()));
        audit(principal, "project.create", "project", project.id().asString(),
            Optional.of(organizationId), Optional.of(project.id()));
        return project;
    }

    /**
     * 列出组织内项目，授权失败时不返回部分结果。
     *
     * @param principal      已认证主体
     * @param organizationId 组织标识
     * @return 最多一百个项目
     */
    @Transactional(readOnly = true)
    public List<Project> listProjects(AgentArkPrincipal principal, OrganizationId organizationId) {
        requireOrganization(organizationId);
        authorizationService.requirePermission(
            principal,
            organizationId,
            Optional.empty(),
            Optional.empty(),
            PermissionRegistry.ORGANIZATION_READ);
        return tenantRepository.listProjects(organizationId, LIST_LIMIT);
    }

    /**
     * 创建项目环境。
     *
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @param key       项目内环境 Key
     * @param name      展示名称
     * @return 新环境
     */
    @Transactional
    public Environment createEnvironment(AgentArkPrincipal principal, ProjectId projectId, String key, String name) {
        Project project = requireProject(projectId);
        authorizationService.requirePermission(
            principal,
            project.organizationId(),
            Optional.of(project.id()),
            Optional.empty(),
            PermissionRegistry.ENVIRONMENT_CREATE);
        Environment environment = Environment.create(project.organizationId(), project.id(), key, name, clock.instant());
        tenantRepository.insertEnvironment(environment);
        audit(principal, "environment.create", "environment", environment.id().asString(),
            Optional.of(project.organizationId()), Optional.of(project.id()));
        return environment;
    }

    /**
     * 列出项目环境。
     *
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @return 最多一百个环境
     */
    @Transactional(readOnly = true)
    public List<Environment> listEnvironments(AgentArkPrincipal principal, ProjectId projectId) {
        Project project = requireProject(projectId);
        authorizationService.requirePermission(
            principal,
            project.organizationId(),
            Optional.of(project.id()),
            Optional.empty(),
            PermissionRegistry.ENVIRONMENT_READ);
        return tenantRepository.listEnvironments(project.organizationId(), project.id(), LIST_LIMIT);
    }

    /**
     * 创建项目成员关系，并在事务提交后失效授权缓存。
     *
     * @param principal     操作主体
     * @param projectId     项目标识
     * @param principalKind 新成员主体类别
     * @param principalId   新成员 UUIDv7
     * @return 新成员关系
     */
    @Transactional
    public Membership createMembership(AgentArkPrincipal principal, ProjectId projectId, PrincipalKind principalKind, UUID principalId) {
        Project project = requireProject(projectId);
        authorizationService.requirePermission(principal, project.organizationId(), Optional.of(project.id()),
            Optional.empty(), PermissionRegistry.MEMBERSHIP_MANAGE);
        if (!identityRepository.principalExists(principalKind, principalId)) {
            throw new IamNotFoundException("principal was not found");
        }

        Membership membership = Membership.create(project.organizationId(), project.id(), principalKind, principalId, clock.instant());
        identityRepository.insertMembership(membership);
        changed(project.organizationId(), Optional.of(project.id()));
        audit(principal, "membership.create", "membership", membership.id().asString(),
            Optional.of(project.organizationId()), Optional.of(project.id()));
        return membership;
    }

    /**
     * 列出项目成员关系。
     *
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @return 最多一百个成员关系
     */
    @Transactional(readOnly = true)
    public List<Membership> listMemberships(AgentArkPrincipal principal, ProjectId projectId) {
        Project project = requireProject(projectId);
        authorizationService.requirePermission(principal, project.organizationId(), Optional.of(project.id()),
            Optional.empty(), PermissionRegistry.MEMBERSHIP_READ);
        return identityRepository.listMemberships(project.organizationId(), project.id(), LIST_LIMIT);
    }

    /**
     * 创建项目自定义角色。
     *
     * @param principal      已认证主体
     * @param projectId      项目标识
     * @param key            角色键
     * @param name           展示名称
     * @param permissionKeys 权限键集合
     * @return 新自定义角色
     */
    @Transactional
    public Role createRole(AgentArkPrincipal principal, ProjectId projectId, String key, String name, Set<String> permissionKeys) {
        Project project = requireProject(projectId);
        authorizationService.requirePermission(principal, project.organizationId(), Optional.of(project.id()), Optional.empty(), PermissionRegistry.ROLE_MANAGE);
        Set<String> checkedPermissions = PermissionRegistry.requireRegistered(permissionKeys);
        Role role = Role.create(project.organizationId(), Optional.of(project.id()), key, name, false, checkedPermissions, clock.instant());
        authorizationRepository.insertRole(role);
        changed(project.organizationId(), Optional.of(project.id()));
        audit(principal, "role.create", "role", role.id().asString(), Optional.of(project.organizationId()), Optional.of(project.id()));
        return role;
    }

    /**
     * 列出项目可使用的组织级和项目级角色。
     *
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @return 最多一百个角色
     */
    @Transactional(readOnly = true)
    public List<Role> listRoles(AgentArkPrincipal principal, ProjectId projectId) {
        Project project = requireProject(projectId);
        authorizationService.requirePermission(principal, project.organizationId(), Optional.of(project.id()), Optional.empty(), PermissionRegistry.ROLE_READ);
        return authorizationRepository.listRoles(project.organizationId(), project.id(), LIST_LIMIT);
    }

    /**
     * 在项目或环境 Scope 创建角色绑定。
     *
     * @param principal     已认证主体
     * @param projectId     项目标识
     * @param roleId        角色标识
     * @param principalKind 被授权主体类别
     * @param principalId   被授权主体 UUIDv7
     * @param scopeType     PROJECT 或 ENVIRONMENT
     * @param scopeId       项目或环境 UUIDv7
     * @return 新角色绑定
     */
    @Transactional
    public RoleBinding createRoleBinding(AgentArkPrincipal principal, ProjectId projectId, RoleId roleId,
                                         PrincipalKind principalKind, UUID principalId, IamScopeType scopeType, UUID scopeId) {

        Project project = requireProject(projectId);
        authorizationService.requirePermission(principal, project.organizationId(), Optional.of(project.id()),
            Optional.empty(), PermissionRegistry.ROLE_MANAGE);
        if (!identityRepository.principalExists(principalKind, principalId) || !identityRepository.isActiveMember(
            project.organizationId(), project.id(), principalKind, principalId)) {
            throw new IamNotFoundException("project principal was not found");
        }

        Role role = authorizationRepository.findRole(roleId)
            .filter(value -> value.organizationId().equals(project.organizationId()))
            .filter(value -> value.projectId().isEmpty() || value.projectId().filter(projectId::equals).isPresent())
            .orElseThrow(() -> new IamNotFoundException("role was not found"));
        validateBindingScope(project, scopeType, scopeId);
        RoleBinding binding = RoleBinding.create(project.organizationId(), Optional.of(project.id()), role.id(),
            principalKind, principalId, scopeType, scopeId, clock.instant());
        authorizationRepository.insertRoleBinding(binding);
        changed(project.organizationId(), Optional.of(project.id()));
        audit(principal, "role_binding.create", "role-binding", binding.id().asString(),
            Optional.of(project.organizationId()), Optional.of(project.id()));
        return binding;
    }

    /**
     * 列出项目及继承组织范围内的角色绑定。
     *
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @return 最多一百个绑定
     */
    @Transactional(readOnly = true)
    public List<RoleBinding> listRoleBindings(AgentArkPrincipal principal, ProjectId projectId) {
        Project project = requireProject(projectId);
        authorizationService.requirePermission(principal, project.organizationId(), Optional.of(project.id()),
            Optional.empty(), PermissionRegistry.ROLE_READ);
        return authorizationRepository.listRoleBindings(project.organizationId(), project.id(), LIST_LIMIT);
    }

    /**
     * 创建项目服务账号，并自动建立活动成员关系。
     *
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @param name      项目内稳定名称
     * @return 新服务账号
     */
    @Transactional
    public ServiceAccount createServiceAccount(AgentArkPrincipal principal, ProjectId projectId, String name) {
        Project project = requireProject(projectId);
        authorizationService.requirePermission(principal, project.organizationId(), Optional.of(project.id()),
            Optional.empty(), PermissionRegistry.SERVICE_ACCOUNT_MANAGE);
        ServiceAccount account = ServiceAccount.create(project.organizationId(), project.id(), name, clock.instant());
        identityRepository.insertServiceAccount(account);
        identityRepository.insertMembership(Membership.create(project.organizationId(), project.id(),
            PrincipalKind.SERVICE_ACCOUNT, account.id().value(), clock.instant()));
        changed(project.organizationId(), Optional.of(project.id()));
        audit(principal, "service_account.create", "service-account", account.id().asString(),
            Optional.of(project.organizationId()), Optional.of(project.id()));
        return account;
    }

    /**
     * 列出项目服务账号。
     *
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @return 最多一百个服务账号
     */
    @Transactional(readOnly = true)
    public List<ServiceAccount> listServiceAccounts(AgentArkPrincipal principal, ProjectId projectId) {
        Project project = requireProject(projectId);
        authorizationService.requirePermission(principal, project.organizationId(), Optional.of(project.id()),
            Optional.empty(), PermissionRegistry.SERVICE_ACCOUNT_READ);
        return identityRepository.listServiceAccounts(project.organizationId(), project.id(), LIST_LIMIT);
    }

    /**
     * 返回平台权限注册项。
     *
     * @param principal 已认证主体
     * @param projectId 用于授权的项目
     * @return 全局权限注册项
     */
    @Transactional(readOnly = true)
    public List<Permission> listPermissions(AgentArkPrincipal principal, ProjectId projectId) {
        Project project = requireProject(projectId);
        authorizationService.requirePermission(principal, project.organizationId(), Optional.of(project.id()),
            Optional.empty(), PermissionRegistry.ROLE_READ);
        return authorizationRepository.listPermissions();
    }

    /**
     * 读取存在的项目，避免 Controller 或其他应用服务接触 Mapper。
     *
     * @param projectId 项目标识
     * @return 项目聚合
     */
    @Transactional(readOnly = true)
    public Project requireProject(ProjectId projectId) {
        return tenantRepository.findProject(projectId)
            .orElseThrow(() -> new IamNotFoundException("project was not found"));
    }

    /**
     * 创建项目三个内置角色并持久化权限关联。
     *
     * @param project 新项目
     * @return 已持久化角色列表
     */
    private List<Role> createBuiltInProjectRoles(Project project) {
        List<Role> roles = List.of(
            Role.create(project.organizationId(), Optional.of(project.id()), "project-admin",
                "项目管理员", true, PermissionRegistry.projectAdminPermissions(), clock.instant()),
            Role.create(project.organizationId(), Optional.of(project.id()), "project-developer",
                "项目开发者", true, PermissionRegistry.projectDeveloperPermissions(), clock.instant()),
            Role.create(project.organizationId(), Optional.of(project.id()), "project-viewer",
                "项目只读成员", true, PermissionRegistry.projectViewerPermissions(), clock.instant()));
        roles.forEach(authorizationRepository::insertRole);
        return roles;
    }

    /**
     * 校验组织存在。
     *
     * @param organizationId 组织标识
     */
    private void requireOrganization(OrganizationId organizationId) {
        if (tenantRepository.findOrganization(organizationId).isEmpty()) {
            throw new IamNotFoundException("organization was not found");
        }
    }

    /**
     * 校验项目或环境绑定 Scope 与路径项目一致。
     *
     * @param project   路径项目
     * @param scopeType Scope 类型
     * @param scopeId   Scope UUIDv7
     */
    private void validateBindingScope(Project project, IamScopeType scopeType, UUID scopeId) {
        if (scopeType == IamScopeType.PROJECT && project.id().value().equals(scopeId)) {
            return;
        }

        if (scopeType == IamScopeType.ENVIRONMENT) {
            EnvironmentId environmentId = new EnvironmentId(scopeId);
            if (tenantRepository.findEnvironment(environmentId)
                .filter(value -> value.organizationId().equals(project.organizationId()))
                .filter(value -> value.projectId().equals(project.id()))
                .isPresent()) {
                return;
            }
        }
        throw new IamNotFoundException("binding scope was not found");
    }

    /**
     * 发布事务后缓存失效事件。
     *
     * @param organizationId 组织标识
     * @param projectId      可选项目
     */
    private void changed(OrganizationId organizationId, Optional<ProjectId> projectId) {
        eventPublisher.publishEvent(new IamAuthorizationChanged(organizationId, projectId));
    }

    /**
     * 安排成功事务提交后的安全审计记录。
     *
     * @param principal      操作主体
     * @param action         操作代码
     * @param resourceType   资源类型
     * @param resourceId     资源标识
     * @param organizationId 可选组织
     * @param projectId      可选项目
     */
    private void audit(AgentArkPrincipal principal, String action, String resourceType, String resourceId,
                       Optional<OrganizationId> organizationId, Optional<ProjectId> projectId) {

        auditPublisher.append(new IamAuditRecord(
            action,
            principal.subject(),
            resourceType,
            resourceId,
            organizationId,
            projectId,
            "SUCCEEDED",
            clock.instant()));
    }
}
