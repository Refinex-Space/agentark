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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.scheduling.adapter.in.web.SchedulerApiModels.*;
import space.refinex.agentark.scheduling.application.SchedulerApplicationService;
import space.refinex.agentark.scheduling.application.SchedulerAuthorizationService;
import space.refinex.agentark.scheduling.application.TriggerDefinitionService;
import space.refinex.agentark.scheduling.application.TriggerDefinitionService.CreateTriggerCommand;
import space.refinex.agentark.scheduling.application.WebhookIngressService;
import space.refinex.agentark.scheduling.domain.SchedulerException;
import space.refinex.agentark.scheduling.domain.SchedulerModels.DeadLetter;
import space.refinex.agentark.scheduling.domain.SchedulerModels.Job;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;

import java.io.IOException;
import java.net.URI;
import java.util.*;

/**
 * 提供 Job 状态、取消、Dead Letter Redrive 与 HMAC Webhook 接入端点，不暴露执行 Payload。
 *
 * @author refinex
 */
@RestController
public final class SchedulerController {

    /**
     * 入站 Webhook 最大正文为 1 MiB，额外读取一个字节用于判定溢出。
     */
    private static final int MAX_WEBHOOK_BODY_BYTES = 1_048_576;

    /**
     * Scheduler 应用服务。
     */
    private final SchedulerApplicationService service;

    /**
     * Scheduler 授权服务。
     */
    private final SchedulerAuthorizationService authorizationService;

    /**
     * Webhook 接入服务。
     */
    private final WebhookIngressService webhookService;

    /**
     * Trigger 定义服务。
     */
    private final TriggerDefinitionService triggerService;

