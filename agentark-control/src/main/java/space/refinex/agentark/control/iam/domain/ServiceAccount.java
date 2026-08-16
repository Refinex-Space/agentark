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
import space.refinex.agentark.kernel.id.ServiceAccountId;

import java.time.Instant;
import java.util.Objects;

/**
 * 表示明确归属于组织和项目的非交互式服务账号。
 *
 * @param id             服务账号标识
 * @param organizationId 所属组织
 * @param projectId      所属项目
 * @param name           项目内唯一名称
 * @param status         生命周期状态
 * @param version        乐观锁版本
 * @param createdAt      创建时刻
 * @param updatedAt      最近更新时间
 * @author refinex
 */
public record ServiceAccount(
    ServiceAccountId id,
    OrganizationId organizationId,
    ProjectId projectId,
    String name,
    IamStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验服务账号租户归属和字段边界。
     *
     * @param id             服务账号标识
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param name           项目内名称
     * @param status         状态
     * @param version        非负版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     */
    public ServiceAccount {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        name = IamFieldPolicy.slug(name, "name");
        Objects.requireNonNull(status, "status must not be null");
        version = IamFieldPolicy.version(version);
        createdAt = IamFieldPolicy.instant(createdAt, "createdAt");
        updatedAt = IamFieldPolicy.instant(updatedAt, "updatedAt");
    }

    /**
     * 创建活动服务账号。
     *
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param name           项目内名称
     * @param now            当前时刻
     * @return 新服务账号
     */
    public static ServiceAccount create(
        OrganizationId organizationId, ProjectId projectId, String name, Instant now) {
        Instant timestamp = IamFieldPolicy.instant(now, "now");
        return new ServiceAccount(
            ServiceAccountId.generate(),
            organizationId,
            projectId,
            name,
            IamStatus.ACTIVE,
            0,
            timestamp,
            timestamp);
    }
}
