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

package space.refinex.agentark.kernel.snapshot;

import java.time.Duration;
import java.util.Objects;

/**
 * 表示固化到 Agent 修订版本 Snapshot 的 Runtime 硬限制。
 *
 * @param turnTimeout  单个 Turn 的整秒超时时间
 * @param maxToolCalls 单个 Turn 允许的最大 Tool 调用次数
 * @param maxSubAgents 单个 Turn 允许创建的最大 Sub-Agent 数
 * @author refinex
 */
public record RuntimeLimits(Duration turnTimeout, int maxToolCalls, int maxSubAgents) {

    /**
     * 单个 Turn 允许配置的最大执行时长。
     */
    private static final Duration MAX_TURN_TIMEOUT = Duration.ofHours(24);

    /**
     * 校验并创建 Runtime 限制。
     *
     * @param turnTimeout  大于零、不超过 24 小时且没有纳秒余数的时长
     * @param maxToolCalls 范围为 0 到 100000
     * @param maxSubAgents 范围为 0 到 1000
     * @throws NullPointerException     当超时时间为 {@code null} 时抛出
     * @throws IllegalArgumentException 当任一限制超出平台边界时抛出
     */
    public RuntimeLimits {
        Objects.requireNonNull(turnTimeout, "RuntimeLimits turnTimeout must not be null");
        if (turnTimeout.isZero()
            || turnTimeout.isNegative()
            || turnTimeout.compareTo(MAX_TURN_TIMEOUT) > 0
            || turnTimeout.getNano() != 0) {
            throw new IllegalArgumentException(
                "RuntimeLimits turnTimeout must be a whole number of seconds up to 24 hours");
        }
        if (maxToolCalls < 0 || maxToolCalls > 100_000) {
            throw new IllegalArgumentException("RuntimeLimits maxToolCalls must be between 0 and 100000");
        }
        if (maxSubAgents < 0 || maxSubAgents > 1_000) {
            throw new IllegalArgumentException("RuntimeLimits maxSubAgents must be between 0 and 1000");
        }
    }
}
