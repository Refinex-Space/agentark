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

package space.refinex.agentark.scheduling.port;

import space.refinex.agentark.scheduling.domain.SchedulerModels.ClaimedJob;
import space.refinex.agentark.scheduling.domain.SchedulerModels.IdempotencyCapability;
import space.refinex.agentark.scheduling.domain.SchedulerModels.JobType;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 定义按 Job Type 隔离的中立 Handler；实现不得自行修改 Scheduler 状态。
 *
 * @author refinex
 */
public interface JobHandler {

    /**
     * 返回当前 Handler 唯一处理的 Job 类型。
     *
     * @return Job 类型
     */
    JobType type();

    /**
     * 声明外部副作用幂等能力，Worker 据此决定是否允许自动重试。
     *
     * @return 幂等能力
     */
    IdempotencyCapability idempotencyCapability();

    /**
     * 异步执行当前 Claim；实现必须传播失败，不得吞异常。
     *
     * @param claim 当前 Claim
     * @return Handler 结果
     */
    CompletionStage<HandlerResult> handle(ClaimedJob claim);

    /**
     * @param successful 是否成功
     * @param retryable  失败是否属于可重试分类
     * @param errorCode  失败稳定码，成功时为空
     * @param resultRef  可选结果引用，不含签名 URL
     * @author refinex
     */
    record HandlerResult(
        boolean successful,
        boolean retryable,
        Optional<String> errorCode,
        Optional<String> resultRef) {

        /**
         * 校验 Handler 成功与失败字段形态。
         */
        public HandlerResult {
            errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
            resultRef = Objects.requireNonNull(resultRef, "resultRef must not be null");
            if ((successful && (retryable || errorCode.isPresent()))
                || (!successful && errorCode.isEmpty())) {
                throw new IllegalArgumentException("handler result does not match outcome");
            }
        }

        /**
         * 创建成功结果。
         *
         * @param resultRef 可选结果引用
         * @return 成功结果
         */
        public static HandlerResult success(Optional<String> resultRef) {
            return new HandlerResult(true, false, Optional.empty(), resultRef);
        }

        /**
         * 创建失败结果。
         *
         * @param errorCode 稳定错误码
         * @param retryable 是否属于可重试错误
         * @return 失败结果
         */
        public static HandlerResult failure(String errorCode, boolean retryable) {
            return new HandlerResult(false, retryable,
                Optional.of(Objects.requireNonNull(errorCode, "errorCode must not be null")),
                Optional.empty());
        }
    }
}
