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

package space.refinex.agentark.scheduling.adapter.out.persistence;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.scheduling.adapter.out.persistence.SchedulerPersistenceRows.*;
import space.refinex.agentark.scheduling.domain.SchedulerException;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;
import space.refinex.agentark.scheduling.domain.SchedulerUuidV7;
import space.refinex.agentark.scheduling.port.DeliveryRepository;
import space.refinex.agentark.scheduling.port.SchedulerRepository;
import space.refinex.agentark.scheduling.port.TriggerRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * 使用 Scheduler 独占 MyBatis Mapper 实现 Job、Trigger、Delivery 与 Fencing 权威事务。
 *
 * @author refinex
 */
public class MybatisSchedulerStore
    implements SchedulerRepository, TriggerRepository, DeliveryRepository {

    /**
     * 调度数据库 Mapper。
     */
    private final SchedulerMapper mapper;

    /**
     * 持久化 JSON 映射器。
     */
    private final JsonMapper jsonMapper;

    /**
     * 创建 Scheduler MySQL Store。
     *
     * @param mapper     Scheduler Mapper
     * @param jsonMapper JSON Mapper
     */
    public MybatisSchedulerStore(SchedulerMapper mapper, JsonMapper jsonMapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * 在同一事务中插入 Trigger、可选 Cursor 与创建事件。
     *
     * @param trigger Trigger 定义
     * @param cursor  Cron Cursor
     * @param outbox  创建事件
     */
    @Override
    @Transactional
    public void insert(
        TriggerDefinition trigger,
        Optional<TriggerCursor> cursor,
        SchedulerOutbox outbox) {
        mapper.insertTrigger(toRow(trigger));
        cursor.ifPresent(value -> mapper.insertTriggerCursor(new CursorRow(
            value.triggerId(), value.nextFireAt(), value.lastFireAt().orElse(null),
            value.lastToken().orElse(null), value.version())));
        insertOutbox(outbox);
    }

    /**
     * 按租户和稳定 Key 读取 Trigger。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param key            Trigger Key
     * @return Trigger
     */
    @Override
    public Optional<TriggerDefinition> findByKey(
        OrganizationId organizationId, ProjectId projectId, String key) {
        return mapper.findTriggerByKey(
            organizationId.value(), projectId.value(), key).map(this::toDomain);
    }

    /**
     * 插入 Durable Job。
     *
     * @param job 待持久化 Job
     */
    @Override
    @Transactional
    public void insert(Job job, SchedulerOutbox outbox) {
        mapper.insertJob(toRow(job));
        insertOutbox(outbox);
    }

    /**
     * 按类型与业务键读取 Job。
     *
     * @param type        Job 类型
     * @param businessKey 业务键
     * @return Job
     */
    @Override
    public Optional<Job> findByBusinessKey(JobType type, String businessKey) {
        return mapper.findJobByBusinessKey(type.name(), businessKey).map(this::toDomain);
    }

    /**
     * 按标识读取 Job。
     *
     * @param jobId Job 标识
     * @return Job
     */
    @Override
    public Optional<Job> find(JobId jobId) {
        return mapper.findJob(jobId.value()).map(this::toDomain);
    }

    /**
     * 按 UUIDv7 游标列出租户 Job。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param afterId        排除的最后 Job
     * @param limit          读取上限
     * @return Job 列表
     */
    @Override
    public List<Job> list(
        OrganizationId organizationId, ProjectId projectId, JobId afterId, int limit) {
        return mapper.listJobs(
                organizationId.value(), projectId.value(), afterId.value(), limit)
            .stream().map(this::toDomain).toList();
    }

    /**
     * 在 MySQL 行锁内 Claim Job、接管过期 Attempt、递增 Token 并追加新 Attempt。
     *
     * @param type      Job 类型
     * @param owner     Worker Key
     * @param now       当前时间
     * @param leaseTtl  Lease 时长
     * @param attemptId Attempt UUIDv7
     * @return Claim
     */
    @Override
    @Transactional
    public Optional<ClaimedJob> claim(
        JobType type, String owner, Instant now, Duration leaseTtl, UUID attemptId) {
        Optional<JobRow> candidate = mapper.lockClaimCandidate(type.name(), now);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        JobRow row = candidate.orElseThrow();
        if (JobStatus.CLAIMED.name().equals(row.status())) {
            mapper.abandonAttempt(row.id(), row.currentAttempt(), now);
        }
        int attempt = row.currentAttempt() + 1;
        long token = row.currentFencingToken() + 1;
        Instant leaseUntil = now.plus(leaseTtl);
        requireOne(mapper.claimJob(row.id(), owner, attempt, token, leaseUntil, now),
            "JOB_CLAIM_CONFLICT");
        mapper.upsertLease(row.id(), owner, token, leaseUntil);
        mapper.insertAttempt(attemptId, row.id(), attempt, owner, token, now);
        Job claimed = toDomain(new JobRow(
            row.id(), row.organizationId(), row.projectId(), row.type(), row.businessKey(),
            row.payloadJson(), row.payloadObjectUri(), row.payloadHash(), JobStatus.CLAIMED.name(),
            row.priority(), row.availableAt(), row.retryPolicyJson(),
            row.idempotencyCapability(), attempt, token, owner, leaseUntil,
            row.resultRef(), null, row.createdAt(), now));
        return Optional.of(new ClaimedJob(
            claimed, attemptId, attempt, owner, token, leaseUntil));
    }

    /**
     * 原子续租 Lease 和 Job 冗余 Claim 时间。
     *
     * @param jobId        Job 标识
     * @param owner        Worker Key
     * @param fencingToken Token
     * @param leaseUntil   新到期时间
     * @return 两行均更新时为 true
     */
    @Override
    @Transactional
    public boolean renew(JobId jobId, String owner, long fencingToken, Instant leaseUntil) {
        int lease = mapper.renewLease(jobId.value(), owner, fencingToken, leaseUntil);
        int job = lease == 1
            ? mapper.renewJobClaim(jobId.value(), owner, fencingToken, leaseUntil) : 0;
        return lease == 1 && job == 1;
    }

    /**
     * 以当前 Token 原子完成 Attempt、Job、Lease 和 Outbox。
     *
     * @param claim       当前 Claim
     * @param resultRef   结果引用
     * @param outbox      Outbox
     * @param completedAt 完成时间
     */
    @Override
    @Transactional
    public void succeed(
        ClaimedJob claim, Optional<String> resultRef,
        SchedulerOutbox outbox, Instant completedAt) {
        requireOne(mapper.completeAttempt(
            claim.attemptId(), claim.fencingToken(), AttemptStatus.SUCCEEDED.name(),
            null, resultRef.orElse(null), completedAt), "STALE_FENCING_TOKEN");
        requireOne(mapper.completeJob(
                claim.job().id().value(), claim.owner(), claim.fencingToken(),
                JobStatus.SUCCEEDED.name(), completedAt, null, resultRef.orElse(null), completedAt),
            "STALE_FENCING_TOKEN");
        requireOne(mapper.deleteLease(
                claim.job().id().value(), claim.owner(), claim.fencingToken()),
            "STALE_FENCING_TOKEN");
        insertOutbox(outbox);
    }

    /**
     * 以当前 Token 原子记录失败、重试或 Dead Letter。
     *
     * @param claim         当前 Claim
     * @param attemptStatus Attempt 终态
     * @param errorCode     错误码
     * @param nextStatus    Job 目标状态
     * @param availableAt   下次时间
     * @param deadLetter    可选 Dead Letter
     * @param outbox        Outbox
     * @param completedAt   完成时间
     */
    @Override
    @Transactional
    public void fail(
        ClaimedJob claim,
        AttemptStatus attemptStatus,
        String errorCode,
        JobStatus nextStatus,
        Instant availableAt,
        Optional<DeadLetter> deadLetter,
        SchedulerOutbox outbox,
        Instant completedAt) {
        requireOne(mapper.completeAttempt(
            claim.attemptId(), claim.fencingToken(), attemptStatus.name(), errorCode,
            null, completedAt), "STALE_FENCING_TOKEN");
        requireOne(mapper.completeJob(
            claim.job().id().value(), claim.owner(), claim.fencingToken(), nextStatus.name(),
            availableAt, errorCode, null, completedAt), "STALE_FENCING_TOKEN");
        requireOne(mapper.deleteLease(
                claim.job().id().value(), claim.owner(), claim.fencingToken()),
            "STALE_FENCING_TOKEN");
        deadLetter.ifPresent(value -> mapper.insertDeadLetter(toRow(value)));
        insertOutbox(outbox);
    }

    /**
     * 取消租户 Job 并终结当前 Attempt、删除 Lease、写入 Outbox。
     */
    @Override
    @Transactional
    public void cancel(
        JobId jobId, OrganizationId organizationId, ProjectId projectId,
        String actor, SchedulerOutbox outbox, Instant cancelledAt) {
        requireOne(mapper.cancelJob(
                jobId.value(), organizationId.value(), projectId.value(), cancelledAt),
            "JOB_CANCEL_CONFLICT");
        mapper.cancelRunningAttempt(jobId.value(), cancelledAt);
        mapper.deleteLeaseByJob(jobId.value());
        insertOutbox(outbox);
    }

    /**
     * Redrive OPEN Dead Letter 并重新激活原 Job。
     */
    @Override
    @Transactional
    public void redrive(
        JobId jobId, OrganizationId organizationId, ProjectId projectId,
        String actor, String reason, SchedulerOutbox outbox, Instant redrivenAt) {
        requireOne(mapper.redriveDeadLetter(jobId.value(), actor, reason, redrivenAt),
            "DEAD_LETTER_NOT_OPEN");
        requireOne(mapper.reactivateJob(
                jobId.value(), organizationId.value(), projectId.value(), redrivenAt),
            "JOB_REDRIVE_CONFLICT");
        insertOutbox(outbox);
    }

    /**
     * 列出租户 OPEN Dead Letter。
     */
    @Override
    public List<DeadLetter> listOpenDeadLetters(
        OrganizationId organizationId, ProjectId projectId, int limit) {
        return mapper.listOpenDeadLetters(
                organizationId.value(), projectId.value(), limit).stream()
            .map(this::toDomain).toList();
    }

    /**
     * 统计到期队列深度。
     */
    @Override
    public long dueDepth(JobType type, Instant now) {
        return mapper.countDue(type.name(), now);
    }

    /**
     * 计算最老到期 Job 年龄。
     */
    @Override
    public Optional<Duration> oldestAge(JobType type, Instant now) {
        return mapper.oldestDue(type.name(), now)
            .map(oldest -> Duration.between(oldest, now));
    }

    /**
     * 读取 Trigger。
     */
    @Override
    public Optional<TriggerDefinition> find(UUID triggerId) {
        return mapper.findTrigger(triggerId).map(this::toDomain);
    }

    /**
     * 按 UUIDv7 游标列出租户 Trigger。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param afterId        排除的最后 Trigger
     * @param limit          读取上限
     * @return Trigger 列表
     */
    @Override
    public List<TriggerDefinition> list(
        OrganizationId organizationId, ProjectId projectId, UUID afterId, int limit) {
        return mapper.listTriggers(
                organizationId.value(), projectId.value(), afterId, limit)
            .stream().map(this::toDomain).toList();
    }

    /**
     * 列出到期 Trigger 与 Cursor。
     */
    @Override
    public List<DueTrigger> findDue(Instant now, int limit) {
        return mapper.findDueTriggers(now, limit).stream()
            .map(trigger -> new DueTrigger(
                toDomain(trigger), mapper.findCursor(trigger.id()).map(this::toDomain)
                .orElseThrow(() -> new SchedulerException(
                    "TRIGGER_CURSOR_MISSING", "cron trigger cursor is missing"))))
            .toList();
    }

    /**
     * 原子推进 Cron Cursor 并插入 Job。
     */
    @Override
    @Transactional
    public boolean fire(
        TriggerDefinition trigger, TriggerCursor cursor, Job job, Instant scheduledAt,
        Instant nextFireAt, String fireToken, SchedulerOutbox outbox) {
        int updated = mapper.advanceCursor(
            trigger.id(), cursor.version(), scheduledAt, nextFireAt, fireToken);
        if (updated == 0) {
            return false;
        }
        mapper.insertJob(toRow(job));
        insertOutbox(outbox);
        return true;
    }

    /**
     * 原子消费 Webhook Nonce 并插入 Job。
     */
    @Override
    @Transactional
    public boolean acceptWebhook(
        TriggerDefinition trigger, String nonce, String requestHash,
        Instant expiresAt, Job job, SchedulerOutbox outbox) {
        byte[] hash = HexFormat.of().parseHex(new Checksum(requestHash).hex());
        try {
            mapper.insertWebhookNonce(
                SchedulerUuidV7.generate(job.createdAt()), trigger.id(), nonce, hash,
                expiresAt, job.createdAt());
        } catch (DuplicateKeyException exception) {
            return false;
        }
        mapper.insertJob(toRow(job));
        insertOutbox(outbox);
        return true;
    }

    /**
     * 按 Provider Key 读取 Delivery。
     */
    @Override
    public Optional<Delivery> findByProviderKey(String providerKey) {
        return mapper.findDeliveryByProviderKey(providerKey).map(this::toDomain);
    }

    /**
     * 插入 Delivery。
     */
    @Override
    public void insert(Delivery delivery) {
        mapper.insertDelivery(new DeliveryRow(
            delivery.id(), delivery.jobId().value(), delivery.channelType(),
            delivery.endpointIdentity(), delivery.providerIdempotencyKey().orElse(null),
            delivery.status().name(), delivery.providerMessageId().orElse(null),
            delivery.responseSummary().orElse(null), delivery.createdAt(), delivery.updatedAt()));
    }

    /**
     * 使用 Job 当前 Token 转换 Delivery。
     */
    @Override
    public void transition(
        String deliveryId, JobId jobId, long fencingToken,
        DeliveryStatus current, DeliveryStatus target,
        Optional<String> providerMessageId, Optional<String> responseSummary,
        Instant updatedAt) {
        requireOne(mapper.transitionDelivery(
            UUID.fromString(deliveryId), jobId.value(), fencingToken,
            current.name(), target.name(), providerMessageId.orElse(null),
            responseSummary.orElse(null), updatedAt), "STALE_FENCING_TOKEN");
    }

    /**
     * 将 Domain Job 映射为数据库行。
     *
     * @param job Domain Job
     * @return Job 行
     */
    private JobRow toRow(Job job) {
        RetryPolicy policy = job.retryPolicy();
        String retryJson = jsonMapper.writeValueAsString(Map.of(
            "maxAttempts", policy.maxAttempts(),
            "initialBackoffMillis", policy.initialBackoff().toMillis(),
            "maxBackoffMillis", policy.maxBackoff().toMillis(),
            "multiplier", policy.multiplier(),
            "jitterRatio", policy.jitterRatio(),
            "timeoutMillis", policy.timeout().toMillis()));
        return new JobRow(
            job.id().value(), job.organizationId().value(), job.projectId().value(),
            job.type().name(), job.businessKey(), job.payload(), null,
            HexFormat.of().parseHex(job.payloadHash().hex()), job.status().name(),
            job.priority(), job.availableAt(), retryJson, job.idempotency().name(),
            job.currentAttempt(), job.currentFencingToken(), null,
            null, null, null, job.createdAt(), job.updatedAt());
    }

    /**
     * 将数据库 Job 行映射为领域对象。
     *
     * @param row Job 行
     * @return Domain Job
     */
    @SuppressWarnings("unchecked")
    private Job toDomain(JobRow row) {
        Map<String, Object> policy = jsonMapper.readValue(row.retryPolicyJson(), Map.class);
        RetryPolicy retryPolicy = new RetryPolicy(
            number(policy, "maxAttempts").intValue(),
            Duration.ofMillis(number(policy, "initialBackoffMillis").longValue()),
            Duration.ofMillis(number(policy, "maxBackoffMillis").longValue()),
            number(policy, "multiplier").doubleValue(),
            number(policy, "jitterRatio").doubleValue(),
            Duration.ofMillis(number(policy, "timeoutMillis").longValue()));
        return new Job(
            new JobId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), JobType.valueOf(row.type()), row.businessKey(),
            row.payloadJson(), new Checksum("sha256:" + HexFormat.of().formatHex(row.payloadHash())),
            JobStatus.valueOf(row.status()), row.priority(), row.availableAt(), retryPolicy,
            IdempotencyCapability.valueOf(row.idempotencyCapability()),
            row.currentAttempt(), row.currentFencingToken(), row.createdAt(), row.updatedAt());
    }

    /**
     * 将 Trigger 行映射为领域对象。
     *
     * @param row Trigger 行
     * @return Trigger
     */
    @SuppressWarnings("unchecked")
    private TriggerDefinition toDomain(TriggerRow row) {
        Map<String, String> config = jsonMapper.readValue(row.configJson(), Map.class);
        return new TriggerDefinition(
            row.id(), new OrganizationId(row.organizationId()), new ProjectId(row.projectId()),
            row.triggerKey(), TriggerType.valueOf(row.type()),
            Optional.ofNullable(row.scheduleExpression()),
            Optional.ofNullable(row.timeZone()).map(ZoneId::of), config,
            Optional.ofNullable(row.secretRef()), row.targetContract(),
            JobType.valueOf(row.targetJobType()), TriggerStatus.valueOf(row.status()),
            row.version(), row.createdAt(), row.updatedAt());
    }

    /**
     * 将 Trigger 领域对象映射为数据库行。
     *
     * @param value Trigger
     * @return Trigger 行
     */
    private TriggerRow toRow(TriggerDefinition value) {
        return new TriggerRow(
            value.id(), value.organizationId().value(), value.projectId().value(),
            value.key(), value.type().name(), value.cronExpression().orElse(null),
            value.zoneId().map(ZoneId::getId).orElse(null),
            jsonMapper.writeValueAsString(new TreeMap<>(value.config())),
            value.secretRef().orElse(null), value.targetContract(),
            value.targetJobType().name(), value.status().name(), value.version(),
            value.createdAt(), value.updatedAt());
    }

    /**
     * 将 Cursor 行映射为领域对象。
     *
     * @param row Cursor 行
     * @return Cursor
     */
    private TriggerCursor toDomain(CursorRow row) {
        return new TriggerCursor(
            row.triggerId(), row.nextFireAt(), Optional.ofNullable(row.lastFireAt()),
            Optional.ofNullable(row.lastToken()), row.version());
    }

    /**
     * 将 Delivery 行映射为领域对象。
     *
     * @param row Delivery 行
     * @return Delivery
     */
    private Delivery toDomain(DeliveryRow row) {
        return new Delivery(
            row.id(), new JobId(row.jobId()), row.channelType(), row.endpointIdentity(),
            Optional.ofNullable(row.providerIdempotencyKey()),
            DeliveryStatus.valueOf(row.status()), Optional.ofNullable(row.providerMessageId()),
            Optional.ofNullable(row.responseSummary()), row.createdAt(), row.updatedAt());
    }

    /**
     * 将 Dead Letter 行映射为领域对象。
     *
     * @param row Dead Letter 行
     * @return Dead Letter
     */
    private DeadLetter toDomain(DeadLetterRow row) {
        return new DeadLetter(
            row.id(), new JobId(row.jobId()), row.finalAttemptId(), row.reason(),
            row.redriveCount(), DeadLetterStatus.valueOf(row.status()),
            row.createdAt(), row.updatedAt());
    }

    /**
     * 将 Dead Letter 领域对象映射为数据库行。
     *
     * @param value Dead Letter
     * @return Dead Letter 行
     */
    private DeadLetterRow toRow(DeadLetter value) {
        return new DeadLetterRow(
            value.id(), value.jobId().value(), value.finalAttemptId(), value.reason(),
            value.redriveCount(), value.status().name(), value.createdAt(), value.updatedAt());
    }

    /**
     * 插入 Outbox。
     *
     * @param outbox Outbox 事件
     */
    private void insertOutbox(SchedulerOutbox outbox) {
        mapper.insertOutbox(
            outbox.eventId(), outbox.aggregateType(), outbox.aggregateId(), outbox.type(),
            outbox.payload(), outbox.status().name(), outbox.availableAt(), outbox.attempts(),
            outbox.createdAt());
    }

    /**
     * 读取重试策略数值字段。
     *
     * @param map  JSON Map
     * @param name 字段名
     * @return 数值
     */
    private Number number(Map<String, Object> map, String name) {
        Object value = map.get(name);
        if (!(value instanceof Number number)) {
            throw new SchedulerException("RETRY_POLICY_INVALID", "stored retry policy is invalid");
        }
        return number;
    }

    /**
     * 要求持久化条件更新精确命中一行。
     *
     * @param count 更新行数
     * @param code  失败稳定码
     */
    private void requireOne(int count, String code) {
        if (count != 1) {
            throw new SchedulerException(code, "scheduler conditional write was rejected");
        }
    }
}
