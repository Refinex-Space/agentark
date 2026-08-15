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

package space.refinex.agentark.foundation.web;

import space.refinex.agentark.kernel.id.EnvironmentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.util.Objects;
import java.util.Optional;

/**
 * 表示认证与授权完成后写入请求链路的租户范围，客户端 Header 不能直接创建该上下文。
 *
 * @param organizationId 必需的组织标识
 * @param projectId      可选的项目标识
 * @param environmentId  可选的环境标识，仅在项目已确定时允许存在
 * @author refinex
 */
public record TenantContext(
    OrganizationId organizationId,
    Optional<ProjectId> projectId,
    Optional<EnvironmentId> environmentId) {

    /**
     * 校验租户层级并创建不可变上下文。
     *
     * @param organizationId 必需的组织标识
     * @param projectId      可选项目标识
     * @param environmentId  可选环境标识
     * @throws NullPointerException     当任一参数容器为 {@code null} 时抛出
     * @throws IllegalArgumentException 当环境存在但项目缺失时抛出
     */
    public TenantContext {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        environmentId = Objects.requireNonNull(environmentId, "environmentId must not be null");
        if (environmentId.isPresent() && projectId.isEmpty()) {
            throw new IllegalArgumentException("environmentId requires projectId");
        }
    }
}
