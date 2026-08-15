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

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * 使用 StringRedisTemplate 创建显式 Codec 的类型化缓存，Key 始终经过受控命名空间编码。
 *
 * @author refinex
 */
public final class RedisTypedCacheFactory implements TypedCacheFactory {

    /**
     * Redis 字符串操作模板。
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 统一 Key 和 TTL 约束。
     */
    private final RedisKeyNamespace keyNamespace;

    /**
     * 创建 Redis 类型化缓存工厂。
     *
     * @param redisTemplate Redis 字符串模板
     * @param keyNamespace  Key 与 TTL 约束
     */
    public RedisTypedCacheFactory(StringRedisTemplate redisTemplate, RedisKeyNamespace keyNamespace) {
        this.redisTemplate =
            java.util.Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.keyNamespace =
            java.util.Objects.requireNonNull(keyNamespace, "keyNamespace must not be null");
    }

    /**
     * 创建独立命名空间与 Codec 的类型化缓存。
     *
     * @param namespace  业务命名空间
     * @param codec      值 Codec
     * @param defaultTtl 默认 TTL
     * @param <K>        业务键类型
     * @param <V>        缓存值类型
     * @return Redis 类型化缓存
     */
    @Override
    public <K, V> TypedCache<K, V> create(
        String namespace, RedisValueCodec<V> codec, Duration defaultTtl) {
        keyNamespace.key(namespace, "validation");
        keyNamespace.requireTtl(defaultTtl);
        return new RedisTypedCache<>(namespace, codec, defaultTtl);
    }

    /**
     * 单个命名空间和 Codec 绑定后的 Redis 缓存实现。
     *
     * @param <K> 业务键类型
     * @param <V> 缓存值类型
     * @author refinex
     */
    private final class RedisTypedCache<K, V> implements TypedCache<K, V> {

        /**
         * 当前缓存业务命名空间。
         */
        private final String namespace;

        /**
         * 当前缓存值 Codec。
         */
        private final RedisValueCodec<V> codec;

        /**
         * 当前缓存默认 TTL。
         */
        private final Duration defaultTtl;

        /**
         * 创建单个 Redis 类型化缓存。
         *
         * @param namespace  业务命名空间
         * @param codec      值 Codec
         * @param defaultTtl 默认 TTL
         */
        private RedisTypedCache(String namespace, RedisValueCodec<V> codec, Duration defaultTtl) {
            this.namespace = namespace;
            this.codec = java.util.Objects.requireNonNull(codec, "codec must not be null");
            this.defaultTtl = defaultTtl;
        }

        /**
         * 读取并严格解码缓存值。
         *
         * @param key 非空业务键
         * @return 未命中时为空
         */
        @Override
        public Optional<V> get(K key) {
            String value = redisTemplate.opsForValue().get(redisKey(key));
            return Optional.ofNullable(value).map(codec::decode);
        }

        /**
         * 使用默认 TTL 写入缓存值。
         *
         * @param key   非空业务键
         * @param value 非空缓存值
         */
        @Override
        public void put(K key, V value) {
            put(key, value, defaultTtl);
        }

        /**
         * 使用显式有限 TTL 写入缓存值。
         *
         * @param key   非空业务键
         * @param value 非空缓存值
         * @param ttl   有限 TTL
         */
        @Override
        public void put(K key, V value, Duration ttl) {
            redisTemplate
                .opsForValue()
                .set(
                    redisKey(key),
                    codec.encode(java.util.Objects.requireNonNull(value, "value must not be null")),
                    keyNamespace.requireTtl(ttl));
        }

        /**
         * 删除缓存值而不影响持久事实。
         *
         * @param key 非空业务键
         * @return 原键存在时为 {@code true}
         */
        @Override
        public boolean evict(K key) {
            return Boolean.TRUE.equals(redisTemplate.delete(redisKey(key)));
        }

        /**
         * 将类型化业务键转换为受控 Redis Key。
         *
         * @param key 非空业务键
         * @return 完整 Redis Key
         */
        private String redisKey(K key) {
            return keyNamespace.key(
                namespace, java.util.Objects.requireNonNull(key, "key must not be null").toString());
        }
    }
}
