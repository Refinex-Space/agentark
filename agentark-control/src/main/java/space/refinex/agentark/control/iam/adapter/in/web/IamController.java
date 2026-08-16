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

package space.refinex.agentark.control.iam.adapter.in.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import space.refinex.agentark.control.iam.adapter.in.web.IamApiModels.*;
import space.refinex.agentark.control.iam.application.IamAccessDeniedException;
import space.refinex.agentark.control.iam.application.IamApiKeyService;
import space.refinex.agentark.control.iam.application.IamApplicationService;
import space.refinex.agentark.control.iam.domain.*;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.kernel.id.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * 暴露 IAM Public API，只调用应用服务且不直接访问 Mapper 或数据库行对象。
 *
 * @author refinex
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("isAuthenticated()")
public class IamController {

    /**
     * IAM 聚合应用服务。
     */
    private final IamApplicationService iamService;

    /**
     * API Key 安全生命周期服务。
     */
    private final IamApiKeyService apiKeyService;

    /**
     * 创建 IAM REST 入口。
     *
     * @param iamService    IAM 应用服务
     * @param apiKeyService API Key 服务
     */
    public IamController(IamApplicationService iamService, IamApiKeyService apiKeyService) {
        this.iamService = java.util.Objects.requireNonNull(iamService, "iamService must not be null");
        this.apiKeyService = java.util.Objects.requireNonNull(
            apiKeyService, "apiKeyService must not be null");
    }

    /**
     * 创建组织并为当前外部身份建立组织所有者绑定。
     *
     * @param authentication 已认证安全上下文
     * @param request        创建组织请求
     * @return 带资源地址的新组织响应
     */
    @PostMapping("/organizations")
    public ResponseEntity<Organization> createOrganization(
        Authentication authentication,
        @Valid @RequestBody CreateOrganizationRequest request) {
        Organization created = iamService.createOrganization(
            principal(authentication), request.slug(), request.name());
        return ResponseEntity.created(URI.create("/api/v1/organizations/" + created.id().asString()))
            .body(created);
    }

    /**
     * 列出当前主体拥有读取权限的组织。
     *
     * @param authentication 已认证安全上下文
     * @return 当前主体可见的组织
     */
    @GetMapping("/organizations")
    public List<Organization> listOrganizations(Authentication authentication) {
        return iamService.listOrganizations(principal(authentication));
    }

