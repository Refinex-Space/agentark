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
import space.refinex.agentark.kernel.id.RoleId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 表示组织级或项目级角色及其不可重复权限键集合。
 *
 * @param id             角色标识
 * @param organizationId 所属组织
 * @param projectId      项目角色的所属项目；组织角色为空
 * @param key            Scope 内唯一角色键
 * @param name           展示名称
 * @param builtIn        是否由平台维护
 * @param status         生命周期状态
 * @param permissionKeys 已注册权限键集合
 * @param version        乐观锁版本
 * @param createdAt      创建时刻
 * @param updatedAt      最近更新时间
 * @author refinex
 */
public record Role(
    RoleId id,
    OrganizationId organizationId,
    Optional<ProjectId> projectId,
    String key,
    String name,
    boolean builtIn,
    IamStatus status,
    Set<String> permissionKeys,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验角色 Scope、权限集合和乐观锁字段。
     *
     * @param id             角色标识
     * @param organizationId 所属组织
     * @param projectId      可选项目
     * @param key            角色键
     * @param name           展示名称
     * @param builtIn        内置标志
     * @param status         状态
     * @param permissionKeys 权限键集合
     * @param version        非负版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     */
    public Role {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        key = IamFieldPolicy.slug(key, "key");
        name = IamFieldPolicy.text(name, "name", 128);
        Objects.requireNonNull(status, "status must not be null");
        permissionKeys = Set.copyOf(
            Objects.requireNonNull(permissionKeys, "permissionKeys must not be null"));
        if (permissionKeys.isEmpty()
            || permissionKeys.stream()
            .anyMatch(value -> !value.matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_]*"))) {
            throw new IllegalArgumentException("role must contain registered permission keys");
        }
        version = IamFieldPolicy.version(version);
        createdAt = IamFieldPolicy.instant(createdAt, "createdAt");
        updatedAt = IamFieldPolicy.instant(updatedAt, "updatedAt");
    }

    /**
     * 创建活动角色。
     *
     * @param organizationId 所属组织
     * @param projectId      可选项目
     * @param key            Scope 内角色键
     * @param name           展示名称
     * @param builtIn        是否由平台维护
     * @param permissionKeys 权限键集合
     * @param now            当前时刻
     * @return 新角色
     */
    public static Role create(
        OrganizationId organizationId,
        Optional<ProjectId> projectId,
        String key,
        String name,
        boolean builtIn,
        Set<String> permissionKeys,
        Instant now) {
        Instant timestamp = IamFieldPolicy.instant(now, "now");
        return new Role(
            RoleId.generate(),
            organizationId,
            projectId,
            key,
            name,
            builtIn,
            IamStatus.ACTIVE,
            permissionKeys,
            0,
            timestamp,
            timestamp);
    }
}
