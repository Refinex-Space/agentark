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

import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.scheduling.domain.SchedulerModels.Delivery;
import space.refinex.agentark.scheduling.domain.SchedulerModels.DeliveryStatus;

import java.time.Instant;
import java.util.Optional;

/**
 * 定义 Outbound Webhook 与 Channel Delivery 的幂等持久化和 Fencing 更新端口。
 *
 * @author refinex
 */
public interface DeliveryRepository {

    /**
     * 按 Provider 幂等键读取既有 Delivery。
     *
     * @param providerKey Provider 幂等键
     * @return Delivery
     */
    Optional<Delivery> findByProviderKey(String providerKey);

    /**
     * 插入 PENDING Delivery。
     *
     * @param delivery Delivery
     */
    void insert(Delivery delivery);

    /**
     * 以 Job 当前 Fencing Token 转换 Delivery 状态。
     *
     * @param deliveryId        Delivery UUIDv7 字符串
     * @param jobId             所属 Job
     * @param fencingToken      当前 Fencing Token
     * @param current           当前状态
     * @param target            目标状态
     * @param providerMessageId Provider 消息标识
     * @param responseSummary   脱敏响应摘要
     * @param updatedAt         更新时间
     */
    void transition(
        String deliveryId,
        JobId jobId,
        long fencingToken,
        DeliveryStatus current,
        DeliveryStatus target,
        Optional<String> providerMessageId,
        Optional<String> responseSummary,
        Instant updatedAt);
}
