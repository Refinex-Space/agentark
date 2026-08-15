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

package space.refinex.agentark.foundation.security;

import space.refinex.agentark.kernel.id.EnvironmentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.util.Objects;
import java.util.Optional;

/**
 * 表示认证声明中的候选租户选择，业务授权仍须由 Control IAM 对资源进行校验。
 *
 * @param organizationId 必需组织标识
 * @param projectId      可选项目标识
 * @param environmentId  可选环境标识，仅允许在项目存在时出现
 * @author refinex
 */
public record TenantSelection(
    OrganizationId organizationId,
    Optional<ProjectId> projectId,
    Optional<EnvironmentId> environmentId) {

    /**
     * 校验租户层级并创建候选选择。
     *
     * @param organizationId 组织标识
     * @param projectId      可选项目标识
     * @param environmentId  可选环境标识
     * @throws NullPointerException     当参数或 Optional 容器为 {@code null} 时抛出
     * @throws IllegalArgumentException 当环境存在但项目缺失时抛出
     */
    public TenantSelection {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        environmentId = Objects.requireNonNull(environmentId, "environmentId must not be null");
        if (environmentId.isPresent() && projectId.isEmpty()) {
            throw new IllegalArgumentException("environmentId requires projectId");
        }
    }
}
