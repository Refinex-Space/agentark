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

import java.time.Instant;
import java.util.Objects;

/**
 * 表示明确归属于组织的项目聚合。
 *
 * @param id             项目标识
 * @param organizationId 所属组织
 * @param slug           组织内唯一 Slug
 * @param name           展示名称
 * @param status         生命周期状态
 * @param version        乐观锁版本
 * @param createdAt      创建时刻
 * @param updatedAt      最近更新时间
 * @author refinex
 */
public record Project(
    ProjectId id,
    OrganizationId organizationId,
    String slug,
    String name,
    IamStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验项目聚合不变量和组织归属。
     *
     * @param id             项目标识
     * @param organizationId 所属组织
     * @param slug           组织内 Slug
     * @param name           展示名称
     * @param status         状态
     * @param version        非负版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     */
    public Project {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        slug = IamFieldPolicy.slug(slug, "slug");
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
     * 创建新的活动项目。
     *
     * @param organizationId 所属组织
     * @param slug           组织内唯一 Slug
     * @param name           展示名称
     * @param now            创建时刻
     * @return 版本为零的新项目
     */
    public static Project create(OrganizationId organizationId, String slug, String name, Instant now) {
        Instant timestamp = IamFieldPolicy.instant(now, "now");
        return new Project(
            ProjectId.generate(),
            organizationId,
            slug,
            name,
            IamStatus.ACTIVE,
            0,
            timestamp,
            timestamp);
    }
}
