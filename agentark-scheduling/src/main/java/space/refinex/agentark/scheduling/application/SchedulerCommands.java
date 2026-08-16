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

package space.refinex.agentark.scheduling.application;

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.scheduling.domain.SchedulerModels.IdempotencyCapability;
import space.refinex.agentark.scheduling.domain.SchedulerModels.JobType;
import space.refinex.agentark.scheduling.domain.SchedulerModels.RetryPolicy;

import java.time.Instant;
import java.util.Objects;

/**
 * 集中定义 Scheduler 应用层的语言中立命令。
 *
 * @author refinex
 */
public final class SchedulerCommands {

    /**
     * 禁止实例化命令容器。
     */
    private SchedulerCommands() {
    }

    /**
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param type           Job 类型
     * @param businessKey    类型内稳定幂等业务键
     * @param payload        不含 Secret 的规范 JSON
     * @param payloadHash    任务载荷 SHA-256
     * @param priority       优先级，范围 -1000 至 1000
     * @param availableAt    最早执行时间
     * @param retryPolicy    固定重试策略
     * @param idempotency    Handler 幂等能力
     * @author refinex
     */
    public record EnqueueJobCommand(
        OrganizationId organizationId,
        ProjectId projectId,
        JobType type,
        String businessKey,
        String payload,
        Checksum payloadHash,
        int priority,
        Instant availableAt,
        RetryPolicy retryPolicy,
        IdempotencyCapability idempotency) {

        /**
         * 校验接单命令必需字段，完整不变量由 Job 构造器再次验证。
         */
        public EnqueueJobCommand {
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(payloadHash, "payloadHash must not be null");
            Objects.requireNonNull(availableAt, "availableAt must not be null");
            Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
            Objects.requireNonNull(idempotency, "idempotency must not be null");
            if (businessKey == null || payload == null) {
                throw new IllegalArgumentException("businessKey and payload are required");
            }
        }
    }

    /**
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param jobId          待 Redrive Job 字符串标识
     * @param actor          已认证操作者稳定引用
     * @param reason         不含敏感数据的 Redrive 原因
     * @author refinex
     */
    public record RedriveJobCommand(
        OrganizationId organizationId,
        ProjectId projectId,
        String jobId,
        String actor,
        String reason) {

        /**
         * 校验 Redrive 的租户、操作者和审计原因。
         */
        public RedriveJobCommand {
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            if (jobId == null || jobId.isBlank() || actor == null || actor.isBlank()
                || reason == null || reason.isBlank() || reason.length() > 255) {
                throw new IllegalArgumentException("redrive command fields are invalid");
            }
        }
    }
}
