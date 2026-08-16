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

package space.refinex.agentark.scheduling.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 集中定义 Scheduler 管理与 Webhook API 的语言中立 DTO。
 *
 * @author refinex
 */
public final class SchedulerApiModels {

    /**
     * 禁止实例化 API DTO 容器。
     */
    private SchedulerApiModels() {
    }

    /**
     * @param reason 取消或 Redrive 的人工原因
     * @author refinex
     */
    public record ActionRequest(
        @NotBlank @Size(max = 255) String reason) {

        /**
         * 创建并由 Bean Validation 校验管理动作请求。
         */
        public ActionRequest {
            // Bean Validation 在 Controller 边界校验空白和长度。
        }
    }

    /**
     * @param id             调度任务 UUIDv7
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param type           Job 类型
     * @param businessKey    幂等业务键
     * @param status         Job 状态
     * @param priority       优先级
     * @param availableAt    最早执行时间
     * @param currentAttempt 当前 Attempt 数
     * @param updatedAt      最近更新时间
     * @author refinex
     */
    public record JobResponse(
        String id,
        String organizationId,
        String projectId,
        String type,
        String businessKey,
        String status,
        int priority,
        Instant availableAt,
        int currentAttempt,
        Instant updatedAt) {
    }

    /**
     * @param id             死信记录 UUIDv7
     * @param jobId          所属调度任务 UUIDv7
     * @param finalAttemptId 最终 Attempt UUIDv7
     * @param reason         稳定失败原因
     * @param redriveCount   已 Redrive 次数
     * @param status         Dead Letter 状态
     * @param createdAt      创建时间
     * @author refinex
     */
    public record DeadLetterResponse(
        String id,
        String jobId,
        String finalAttemptId,
        String reason,
        int redriveCount,
        String status,
        Instant createdAt) {
    }

    /**
     * @param items OPEN Dead Letter 列表
     * @author refinex
     */
    public record DeadLetterListResponse(List<DeadLetterResponse> items) {

        /**
         * 防御性复制响应列表。
         */
        public DeadLetterListResponse {
            items = List.copyOf(items);
        }
    }

    /**
     * @param jobId  新建 Durable Job UUIDv7
     * @param status Job 初始状态
     * @author refinex
     */
    public record WebhookAcceptedResponse(String jobId, String status) {
    }
}
