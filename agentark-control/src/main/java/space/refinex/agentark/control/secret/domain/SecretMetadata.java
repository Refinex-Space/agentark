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

package space.refinex.agentark.control.secret.domain;

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.SecretMetadataId;
import space.refinex.agentark.kernel.ref.SecretRef;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 表示外部 Secret Provider 的非敏感定位、版本和 Scope 元数据。
 *
 * @param id              元数据标识
 * @param organizationId  所属组织
 * @param projectId       所属项目
 * @param key             项目内稳定 Key
 * @param name            显示名称
 * @param provider        Provider 类型
 * @param externalPath    外部定位路径
 * @param externalVersion 可选外部版本
 * @param scope           引用范围
 * @param status          生命周期
 * @param version         乐观锁版本
 * @param createdAt       创建时刻
 * @param updatedAt       更新时间
 * @author refinex
 */
public record SecretMetadata(
    SecretMetadataId id,
    OrganizationId organizationId,
    ProjectId projectId,
    String key,
    String name,
    SecretProviderType provider,
    String externalPath,
    String externalVersion,
    SecretScope scope,
    SecretMetadataStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验非敏感定位字段，不接受 URI 凭据、Query、Fragment 或相对目录穿越。
     */
    public SecretMetadata {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        if (key == null || !key.matches("[a-z][a-z0-9-]{1,62}")) {
            throw new IllegalArgumentException("key must be a stable lowercase key");
        }
        if (name == null || name.isBlank() || name.length() > 128) {
            throw new IllegalArgumentException("name has invalid length");
        }
        Objects.requireNonNull(provider, "provider must not be null");
        if (externalPath == null || externalPath.isBlank() || externalPath.length() > 1024
            || externalPath.contains("..") || externalPath.contains("?")
            || externalPath.contains("#") || externalPath.contains("@")) {
            throw new IllegalArgumentException("externalPath is not a safe provider path");
        }
        externalVersion = externalVersion == null ? "" : externalVersion;
        if (externalVersion.length() > 255) {
            throw new IllegalArgumentException("externalVersion is too long");
        }
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
        updatedAt = updatedAt.truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * @return 仅含 Scope、项目和稳定 Key 的非敏感项目 SecretRef
     * @throws IllegalStateException 当前元数据要求 Environment Binding 时抛出
     */
    public SecretRef projectRef() {
        if (scope != SecretScope.PROJECT) {
            throw new IllegalStateException("environment scoped secret requires a binding");
        }
        return SecretRef.parse("secret://project/" + projectId.asString() + "/" + key);
    }
}
