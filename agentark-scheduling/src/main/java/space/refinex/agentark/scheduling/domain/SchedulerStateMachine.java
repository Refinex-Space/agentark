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

package space.refinex.agentark.scheduling.domain;

import space.refinex.agentark.scheduling.domain.SchedulerModels.JobStatus;

import java.util.Map;
import java.util.Set;

/**
 * 集中校验 Durable Job 状态转换，禁止 Adapter 绕过领域规则。
 *
 * @author refinex
 */
public final class SchedulerStateMachine {

    /**
     * 允许的非 Redrive 状态转换集合。
     */
    private static final Map<JobStatus, Set<JobStatus>> TRANSITIONS = Map.of(
        JobStatus.READY, Set.of(JobStatus.CLAIMED, JobStatus.CANCELLED),
        JobStatus.RETRY_WAIT, Set.of(JobStatus.CLAIMED, JobStatus.CANCELLED),
        JobStatus.CLAIMED, Set.of(
            JobStatus.SUCCEEDED, JobStatus.RETRY_WAIT, JobStatus.DEAD_LETTERED,
            JobStatus.CANCELLED, JobStatus.TIMED_OUT),
        JobStatus.TIMED_OUT, Set.of(JobStatus.RETRY_WAIT, JobStatus.DEAD_LETTERED),
        JobStatus.DEAD_LETTERED, Set.of(JobStatus.READY),
        JobStatus.SUCCEEDED, Set.of(),
        JobStatus.CANCELLED, Set.of());

    /**
     * 创建无状态的 Scheduler 状态机。
     */
    public SchedulerStateMachine() {
    }

    /**
     * 校验 Job 状态转换；Dead Letter 到 READY 只允许由授权 Redrive 调用。
     *
     * @param current 当前状态
     * @param target  目标状态
     * @param redrive 是否为授权 Redrive
     * @throws IllegalStateException 当转换非法或绕过 Redrive 授权时抛出
     */
    public void requireTransition(JobStatus current, JobStatus target, boolean redrive) {
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)
            || (current == JobStatus.DEAD_LETTERED && !redrive)) {
            throw new IllegalStateException(
                "illegal scheduler job transition: " + current + " -> " + target);
        }
    }
}
