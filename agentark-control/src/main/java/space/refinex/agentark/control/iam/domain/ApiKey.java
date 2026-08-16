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

package space.refinex.agentark.control.iam.domain;

import space.refinex.agentark.kernel.id.ApiKeyId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.ServiceAccountId;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 表示只保存 SHA-256 摘要和公开前缀的 API Key 元数据。
 *
 * @param id               API Key 标识
 * @param organizationId   所属组织
 * @param projectId        所属项目
 * @param serviceAccountId 所属服务账号
 * @param name             项目内展示名称
 * @param prefix           用于定位摘要记录的公开随机前缀
 * @param digest           32 字节 SHA-256 摘要；不得是明文 Key
 * @param scopes           对服务账号有效权限的进一步收窄集合
 * @param expiresAt        可选到期时刻
 * @param revokedAt        可选吊销时刻
 * @param version          乐观锁版本
 * @param createdAt        创建时刻
 * @param updatedAt        最近更新时间
 * @author refinex
 */
public record ApiKey(
    ApiKeyId id,
    OrganizationId organizationId,
    ProjectId projectId,
    ServiceAccountId serviceAccountId,
    String name,
    String prefix,
    byte[] digest,
    Set<String> scopes,
    Optional<Instant> expiresAt,
    Optional<Instant> revokedAt,
    long version,
    Instant createdAt,
    Instant updatedAt) {

    /**
     * 校验 API Key 元数据并防御性复制摘要与 Scope。
     *
     * @param id               API Key 标识
     * @param organizationId   所属组织
     * @param projectId        所属项目
     * @param serviceAccountId 所属服务账号
     * @param name             展示名称
     * @param prefix           公开前缀
     * @param digest           SHA-256 摘要
     * @param scopes           Scope 集合
     * @param expiresAt        可选到期时刻
     * @param revokedAt        可选吊销时刻
     * @param version          非负版本
     * @param createdAt        创建时刻
     * @param updatedAt        更新时间
     */
    public ApiKey {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(serviceAccountId, "serviceAccountId must not be null");
        name = IamFieldPolicy.text(name, "name", 128);
        if (prefix == null || !prefix.matches("[A-Za-z0-9_-]{12}")) {
            throw new IllegalArgumentException("prefix must contain 12 base64url characters");
        }
        if (digest == null || digest.length != 32) {
            throw new IllegalArgumentException("digest must contain 32 bytes");
        }
        digest = Arrays.copyOf(digest, digest.length);
        scopes = Set.copyOf(Objects.requireNonNull(scopes, "scopes must not be null"));
        if (scopes.isEmpty()
            || scopes.stream()
            .anyMatch(value -> !value.matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_]*"))) {
            throw new IllegalArgumentException("scopes must contain permission keys");
        }
        expiresAt = normalize(expiresAt);
        revokedAt = normalize(revokedAt);
        version = IamFieldPolicy.version(version);
        createdAt = IamFieldPolicy.instant(createdAt, "createdAt");
        updatedAt = IamFieldPolicy.instant(updatedAt, "updatedAt");
        if (expiresAt.isPresent() && !expiresAt.orElseThrow().isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    /**
     * 返回摘要防御性副本，调用方不能修改领域对象内部字节。
     *
     * @return 32 字节摘要副本
     */
    @Override
    public byte[] digest() {
        return Arrays.copyOf(digest, digest.length);
    }

    /**
     * 判断 Key 在指定时刻是否仍可用于认证。
     *
     * @param now 当前时刻
     * @return 未吊销且未到期时为 {@code true}
     */
    public boolean isUsableAt(Instant now) {
        Instant checked = IamFieldPolicy.instant(now, "now");
        return revokedAt.isEmpty() && expiresAt.map(checked::isBefore).orElse(true);
    }

    /**
     * 创建只含摘要的 API Key 元数据。
     *
     * @param organizationId   所属组织
     * @param projectId        所属项目
     * @param serviceAccountId 所属服务账号
     * @param name             展示名称
     * @param prefix           公开前缀
     * @param digest           SHA-256 摘要
     * @param scopes           权限 Scope
     * @param expiresAt        可选到期时刻
     * @param now              当前时刻
     * @return 新 API Key 元数据
     */
    public static ApiKey create(
        OrganizationId organizationId,
        ProjectId projectId,
        ServiceAccountId serviceAccountId,
        String name,
        String prefix,
        byte[] digest,
        Set<String> scopes,
        Optional<Instant> expiresAt,
        Instant now) {
        Instant timestamp = IamFieldPolicy.instant(now, "now");
        return new ApiKey(
            ApiKeyId.generate(),
            organizationId,
            projectId,
            serviceAccountId,
            name,
            prefix,
            digest,
            scopes,
            expiresAt,
            Optional.empty(),
            0,
            timestamp,
            timestamp);
    }

    /**
     * 规范化可选时刻到微秒精度。
     *
     * @param value 可选时刻
     * @return 规范化后的容器
     */
    private static Optional<Instant> normalize(Optional<Instant> value) {
        return Objects.requireNonNull(value, "instant optional must not be null")
            .map(item -> IamFieldPolicy.instant(item, "instant"));
    }
}
