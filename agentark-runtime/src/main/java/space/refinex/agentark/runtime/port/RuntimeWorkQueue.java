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
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimeWorkItem;
import space.refinex.agentark.runtime.domain.RuntimeModels.WorkItemStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 定义持久 Work Queue 的入队、SKIP LOCKED Claim、续约和完成边界。
 *
 * @author refinex
 */
public interface RuntimeWorkQueue {

    /**
     * 持久化与 Run 一对一的 Work Item。
     *
     * @param item 新 Work Item
     */
    void enqueue(RuntimeWorkItem item);

    /**
     * Claim 一项到期 READY 或过期 CLAIMED 任务并递增 Fencing Token。
     *
     * @param instanceKey Owner Runtime Instance Key
     * @param now         当前时刻
     * @param ttl         Lease 有效期
     * @return Claim 后的 Work Item；无任务时为空
     */
    Optional<RuntimeWorkItem> claimNext(String instanceKey, Instant now, Duration ttl);

    /**
     * 使用当前令牌将 Work Item 写入终态。
     *
     * @param runId        Run 标识
     * @param fencingToken 当前令牌
     * @param terminal     COMPLETED、FAILED 或 CANCELLED
     */
    void complete(RunId runId, FencingToken fencingToken, WorkItemStatus terminal);

    /**
     * 在同一暂停点全部 Approval 已决后，将已完成 Work Item 重新置为 READY。
     *
     * @param runId        PAUSED Run 标识
     * @param fencingToken 暂停时有效的旧令牌
     * @param availableAt  最早可重新 Claim 的时刻
     */
    void requeueForResume(RunId runId, FencingToken fencingToken, Instant availableAt);
}
