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

package space.refinex.agentark.control.iam.application;

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 表示可安全写入审计 Sink 的 IAM 操作事实，不包含 API Key、Token 或请求正文。
 *
 * @param action         稳定操作代码
 * @param principal      Issuer 与 Subject 摘要化前的稳定非秘密引用
 * @param resourceType   资源类型
 * @param resourceId     资源 UUIDv7 字符串
 * @param organizationId 可选组织
 * @param projectId      可选项目
 * @param outcome        固定成功或拒绝结果
 * @param occurredAt     操作发生时刻
 * @author refinex
 */
public record IamAuditRecord(
    String action,
    String principal,
    String resourceType,
    String resourceId,
    Optional<OrganizationId> organizationId,
    Optional<ProjectId> projectId,
    String outcome,
    Instant occurredAt) {

    /**
     * 校验审计字段长度并保证 Scope 层级一致。
     *
     * @param action         操作代码
     * @param principal      主体引用
     * @param resourceType   资源类型
     * @param resourceId     资源标识
     * @param organizationId 可选组织
     * @param projectId      可选项目
     * @param outcome        操作结果
     * @param occurredAt     发生时刻
     */
    public IamAuditRecord {
        action = requireCode(action, "action");
        principal = requireText(principal, "principal", 255);
        resourceType = requireCode(resourceType, "resourceType");
        resourceId = requireText(resourceId, "resourceId", 64);
        organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
        projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        if (projectId.isPresent() && organizationId.isEmpty()) {
            throw new IllegalArgumentException("project audit scope requires organization");
        }
        if (!SetHolder.OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("outcome must be SUCCEEDED, DENIED or FAILED");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * 校验小写稳定代码。
     *
     * @param value 待校验值
     * @param name  字段名
     * @return 原值
     */
    private static String requireCode(String value, String name) {
        if (value == null || !value.matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException(name + " must be a stable lowercase code");
        }
        return value;
    }

    /**
     * 校验普通非秘密短文本。
     *
     * @param value     待校验值
     * @param name      字段名
     * @param maxLength 最大字符数
     * @return 原值
     */
    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " has invalid length");
        }
        return value;
    }

    /**
     * 延迟持有固定 Outcome 集合，避免记录构造时重复分配。
     *
     * @author refinex
     */
    private static final class SetHolder {

        /**
         * 允许的审计结果代码。
         */
        private static final java.util.Set<String> OUTCOMES = java.util.Set.of(
            "SUCCEEDED", "DENIED", "FAILED");

        /**
         * 禁止实例化常量持有类。
         */
        private SetHolder() {
        }
    }
}