    /**
     * 创建 Scheduler API Controller。
     *
     * @param service              Scheduler 应用服务
     * @param authorizationService 授权服务
     * @param webhookService       Webhook 接入服务
     * @param triggerService       Trigger 定义服务
     */
    public SchedulerController(
        SchedulerApplicationService service,
        SchedulerAuthorizationService authorizationService,
        WebhookIngressService webhookService,
        TriggerDefinitionService triggerService) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.authorizationService = Objects.requireNonNull(
            authorizationService, "authorizationService must not be null");
        this.webhookService = Objects.requireNonNull(
            webhookService, "webhookService must not be null");
        this.triggerService = Objects.requireNonNull(
            triggerService, "triggerService must not be null");
    }

    /**
     * 按 UUIDv7 游标列出租户 Job，不返回 Payload、Result 正文或 Secret。
     *
     * @param authentication 已认证主体
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param after          可选 Job UUIDv7 游标
     * @param limit          页大小
     * @return Job 游标页
     */
    @GetMapping("/api/v1/scheduler/jobs")
    public JobPageResponse listJobs(
        Authentication authentication,
        @RequestParam String organizationId,
        @RequestParam String projectId,
        @RequestParam(required = false) String after,
        @RequestParam(defaultValue = "50") int limit) {
        requirePageLimit(limit);
        OrganizationId organization = OrganizationId.parse(organizationId);
        ProjectId project = ProjectId.parse(projectId);
        authorizationService.requireProject(
            principal(authentication), SchedulerAuthorizationService.READ,
            organization, project);
        JobId cursor = JobId.parse(after == null
            ? "00000000-0000-7000-8000-000000000000" : after);
        List<JobResponse> items = service.list(
                organization, project, cursor, Math.min(limit + 1, 101))
            .stream().map(this::response).toList();
        boolean hasMore = items.size() > limit;
        List<JobResponse> page = items.stream().limit(limit).toList();
        return new JobPageResponse(page,
            hasMore ? page.getLast().id() : null);
    }

    /**
     * 创建或幂等复用租户 Trigger。
     *
     * @param authentication 已认证主体
     * @param request        Trigger 创建请求
     * @return 201 Created Trigger
     */
    @PostMapping("/api/v1/scheduler/triggers")
    public ResponseEntity<TriggerResponse> createTrigger(
        Authentication authentication,
        @Valid @RequestBody CreateTriggerRequest request) {
        OrganizationId organization = OrganizationId.parse(request.organizationId());
        ProjectId project = ProjectId.parse(request.projectId());
        authorizationService.requireProject(
            principal(authentication), SchedulerAuthorizationService.MANAGE,
            organization, project);
        TriggerDefinition created = triggerService.create(new CreateTriggerCommand(
            organization, project, request.key(), TriggerType.valueOf(request.type()),
            Optional.ofNullable(request.cronExpression()), Optional.ofNullable(request.zoneId()),
            request.config(), Optional.ofNullable(request.secretRef()), request.targetContract(),
            JobType.valueOf(request.targetJobType())));
        return ResponseEntity.created(URI.create(
                "/api/v1/scheduler/triggers/" + created.id()))
            .body(response(created));
    }

    /**
     * 按 UUIDv7 游标列出租户 Trigger。
     *
     * @param authentication 已认证主体
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param after          可选 Trigger UUIDv7 游标
     * @param limit          页大小
     * @return Trigger 游标页
     */
    @GetMapping("/api/v1/scheduler/triggers")
    public TriggerPageResponse listTriggers(
        Authentication authentication,
        @RequestParam String organizationId,
        @RequestParam String projectId,
        @RequestParam(required = false) String after,
        @RequestParam(defaultValue = "50") int limit) {
        requirePageLimit(limit);
        OrganizationId organization = OrganizationId.parse(organizationId);
        ProjectId project = ProjectId.parse(projectId);
        authorizationService.requireProject(
            principal(authentication), SchedulerAuthorizationService.READ,
            organization, project);
        UUID cursor = UUID.fromString(after == null
            ? "00000000-0000-7000-8000-000000000000" : after);
        List<TriggerResponse> items = triggerService.list(
                organization, project, cursor, Math.min(limit + 1, 101))
            .stream().map(this::response).toList();
        boolean hasMore = items.size() > limit;
        List<TriggerResponse> page = items.stream().limit(limit).toList();
        return new TriggerPageResponse(page,
            hasMore ? page.getLast().id() : null);
    }

    /**
     * 读取租户 Job 状态，不返回 Payload 或 Provider 响应正文。
     *
     * @param authentication 已认证主体
     * @param jobId          Job UUIDv7
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @return Job 状态
     */
    @GetMapping("/api/v1/scheduler/jobs/{jobId}")
    public JobResponse getJob(
        Authentication authentication,
        @PathVariable String jobId,
        @RequestParam String organizationId,
        @RequestParam String projectId) {
        OrganizationId organization = OrganizationId.parse(organizationId);
        ProjectId project = ProjectId.parse(projectId);
        authorizationService.requireProject(
            principal(authentication), SchedulerAuthorizationService.READ,
            organization, project);
        return response(service.get(organization, project, JobId.parse(jobId)));
    }

    /**
     * 幂等取消尚未终态的 Job。
     *
     * @param authentication 已认证主体
     * @param jobId          Job UUIDv7
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param request        取消原因
     * @return 202 Accepted
     */
    @PostMapping("/api/v1/scheduler/jobs/{jobId}:cancel")
    public ResponseEntity<Void> cancelJob(
        Authentication authentication,
        @PathVariable String jobId,
        @RequestParam String organizationId,
        @RequestParam String projectId,
        @Valid @RequestBody ActionRequest request) {
        OrganizationId organization = OrganizationId.parse(organizationId);
        ProjectId project = ProjectId.parse(projectId);
        AgentArkPrincipal principal = principal(authentication);
        authorizationService.requireProject(
            principal, SchedulerAuthorizationService.MANAGE, organization, project);
        service.cancel(
            organization, project, JobId.parse(jobId),
            authorizationService.actor(principal), request.reason());
        return ResponseEntity.accepted().build();
    }

    /**
     * 列出租户 OPEN Dead Letter。
     *
     * @param authentication 已认证主体
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param limit          最大数量
     * @return Dead Letter 列表
     */
    @GetMapping("/api/v1/scheduler/dead-letters")
    public DeadLetterListResponse deadLetters(
        Authentication authentication,
        @RequestParam String organizationId,
        @RequestParam String projectId,
        @RequestParam(defaultValue = "50") int limit) {
        OrganizationId organization = OrganizationId.parse(organizationId);
        ProjectId project = ProjectId.parse(projectId);
        authorizationService.requireProject(
            principal(authentication), SchedulerAuthorizationService.READ,
            organization, project);
        List<DeadLetterResponse> items = service.deadLetters(organization, project, limit)
            .stream().map(this::response).toList();
        return new DeadLetterListResponse(items);
    }

    /**
     * 经单独权限和审计 Redrive OPEN Dead Letter。
     *
     * @param authentication 已认证主体
     * @param jobId          Job UUIDv7
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param request        Redrive 原因
     * @return 202 Accepted
     */
    @PostMapping("/api/v1/scheduler/jobs/{jobId}:redrive")
    public ResponseEntity<Void> redrive(
        Authentication authentication,
        @PathVariable String jobId,
        @RequestParam String organizationId,
        @RequestParam String projectId,
        @Valid @RequestBody ActionRequest request) {
        OrganizationId organization = OrganizationId.parse(organizationId);
        ProjectId project = ProjectId.parse(projectId);
        AgentArkPrincipal principal = principal(authentication);
        authorizationService.requireProject(
            principal, SchedulerAuthorizationService.REDRIVE, organization, project);
        service.redrive(
            organization, project, JobId.parse(jobId),
            authorizationService.actor(principal), request.reason());
        return ResponseEntity.accepted().build();
    }

    /**
     * 接收使用 Trigger SecretRef 验签的 Webhook 并原子创建 Durable Job。
     *
     * @param triggerId Trigger UUID
     * @param timestamp Unix 秒时间戳
     * @param nonce     唯一 Nonce
     * @param signature HMAC 签名
     * @param request   尚未无界缓冲的 Servlet 请求
     * @return 202 Accepted Job
     */
    @PostMapping("/api/v1/scheduler/webhooks/{triggerId}")
    public ResponseEntity<WebhookAcceptedResponse> webhook(
        @PathVariable String triggerId,
        @RequestHeader("X-AgentArk-Timestamp") String timestamp,
        @RequestHeader("X-AgentArk-Nonce") String nonce,
        @RequestHeader("X-AgentArk-Signature") String signature,
        HttpServletRequest request) {
        byte[] body = readWebhookBody(request);
        Job job = webhookService.accept(
            UUID.fromString(triggerId), timestamp, nonce, signature, body);
        return ResponseEntity.accepted()
            .location(URI.create("/api/v1/scheduler/jobs/" + job.id().asString()))
            .body(new WebhookAcceptedResponse(job.id().asString(), job.status().name()));
    }

    /**
     * 流式读取有界 Webhook 正文，在解析或验签前拒绝超过 1 MiB 的请求。
     *
     * @param request Servlet 请求
     * @return 原始正文
     */
    private byte[] readWebhookBody(HttpServletRequest request) {
        if (request.getContentLengthLong() > MAX_WEBHOOK_BODY_BYTES) {
            throw new SchedulerException(
                "WEBHOOK_PAYLOAD_TOO_LARGE", "webhook payload exceeds limit");
        }
        try {
            byte[] body = request.getInputStream().readNBytes(MAX_WEBHOOK_BODY_BYTES + 1);
            if (body.length > MAX_WEBHOOK_BODY_BYTES) {
                throw new SchedulerException(
                    "WEBHOOK_PAYLOAD_TOO_LARGE", "webhook payload exceeds limit");
            }
            return body;
        } catch (IOException exception) {
            throw new SchedulerException(
                "WEBHOOK_PAYLOAD_UNREADABLE", "webhook payload could not be read");
        }
    }

    /**
     * 提取已认证 AgentArk Principal。
     *
     * @param authentication Spring Security Authentication
     * @return AgentArk Principal
     */
    private AgentArkPrincipal principal(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof AgentArkPrincipal principal)) {
            throw new SchedulerException(
                "SCHEDULER_AUTHENTICATION_REQUIRED", "scheduler authentication is required");
        }
        return principal;
    }

    /**
     * 将 Trigger 领域模型映射为不含 Secret 值的 Public API 视图。
     *
     * @param trigger Trigger 定义
     * @return Trigger 响应
     */
    private TriggerResponse response(TriggerDefinition trigger) {
        return new TriggerResponse(
            trigger.id().toString(), trigger.organizationId().asString(),
            trigger.projectId().asString(), trigger.key(), trigger.type().name(),
            trigger.cronExpression().orElse(null),
            trigger.zoneId().map(java.time.ZoneId::getId).orElse(null), trigger.config(),
            trigger.secretRef().orElse(null), trigger.targetContract(),
            trigger.targetJobType().name(), trigger.status().name(), trigger.version(),
            trigger.createdAt(), trigger.updatedAt());
    }

    /**
     * 限制 Public 列表页大小，内部多读取一项只用于判断下一页。
     *
     * @param limit 请求页大小
     */
    private void requirePageLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
    }

    /**
     * 映射 Job 状态响应。
     *
     * @param job Job
     * @return API 响应
     */
    private JobResponse response(Job job) {
        return new JobResponse(
            job.id().asString(), job.organizationId().asString(), job.projectId().asString(),
            job.type().name(), job.businessKey(), job.status().name(), job.priority(),
            job.availableAt(), job.currentAttempt(), job.updatedAt());
    }

    /**
     * 映射 Dead Letter 响应。
     *
     * @param value Dead Letter
     * @return API 响应
     */
    private DeadLetterResponse response(DeadLetter value) {
        return new DeadLetterResponse(
            value.id().toString(), value.jobId().asString(), value.finalAttemptId().toString(),
            value.reason(), value.redriveCount(), value.status().name(), value.createdAt());
    }
}
