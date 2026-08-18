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

import java.time.Instant;
import java.util.Objects;

/**
 * 表示租户资源树的组织根聚合。
 *
 * @param id        组织强类型标识
 * @param slug      全局唯一稳定 Slug
 * @param name      展示名称
 * @param status    生命周期状态
 * @param version   乐观锁版本
 * @param createdAt 创建时刻
 * @param updatedAt 最近更新时间
 * @author refinex
 */
public record Organization(
    OrganizationId id,
    String slug,
    String name,
    IamStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验组织聚合不变量。
     *
     * @param id        组织标识
     * @param slug      全局 Slug
     * @param name      展示名称
     * @param status    状态
     * @param version   非负版本
     * @param createdAt 创建时刻
     * @param updatedAt 更新时间
     */
    public Organization {
        Objects.requireNonNull(id, "id must not be null");
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
     * 创建新的活动组织。
     *
     * @param slug 全局唯一 Slug
     * @param name 展示名称
     * @param now  创建时刻
     * @return 版本为零的新组织
     */
    public static Organization create(String slug, String name, Instant now) {
        Instant timestamp = IamFieldPolicy.instant(now, "now");
        return new Organization(OrganizationId.generate(), slug, name, IamStatus.ACTIVE, 0, timestamp, timestamp);
    }
}
