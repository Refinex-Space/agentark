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

package space.refinex.agentark.control.iam.adapter.in.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import space.refinex.agentark.control.iam.application.IamAuthorizationService;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.web.TenantContext;
import space.refinex.agentark.foundation.web.TenantContextResolver;

import java.util.Optional;

/**
 * 仅从已认证 Principal 和数据库授权解析 TenantContext，完全忽略客户端 Tenant Header。
 *
 * @author refinex
 */
public final class IamTenantContextResolver implements TenantContextResolver {

    /**
     * IAM 应用授权服务。
     */
    private final IamAuthorizationService authorizationService;

    /**
     * 创建租户上下文解析器。
     *
     * @param authorizationService IAM 授权服务
     */
    public IamTenantContextResolver(IamAuthorizationService authorizationService) {
        this.authorizationService = java.util.Objects.requireNonNull(
            authorizationService, "authorizationService must not be null");
    }

    /**
     * 解析当前 SecurityContext 中已经通过数据库授权的租户选择。
     *
     * @return 已授权 TenantContext；匿名、无选择或无权限时为空
     */
    @Override
    public Optional<TenantContext> resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
            || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof AgentArkPrincipal principal)) {
            return Optional.empty();
        }
        return authorizationService.authorizedTenantSelection(principal)
            .map(selection -> new TenantContext(
                selection.organizationId(), selection.projectId(), selection.environmentId()));
    }
}
