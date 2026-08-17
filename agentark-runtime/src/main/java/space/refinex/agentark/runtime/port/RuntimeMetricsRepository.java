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

import java.time.Instant;

/**
 * 定义 Runtime 低基数 Gauge 所需的聚合查询，不返回 Session、Run 或 Project 标签。
 *
 * @author refinex
 */
public interface RuntimeMetricsRepository {

    /** @return 活跃 Session 数量。 */
    long activeSessions();

    /** @return RUNNING/PAUSED/CLAIMED Run 数量。 */
    long activeRuns();

    /** @return PENDING Approval 数量。 */
    long pendingApprovals();

    /**
     * 返回最老 PENDING Runtime Outbox 年龄。
     *
     * @param now 当前时间
     * @return 无积压时为 0 秒
     */
    long outboxLagSeconds(Instant now);
}
