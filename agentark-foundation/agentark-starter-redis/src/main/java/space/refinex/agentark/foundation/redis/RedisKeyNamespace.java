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

package space.refinex.agentark.foundation.redis;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 生成平台、应用、能力与业务键四级隔离的 Redis Key，并统一限制 TTL。
 *
 * @author refinex
 */
public final class RedisKeyNamespace {

    /**
     * 平台级 Key 前缀。
     */
    private final String keyPrefix;

    /**
     * 应用级隔离名称。
     */
    private final String applicationName;

    /**
     * 允许的最大 TTL。
     */
    private final Duration maxTtl;

    /**
     * 创建 Redis Key 命名空间。
     *
     * @param properties 已绑定并待校验的 Redis 属性
     * @throws IllegalStateException 当启用后未配置应用名称时抛出
     */
    public RedisKeyNamespace(AgentArkRedisProperties properties) {
        java.util.Objects.requireNonNull(properties, "properties must not be null");
        if (properties.getApplicationName() == null) {
            throw new IllegalStateException("redis applicationName must be configured");
        }
        this.keyPrefix = properties.getKeyPrefix();
        this.applicationName = properties.getApplicationName();
        this.maxTtl = properties.getMaxTtl();
    }

    /**
     * 生成不可注入额外分隔段的 Redis Key。
     *
     * @param capability  稳定能力命名空间
     * @param businessKey 任意非空业务键，将使用 Base64 URL 编码
     * @return 完整 Redis Key
     */
    public String key(String capability, String businessKey) {
        if (capability == null || !capability.matches("[a-z][a-z0-9-]{0,62}")) {
            throw new IllegalArgumentException("capability must be a stable lowercase segment");
        }
        if (businessKey == null || businessKey.isBlank() || businessKey.length() > 1024) {
            throw new IllegalArgumentException("businessKey must contain 1 to 1024 characters");
        }
        String encoded =
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(businessKey.getBytes(StandardCharsets.UTF_8));
        return keyPrefix + ":" + applicationName + ":" + capability + ":" + encoded;
    }

    /**
     * 校验业务请求的 TTL 为正数且不超过 Starter 上限。
     *
     * @param ttl 待校验时长
     * @return 原时长
     * @throws IllegalArgumentException 当时长为零、负数或超过上限时抛出
     */
    public Duration requireTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(maxTtl) > 0) {
            throw new IllegalArgumentException("ttl must be positive and within configured maximum");
        }
        return ttl;
    }
}
