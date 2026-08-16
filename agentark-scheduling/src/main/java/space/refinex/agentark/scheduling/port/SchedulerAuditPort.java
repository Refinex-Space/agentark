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
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Instant;

/**
 * 定义 Scheduler 高风险管理操作的真实审计输出端口。
 *
 * @author refinex
 */
@FunctionalInterface
public interface SchedulerAuditPort {

    /**
     * 记录不可静默丢弃的管理操作审计事实。
     *
     * @param action         稳定动作名
     * @param actor          已认证主体
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param jobId          Job 标识
     * @param reason         操作原因
     * @param occurredAt     发生时间
     */
    void record(
        String action,
        String actor,
        OrganizationId organizationId,
        ProjectId projectId,
        JobId jobId,
        String reason,
        Instant occurredAt);
}
