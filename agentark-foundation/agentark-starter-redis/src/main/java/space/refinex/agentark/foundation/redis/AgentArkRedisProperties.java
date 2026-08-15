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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 定义 Redis Key 前缀、应用隔离和最大 TTL；连接、TLS 与超时使用 Spring Boot 标准属性。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.foundation.redis")
public class AgentArkRedisProperties {

    /**
     * 是否启用 Redis 语义组件；默认关闭以避免未配置实例产生 Key 冲突。
     */
    private boolean enabled;

    /**
     * AgentArk 平台级 Key 前缀。
     */
    private String keyPrefix = "agentark";

    /**
     * 当前服务或应用的稳定小写名称。
     */
    private String applicationName;

    /**
     * Starter 允许业务请求的最大 TTL。
     */
    private Duration maxTtl = Duration.ofDays(7);

    /**
     * 返回 Redis 语义组件是否启用。
     *
     * @return 启用时为 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 Redis 语义组件启用状态。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回平台级 Key 前缀。
     *
     * @return 稳定小写前缀
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * 设置平台级 Key 前缀。
     *
     * @param keyPrefix 稳定小写前缀
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = requireSegment(keyPrefix, "keyPrefix");
    }

    /**
     * 返回当前应用稳定名称。
     *
     * @return 可为空的应用名称
     */
    public String getApplicationName() {
        return applicationName;
    }

    /**
     * 设置当前应用稳定名称。
     *
     * @param applicationName 稳定小写名称
     */
    public void setApplicationName(String applicationName) {
        this.applicationName = requireSegment(applicationName, "applicationName");
    }

    /**
     * 返回允许的最大 TTL。
     *
     * @return 正数有限时长
     */
    public Duration getMaxTtl() {
        return maxTtl;
    }

    /**
     * 设置允许的最大 TTL。
     *
     * @param maxTtl 正数且不超过 30 天的时长
     * @throws IllegalArgumentException 当时长不合法时抛出
     */
    public void setMaxTtl(Duration maxTtl) {
        if (maxTtl == null
            || maxTtl.isZero()
            || maxTtl.isNegative()
            || maxTtl.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("maxTtl must be positive and at most 30 days");
        }
        this.maxTtl = maxTtl;
    }

    /**
     * 校验 Redis Key 的结构化前缀段。
     *
     * @param value 待校验值
     * @param name  配置字段名
     * @return 原值
     * @throws IllegalArgumentException 当格式不合法时抛出
     */
    private String requireSegment(String value, String name) {
        if (value == null || !value.matches("[a-z][a-z0-9-]{0,62}")) {
            throw new IllegalArgumentException(name + " must be a stable lowercase segment");
        }
        return value;
    }
}
