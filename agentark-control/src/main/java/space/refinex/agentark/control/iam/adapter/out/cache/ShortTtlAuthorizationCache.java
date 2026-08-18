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

package space.refinex.agentark.control.iam.adapter.out.cache;

import space.refinex.agentark.control.iam.application.AuthorizationCacheKey;
import space.refinex.agentark.control.iam.application.port.AuthorizationCache;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 使用进程内短 TTL 和显式失效提供授权缓存；MySQL 始终是事实源。
 *
 * <p>多副本最坏陈旧窗口受 TTL 限制。后续接入共享 Redis TypedCache 时保持同一端口。
 *
 * @author refinex
 */
public final class ShortTtlAuthorizationCache implements AuthorizationCache {

    /**
     * 缓存时钟。
     */
    private final Clock clock;

    /**
     * 最大陈旧窗口。
     */
    private final Duration ttl;

    /**
     * 当前进程权限缓存。
     */
    private final Map<AuthorizationCacheKey, Entry> entries = new ConcurrentHashMap<>();

    /**
     * 创建短 TTL 缓存。
     *
     * @param clock 缓存时钟
     * @param ttl   正数且不超过一分钟的 TTL
     */
    public ShortTtlAuthorizationCache(Clock clock, Duration ttl) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("authorization cache ttl must be within one minute");
        }
        this.ttl = ttl;
    }

    /**
     * 返回未过期权限，过期项会被即时删除。
     *
     * @param key 授权复合键
     * @return 缓存命中时返回权限集合
     */
    @Override
    public Optional<Set<String>> get(AuthorizationCacheKey key) {
        Entry entry = entries.get(Objects.requireNonNull(key, "key must not be null"));
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.permissions());
    }

    /**
     * 写入不可变权限集合并设置固定短 TTL。
     *
     * @param key         授权复合键
     * @param permissions 权限集合
     */
    @Override
    public void put(AuthorizationCacheKey key, Set<String> permissions) {
        entries.put(
            Objects.requireNonNull(key, "key must not be null"),
            new Entry(
                Set.copyOf(Objects.requireNonNull(permissions, "permissions must not be null")),
                clock.instant().plus(ttl)
            )
        );
    }

    /**
     * 删除组织内所有权限缓存。
     *
     * @param organizationId 组织标识
     */
    @Override
    public void evictOrganization(OrganizationId organizationId) {
        entries.keySet().removeIf(key ->
            key.organizationId().equals(Objects.requireNonNull(organizationId, "organizationId must not be null")));
    }

    /**
     * 删除项目及其环境权限缓存。
     *
     * @param projectId 项目标识
     */
    @Override
    public void evictProject(ProjectId projectId) {
        entries.keySet().removeIf(key ->
            key.projectId()
                .filter(value -> value.equals(Objects.requireNonNull(projectId, "projectId must not be null")))
                .isPresent());
    }

    /**
     * 表示权限集合及其绝对过期时刻。
     *
     * @param permissions 不可变权限集合
     * @param expiresAt   过期时刻
     * @author refinex
     */
    private record Entry(Set<String> permissions, Instant expiresAt) {

        /**
         * 校验缓存条目字段。
         *
         * @param permissions 权限集合
         * @param expiresAt   过期时刻
         */
        private Entry {
            permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions must not be null"));
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }
    }
}
