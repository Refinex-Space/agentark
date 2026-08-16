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

import space.refinex.agentark.kernel.id.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表示 Knowledge Base 内文档的稳定身份、ACL 与非敏感检索元数据。
 *
 * @param id              文档标识
 * @param organizationId  组织标识
 * @param projectId       项目标识
 * @param knowledgeBaseId Knowledge Base 标识
 * @param dataSourceId    数据源标识
 * @param title           文档标题
 * @param metadata        不含原文和 Secret 的文档元数据
 * @param acl             显式文档 ACL
 * @param status          生命周期状态
 * @param version         乐观锁版本
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 * @author refinex
 */
public record Document(
    DocumentId id,
    OrganizationId organizationId,
    ProjectId projectId,
    KnowledgeBaseId knowledgeBaseId,
    DataSourceId dataSourceId,
    String title,
    Map<String, String> metadata,
    List<DocumentAcl> acl,
    DocumentStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验租户链、标题、元数据、ACL 和时间不变量。
     *
     * @param id              文档标识
     * @param organizationId  组织标识
     * @param projectId       项目标识
     * @param knowledgeBaseId Knowledge Base 标识
     * @param dataSourceId    数据源标识
     * @param title           文档标题
     * @param metadata        文档元数据
     * @param acl             文档 ACL
     * @param status          生命周期状态
     * @param version         乐观锁版本
     * @param createdAt       创建时间
     * @param updatedAt       更新时间
     */
    public Document {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(knowledgeBaseId, "knowledgeBaseId must not be null");
        Objects.requireNonNull(dataSourceId, "dataSourceId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (title == null || title.isBlank() || title.length() > 255) {
            throw new IllegalArgumentException("title must contain 1 to 255 characters");
        }
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
        acl = List.copyOf(Objects.requireNonNull(acl, "acl must not be null"));
        if (metadata.size() > 64
            || metadata.entrySet().stream().anyMatch(entry -> invalidMetadata(entry.getKey(), entry.getValue()))
            || acl.isEmpty()
            || version < 0
            || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("document metadata, acl, version, or time is invalid");
        }
    }

    /**
     * 判断单个元数据键值是否超过平台边界。
     *
     * @param key   元数据键
     * @param value 元数据值
     * @return 字段不合法时返回 {@code true}
     */
    private static boolean invalidMetadata(String key, String value) {
        return key == null
            || !key.matches("[a-z][a-z0-9_.-]{0,62}")
            || value == null
            || value.length() > 1024;
    }
}
