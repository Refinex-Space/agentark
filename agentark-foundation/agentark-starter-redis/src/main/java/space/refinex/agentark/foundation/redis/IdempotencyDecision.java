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

import java.util.Optional;

/**
 * 表示幂等键首次占用、相同请求重放或不同请求冲突的判断结果。
 *
 * @param status          幂等判断状态
 * @param resultReference 已有结果的非秘密稳定引用；首次占用时为空
 * @author refinex
 */
public record IdempotencyDecision(Status status, Optional<String> resultReference) {

    /**
     * 校验幂等判断结果。
     *
     * @param status          状态
     * @param resultReference 可选结果引用
     * @throws NullPointerException 当参数为 {@code null} 时抛出
     */
    public IdempotencyDecision {
        java.util.Objects.requireNonNull(status, "status must not be null");
        resultReference =
            java.util.Objects.requireNonNull(resultReference, "resultReference must not be null");
    }

    /**
     * 定义幂等键处理状态。
     *
     * @author refinex
     */
    public enum Status {

        /**
         * 当前请求首次成功占用幂等键。
         */
        NEW,

        /**
         * 请求 Hash 相同，应复用已有结果。
         */
        REPLAY,

        /**
         * 请求 Hash 不同，必须返回冲突而不能覆盖。
         */
        CONFLICT
    }
}
