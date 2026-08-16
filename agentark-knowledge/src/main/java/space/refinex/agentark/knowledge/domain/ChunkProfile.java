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

package space.refinex.agentark.knowledge.domain;

import space.refinex.agentark.kernel.id.ChunkProfileId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;

import java.time.Instant;
import java.util.Objects;

/**
 * 表示切分策略选择与参数的不可变版本，不包含切分器实现类型。
 *
 * @param id             Chunk Profile 版本标识
 * @param organizationId 组织标识
 * @param projectId      项目标识
 * @param key            项目内稳定 Key
 * @param versionNumber  Key 下版本号
 * @param configJson     规范化配置 JSON
 * @param contentHash    配置 SHA-256
 * @param status         发布状态
 * @param createdAt      创建时间
 * @author refinex
 */
public record ChunkProfile(
    ChunkProfileId id,
    OrganizationId organizationId,
    ProjectId projectId,
    String key,
    long versionNumber,
    String configJson,
    Checksum contentHash,
    KnowledgeProfileStatus status,
    Instant createdAt) {

    /**
     * 校验 Chunk Profile 的租户与不可变版本字段。
     *
     * @param id             Profile 标识
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param key            稳定 Key
     * @param versionNumber  版本号
     * @param configJson     配置 JSON
     * @param contentHash    内容摘要
     * @param status         发布状态
     * @param createdAt      创建时间
     */
    public ChunkProfile {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        ProfileRules.require(key, versionNumber, configJson, contentHash, status, createdAt);
    }
}
