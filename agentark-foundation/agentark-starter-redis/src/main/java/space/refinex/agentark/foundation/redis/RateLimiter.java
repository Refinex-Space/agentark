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
 * 定义按租户、主体或资源语义键执行的原子限流能力。
 *
 * @author refinex
 */
public interface RateLimiter {

    /**
     * 在固定窗口内消耗一个配额。
     *
     * @param namespace  业务命名空间
     * @param subjectKey 租户、主体或资源稳定键
     * @param limit      窗口内正数上限
     * @param window     正数有限窗口
     * @return 原子限流结果
     */
    RateLimitDecision acquire(String namespace, String subjectKey, long limit, Duration window);
}
