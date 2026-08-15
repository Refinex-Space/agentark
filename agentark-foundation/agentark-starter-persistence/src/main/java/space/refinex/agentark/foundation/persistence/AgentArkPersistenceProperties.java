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

package space.refinex.agentark.foundation.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 定义 MyBatis-Plus 分页和乐观锁基础开关；连接池与 Flyway 使用 Spring Boot 标准属性。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.foundation.persistence")
public class AgentArkPersistenceProperties {

    /**
     * 是否启用 AgentArk 持久化增强；仍需存在 DataSource 才会装配。
     */
    private boolean enabled = true;

    /**
     * 单次 MyBatis-Plus 分页查询允许返回的最大条目数。
     */
    private long maxPageSize = 500;

    /**
     * 返回持久化增强是否启用。
     *
     * @return 启用时为 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置持久化增强启用状态。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回最大分页条目数。
     *
     * @return 1 到 10000 之间的条目数
     */
    public long getMaxPageSize() {
        return maxPageSize;
    }

    /**
     * 设置最大分页条目数。
     *
     * @param maxPageSize 1 到 10000 之间的条目数
     * @throws IllegalArgumentException 当数值越界时抛出
     */
    public void setMaxPageSize(long maxPageSize) {
        if (maxPageSize < 1 || maxPageSize > 10_000) {
            throw new IllegalArgumentException("maxPageSize must be between 1 and 10000");
        }
        this.maxPageSize = maxPageSize;
    }
}
