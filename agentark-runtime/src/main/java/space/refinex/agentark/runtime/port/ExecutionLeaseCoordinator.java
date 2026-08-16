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

package space.refinex.agentark.runtime.port;

import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimeWorkItem;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 组合 Redis 互斥租约与 MySQL 权威 Fencing Token，并在执行期间负责续约。
 *
 * @author refinex
 */
public interface ExecutionLeaseCoordinator {

    /**
     * 激活已由 MySQL Claim 的 Work Item；Redis 竞争失败时不得开始外部调用。
     *
     * @param workItem 已 Claim Work Item
     * @param ttl      Lease 有效期
     * @return 当前执行租约；竞争失败时为空
     */
    Optional<ExecutionLease> activate(RuntimeWorkItem workItem, Duration ttl);

    /**
     * 表达一次外部执行期间可校验、可关闭的双层 Lease。
     *
     * @author refinex
     */
    interface ExecutionLease extends AutoCloseable {

        /**
         * 校验 Redis 续约状态与 MySQL Owner/Fencing 仍然有效。
         */
        void requireCurrent();

        /**
         * 判断后台续约是否仍保持有效。
         *
         * @return Lease 仍有效时为 true
         */
        boolean active();

        /**
         * 注册 Lease 丢失回调，使执行引擎停止后续外部调用。
         *
         * @param action Lease 丢失后执行的幂等动作
         */
        default void onLost(Runnable action) {
            Objects.requireNonNull(action, "action must not be null");
        }

        /**
         * 停止续约并以 Owner/Token 条件释放 Redis Lease。
         */
        @Override
        void close();
    }
}
