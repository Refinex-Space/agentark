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

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.SecretRef;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 表示 Environment 内别名到同项目 Secret Metadata 的受控绑定。
 *
 * @param id               Binding 标识
 * @param organizationId   所属组织
 * @param projectId        所属项目
 * @param environmentId    所属环境
 * @param secretMetadataId Secret Metadata 标识
 * @param bindingKey       环境内稳定别名
 * @param active           是否启用
 * @param version          乐观锁版本
 * @param createdAt        创建时刻
 * @param updatedAt        更新时间
 * @author refinex
 */
public record SecretBinding(
    SecretBindingId id,
    OrganizationId organizationId,
    ProjectId projectId,
    EnvironmentId environmentId,
    SecretMetadataId secretMetadataId,
    String bindingKey,
    boolean active,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验绑定所有权和稳定别名。
     */
    public SecretBinding {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(environmentId, "environmentId must not be null");
        Objects.requireNonNull(secretMetadataId, "secretMetadataId must not be null");
        if (bindingKey == null || !bindingKey.matches("[a-z][a-z0-9-]{1,62}")) {
            throw new IllegalArgumentException("bindingKey must be a stable lowercase key");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
        updatedAt = updatedAt.truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * @return 不含 Secret 值的 Environment SecretRef
     */
    public SecretRef ref() {
        return SecretRef.parse(
            "secret://environment/" + environmentId.asString() + "/" + bindingKey);
    }
}
