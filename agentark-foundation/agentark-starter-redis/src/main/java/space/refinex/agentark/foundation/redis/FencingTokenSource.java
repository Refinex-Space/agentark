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
 * 为指定业务资源生成单调递增栅栏令牌，允许出现间隙但禁止回退或复用。
 *
 * @author refinex
 */
public interface FencingTokenSource {

    /**
     * 获取下一个栅栏令牌。
     *
     * @param namespace   业务命名空间
     * @param resourceKey 业务资源键
     * @return 正数且单调递增的令牌
     */
    long next(String namespace, String resourceKey);
}
