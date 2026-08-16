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

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.scheduling.application.SchedulerApplicationService;
import space.refinex.agentark.scheduling.application.SchedulerCommands.EnqueueJobCommand;
import space.refinex.agentark.scheduling.application.TriggerDefinitionService;
import space.refinex.agentark.scheduling.application.TriggerDefinitionService.CreateTriggerCommand;
import space.refinex.agentark.scheduling.domain.SchedulerException;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 提供只允许 Audience 受限服务身份调用的 Scheduler v1 持久接单契约。
 *
 * @author refinex
 */
@RestController
public final class SchedulerInternalController {

    /**
     * Scheduler Internal API 要求的服务 Audience。
     */
    private static final String REQUIRED_AUDIENCE = "agentark-scheduler";

    /**
     * Scheduler 应用服务。
     */
    private final SchedulerApplicationService service;

    /**
     * 持久 Trigger 定义服务。
     */
    private final TriggerDefinitionService triggerService;

    /**
     * 创建 Scheduler Internal Controller。
     *
     * @param service        Scheduler 应用服务
     * @param triggerService Trigger 定义服务
     */
    public SchedulerInternalController(
        SchedulerApplicationService service,
        TriggerDefinitionService triggerService) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.triggerService = Objects.requireNonNull(
            triggerService, "triggerService must not be null");
    }

    /**
     * 幂等登记 Cron 或 Webhook Trigger；Cron 同事务创建首个 Cursor。
     *
     * @param authentication 已认证服务主体
     * @param request        Trigger 定义命令
     * @return 201 Created 或语义相同定义
     */
    @PostMapping("/internal/v1/scheduler/triggers")
    public ResponseEntity<InternalTriggerResponse> createTrigger(
        Authentication authentication,
        @Valid @RequestBody InternalTriggerRequest request) {
        requireService(authentication);
        TriggerDefinition trigger = triggerService.create(new CreateTriggerCommand(
            OrganizationId.parse(request.organizationId()),
            ProjectId.parse(request.projectId()), request.key(),
            TriggerType.valueOf(request.type()), Optional.ofNullable(request.cronExpression()),
            Optional.ofNullable(request.zoneId()), request.config(),
            Optional.ofNullable(request.secretRef()), request.targetContract(),
            JobType.valueOf(request.targetJobType())));
        return ResponseEntity.status(201).body(new InternalTriggerResponse(
            trigger.id().toString(), trigger.status().name()));
    }

    /**
     * 幂等创建 Durable Job；同 Job Type 和业务键的不同 Payload 返回冲突。
     *
     * @param authentication 已认证服务主体
     * @param request        Job 接单命令
     * @return 202 Accepted 与稳定 Job 标识
     */
    @PostMapping("/internal/v1/scheduler/jobs")
    public ResponseEntity<InternalJobResponse> enqueue(
        Authentication authentication,
        @Valid @RequestBody InternalJobRequest request) {
        requireService(authentication);
        Checksum payloadHash = new Checksum(request.payloadHash());
        if (!payloadHash.equals(Checksum.sha256(request.payload()))) {
            throw new IllegalArgumentException("payloadHash does not match payload");
        }
        Job job = service.enqueue(new EnqueueJobCommand(
            OrganizationId.parse(request.organizationId()),
            ProjectId.parse(request.projectId()),
            JobType.valueOf(request.type()), request.businessKey(), request.payload(), payloadHash,
            request.priority(), request.availableAt(),
            new RetryPolicy(
                request.retryPolicy().maxAttempts(),
                Duration.ofMillis(request.retryPolicy().initialBackoffMillis()),
                Duration.ofMillis(request.retryPolicy().maxBackoffMillis()),
                request.retryPolicy().multiplier(), request.retryPolicy().jitterRatio(),
                Duration.ofMillis(request.retryPolicy().timeoutMillis())),
            IdempotencyCapability.valueOf(request.idempotencyCapability())));
        return ResponseEntity.accepted()
            .location(URI.create("/api/v1/scheduler/jobs/" + job.id().asString()))
            .body(new InternalJobResponse(job.id().asString(), job.status().name()));
    }

    /**
     * 校验调用者是 Audience 包含 agentark-scheduler 的非交互式服务身份。
     *
     * @param authentication Spring Security 认证对象
     */
    private void requireService(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof AgentArkPrincipal principal)
            || principal.type() != PrincipalType.SERVICE
            || principal.serviceIdentity().isEmpty()
            || !principal.serviceIdentity().orElseThrow().audiences().contains(REQUIRED_AUDIENCE)) {
            throw new SchedulerException(
                "SCHEDULER_ACCESS_DENIED",
                "scheduler internal API requires an audience-bound service identity");
        }
    }

    /**
     * @param organizationId        所属组织 UUIDv7
     * @param projectId             所属项目 UUIDv7
     * @param type                  Job 类型，可选 KNOWLEDGE_INGESTION、RUNTIME_TURN、OUTBOUND_WEBHOOK、CHANNEL_MESSAGE
     * @param businessKey           Job Type 内稳定幂等业务键
     * @param payload               不含 Secret 的中立 JSON
     * @param payloadHash           Payload 规范 SHA-256
     * @param priority              优先级，范围 -1000 到 1000
     * @param availableAt           最早执行时间
     * @param retryPolicy           固定重试与超时策略
     * @param idempotencyCapability Handler 幂等能力，可选 NONE、PROVIDER_KEY、INHERENT
     * @author refinex
     */
    public record InternalJobRequest(
        @NotBlank String organizationId,
        @NotBlank String projectId,
        @NotBlank String type,
        @NotBlank @Size(max = 255) String businessKey,
        @NotBlank @Size(max = 1_048_576) String payload,
        @NotBlank String payloadHash,
        @Min(-1000) @Max(1000) int priority,
        @NotNull Instant availableAt,
        @NotNull @Valid InternalRetryPolicy retryPolicy,
        @NotBlank String idempotencyCapability) {
    }

    /**
     * @param maxAttempts          最大 Attempt 数，范围 1 到 100
     * @param initialBackoffMillis 首次退避毫秒数
     * @param maxBackoffMillis     最大退避毫秒数
     * @param multiplier           指数倍率
     * @param jitterRatio          Jitter 比例，范围 0 到 1
     * @param timeoutMillis        单 Attempt 超时毫秒数
     * @author refinex
     */
    public record InternalRetryPolicy(
        @Min(1) @Max(100) int maxAttempts,
        @Positive long initialBackoffMillis,
        @Positive long maxBackoffMillis,
        @DecimalMin("1.0") @DecimalMax("10.0") double multiplier,
        @DecimalMin("0.0") @DecimalMax("1.0") double jitterRatio,
        @Positive long timeoutMillis) {
    }

    /**
     * @param jobId  已持久接收的调度任务 UUIDv7
     * @param status Job 初始状态，当前固定为 READY
     * @author refinex
     */
    public record InternalJobResponse(String jobId, String status) {
    }

    /**
     * @param organizationId 所属组织 UUIDv7
     * @param projectId      所属项目 UUIDv7
     * @param key            项目内稳定 Trigger Key
     * @param type           Trigger 类型，可选 CRON、WEBHOOK
     * @param cronExpression CRON 的 Spring 六段表达式
     * @param zoneId         CRON 的 IANA 时区
     * @param config         不含敏感值的目标 Job Payload 字段
     * @param secretRef      WEBHOOK 验签 SecretRef
     * @param targetContract 目标 Job Payload Contract
     * @param targetJobType  目标 Job 类型
     * @author refinex
     */
    public record InternalTriggerRequest(
        @NotBlank String organizationId,
        @NotBlank String projectId,
        @NotBlank @Size(max = 255) String key,
        @NotBlank String type,
        @Size(max = 255) String cronExpression,
        @Size(max = 64) String zoneId,
        @NotNull @Size(max = 32) Map<String, String> config,
        @Size(max = 1024) String secretRef,
        @NotBlank @Size(max = 255) String targetContract,
        @NotBlank String targetJobType) {
    }

    /**
     * @param triggerId 已创建或幂等复用的 Trigger UUIDv7
     * @param status    Trigger 状态，当前固定为 ENABLED
     * @author refinex
     */
    public record InternalTriggerResponse(String triggerId, String status) {
    }
}
