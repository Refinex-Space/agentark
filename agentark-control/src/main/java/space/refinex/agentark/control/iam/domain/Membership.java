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

import space.refinex.agentark.kernel.id.MembershipId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 表示用户或服务账号加入指定项目的显式成员关系。
 *
 * @param id             成员关系标识
 * @param organizationId 所属组织
 * @param projectId      所属项目
 * @param principalKind  主体类别
 * @param principalId    用户身份或服务账号 UUIDv7
 * @param status         成员关系状态
 * @param version        乐观锁版本
 * @param createdAt      创建时刻
 * @param updatedAt      最近更新时间
 * @author refinex
 */
public record Membership(
    MembershipId id,
    OrganizationId organizationId,
    ProjectId projectId,
    PrincipalKind principalKind,
    UUID principalId,
    IamStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验成员关系的租户、主体和状态不变量。
     *
     * @param id             成员关系标识
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param principalKind  主体类别
     * @param principalId    主体标识
     * @param status         状态
     * @param version        非负版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     */
    public Membership {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(principalKind, "principalKind must not be null");
        requireUuidV7(principalId);
        Objects.requireNonNull(status, "status must not be null");
        version = IamFieldPolicy.version(version);
        createdAt = IamFieldPolicy.instant(createdAt, "createdAt");
        updatedAt = IamFieldPolicy.instant(updatedAt, "updatedAt");
    }

    /**
     * 创建活动成员关系。
     *
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param principalKind  主体类别
     * @param principalId    主体 UUIDv7
     * @param now            当前时刻
     * @return 新成员关系
     */
    public static Membership create(
        OrganizationId organizationId,
        ProjectId projectId,
        PrincipalKind principalKind,
        UUID principalId,
        Instant now) {

        Instant timestamp = IamFieldPolicy.instant(now, "now");
        return new Membership(
            MembershipId.generate(),
            organizationId,
            projectId,
            principalKind,
            principalId,
            IamStatus.ACTIVE,
            0,
            timestamp,
            timestamp);
    }

    /**
     * 校验多态主体标识仍为平台规定的 UUIDv7。
     *
     * @param value 待校验 UUID
     */
    private static void requireUuidV7(UUID value) {
        if (value == null || value.version() != 7 || value.variant() != 2) {
            throw new IllegalArgumentException("principalId must be an RFC 9562 UUIDv7");
        }
    }
}
