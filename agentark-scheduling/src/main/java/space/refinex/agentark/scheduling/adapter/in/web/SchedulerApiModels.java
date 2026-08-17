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
import java.util.Map;

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

    /**
     * @param organizationId 所属组织 UUIDv7
     * @param projectId      所属项目 UUIDv7
     * @param key            项目内稳定 Trigger Key
     * @param type           CRON 或 WEBHOOK
     * @param cronExpression CRON 六段表达式
     * @param zoneId         CRON IANA 时区
     * @param config         不含敏感值的目标 Job 配置
     * @param secretRef      WEBHOOK 验签 SecretRef
     * @param targetContract 目标 Payload Contract
     * @param targetJobType  目标 Job 类型
     * @author refinex
     */
    public record CreateTriggerRequest(
        @NotBlank String organizationId,
        @NotBlank String projectId,
        @NotBlank @Size(max = 255) String key,
        @NotBlank String type,
        @Size(max = 255) String cronExpression,
        @Size(max = 64) String zoneId,
        @jakarta.validation.constraints.NotNull @Size(max = 32) Map<String, String> config,
        @Size(max = 1024) String secretRef,
        @NotBlank @Size(max = 255) String targetContract,
        @NotBlank String targetJobType) {

        /**
         * 防御性复制 Trigger 配置，敏感键和值由应用服务继续校验。
         */
        public CreateTriggerRequest {
            config = config == null ? null : Map.copyOf(config);
        }
    }

    /**
     * @param id             持久 Trigger UUIDv7 标识
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param key            稳定 Key
     * @param type           CRON 或 WEBHOOK
     * @param cronExpression Cron 表达式
     * @param zoneId         IANA 时区
     * @param config         不含敏感值的配置
     * @param secretRef      可选 SecretRef 元数据
     * @param targetContract 目标 Contract
     * @param targetJobType  目标 Job 类型
     * @param status         ENABLED、DISABLED 或 ARCHIVED
     * @param version        乐观锁版本
     * @param createdAt      创建时间
     * @param updatedAt      更新时间
     * @author refinex
     */
    public record TriggerResponse(
        String id,
        String organizationId,
        String projectId,
        String key,
        String type,
        String cronExpression,
        String zoneId,
        Map<String, String> config,
        String secretRef,
        String targetContract,
        String targetJobType,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

        /**
         * 防御性复制非敏感 Trigger 配置。
         */
        public TriggerResponse {
            config = Map.copyOf(config);
        }
    }

    /**
     * @param items      当前页 Job
     * @param nextCursor 下一页 UUIDv7 游标；末页为空
     * @author refinex
     */
    public record JobPageResponse(List<JobResponse> items, String nextCursor) {

        /**
         * 防御性复制 Job 列表。
         */
        public JobPageResponse {
            items = List.copyOf(items);
        }
    }

    /**
     * @param items      当前页 Trigger
     * @param nextCursor 下一页 UUIDv7 游标；末页为空
     * @author refinex
     */
    public record TriggerPageResponse(List<TriggerResponse> items, String nextCursor) {

        /**
         * 防御性复制 Trigger 列表。
         */
        public TriggerPageResponse {
            items = List.copyOf(items);
        }
    }
}
