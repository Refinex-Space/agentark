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

package space.refinex.agentark.runtime.application;

import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.TenantSelection;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.runtime.domain.RuntimeAccessDeniedException;
import space.refinex.agentark.runtime.domain.RuntimeNotFoundException;

import java.util.Objects;

/**
 * 校验 Runtime API 权限声明与 Token 中已选择的 Organization/Project 租户范围。
 *
 * @author refinex
 */
public final class RuntimeAuthorizationService {

    /**
     * 创建 Session 和 Turn 的权限。
     */
    public static final String EXECUTE = "runtime:execute";

    /**
     * 读取 Session、Run 和 Event 的权限。
     */
    public static final String READ = "runtime:read";

    /**
     * 取消 Run 的权限。
     */
    public static final String CANCEL = "runtime:cancel";

    /**
     * 查看和决策 Approval 的权限。
     */
    public static final String APPROVE = "runtime:approve";

    /**
     * 要求主体同时具有权限和精确 Project 租户选择。
     *
     * @param principal      已认证主体
     * @param permission     稳定权限
     * @param organizationId 资源组织
     * @param projectId      资源项目
     */
    public void requireProject(
        AgentArkPrincipal principal,
        String permission,
        OrganizationId organizationId,
        ProjectId projectId) {
        Objects.requireNonNull(principal, "principal must not be null");
        if (!principal.authorities().contains(permission)) {
            throw new RuntimeAccessDeniedException("runtime permission is required");
        }
        TenantSelection tenant = principal.tenantSelection()
            .orElseThrow(() -> new RuntimeAccessDeniedException(
                "runtime project selection is required"));
        if (!tenant.organizationId().equals(organizationId)
            || tenant.projectId().isEmpty()
            || !tenant.projectId().orElseThrow().equals(projectId)) {
            throw new RuntimeNotFoundException("runtime resource is not available");
        }
    }
}
