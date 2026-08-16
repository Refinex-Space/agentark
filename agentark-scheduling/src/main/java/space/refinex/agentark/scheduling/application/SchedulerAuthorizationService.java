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

package space.refinex.agentark.scheduling.application;

import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.TenantSelection;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.scheduling.domain.SchedulerException;

import java.util.Objects;

/**
 * 校验 Scheduler 管理权限和 Token 中精确 Project 租户选择。
 *
 * @author refinex
 */
public final class SchedulerAuthorizationService {

    /**
     * 读取 Job 与 Dead Letter 的权限。
     */
    public static final String READ = "scheduler:read";

    /**
     * 取消 Job 的权限。
     */
    public static final String MANAGE = "scheduler:manage";

    /**
     * Redrive Dead Letter 的高风险权限。
     */
    public static final String REDRIVE = "scheduler:redrive";

    /**
     * 创建无状态 Scheduler 授权服务。
     */
    public SchedulerAuthorizationService() {
    }

    /**
     * 要求主体具有指定权限和精确 Organization/Project 选择。
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
            throw new SchedulerException(
                "SCHEDULER_ACCESS_DENIED", "scheduler permission is required");
        }
        TenantSelection tenant = principal.tenantSelection()
            .orElseThrow(() -> new SchedulerException(
                "SCHEDULER_ACCESS_DENIED", "scheduler project selection is required"));
        if (!tenant.organizationId().equals(organizationId)
            || tenant.projectId().isEmpty()
            || !tenant.projectId().orElseThrow().equals(projectId)) {
            throw new SchedulerException("JOB_NOT_FOUND", "scheduler resource is not available");
        }
    }

    /**
     * 返回审计使用的 Issuer 与 Subject 稳定组合，不包含 Token。
     *
     * @param principal 已认证主体
     * @return 稳定操作者引用
     */
    public String actor(AgentArkPrincipal principal) {
        Objects.requireNonNull(principal, "principal must not be null");
        return principal.issuer() + ':' + principal.subject();
    }
}
