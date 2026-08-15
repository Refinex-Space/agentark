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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 在显式启用且存在 StringRedisTemplate 时装配类型化缓存与原子协调语义。
 *
 * @author refinex
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentArkRedisProperties.class)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "agentark.foundation.redis", name = "enabled", havingValue = "true")
public class AgentArkRedisAutoConfiguration {

    /**
     * 创建 Redis 语义自动配置。
     */
    public AgentArkRedisAutoConfiguration() {
        // Spring Boot 通过公开构造器创建自动配置实例。
    }

    /**
     * 创建统一 Key 和 TTL 约束。
     *
     * @param properties Redis 配置属性
     * @return Key 命名空间
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisKeyNamespace redisKeyNamespace(AgentArkRedisProperties properties) {
        return new RedisKeyNamespace(properties);
    }

    /**
     * 创建类型化缓存工厂。
     *
     * @param redisTemplate Redis 字符串模板
     * @param keyNamespace  Key 命名空间
     * @return 类型化缓存工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public TypedCacheFactory typedCacheFactory(
        StringRedisTemplate redisTemplate, RedisKeyNamespace keyNamespace) {
        return new RedisTypedCacheFactory(redisTemplate, keyNamespace);
    }

    /**
     * 创建同时实现 Lease、Fencing、Idempotency 和 Rate Limit 的原子协调组件。
     *
     * @param redisTemplate Redis 字符串模板
     * @param keyNamespace  Key 命名空间
     * @return Redis 协调组件
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisCoordinationStore redisCoordinationStore(
        StringRedisTemplate redisTemplate, RedisKeyNamespace keyNamespace) {
        return new RedisCoordinationStore(redisTemplate, keyNamespace);
    }
}
