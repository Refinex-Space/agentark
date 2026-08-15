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

/**
 * 定义 TypedCache 值的显式字符串编码契约，禁止启用 Java 原生或任意多态反序列化。
 *
 * @param <V> 缓存值类型
 * @author refinex
 */
public interface RedisValueCodec<V> {

    /**
     * 将非空值编码为不包含明文 Secret 的稳定字符串。
     *
     * @param value 待编码值
     * @return 可写入 Redis 的字符串
     */
    String encode(V value);

    /**
     * 将缓存字符串严格解码为目标类型。
     *
     * @param encoded Redis 字符串
     * @return 解码后的非空值
     * @throws IllegalArgumentException 当输入格式不合法时抛出
     */
    V decode(String encoded);
}
