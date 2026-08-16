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

package space.refinex.agentark.control.catalog.domain;

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.StrongId;

import java.time.Instant;
import java.util.Objects;

/**
 * 表示 Prompt、Model Provider、MCP Server、Skill、Profile、Policy 或 Agent 的稳定身份。
 *
 * @param id             强类型稳定身份
 * @param kind           资产分类
 * @param organizationId 所属组织
 * @param projectId      所属项目
 * @param key            项目内稳定 Key
 * @param name           显示名称
 * @param description    可选用途说明
 * @param metadataJson   分类专属非敏感元数据规范 JSON
 * @param status         生命周期状态
 * @param version        乐观锁版本
 * @param createdAt      创建时刻
 * @param updatedAt      最后更新时间
 * @author refinex
 */
public record CatalogAsset(
    StrongId id,
    CatalogAssetKind kind,
    OrganizationId organizationId,
    ProjectId projectId,
    String key,
    String name,
    String description,
    String metadataJson,
    CatalogAssetStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验稳定身份和项目归属。
     *
     * @param id             强类型稳定身份
     * @param kind           资产分类
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param key            稳定 Key
     * @param name           显示名称
     * @param description    可选说明
     * @param metadataJson   非敏感元数据 JSON
     * @param status         生命周期状态
     * @param version        非负乐观锁版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     */
    public CatalogAsset {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        key = CatalogFieldPolicy.key(key, "key");
        name = CatalogFieldPolicy.text(name, "name", 128);
        description = CatalogFieldPolicy.optionalText(description, "description", 512);
        metadataJson = CatalogFieldPolicy.json(metadataJson, "metadataJson");
        Objects.requireNonNull(status, "status must not be null");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        createdAt = CatalogFieldPolicy.instant(createdAt, "createdAt");
        updatedAt = CatalogFieldPolicy.instant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }
}

