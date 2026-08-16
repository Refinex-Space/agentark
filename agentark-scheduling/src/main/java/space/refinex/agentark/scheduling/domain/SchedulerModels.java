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

import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * 集中定义 Scheduler Plane 的持久 Job、Trigger、Attempt、Delivery 与 Dead Letter 中立模型。
 *
 * @author refinex
 */
public final class SchedulerModels {

    /**
     * 禁止实例化领域模型容器。
     */
    private SchedulerModels() {
    }

    /**
     * 定义 Scheduler 可隔离执行的任务类型。
     *
     * @author refinex
     */
    public enum JobType {

        /**
         * 执行固定 KnowledgeRevision 的安全摄取。
         */
        KNOWLEDGE_INGESTION,

        /**
         * 通过 Runtime Internal API 创建 Agent Turn。
         */
        RUNTIME_TURN,

        /**
         * 向经过 SSRF 校验的外部端点投递 Webhook。
         */
        OUTBOUND_WEBHOOK,

        /**
         * 通过中立 Channel Adapter 投递消息。
         */
        CHANNEL_MESSAGE
    }

    /**
     * 定义 Durable Job 状态。
     *
     * @author refinex
     */
    public enum JobStatus {

        /**
         * 已持久化且到达可执行时间后可被领取。
         */
        READY,

        /**
         * 当前由一个持有有效 Fencing Token 的 Worker 执行。
         */
        CLAIMED,

        /**
         * 当前 Attempt 失败，等待下一次退避时间。
         */
        RETRY_WAIT,

        /**
         * Handler 已成功完成且结果已持久化。
         */
        SUCCEEDED,

        /**
         * Retry Budget 耗尽并已形成 Dead Letter。
         */
        DEAD_LETTERED,

        /**
         * 在执行前或有效 Owner 协作下取消。
         */
        CANCELLED,

        /**
         * 当前执行超过 Job Timeout，是否重试由策略决定。
         */
        TIMED_OUT
    }

    /**
     * 定义只追加 Job Attempt 状态。
     *
     * @author refinex
     */
    public enum AttemptStatus {

        /**
         * Attempt 已由 Claim 事务创建并开始执行。
         */
        RUNNING,

        /**
         * Handler 已成功完成。
         */
        SUCCEEDED,

        /**
         * Handler 返回失败或抛出已分类异常。
         */
        FAILED,

        /**
         * Handler 超过 Job Timeout。
         */
        TIMED_OUT,

        /**
         * Owner Lease 过期后被新 Worker 接管。
         */
        ABANDONED
    }

    /**
     * 定义 Trigger 类型。
     *
     * @author refinex
     */
    public enum TriggerType {

        /**
         * 使用 Cron 表达式和 IANA 时区计算下一点火时间。
         */
        CRON,

        /**
         * 通过签名、时间戳和 Nonce 校验接收外部事件。
         */
        WEBHOOK
    }

    /**
     * 定义 Trigger 生命周期状态。
     *
     * @author refinex
     */
    public enum TriggerStatus {

        /**
         * Trigger 允许推进 Cursor 并生成 Job。
         */
        ENABLED,

        /**
         * Trigger 暂停点火但保留定义和 Cursor。
         */
        DISABLED,

        /**
         * Trigger 已归档且不得再次启用。
         */
        ARCHIVED
    }

    /**
     * 定义 Delivery 生命周期状态。
     *
     * @author refinex
     */
    public enum DeliveryStatus {

        /**
         * Delivery 已创建但尚未调用 Provider。
         */
        PENDING,

        /**
         * 持有有效 Job Fencing Token 的 Handler 正在发送。
         */
        SENDING,

        /**
         * Provider 已确认接受消息。
         */
        SUCCEEDED,

        /**
         * 可安全重试且等待 Job 退避。
         */
        RETRY_WAIT,

        /**
         * 不可重试或预算耗尽。
         */
        FAILED,

        /**
         * 所属 Job 已取消。
         */
        CANCELLED
    }

    /**
     * 定义 Dead Letter 状态。
     *
     * @author refinex
     */
    public enum DeadLetterStatus {

        /**
         * 等待授权操作者检查或 Redrive。
         */
        OPEN,

        /**
         * 已经授权 Redrive 并重新进入 READY。
         */
        REDRIVEN,

        /**
         * 已人工确认不再执行。
         */
        RESOLVED
    }

