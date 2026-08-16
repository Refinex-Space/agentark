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

import space.refinex.agentark.kernel.id.RunId;
import space.refinex.agentark.runtime.domain.RuntimeModels.FencingToken;

import java.time.Duration;
import java.time.Instant;

/**
 * 定义 MySQL 权威 Lease 与 Fencing 校验端口；Redis 只能做加速或通知。
 *
 * @author refinex
 */
public interface LeaseManager {

    /**
     * 续约当前 Owner 的 Lease，不改变 Fencing Token。
     *
     * @param runId        Run 标识
     * @param owner        Runtime Instance Key
     * @param fencingToken 当前令牌
     * @param now          当前时刻
     * @param ttl          新 Lease 有效期
     * @return 续约成功时返回 true
     */
    boolean renew(
        RunId runId, String owner, FencingToken fencingToken, Instant now, Duration ttl);

    /**
     * 校验写入者仍持有未过期的当前令牌。
     *
     * @param runId        Run 标识
     * @param owner        Runtime Instance Key
     * @param fencingToken 待校验令牌
     * @param now          当前时刻
     */
    void requireCurrent(RunId runId, String owner, FencingToken fencingToken, Instant now);
}
