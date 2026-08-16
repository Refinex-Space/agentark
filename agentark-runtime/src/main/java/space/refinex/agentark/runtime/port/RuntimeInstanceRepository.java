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

import space.refinex.agentark.runtime.domain.RuntimeModels.DrainStatus;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimeInstance;

import java.time.Instant;

/**
 * 持久化 Runtime Instance 心跳与排空状态，不拥有 Run 执行事实。
 *
 * @author refinex
 */
public interface RuntimeInstanceRepository {

    /**
     * 注册新实例或刷新同 Key 实例的启动元数据。
     *
     * @param instance Runtime Instance
     */
    void register(RuntimeInstance instance);

    /**
     * 刷新实例心跳并保持当前排空状态。
     *
     * @param instanceKey 实例稳定 Key
     * @param heartbeatAt 当前 UTC 时刻
     * @return 实例存在时为 true
     */
    boolean heartbeat(String instanceKey, Instant heartbeatAt);

    /**
     * 更新实例排空状态，Pod 关闭时禁止继续 Claim。
     *
     * @param instanceKey 实例稳定 Key
     * @param status      目标排空状态
     * @param occurredAt  状态变化时刻
     * @return 实例存在时为 true
     */
    boolean updateDrainStatus(String instanceKey, DrainStatus status, Instant occurredAt);
}
