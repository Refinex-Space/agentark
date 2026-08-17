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

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Duration;
import java.util.Optional;

/**
 * 定义 Runtime 接单前的 Control Quota Reservation 端口，不暴露 Control 领域类型。
 *
 * @author refinex
 */
public interface RuntimeQuotaPort {

    /**
     * 返回显式禁用治理时的允许实现。
     *
     * @return 不创建 Reservation 的端口
     */
    static RuntimeQuotaPort noop() {
        return new RuntimeQuotaPort() {
            /** 无治理组合根时允许接单。 */
            @Override
            public Reservation reserveConcurrentRun(
                OrganizationId organizationId,
                ProjectId projectId,
                String idempotencyKey,
                String subjectRef,
                Duration ttl) {
                return Reservation.allow();
            }

            /** 无 Reservation 时无需释放。 */
            @Override
            public void release(String reservationId) {
                // 显式禁用治理时没有远端 Reservation。
            }
        };
    }

    /**
     * 幂等申请一个并发 Run 名额。
     *
     * @param organizationId 组织
     * @param projectId      项目
     * @param idempotencyKey Turn 接单幂等键
     * @param subjectRef     Session 或待接收工作引用
     * @param ttl            Reservation TTL
     * @return 无匹配 Policy 或成功预留；拒绝时 {@code allowed=false}
     */
    Reservation reserveConcurrentRun(
        OrganizationId organizationId,
        ProjectId projectId,
        String idempotencyKey,
        String subjectRef,
        Duration ttl);

    /**
     * 本地接单事务失败时释放已经创建的 Reservation。
     *
     * @param reservationId Reservation UUIDv7 字符串
     */
    void release(String reservationId);

    /**
     * Quota Reservation 的最小中立结果。
     *
     * @param allowed       是否允许接单
     * @param reservationId 可选 Reservation UUIDv7
     * @param action        可选运行中预算动作
     * @author refinex
     */
    record Reservation(boolean allowed, Optional<String> reservationId, Optional<String> action) {

        /** 校验 Optional 容器和拒绝结果不携带 Reservation。 */
        public Reservation {
            reservationId = java.util.Objects.requireNonNull(
                reservationId, "reservationId must not be null");
            action = java.util.Objects.requireNonNull(action, "action must not be null");
            if (!allowed && reservationId.isPresent()) {
                throw new IllegalArgumentException("denied quota must not reserve capacity");
            }
        }

        /**
         * 返回没有活动 Policy 的允许结果。
         *
         * @return 无 Reservation 的允许结果
         */
        public static Reservation allow() {
            return new Reservation(true, Optional.empty(), Optional.empty());
        }
    }
}
