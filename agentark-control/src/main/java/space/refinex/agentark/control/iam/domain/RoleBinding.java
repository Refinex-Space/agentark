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

package space.refinex.agentark.control.iam.domain;

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.RoleBindingId;
import space.refinex.agentark.kernel.id.RoleId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 表示主体、角色和组织/项目/环境资源范围之间的显式授权关系。
 *
 * @param id             绑定标识
 * @param organizationId 所属组织
 * @param projectId      项目或环境绑定的所属项目；组织绑定为空
 * @param roleId         角色标识
 * @param principalKind  主体类别
 * @param principalId    用户身份或服务账号 UUIDv7
 * @param scopeType      资源范围类别
 * @param scopeId        对应组织、项目或环境 UUIDv7
 * @param version        乐观锁版本
 * @param createdAt      创建时刻
 * @param updatedAt      最近更新时间
 * @author refinex
 */
public record RoleBinding(
    RoleBindingId id,
    OrganizationId organizationId,
    Optional<ProjectId> projectId,
    RoleId roleId,
    PrincipalKind principalKind,
    UUID principalId,
    IamScopeType scopeType,
    UUID scopeId,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验角色绑定的 Scope 层级与主体标识。
     *
     * @param id             绑定标识
     * @param organizationId 所属组织
     * @param projectId      可选项目
     * @param roleId         角色标识
     * @param principalKind  主体类别
     * @param principalId    主体标识
     * @param scopeType      Scope 类别
     * @param scopeId        Scope 标识
     * @param version        非负版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     */
    public RoleBinding {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(roleId, "roleId must not be null");
        Objects.requireNonNull(principalKind, "principalKind must not be null");
        requireUuidV7(principalId, "principalId");
        Objects.requireNonNull(scopeType, "scopeType must not be null");
        requireUuidV7(scopeId, "scopeId");
        if ((scopeType == IamScopeType.ORGANIZATION) != projectId.isEmpty()) {
            throw new IllegalArgumentException("organization binding must omit projectId and child binding must include it");
        }
        version = IamFieldPolicy.version(version);
        createdAt = IamFieldPolicy.instant(createdAt, "createdAt");
        updatedAt = IamFieldPolicy.instant(updatedAt, "updatedAt");
    }

    /**
     * 创建新的角色绑定。
     *
     * @param organizationId 所属组织
     * @param projectId      可选项目
     * @param roleId         角色标识
     * @param principalKind  主体类别
     * @param principalId    主体 UUIDv7
     * @param scopeType      Scope 类别
     * @param scopeId        Scope UUIDv7
     * @param now            当前时刻
     * @return 新角色绑定
     */
    public static RoleBinding create(
        OrganizationId organizationId,
        Optional<ProjectId> projectId,
        RoleId roleId,
        PrincipalKind principalKind,
        UUID principalId,
        IamScopeType scopeType,
        UUID scopeId,
        Instant now) {

        Instant timestamp = IamFieldPolicy.instant(now, "now");
        return new RoleBinding(
            RoleBindingId.generate(),
            organizationId,
            projectId,
            roleId,
            principalKind,
            principalId,
            scopeType,
            scopeId,
            0,
            timestamp,
            timestamp);
    }

    /**
     * 校验多态 UUID 值符合平台 UUIDv7 约束。
     *
     * @param value 待校验值
     * @param name  字段名
     */
    private static void requireUuidV7(UUID value, String name) {
        if (value == null || value.version() != 7 || value.variant() != 2) {
            throw new IllegalArgumentException(name + " must be an RFC 9562 UUIDv7");
        }
    }
}
