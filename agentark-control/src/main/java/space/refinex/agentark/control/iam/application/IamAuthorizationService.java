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

import space.refinex.agentark.control.iam.application.port.AuthorizationCache;
import space.refinex.agentark.control.iam.application.port.AuthorizationRepository;
import space.refinex.agentark.control.iam.application.port.IdentityRepository;
import space.refinex.agentark.control.iam.domain.PrincipalKind;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.foundation.security.TenantSelection;
import space.refinex.agentark.kernel.id.EnvironmentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.ServiceAccountId;

import java.time.Clock;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 将认证主体映射为持久 IAM 主体，并执行 Claim Scope 与数据库角色的双重授权。
 *
 * @author refinex
 */
public final class IamAuthorizationService {

    /**
     * 外部身份与服务账号端口。
     */
    private final IdentityRepository identityRepository;

    /**
     * 外部身份首次映射事务服务。
     */
    private final IamIdentityMappingService identityMappingService;

    /**
     * 角色与有效权限端口。
     */
    private final AuthorizationRepository authorizationRepository;

    /**
     * 短 TTL 权限缓存。
     */
    private final AuthorizationCache authorizationCache;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 创建应用授权服务。
     *
     * @param identityRepository      身份端口
     * @param identityMappingService  外部身份映射事务服务
     * @param authorizationRepository 授权端口
     * @param authorizationCache      短 TTL 缓存
     * @param clock                   UTC 时钟
     */
    public IamAuthorizationService(
        IdentityRepository identityRepository,
        IamIdentityMappingService identityMappingService,
        AuthorizationRepository authorizationRepository,
        AuthorizationCache authorizationCache,
        Clock clock) {
        this.identityRepository = java.util.Objects.requireNonNull(
            identityRepository, "identityRepository must not be null");
        this.identityMappingService = java.util.Objects.requireNonNull(
            identityMappingService, "identityMappingService must not be null");
        this.authorizationRepository = java.util.Objects.requireNonNull(
            authorizationRepository, "authorizationRepository must not be null");
        this.authorizationCache = java.util.Objects.requireNonNull(
            authorizationCache, "authorizationCache must not be null");
        this.clock = java.util.Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 校验平台级权限；当前仅用于由受信 IdP Claim 控制的首个组织创建。
     *
     * @param principal  已认证主体
     * @param permission 平台权限键
     * @return 映射后的用户主体
     * @throws IamAccessDeniedException 当主体不是用户或缺少平台权限时抛出
     */
    public ResolvedPrincipal requirePlatformPermission(
        AgentArkPrincipal principal, String permission) {
        PermissionRegistry.requireRegistered(Set.of(permission));
        if (principal.type() != PrincipalType.USER
            || !principal.authorities().contains(permission)) {
            throw new IamAccessDeniedException("platform permission is required");
        }
        return resolve(principal);
    }

    /**
     * 校验主体在指定资源 Scope 上拥有权限，并拒绝 JWT 候选租户与资源不一致。
     *
     * @param principal      已认证主体
     * @param organizationId 目标组织
     * @param projectId      可选目标项目
     * @param environmentId  可选目标环境
     * @param permission     必需权限键
     * @return 映射后的持久主体
     * @throws IamAccessDeniedException 当 Scope 或有效权限不满足时抛出
     */
    public ResolvedPrincipal requirePermission(
        AgentArkPrincipal principal,
        OrganizationId organizationId,
        Optional<ProjectId> projectId,
        Optional<EnvironmentId> environmentId,
        String permission) {
        java.util.Objects.requireNonNull(principal, "principal must not be null");
        java.util.Objects.requireNonNull(organizationId, "organizationId must not be null");
        projectId = java.util.Objects.requireNonNull(projectId, "projectId must not be null");
        environmentId = java.util.Objects.requireNonNull(
            environmentId, "environmentId must not be null");
        PermissionRegistry.requireRegistered(Set.of(permission));
        requireClaimScope(principal.tenantSelection(), organizationId, projectId, environmentId);

        ResolvedPrincipal resolved = resolve(principal);
        Set<String> permissions = effectivePermissions(
            principal, resolved, organizationId, projectId, environmentId);
        if (!permissions.contains(permission)) {
            throw new IamAccessDeniedException("resource permission is required");
        }
        return resolved;
    }

    /**
     * 返回服务账号在项目 Scope 的数据库有效权限，供创建 API Key 时验证 Scope 收窄。
     *
     * @param organizationId   组织标识
     * @param projectId        项目标识
     * @param serviceAccountId 服务账号标识
     * @return 不可变有效权限集合
     */
    public Set<String> serviceAccountPermissions(
        OrganizationId organizationId,
        ProjectId projectId,
        ServiceAccountId serviceAccountId) {
        return authorizationRepository.findEffectivePermissions(
            organizationId,
            Optional.of(projectId),
            Optional.empty(),
            PrincipalKind.SERVICE_ACCOUNT,
            serviceAccountId.value());
    }

    /**
     * 解析协议主体到持久主体；用户首次出现时创建 Issuer/Subject 映射。
     *
     * @param principal 已认证主体
     * @return 持久主体引用
     */
    public ResolvedPrincipal resolve(AgentArkPrincipal principal) {
        java.util.Objects.requireNonNull(principal, "principal must not be null");
        if (principal.type() == PrincipalType.USER) {
            var identity = identityRepository.findUserIdentity(
                    principal.issuer(), principal.subject())
                .orElseGet(() -> identityMappingService.resolveOrCreate(
                    principal.issuer(), principal.subject()));
            return new ResolvedPrincipal(PrincipalKind.USER, identity.id().value());
        }

        ServiceAccountId serviceAccountId;
        try {
            serviceAccountId = ServiceAccountId.parse(principal.subject());
        } catch (IllegalArgumentException exception) {
            throw new IamAccessDeniedException("service principal subject is invalid");
        }
        if (identityRepository.findServiceAccount(serviceAccountId).isEmpty()) {
            throw new IamAccessDeniedException("service principal is not active");
        }
        return new ResolvedPrincipal(
            PrincipalKind.SERVICE_ACCOUNT, serviceAccountId.value());
    }

    /**
     * 判断候选 Tenant Selection 已经由 IAM 有效权限验证，可用于请求上下文。
     *
     * @param principal 已认证主体
     * @return 通过数据库授权的候选选择；未选择或无权限时为空
     */
    public Optional<TenantSelection> authorizedTenantSelection(AgentArkPrincipal principal) {
        Optional<TenantSelection> selection = principal.tenantSelection();
        if (selection.isEmpty()) {
            return Optional.empty();
        }
        try {
            TenantSelection tenant = selection.orElseThrow();
            ResolvedPrincipal resolved = resolve(principal);
            Set<String> permissions = effectivePermissions(
                principal,
                resolved,
                tenant.organizationId(),
                tenant.projectId(),
                tenant.environmentId());
            return permissions.isEmpty() ? Optional.empty() : selection;
        } catch (IamAccessDeniedException exception) {
            return Optional.empty();
        }
    }

    /**
     * 从缓存或数据库读取权限；API Key 的 Scope 始终与角色权限取交集。
     *
     * @param principal      协议主体
     * @param resolved       持久主体
     * @param organizationId 组织标识
     * @param projectId      可选项目
     * @param environmentId  可选环境
     * @return 不可变权限集合
     */
    private Set<String> effectivePermissions(
        AgentArkPrincipal principal,
        ResolvedPrincipal resolved,
        OrganizationId organizationId,
        Optional<ProjectId> projectId,
        Optional<EnvironmentId> environmentId) {
        AuthorizationCacheKey key = new AuthorizationCacheKey(
            resolved.kind(), resolved.id(), organizationId, projectId, environmentId);
        Set<String> permissions = authorizationCache.get(key).orElseGet(
            () -> {
                Set<String> loaded = authorizationRepository.findEffectivePermissions(
                    organizationId,
                    projectId,
                    environmentId.map(EnvironmentId::value),
                    resolved.kind(),
                    resolved.id());
                authorizationCache.put(key, loaded);
                return loaded;
            });
        if (principal.type() != PrincipalType.API_KEY) {
            return permissions;
        }
        Set<String> attenuated = new HashSet<>(permissions);
        attenuated.retainAll(principal.authorities());
        return Set.copyOf(attenuated);
    }

    /**
     * 验证受信 JWT 中的候选租户不能扩大到另一组织、项目或环境。
     *
     * @param selection      可选候选租户
     * @param organizationId 目标组织
     * @param projectId      目标项目
     * @param environmentId  目标环境
     */
    private void requireClaimScope(
        Optional<TenantSelection> selection,
        OrganizationId organizationId,
        Optional<ProjectId> projectId,
        Optional<EnvironmentId> environmentId) {
        if (selection.isEmpty()) {
            return;
        }
        TenantSelection selected = selection.orElseThrow();
        if (!selected.organizationId().equals(organizationId)
            || selected.projectId().isPresent() && !selected.projectId().equals(projectId)
            || selected.environmentId().isPresent()
            && !selected.environmentId().equals(environmentId)) {
            throw new IamAccessDeniedException("credential tenant scope does not match resource");
        }
    }
}
