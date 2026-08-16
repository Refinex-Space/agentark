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

import space.refinex.agentark.kernel.id.EmbeddingProfileId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.SecretRef;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 表示 Embedding Provider 描述、模型参数和可选 SecretRef 的不可变版本。
 *
 * @param id             Embedding Profile 版本标识
 * @param organizationId 组织标识
 * @param projectId      项目标识
 * @param key            项目内稳定 Key
 * @param versionNumber  Key 下版本号
 * @param configJson     规范化配置 JSON，不含凭据值
 * @param credentialRef  可选凭据引用
 * @param contentHash    配置与 SecretRef 的 SHA-256
 * @param status         发布状态
 * @param createdAt      创建时间
 * @author refinex
 */
public record EmbeddingProfile(
    EmbeddingProfileId id,
    OrganizationId organizationId,
    ProjectId projectId,
    String key,
    long versionNumber,
    String configJson,
    Optional<SecretRef> credentialRef,
    Checksum contentHash,
    KnowledgeProfileStatus status,
    Instant createdAt) {

    /**
     * 校验 Embedding Profile 的租户、SecretRef 容器和不可变版本字段。
     *
     * @param id             Profile 标识
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param key            稳定 Key
     * @param versionNumber  版本号
     * @param configJson     配置 JSON
     * @param credentialRef  可选凭据引用
     * @param contentHash    内容摘要
     * @param status         发布状态
     * @param createdAt      创建时间
     */
    public EmbeddingProfile {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        credentialRef = Objects.requireNonNull(credentialRef, "credentialRef must not be null");
        ProfileRules.require(key, versionNumber, configJson, contentHash, status, createdAt);
    }
}
