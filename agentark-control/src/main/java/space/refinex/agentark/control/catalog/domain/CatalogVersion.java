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
import space.refinex.agentark.kernel.ref.Checksum;

import java.time.Instant;
import java.util.Objects;

/**
 * 表示行为资产的只追加不可变版本；内容更新只能创建下一版本。
 *
 * @param id             强类型版本标识
 * @param kind           资产分类
 * @param organizationId 所属组织
 * @param projectId      所属项目
 * @param ownerId        稳定身份标识
 * @param versionNumber  Owner 内单调递增版本号
 * @param payloadJson    规范化语言中立 JSON
 * @param contentHash    内容 SHA-256
 * @param status         版本创建状态
 * @param createdAt      创建时刻
 * @author refinex
 */
public record CatalogVersion(
    StrongId id,
    CatalogAssetKind kind,
    OrganizationId organizationId,
    ProjectId projectId,
    StrongId ownerId,
    long versionNumber,
    String payloadJson,
    Checksum contentHash,
    CatalogVersionStatus status,
    Instant createdAt) {

    /**
     * 校验版本内容、Hash 和 Owner 归属。
     *
     * @param id             强类型版本标识
     * @param kind           资产分类
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param ownerId        稳定身份标识
     * @param versionNumber  正数版本号
     * @param payloadJson    规范 JSON
     * @param contentHash    内容 Hash
     * @param status         版本状态
     * @param createdAt      创建时刻
     */
    public CatalogVersion {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber must be positive");
        }
        payloadJson = CatalogFieldPolicy.json(payloadJson, "payloadJson");
        Objects.requireNonNull(contentHash, "contentHash must not be null");
        Objects.requireNonNull(status, "status must not be null");
        createdAt = CatalogFieldPolicy.instant(createdAt, "createdAt");
    }
}

