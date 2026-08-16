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

import space.refinex.agentark.kernel.id.EnvironmentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Instant;
import java.util.Objects;

/**
 * 表示同时携带组织与项目归属的部署环境聚合。
 *
 * @param id             环境标识
 * @param organizationId 所属组织
 * @param projectId      所属项目
 * @param key            项目内唯一稳定 Key
 * @param name           展示名称
 * @param status         生命周期状态
 * @param version        乐观锁版本
 * @param createdAt      创建时刻
 * @param updatedAt      最近更新时间
 * @author refinex
 */
public record Environment(
    EnvironmentId id,
    OrganizationId organizationId,
    ProjectId projectId,
    String key,
    String name,
    IamStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验环境聚合的完整租户链路。
     *
     * @param id             环境标识
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param key            稳定 Key
     * @param name           展示名称
     * @param status         状态
     * @param version        非负版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     */
    public Environment {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        key = IamFieldPolicy.slug(key, "key");
        name = IamFieldPolicy.text(name, "name", 128);
        Objects.requireNonNull(status, "status must not be null");
        version = IamFieldPolicy.version(version);
        createdAt = IamFieldPolicy.instant(createdAt, "createdAt");
        updatedAt = IamFieldPolicy.instant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }

    /**
     * 创建新的活动环境。
     *
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param key            项目内稳定 Key
     * @param name           展示名称
     * @param now            创建时刻
     * @return 版本为零的新环境
     */
    public static Environment create(
        OrganizationId organizationId,
        ProjectId projectId,
        String key,
        String name,
        Instant now) {
        Instant timestamp = IamFieldPolicy.instant(now, "now");
        return new Environment(
            EnvironmentId.generate(),
            organizationId,
            projectId,
            key,
            name,
            IamStatus.ACTIVE,
            0,
            timestamp,
            timestamp);
    }
}