    /**
     * 定义 Scheduler Outbox 投递状态。
     *
     * @author refinex
     */
    public enum OutboxStatus {

        /**
         * 等待 Outbox Publisher Claim。
         */
        PENDING,

        /**
         * 已由一个 Publisher Claim。
         */
        CLAIMED,

        /**
         * 下游已确认接收。
         */
        PUBLISHED,

        /**
         * 当前投递失败并等待再次尝试。
         */
        FAILED
    }

    /**
     * 定义 Handler 的副作用幂等能力。
     *
     * @author refinex
     */
    public enum IdempotencyCapability {

        /**
         * Handler 只读或天然幂等，可按 Retry Policy 自动重试。
         */
        INHERENT,

        /**
         * Handler 通过稳定 Provider Idempotency Key 保证效果幂等。
         */
        PROVIDER_KEY,

        /**
         * Handler 是无幂等声明的写操作，默认禁止自动重试。
         */
        NONE
    }

    /**
     * @param maxAttempts    包含首次执行在内的最大 Attempt 数，范围为 1 至 100
     * @param initialBackoff 首次失败后的基础退避
     * @param maxBackoff     单次退避上限
     * @param multiplier     指数倍率，范围为 1 至 10
     * @param jitterRatio    对称随机抖动比例，范围为 0 至 0.5
     * @param timeout        每个 Attempt 的执行超时
     * @author refinex
     */
    public record RetryPolicy(
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff,
        double multiplier,
        double jitterRatio,
        Duration timeout) {

        /**
         * 校验 Retry Budget、退避、Jitter 和 Timeout 的有界不变量。
         *
         * @param maxAttempts    最大 Attempt 数
         * @param initialBackoff 初始退避
         * @param maxBackoff     最大退避
         * @param multiplier     指数倍率
         * @param jitterRatio    抖动比例
         * @param timeout        Attempt 超时
         */
        public RetryPolicy {
            Objects.requireNonNull(initialBackoff, "initialBackoff must not be null");
            Objects.requireNonNull(maxBackoff, "maxBackoff must not be null");
            Objects.requireNonNull(timeout, "timeout must not be null");
            if (maxAttempts < 1 || maxAttempts > 100
                || initialBackoff.isNegative() || initialBackoff.isZero()
                || maxBackoff.compareTo(initialBackoff) < 0
                || maxBackoff.compareTo(Duration.ofDays(7)) > 0
                || multiplier < 1.0 || multiplier > 10.0
                || jitterRatio < 0.0 || jitterRatio > 0.5
                || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofDays(1)) > 0) {
                throw new IllegalArgumentException("retry policy is outside scheduler limits");
            }
        }

