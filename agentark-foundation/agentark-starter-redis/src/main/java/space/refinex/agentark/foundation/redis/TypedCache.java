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
import java.util.Optional;

/**
 * 定义具有固定命名空间、显式 Codec 和有限 TTL 的类型化缓存，不能作为持久事实源。
 *
 * @param <K> 业务键类型
 * @param <V> 缓存值类型
 * @author refinex
 */
public interface TypedCache<K, V> {

    /**
     * 读取缓存值。
     *
     * @param key 非空业务键
     * @return 未命中时为空
     */
    Optional<V> get(K key);

    /**
     * 使用当前缓存的默认 TTL 写入值。
     *
     * @param key   非空业务键
     * @param value 非空缓存值
     */
    void put(K key, V value);

    /**
     * 使用显式有限 TTL 写入值。
     *
     * @param key   非空业务键
     * @param value 非空缓存值
     * @param ttl   正数且有上限的存活时间
     */
    void put(K key, V value, Duration ttl);

    /**
     * 删除单个缓存键；该操作不代表删除任何持久事实。
     *
     * @param key 非空业务键
     * @return 键原先存在时为 {@code true}
     */
    boolean evict(K key);
}
