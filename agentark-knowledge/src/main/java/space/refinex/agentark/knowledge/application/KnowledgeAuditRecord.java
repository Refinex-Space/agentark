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

package space.refinex.agentark.knowledge.application;

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Instant;
import java.util.Objects;

/**
 * 表示 Knowledge 模块输出的非敏感审计事实。
 *
 * @param action         稳定操作代码
 * @param actor          主体引用
 * @param resourceType   资源类型
 * @param resourceId     资源 UUIDv7 字符串
 * @param organizationId 组织标识
 * @param projectId      项目标识
 * @param occurredAt     发生时间
 * @author refinex
 */
public record KnowledgeAuditRecord(
    String action,
    String actor,
    String resourceType,
    String resourceId,
    OrganizationId organizationId,
    ProjectId projectId,
    Instant occurredAt) {

    /**
     * 校验稳定代码、主体、租户和时间字段。
     *
     * @param action         操作代码
     * @param actor          主体引用
     * @param resourceType   资源类型
     * @param resourceId     资源标识
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param occurredAt     发生时间
     */
    public KnowledgeAuditRecord {
        if (action == null || !action.matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("action must be a stable lowercase code");
        }
        if (actor == null || actor.isBlank() || actor.length() > 255) {
            throw new IllegalArgumentException("actor has invalid length");
        }
        if (resourceType == null || !resourceType.matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("resourceType must be a stable lowercase code");
        }
        if (resourceId == null || resourceId.isBlank() || resourceId.length() > 64) {
            throw new IllegalArgumentException("resourceId has invalid length");
        }
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
