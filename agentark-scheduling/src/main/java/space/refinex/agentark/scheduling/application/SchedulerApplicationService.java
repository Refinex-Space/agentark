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

import org.springframework.transaction.annotation.Transactional;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.scheduling.application.SchedulerCommands.EnqueueJobCommand;
import space.refinex.agentark.scheduling.domain.SchedulerException;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;
import space.refinex.agentark.scheduling.domain.SchedulerUuidV7;
import space.refinex.agentark.scheduling.port.SchedulerAuditPort;
import space.refinex.agentark.scheduling.port.SchedulerRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 编排 Job 幂等接单、租户管理查询、取消和经授权审计的 Dead Letter Redrive。
 *
 * @author refinex
 */
public class SchedulerApplicationService {

    /**
     * Scheduler 权威仓储。
     */
    private final SchedulerRepository repository;

    /**
     * 高风险操作审计端口。
     */
    private final SchedulerAuditPort auditPort;

    /**
     * UTC 时间来源。
     */
    private final Clock clock;

    /**
     * 创建 Scheduler 应用服务。
     *
     * @param repository Scheduler 权威仓储
     * @param auditPort  审计输出端口
     * @param clock      UTC 时钟
     */
    public SchedulerApplicationService(
        SchedulerRepository repository, SchedulerAuditPort auditPort, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 幂等创建 Job；相同类型和业务键必须具有相同 Payload Hash。
     *
     * @param command 接单命令
     * @return 新建或既有 Job
     */
    public Job enqueue(EnqueueJobCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var existing = repository.findByBusinessKey(command.type(), command.businessKey());
        if (existing.isPresent()) {
            Job job = existing.orElseThrow();
            if (!job.payloadHash().equals(command.payloadHash())
                || !job.organizationId().equals(command.organizationId())
                || !job.projectId().equals(command.projectId())) {
                throw new SchedulerException(
                    "IDEMPOTENCY_CONFLICT", "job business key already has different content");
            }
            return job;
        }
        Instant now = clock.instant();
        Job job = new Job(
            JobId.generate(), command.organizationId(), command.projectId(), command.type(),
            command.businessKey(), command.payload(), command.payloadHash(), JobStatus.READY,
            command.priority(), command.availableAt(), command.retryPolicy(),
            command.idempotency(), 0, 0, now, now);
        try {
            repository.insert(job, outbox(job.id(), "job.accepted", now));
            return job;
        } catch (RuntimeException exception) {
            Job concurrent = repository.findByBusinessKey(command.type(), command.businessKey())
                .orElseThrow(() -> exception);
            if (!concurrent.payloadHash().equals(command.payloadHash())
                || !concurrent.organizationId().equals(command.organizationId())
                || !concurrent.projectId().equals(command.projectId())) {
                throw new SchedulerException(
                    "IDEMPOTENCY_CONFLICT", "concurrent job has different content");
            }
            return concurrent;
        }
    }

    /**
     * 读取租户 Job；跨租户访问返回不存在语义。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param jobId          Job 标识
     * @return Job
     */
    public Job get(OrganizationId organizationId, ProjectId projectId, JobId jobId) {
        Job job = repository.find(jobId)
            .orElseThrow(() -> new SchedulerException("JOB_NOT_FOUND", "job is not available"));
        if (!job.organizationId().equals(organizationId)
            || !job.projectId().equals(projectId)) {
            throw new SchedulerException("JOB_NOT_FOUND", "job is not available");
        }
        return job;
    }

    /**
     * 按 UUIDv7 游标列出租户 Job，Public API 映射时不得暴露 Payload。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param afterId        排除的最后 Job
     * @param limit          页大小
     * @return Job 列表
     */
    public List<Job> list(
        OrganizationId organizationId, ProjectId projectId, JobId afterId, int limit) {
        if (limit < 1 || limit > 101) {
            throw new IllegalArgumentException("internal read limit must be between 1 and 101");
        }
        return repository.list(organizationId, projectId, afterId, limit);
    }

    /**
     * 取消租户 Job，并要求审计写出成功后才返回。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param jobId          Job 标识
     * @param actor          操作者
     * @param reason         取消原因
     */
    @Transactional
    public void cancel(
        OrganizationId organizationId,
        ProjectId projectId,
        JobId jobId,
        String actor,
        String reason) {
        get(organizationId, projectId, jobId);
        Instant now = clock.instant();
        SchedulerOutbox outbox = outbox(jobId, "job.cancelled", now);
        repository.cancel(jobId, organizationId, projectId, actor, outbox, now);
        auditPort.record(
            "scheduler.job.cancel", actor, organizationId, projectId, jobId, reason, now);
    }

    /**
     * 授权 Redrive OPEN Dead Letter，并记录不可吞掉的审计事实。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param jobId          Job 标识
     * @param actor          操作者
     * @param reason         Redrive 原因
     */
    @Transactional
    public void redrive(
        OrganizationId organizationId,
        ProjectId projectId,
        JobId jobId,
        String actor,
        String reason) {
        get(organizationId, projectId, jobId);
        Instant now = clock.instant();
        SchedulerOutbox outbox = outbox(jobId, "job.redriven", now);
        repository.redrive(
            jobId, organizationId, projectId, actor, reason, outbox, now);
        auditPort.record(
            "scheduler.dead-letter.redrive", actor, organizationId, projectId,
            jobId, reason, now);
    }

    /**
     * 列出当前租户最近 OPEN Dead Letter。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param limit          最大返回数量
     * @return Dead Letter 列表
     */
    public List<DeadLetter> deadLetters(
        OrganizationId organizationId, ProjectId projectId, int limit) {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
        return repository.listOpenDeadLetters(organizationId, projectId, limit);
    }

    /**
     * 创建只包含稳定标识的 Scheduler Outbox Payload。
     *
     * @param jobId Job 标识
     * @param type  事件类型
     * @param now   事件时间
     * @return Outbox 事件
     */
    static SchedulerOutbox outbox(JobId jobId, String type, Instant now) {
        return new SchedulerOutbox(
            SchedulerUuidV7.generate(now), "job", jobId.value(), type,
            "{\"jobId\":\"" + jobId.asString() + "\",\"type\":\"" + type + "\"}",
            OutboxStatus.PENDING, now, 0, now);
    }
}
