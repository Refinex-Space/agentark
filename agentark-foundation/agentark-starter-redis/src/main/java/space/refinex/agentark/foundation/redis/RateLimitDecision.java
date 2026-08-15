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
 * 表示固定窗口限流结果和客户端可安全使用的剩余额度信息。
 *
 * @param allowed    当前请求是否允许
 * @param remaining  当前窗口剩余额度，不小于零
 * @param retryAfter 被拒绝时建议等待时间；允许时为零
 * @author refinex
 */
public record RateLimitDecision(boolean allowed, long remaining, Duration retryAfter) {

    /**
     * 校验并创建限流结果。
     *
     * @param allowed    是否允许
     * @param remaining  剩余额度
     * @param retryAfter 建议等待时间
     * @throws IllegalArgumentException 当额度或等待时间为负数时抛出
     * @throws NullPointerException     当等待时间为 {@code null} 时抛出
     */
    public RateLimitDecision {
        if (remaining < 0) {
            throw new IllegalArgumentException("remaining must not be negative");
        }
        java.util.Objects.requireNonNull(retryAfter, "retryAfter must not be null");
        if (retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must not be negative");
        }
    }
}
