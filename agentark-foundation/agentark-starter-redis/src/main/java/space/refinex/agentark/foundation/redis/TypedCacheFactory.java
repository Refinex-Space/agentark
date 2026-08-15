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

import java.time.Duration;

/**
 * 为每种缓存值显式创建独立命名空间、Codec 和 TTL 的 TypedCache。
 *
 * @author refinex
 */
public interface TypedCacheFactory {

    /**
     * 创建类型化缓存。
     *
     * @param namespace  业务拥有者定义的稳定小写命名空间
     * @param codec      显式值 Codec
     * @param defaultTtl 正数且有上限的默认 TTL
     * @param <K>        业务键类型
     * @param <V>        缓存值类型
     * @return 独立类型化缓存
     */
    <K, V> TypedCache<K, V> create(String namespace, RedisValueCodec<V> codec, Duration defaultTtl);
}