    /**
     * 在指定组织内创建项目和内置项目角色。
     *
     * @param authentication 已认证安全上下文
     * @param organizationId 组织 UUIDv7
     * @param request        创建项目请求
     * @return 带资源地址的新项目响应
     */
    @PostMapping("/organizations/{organizationId}/projects")
    public ResponseEntity<Project> createProject(
        Authentication authentication,
        @PathVariable String organizationId,
        @Valid @RequestBody CreateProjectRequest request) {
        Project created = iamService.createProject(
            principal(authentication), OrganizationId.parse(organizationId),
            request.slug(), request.name());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id().asString()))
            .body(created);
    }

    /**
     * 列出指定组织内当前主体可读取的项目。
     *
     * @param authentication 已认证安全上下文
     * @param organizationId 组织 UUIDv7
     * @return 组织内项目
     */
    @GetMapping("/organizations/{organizationId}/projects")
    public List<Project> listProjects(
        Authentication authentication, @PathVariable String organizationId) {
        return iamService.listProjects(
            principal(authentication), OrganizationId.parse(organizationId));
    }

    /**
     * 在指定项目内创建环境。
     *
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param request        创建环境请求
     * @return 带资源地址的新环境响应
     */
    @PostMapping("/projects/{projectId}/environments")
    public ResponseEntity<Environment> createEnvironment(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody CreateEnvironmentRequest request) {
        Environment created = iamService.createEnvironment(
            principal(authentication), ProjectId.parse(projectId), request.key(), request.name());
        return ResponseEntity.created(URI.create("/api/v1/environments/" + created.id().asString()))
            .body(created);
    }

    /**
     * 列出指定项目的环境。
     *
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @return 项目环境
     */
    @GetMapping("/projects/{projectId}/environments")
    public List<Environment> listEnvironments(
        Authentication authentication, @PathVariable String projectId) {
        return iamService.listEnvironments(principal(authentication), ProjectId.parse(projectId));
    }

    /**
     * 为用户身份或服务账号创建项目成员关系。
     *
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param request        创建成员关系请求
     * @return 带资源地址的新成员关系响应
     */
    @PostMapping("/projects/{projectId}/memberships")
    public ResponseEntity<Membership> createMembership(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody CreateMembershipRequest request) {
        Membership created = iamService.createMembership(
            principal(authentication),
            ProjectId.parse(projectId),
            enumValue(PrincipalKind.class, request.principalKind()),
            uuid(request.principalId()));
        return ResponseEntity.created(URI.create("/api/v1/memberships/" + created.id().asString()))
            .body(created);
    }

    /**
     * 列出指定项目的成员关系。
     *
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @return 项目成员关系
     */
    @GetMapping("/projects/{projectId}/memberships")
    public List<Membership> listMemberships(
        Authentication authentication, @PathVariable String projectId) {
        return iamService.listMemberships(principal(authentication), ProjectId.parse(projectId));
    }

    /**
     * 使用权限注册表中的权限创建项目自定义角色。
     *
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param request        创建角色请求
     * @return 带资源地址的新角色响应
     */
    @PostMapping("/projects/{projectId}/roles")
    public ResponseEntity<Role> createRole(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody CreateRoleRequest request) {
        Role created = iamService.createRole(
            principal(authentication), ProjectId.parse(projectId), request.key(), request.name(),
            request.permissions());
        return ResponseEntity.created(URI.create("/api/v1/roles/" + created.id().asString()))
            .body(created);
    }

    /**
     * 列出指定项目的内置角色和自定义角色。
     *
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @return 项目可用角色
     */
    @GetMapping("/projects/{projectId}/roles")
    public List<Role> listRoles(Authentication authentication, @PathVariable String projectId) {
        return iamService.listRoles(principal(authentication), ProjectId.parse(projectId));
    }

    /**
     * 创建项目或环境范围的角色绑定。
     *
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param request        创建角色绑定请求
     * @return 带资源地址的新角色绑定响应
     */
    @PostMapping("/projects/{projectId}/role-bindings")
    public ResponseEntity<RoleBinding> createRoleBinding(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody CreateRoleBindingRequest request) {
        RoleBinding created = iamService.createRoleBinding(
            principal(authentication),
            ProjectId.parse(projectId),
            RoleId.parse(request.roleId()),
            enumValue(PrincipalKind.class, request.principalKind()),
            uuid(request.principalId()),
            enumValue(IamScopeType.class, request.scopeType()),
            uuid(request.scopeId()));
        return ResponseEntity.created(URI.create("/api/v1/role-bindings/" + created.id().asString()))
            .body(created);
    }

    /**
     * 列出项目范围及组织继承的角色绑定。
     *
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @return 当前项目相关的角色绑定
     */
    @GetMapping("/projects/{projectId}/role-bindings")
    public List<RoleBinding> listRoleBindings(
        Authentication authentication, @PathVariable String projectId) {
        return iamService.listRoleBindings(principal(authentication), ProjectId.parse(projectId));
    }

    /**
     * 创建仅属于指定项目的服务账号。
     *
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param request        创建服务账号请求
     * @return 带资源地址的新服务账号响应
     */
    @PostMapping("/projects/{projectId}/service-accounts")
    public ResponseEntity<ServiceAccount> createServiceAccount(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody CreateServiceAccountRequest request) {
        ServiceAccount created = iamService.createServiceAccount(
            principal(authentication), ProjectId.parse(projectId), request.name());
        return ResponseEntity.created(
            URI.create("/api/v1/service-accounts/" + created.id().asString())).body(created);
    }

    /**
     * 列出指定项目的服务账号。
     *
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @return 项目服务账号
     */
    @GetMapping("/projects/{projectId}/service-accounts")
    public List<ServiceAccount> listServiceAccounts(
        Authentication authentication, @PathVariable String projectId) {
        return iamService.listServiceAccounts(
            principal(authentication), ProjectId.parse(projectId));
    }

    /**
     * 在项目读取授权通过后列出平台权限注册项。
     *
     * @param authentication 已认证安全上下文
     * @param projectId      用于授权的项目 UUIDv7
     * @return 全局权限注册项
     */
    @GetMapping("/projects/{projectId}/permissions")
    public List<Permission> listPermissions(
        Authentication authentication, @PathVariable String projectId) {
        return iamService.listPermissions(principal(authentication), ProjectId.parse(projectId));
    }

    /**
     * 创建 API Key，并在当前响应中唯一一次交付明文。
     *
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param request        创建 API Key 请求
     * @return 带资源地址和一次性明文的新 API Key 响应
     */
    @PostMapping("/projects/{projectId}/api-keys")
    public ResponseEntity<CreatedApiKeyResponse> createApiKey(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody CreateApiKeyRequest request) {
        var created = apiKeyService.create(
            principal(authentication),
            ProjectId.parse(projectId),
            ServiceAccountId.parse(request.serviceAccountId()),
            request.name(),
            request.scopes(),
            request.expiresAt());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId
                + "/api-keys/" + created.metadata().id().asString()))
            .body(CreatedApiKeyResponse.from(created));
    }

    /**
     * 列出 API Key 非秘密元数据，不返回摘要或明文。
     *
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @return API Key 安全视图
     */
    @GetMapping("/projects/{projectId}/api-keys")
    public List<ApiKeyView> listApiKeys(
        Authentication authentication, @PathVariable String projectId) {
        return apiKeyService.list(principal(authentication), ProjectId.parse(projectId))
            .stream().map(ApiKeyView::from).toList();
    }

    /**
     * 使用调用方读取的版本号乐观锁吊销 API Key。
     *
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param apiKeyId       API Key UUIDv7
     * @param request        吊销请求
     * @return 无响应体的成功结果
     */
    @PostMapping("/projects/{projectId}/api-keys/{apiKeyId}/revoke")
    public ResponseEntity<Void> revokeApiKey(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String apiKeyId,
        @Valid @RequestBody RevokeApiKeyRequest request) {
        apiKeyService.revoke(
            principal(authentication), ProjectId.parse(projectId), ApiKeyId.parse(apiKeyId),
            request.expectedVersion());
        return ResponseEntity.noContent().build();
    }

    /**
     * 从 Spring Authentication 提取协议主体。
     *
     * @param authentication 已认证安全上下文
     * @return AgentArk 协议主体
     */
    private AgentArkPrincipal principal(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof AgentArkPrincipal principal)) {
            throw new IamAccessDeniedException("authenticated AgentArk principal is required");
        }
        return principal;
    }

    /**
     * 以不区分大小写方式解析受控枚举。
     *
     * @param type  枚举类型
     * @param value 请求值
     * @param <E>   枚举参数
     * @return 已解析枚举值
     */
    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("enum request value is invalid", exception);
        }
    }

    /**
     * 解析 UUIDv7 文本；具体强类型由应用服务路径参数继续约束。
     *
     * @param value UUID 文本
     * @return UUID 值
     */
    private UUID uuid(String value) {
        UUID parsed = UUID.fromString(value);
        if (parsed.version() != 7 || parsed.variant() != 2) {
            throw new IllegalArgumentException("request id must be an RFC 9562 UUIDv7");
        }
        return parsed;
    }
}
