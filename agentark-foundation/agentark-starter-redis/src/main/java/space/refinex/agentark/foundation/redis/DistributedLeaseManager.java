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
import java.util.Optional;

/**
 * 定义具备 Owner 校验、续约、释放和 Fencing 的分布式租约语义。
 *
 * @author refinex
 */
public interface DistributedLeaseManager {

    /**
     * 尝试获取资源租约；竞争失败不会复用旧栅栏令牌。
     *
     * @param namespace   业务命名空间
     * @param resourceKey 业务资源键
     * @param ownerId     当前实例稳定标识
     * @param ttl         正数有限租期
     * @return 获取成功的新租约；竞争失败时为空
     */
    Optional<Lease> tryAcquire(String namespace, String resourceKey, String ownerId, Duration ttl);

    /**
     * 仅在 Owner 和 Fencing 均匹配时续约。
     *
     * @param lease 当前租约
     * @param ttl   新的正数有限租期
     * @return 续约成功时为 {@code true}
     */
    boolean renew(Lease lease, Duration ttl);

    /**
     * 仅在 Owner 和 Fencing 均匹配时释放租约。
     *
     * @param lease 当前租约
     * @return 释放成功时为 {@code true}
     */
    boolean release(Lease lease);
}
