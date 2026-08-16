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

import space.refinex.agentark.control.iam.domain.PrincipalKind;
import space.refinex.agentark.kernel.id.EnvironmentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 表示有效权限缓存的主体和资源 Scope 复合键。
 *
 * @param principalKind  主体类别
 * @param principalId    主体 UUIDv7
 * @param organizationId 组织标识
 * @param projectId      可选项目
 * @param environmentId  可选环境
 * @author refinex
 */
public record AuthorizationCacheKey(
    PrincipalKind principalKind,
    UUID principalId,
    OrganizationId organizationId,
    Optional<ProjectId> projectId,
    Optional<EnvironmentId> environmentId) {

    /**
     * 校验缓存键 UUID 与租户层级。
     *
     * @param principalKind  主体类别
     * @param principalId    主体标识
     * @param organizationId 组织标识
     * @param projectId      可选项目
     * @param environmentId  可选环境
     */
    public AuthorizationCacheKey {
        Objects.requireNonNull(principalKind, "principalKind must not be null");
        if (principalId == null || principalId.version() != 7 || principalId.variant() != 2) {
            throw new IllegalArgumentException("principalId must be an RFC 9562 UUIDv7");
        }
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        environmentId = Objects.requireNonNull(environmentId, "environmentId must not be null");
        if (environmentId.isPresent() && projectId.isEmpty()) {
            throw new IllegalArgumentException("environment cache scope requires project");
        }
    }
}