        /**
         * 计算指定失败 Attempt 之后的指数退避并施加有界对称 Jitter。
         *
         * @param failedAttempt 已失败 Attempt 序号，从 1 开始
         * @param random        随机数来源，测试可注入固定实现
         * @return 不超过 maxBackoff 且不小于 1 毫秒的退避
         */
        public Duration delayAfter(int failedAttempt, RandomGenerator random) {
            Objects.requireNonNull(random, "random must not be null");
            if (failedAttempt < 1) {
                throw new IllegalArgumentException("failedAttempt must be positive");
            }
            double factor = Math.pow(multiplier, Math.max(0, failedAttempt - 1));
            long cappedMillis = Math.min(maxBackoff.toMillis(),
                Math.max(1L, Math.round(initialBackoff.toMillis() * factor)));
            double jitter = 1.0 + ((random.nextDouble() * 2.0 - 1.0) * jitterRatio);
            return Duration.ofMillis(Math.max(1L,
                Math.min(maxBackoff.toMillis(), Math.round(cappedMillis * jitter))));
        }
    }

    /**
     * @param id             触发器 UUIDv7
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param key            项目内稳定 Trigger Key
     * @param type           Trigger 类型
     * @param cronExpression CRON 类型的 Spring Cron 表达式，其余类型为空
     * @param zoneId         CRON 类型的 IANA 时区，其余类型为空
     * @param config         不含 Secret 值的配置
     * @param secretRef      Webhook 验签 Secret 引用，非 Webhook 可为空
     * @param targetContract 目标 Job Contract 版本
     * @param targetJobType  生成的 Job 类型
     * @param status         Trigger 状态
     * @param version        乐观锁版本
     * @param createdAt      创建时间
     * @param updatedAt      最近更新时间
     * @author refinex
     */
    public record TriggerDefinition(
        UUID id,
        OrganizationId organizationId,
        ProjectId projectId,
        String key,
        TriggerType type,
        Optional<String> cronExpression,
        Optional<ZoneId> zoneId,
        Map<String, String> config,
        Optional<String> secretRef,
        String targetContract,
        JobType targetJobType,
        TriggerStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

        /**
         * 校验 Trigger 租户、类型专属字段、配置和版本边界。
         */
        public TriggerDefinition {
            requireUuidV7(id, "TriggerDefinition.id");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            requireSafeKey(key, "trigger key");
            Objects.requireNonNull(type, "type must not be null");
            cronExpression = Objects.requireNonNull(cronExpression,
                "cronExpression must not be null");
            zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
            config = Map.copyOf(Objects.requireNonNull(config, "config must not be null"));
            secretRef = Objects.requireNonNull(secretRef, "secretRef must not be null");
            requireSafeKey(targetContract, "target contract");
            Objects.requireNonNull(targetJobType, "targetJobType must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(updatedAt, "updatedAt must not be null");
            if (version < 0
                || (type == TriggerType.CRON
                && (cronExpression.isEmpty() || zoneId.isEmpty() || secretRef.isPresent()))
                || (type == TriggerType.WEBHOOK
                && (cronExpression.isPresent() || zoneId.isPresent() || secretRef.isEmpty()))) {
                throw new IllegalArgumentException("trigger fields do not match type");
            }
        }
    }

    /**
     * @param triggerId  所属触发器 UUIDv7
     * @param nextFireAt 下一计划点火时间
     * @param lastFireAt 上一次成功创建 Job 的计划时间
     * @param lastToken  上一次点火的稳定 Token
     * @param version    乐观锁版本
     * @author refinex
     */
    public record TriggerCursor(
        UUID triggerId,
        Instant nextFireAt,
        Optional<Instant> lastFireAt,
        Optional<String> lastToken,
        long version) {

        /**
         * 校验 Cursor 时间和单调版本。
         */
        public TriggerCursor {
            requireUuidV7(triggerId, "TriggerCursor.triggerId");
            Objects.requireNonNull(nextFireAt, "nextFireAt must not be null");
            lastFireAt = Objects.requireNonNull(lastFireAt, "lastFireAt must not be null");
            lastToken = Objects.requireNonNull(lastToken, "lastToken must not be null");
            if (version < 0 || lastFireAt.isPresent() != lastToken.isPresent()) {
                throw new IllegalArgumentException("trigger cursor is inconsistent");
            }
        }
    }

    /**
     * @param id                  Job 强类型标识
     * @param organizationId      所属组织
     * @param projectId           所属项目
     * @param type                Job 类型
     * @param businessKey         类型内幂等业务键
     * @param payload             不含 Secret 的规范 JSON
     * @param payloadHash         任务载荷 SHA-256
     * @param status              Job 状态
     * @param priority            优先级，数值越大越优先
     * @param availableAt         最早可 Claim 时间
     * @param retryPolicy         固定重试策略
     * @param idempotency         Handler 副作用幂等能力
     * @param currentAttempt      已创建 Attempt 数
     * @param currentFencingToken 当前有效 Fencing Token，未 Claim 时为 0
     * @param createdAt           创建时间
     * @param updatedAt           最近状态变化时间
     * @author refinex
     */
    public record Job(
        JobId id,
        OrganizationId organizationId,
        ProjectId projectId,
        JobType type,
        String businessKey,
        String payload,
        Checksum payloadHash,
        JobStatus status,
        int priority,
        Instant availableAt,
        RetryPolicy retryPolicy,
        IdempotencyCapability idempotency,
        int currentAttempt,
        long currentFencingToken,
        Instant createdAt,
        Instant updatedAt) {

        /**
         * 校验 Job 的租户、Payload、Attempt 和 Fencing 边界。
         */
        public Job {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(type, "type must not be null");
            requireSafeKey(businessKey, "business key");
            if (payload == null || payload.isBlank()) {
                throw new IllegalArgumentException("payload must not be blank");
            }
            Objects.requireNonNull(payloadHash, "payloadHash must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(availableAt, "availableAt must not be null");
            Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
            Objects.requireNonNull(idempotency, "idempotency must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(updatedAt, "updatedAt must not be null");
            if (priority < -1000 || priority > 1000 || currentAttempt < 0
                || currentFencingToken < 0
                || (status == JobStatus.CLAIMED && currentFencingToken == 0)) {
                throw new IllegalArgumentException("job counters are invalid");
            }
        }
    }

    /**
     * @param job           已领取 Job
     * @param attemptId     当前 Attempt UUIDv7
     * @param attemptNumber 当前 Attempt 序号
     * @param owner         Worker 稳定实例 Key
     * @param fencingToken  当前单调 Fencing Token
     * @param leaseUntil    Lease 到期时间
     * @author refinex
     */
    public record ClaimedJob(
        Job job,
        UUID attemptId,
        int attemptNumber,
        String owner,
        long fencingToken,
        Instant leaseUntil) {

        /**
         * 校验 Claim 与 Job 当前状态完全一致。
         */
        public ClaimedJob {
            Objects.requireNonNull(job, "job must not be null");
            requireUuidV7(attemptId, "ClaimedJob.attemptId");
            requireSafeKey(owner, "owner");
            Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");
            if (job.status() != JobStatus.CLAIMED || attemptNumber < 1
                || attemptNumber != job.currentAttempt() || fencingToken < 1
                || fencingToken != job.currentFencingToken()) {
                throw new IllegalArgumentException("claim does not match job state");
            }
        }
    }

    /**
     * @param id            执行尝试 UUIDv7
     * @param jobId         所属 Job
     * @param attemptNumber Job 内单调序号
     * @param owner         Worker 实例 Key
     * @param fencingToken  当前 Attempt Fencing Token
     * @param status        Attempt 状态
     * @param startedAt     开始时间
     * @param endedAt       终态时间，RUNNING 时为空
     * @param errorCode     稳定错误码，成功或运行中为空
     * @param resultRef     不含授权参数的结果引用
     * @author refinex
     */
    public record JobAttempt(
        UUID id,
        JobId jobId,
        int attemptNumber,
        String owner,
        long fencingToken,
        AttemptStatus status,
        Instant startedAt,
        Optional<Instant> endedAt,
        Optional<String> errorCode,
        Optional<String> resultRef) {

        /**
         * 校验 Attempt 的追加事实形态。
         */
        public JobAttempt {
            requireUuidV7(id, "JobAttempt.id");
            Objects.requireNonNull(jobId, "jobId must not be null");
            requireSafeKey(owner, "owner");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(startedAt, "startedAt must not be null");
            endedAt = Objects.requireNonNull(endedAt, "endedAt must not be null");
            errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
            resultRef = Objects.requireNonNull(resultRef, "resultRef must not be null");
            if (attemptNumber < 1 || fencingToken < 1
                || (status == AttemptStatus.RUNNING && endedAt.isPresent())
                || (status != AttemptStatus.RUNNING && endedAt.isEmpty())) {
                throw new IllegalArgumentException("attempt fields do not match status");
            }
        }
    }

    /**
     * @param jobId        所属 Job
     * @param owner        当前 Worker 实例 Key
     * @param fencingToken 单调 Fencing Token
     * @param leaseUntil   Lease 到期时间
     * @param version      乐观锁版本
     * @author refinex
     */
    public record JobLease(
        JobId jobId, String owner, long fencingToken, Instant leaseUntil, long version) {

        /**
         * 校验 Lease 令牌、Owner 和版本。
         */
        public JobLease {
            Objects.requireNonNull(jobId, "jobId must not be null");
            requireSafeKey(owner, "owner");
            Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");
            if (fencingToken < 1 || version < 0) {
                throw new IllegalArgumentException("lease counters are invalid");
            }
        }
    }

    /**
     * @param id                     投递记录 UUIDv7
     * @param jobId                  所属 Job
     * @param channelType            中立渠道类型
     * @param endpointIdentity       不含 Credential 的目标身份
     * @param providerIdempotencyKey Provider 幂等键，无能力时为空
     * @param status                 Delivery 状态
     * @param providerMessageId      Provider 返回的消息标识
     * @param responseSummary        不含正文和 Secret 的响应摘要
     * @param createdAt              创建时间
     * @param updatedAt              最近更新时间
     * @author refinex
     */
    public record Delivery(
        UUID id,
        JobId jobId,
        String channelType,
        String endpointIdentity,
        Optional<String> providerIdempotencyKey,
        DeliveryStatus status,
        Optional<String> providerMessageId,
        Optional<String> responseSummary,
        Instant createdAt,
        Instant updatedAt) {

        /**
         * 校验 Delivery 中不包含空白身份或 Optional 值。
         */
        public Delivery {
            requireUuidV7(id, "Delivery.id");
            Objects.requireNonNull(jobId, "jobId must not be null");
            requireSafeKey(channelType, "channel type");
            requireSafeKey(endpointIdentity, "endpoint identity");
            providerIdempotencyKey = requireOptionalText(
                providerIdempotencyKey, "providerIdempotencyKey");
            Objects.requireNonNull(status, "status must not be null");
            providerMessageId = requireOptionalText(providerMessageId, "providerMessageId");
            responseSummary = requireOptionalText(responseSummary, "responseSummary");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        }
    }

    /**
     * @param id             死信记录 UUIDv7
     * @param jobId          原 Job
     * @param finalAttemptId 最终失败 Attempt UUIDv7
     * @param reason         稳定失败原因
     * @param redriveCount   已授权 Redrive 次数
     * @param status         Dead Letter 状态
     * @param createdAt      创建时间
     * @param updatedAt      最近更新时间
     * @author refinex
     */
    public record DeadLetter(
        UUID id,
        JobId jobId,
        UUID finalAttemptId,
        String reason,
        int redriveCount,
        DeadLetterStatus status,
        Instant createdAt,
        Instant updatedAt) {

        /**
         * 校验 Dead Letter 身份、原因和 Redrive 计数。
         */
        public DeadLetter {
            requireUuidV7(id, "DeadLetter.id");
            Objects.requireNonNull(jobId, "jobId must not be null");
            requireUuidV7(finalAttemptId, "DeadLetter.finalAttemptId");
            requireSafeKey(reason, "dead letter reason");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(updatedAt, "updatedAt must not be null");
            if (redriveCount < 0) {
                throw new IllegalArgumentException("redriveCount must not be negative");
            }
        }
    }

    /**
     * @param eventId       本地事务 Outbox 事件 UUIDv7
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 UUIDv7
     * @param type          稳定事件类型
     * @param payload       不含 Secret 的 JSON Payload
     * @param status        Outbox 状态
     * @param availableAt   最早投递时间
     * @param attempts      已投递次数
     * @param createdAt     创建时间
     * @author refinex
     */
    public record SchedulerOutbox(
        UUID eventId,
        String aggregateType,
        UUID aggregateId,
        String type,
        String payload,
        OutboxStatus status,
        Instant availableAt,
        int attempts,
        Instant createdAt) {

        /**
         * 校验 Outbox 事件身份、类型、时间和计数。
         */
        public SchedulerOutbox {
            requireUuidV7(eventId, "SchedulerOutbox.eventId");
            requireSafeKey(aggregateType, "aggregate type");
            requireUuidV7(aggregateId, "SchedulerOutbox.aggregateId");
            requireSafeKey(type, "event type");
            if (payload == null || payload.isBlank()) {
                throw new IllegalArgumentException("outbox payload must not be blank");
            }
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(availableAt, "availableAt must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            if (attempts < 0) {
                throw new IllegalArgumentException("outbox attempts must not be negative");
            }
        }
    }

    /**
     * 校验数据库与外部契约使用的稳定安全 Key。
     *
     * @param value Key 值
     * @param name  错误上下文名称
     */
    private static void requireSafeKey(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:/-]{0,254}")) {
            throw new IllegalArgumentException(name + " must contain safe characters");
        }
    }

    /**
     * 校验 Optional 文本容器不为 null 且不存在空白值。
     *
     * @param value Optional 文本
     * @param name  错误上下文名称
     * @return 原 Optional
     */
    private static Optional<String> requireOptionalText(Optional<String> value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException(name + " must not contain blank text");
        }
        return value;
    }

    /**
     * 校验内部追加事实使用 RFC 9562 UUIDv7 与 RFC 4122 Variant。
     *
     * @param value UUID 值
     * @param name  错误上下文名称
     */
    private static void requireUuidV7(UUID value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.version() != 7 || value.variant() != 2) {
            throw new IllegalArgumentException(name + " must be an RFC 9562 UUIDv7");
        }
    }
}
