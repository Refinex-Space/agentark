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

import space.refinex.agentark.kernel.id.DataSourceId;
import space.refinex.agentark.kernel.id.KnowledgeBaseId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Instant;
import java.util.Objects;

/**
 * 表示 Knowledge Base 内受控的文档来源描述，不包含连接器凭据。
 *
 * @param id              数据源标识
 * @param organizationId  组织标识
 * @param projectId       项目标识
 * @param knowledgeBaseId Knowledge Base 标识
 * @param type            来源类型
 * @param name            显示名称
 * @param descriptorJson  不含 Secret 的规范化 JSON 描述
 * @param createdAt       创建时间
 * @author refinex
 */
public record DataSource(
    DataSourceId id,
    OrganizationId organizationId,
    ProjectId projectId,
    KnowledgeBaseId knowledgeBaseId,
    DataSourceType type,
    String name,
    String descriptorJson,
    Instant createdAt) {

    /**
     * 校验租户链、来源描述和创建时间。
     *
     * @param id              数据源标识
     * @param organizationId  组织标识
     * @param projectId       项目标识
     * @param knowledgeBaseId Knowledge Base 标识
     * @param type            来源类型
     * @param name            显示名称
     * @param descriptorJson  规范化 JSON 描述
     * @param createdAt       创建时间
     */
    public DataSource {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(knowledgeBaseId, "knowledgeBaseId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (name == null || name.isBlank() || name.length() > 128) {
            throw new IllegalArgumentException("name must contain 1 to 128 characters");
        }
        if (descriptorJson == null || descriptorJson.isBlank() || descriptorJson.length() > 8192) {
            throw new IllegalArgumentException("descriptorJson has invalid length");
        }
    }
}
