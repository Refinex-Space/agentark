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
import space.refinex.agentark.scheduling.application.WebhookIngressService;
import space.refinex.agentark.scheduling.domain.SchedulerException;
import space.refinex.agentark.scheduling.domain.SchedulerModels.DeadLetter;
import space.refinex.agentark.scheduling.domain.SchedulerModels.Job;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
     * 创建 Scheduler API Controller。
     *
     * @param service              Scheduler 应用服务
     * @param authorizationService 授权服务
     * @param webhookService       Webhook 接入服务
     */
    public SchedulerController(
        SchedulerApplicationService service,
        SchedulerAuthorizationService authorizationService,
        WebhookIngressService webhookService) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.authorizationService = Objects.requireNonNull(
            authorizationService, "authorizationService must not be null");
        this.webhookService = Objects.requireNonNull(
            webhookService, "webhookService must not be null");
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
