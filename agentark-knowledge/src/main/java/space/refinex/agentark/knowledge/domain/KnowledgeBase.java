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

import space.refinex.agentark.kernel.id.KnowledgeBaseId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Instant;
import java.util.Objects;

/**
 * 表示项目内 Knowledge Base 的稳定身份，内容变化通过 Knowledge Revision 表达。
 *
 * @param id             Knowledge Base 标识
 * @param organizationId 组织标识
 * @param projectId      项目标识
 * @param key            项目内稳定 Key
 * @param name           显示名称
 * @param description    可选用途说明
 * @param status         生命周期状态
 * @param version        乐观锁版本
 * @param createdAt      创建时间
 * @param updatedAt      更新时间
 * @author refinex
 */
public record KnowledgeBase(
    KnowledgeBaseId id,
    OrganizationId organizationId,
    ProjectId projectId,
    String key,
    String name,
    String description,
    KnowledgeBaseStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验稳定身份、租户、文本、状态和时间不变量。
     *
     * @param id             Knowledge Base 标识
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param key            稳定 Key
     * @param name           显示名称
     * @param description    用途说明
     * @param status         生命周期状态
     * @param version        乐观锁版本
     * @param createdAt      创建时间
     * @param updatedAt      更新时间
     */
    public KnowledgeBase {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (key == null || !key.matches("[a-z][a-z0-9-]{0,62}")) {
            throw new IllegalArgumentException("key must be a lowercase stable key");
        }
        if (name == null || name.isBlank() || name.length() > 128) {
            throw new IllegalArgumentException("name must contain 1 to 128 characters");
        }
        description = description == null ? "" : description.strip();
        if (description.length() > 512 || version < 0 || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("knowledge base metadata is invalid");
        }
    }
}
