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

import space.refinex.agentark.scheduling.domain.SchedulerException;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;
import space.refinex.agentark.scheduling.domain.SchedulerStateMachine;
import space.refinex.agentark.scheduling.domain.SchedulerUuidV7;
import space.refinex.agentark.scheduling.port.JobHandler;
import space.refinex.agentark.scheduling.port.JobHandler.HandlerResult;
import space.refinex.agentark.scheduling.port.SchedulerRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.random.RandomGenerator;

/**
 * 按 Job Type 隔离 Worker Pool，执行至少一次 Claim、续租、超时、退避和 Dead Letter 编排。
 *
 * @author refinex
 */
public final class SchedulerWorker implements AutoCloseable {

    /**
     * Scheduler 权威仓储。
     */
    private final SchedulerRepository repository;

    /**
     * Job Type 到 Handler 的唯一映射。
     */
    private final Map<JobType, JobHandler> handlers;

    /**
     * 每种 Job Type 独立的有界执行器。
     */
    private final Map<JobType, ExecutorService> executors;

    /**
     * Lease 心跳调度器。
     */
    private final ScheduledExecutorService heartbeatExecutor;

    /**
     * 状态转换校验器。
     */
    private final SchedulerStateMachine stateMachine;

    /**
     * UTC 时间来源。
     */
    private final Clock clock;

    /**
     * 退避 Jitter 随机来源。
     */
    private final RandomGenerator random;

    /**
     * Worker 稳定实例 Key。
     */
    private final String owner;

    /**
     * 单次 Lease 有效期。
     */
    private final Duration leaseTtl;

    /**
     * 创建类型隔离的 Scheduler Worker。
     *
     * @param repository Scheduler 仓储
     * @param handlers   Job Handler 集合
     * @param poolSizes  各 Job Type Worker 数量
     * @param clock      UTC 时钟
     * @param random     Jitter 随机来源
     * @param owner      Worker 实例 Key
     * @param leaseTtl   Lease 有效期
     */
    public SchedulerWorker(
        SchedulerRepository repository,
        List<JobHandler> handlers,
        Map<JobType, Integer> poolSizes,
        Clock clock,
        RandomGenerator random,
        String owner,
        Duration leaseTtl) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.random = Objects.requireNonNull(random, "random must not be null");
        if (owner == null || !owner.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{2,254}")) {
            throw new IllegalArgumentException("owner must be a stable safe key");
        }
        this.owner = owner;
        this.leaseTtl = Objects.requireNonNull(leaseTtl, "leaseTtl must not be null");
        if (leaseTtl.compareTo(Duration.ofSeconds(5)) < 0
            || leaseTtl.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("leaseTtl must be between 5 seconds and 30 minutes");
        }
        this.stateMachine = new SchedulerStateMachine();
        this.handlers = handlerMap(handlers);
        this.executors = executorMap(poolSizes);
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("scheduler-lease-heartbeat-", 0).factory());
    }

    /**
     * 领取并异步执行指定类型的一个到期 Job。
     *
     * @param type Job 类型
     * @return 是否领取到 Job
     */
    public boolean tick(JobType type) {
        Objects.requireNonNull(type, "type must not be null");
        Instant now = clock.instant();
        Optional<ClaimedJob> claimed = repository.claim(
            type, owner, now, leaseTtl, SchedulerUuidV7.generate(now));
        if (claimed.isEmpty()) {
            return false;
        }
        ClaimedJob claim = claimed.orElseThrow();
        executors.get(type).execute(() -> execute(claim));
        return true;
    }

    /**
     * 返回当前进程已装配 Handler 的 Job Type，Worker Loop 不领取缺少 Provider 的任务。
     *
     * @return 不可变支持类型集合
     */
    public Set<JobType> supportedTypes() {
        return handlers.keySet();
    }

    /**
     * 在类型专属线程执行 Handler，并以当前 Fencing Token 提交终态。
     *
     * @param claim 当前 Claim
     */
    private void execute(ClaimedJob claim) {
        JobHandler handler = handlers.get(claim.job().type());
        if (handler == null) {
            fail(claim, HandlerResult.failure("HANDLER_UNAVAILABLE", false), false);
            return;
        }
        if (handler.idempotencyCapability() != claim.job().idempotency()) {
            fail(claim, HandlerResult.failure("HANDLER_IDEMPOTENCY_MISMATCH", false), false);
            return;
        }
        long heartbeatMillis = Math.max(1000L, leaseTtl.toMillis() / 3L);
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
            () -> renew(claim), heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
        try {
            HandlerResult result = handler.handle(claim).toCompletableFuture()
                .orTimeout(claim.job().retryPolicy().timeout().toMillis(), TimeUnit.MILLISECONDS)
                .join();
            if (result.successful()) {
                Instant now = clock.instant();
                stateMachine.requireTransition(JobStatus.CLAIMED, JobStatus.SUCCEEDED, false);
                repository.succeed(
                    claim, result.resultRef(),
                    SchedulerApplicationService.outbox(claim.job().id(), "job.succeeded", now),
                    now);
            } else {
                fail(claim, result, false);
            }
        } catch (CompletionException exception) {
            boolean timedOut = exception.getCause() instanceof TimeoutException;
            fail(claim, HandlerResult.failure(
                timedOut ? "JOB_TIMEOUT" : "HANDLER_FAILED", true), timedOut);
        } catch (RuntimeException exception) {
            fail(claim, HandlerResult.failure("HANDLER_FAILED", true), false);
        } finally {
            heartbeat.cancel(false);
        }
    }

    /**
     * 续租当前 Claim；失败后不主动提交状态，数据库 Fencing 会拒绝陈旧 Owner。
     *
     * @param claim 当前 Claim
     */
    private void renew(ClaimedJob claim) {
        repository.renew(
            claim.job().id(), claim.owner(), claim.fencingToken(),
            clock.instant().plus(leaseTtl));
    }

    /**
     * 根据 Retry Budget、错误分类和幂等能力安排重试或 Dead Letter。
     *
     * @param claim    当前 Claim
     * @param result   Handler 失败结果
     * @param timedOut 是否超时
     */
    private void fail(ClaimedJob claim, HandlerResult result, boolean timedOut) {
        String errorCode = result.errorCode().orElse("HANDLER_FAILED");
        boolean retryAllowed = result.retryable()
            && claim.job().idempotency() != IdempotencyCapability.NONE
            && claim.attemptNumber() < claim.job().retryPolicy().maxAttempts();
        Instant now = clock.instant();
        JobStatus nextStatus = retryAllowed ? JobStatus.RETRY_WAIT : JobStatus.DEAD_LETTERED;
        stateMachine.requireTransition(JobStatus.CLAIMED, nextStatus, false);
        Instant availableAt = retryAllowed
            ? now.plus(claim.job().retryPolicy().delayAfter(claim.attemptNumber(), random))
            : now;
        Optional<DeadLetter> deadLetter = retryAllowed
            ? Optional.empty()
            : Optional.of(new DeadLetter(
            SchedulerUuidV7.generate(now), claim.job().id(), claim.attemptId(), errorCode,
            0, DeadLetterStatus.OPEN, now, now));
        repository.fail(
            claim, timedOut ? AttemptStatus.TIMED_OUT : AttemptStatus.FAILED,
            errorCode, nextStatus, availableAt, deadLetter,
            SchedulerApplicationService.outbox(
                claim.job().id(), retryAllowed ? "job.retry-scheduled" : "job.dead-lettered", now),
            now);
    }

    /**
     * 校验每个 Job Type 最多一个 Handler。
     *
     * @param candidates Handler 集合
     * @return Handler 映射
     */
    private Map<JobType, JobHandler> handlerMap(List<JobHandler> candidates) {
        EnumMap<JobType, JobHandler> result = new EnumMap<>(JobType.class);
        for (JobHandler handler : List.copyOf(
            Objects.requireNonNull(candidates, "handlers must not be null"))) {
            if (result.put(handler.type(), handler) != null) {
                throw new SchedulerException(
                    "DUPLICATE_HANDLER", "job type has more than one handler");
            }
        }
        return Map.copyOf(result);
    }

    /**
     * 创建各 Job Type 独立有界 Worker Pool。
     *
     * @param poolSizes 每种类型并发数
     * @return 执行器映射
     */
    private Map<JobType, ExecutorService> executorMap(Map<JobType, Integer> poolSizes) {
        EnumMap<JobType, ExecutorService> result = new EnumMap<>(JobType.class);
        for (JobType type : JobType.values()) {
            int size = Objects.requireNonNull(poolSizes, "poolSizes must not be null")
                .getOrDefault(type, 1);
            if (size < 1 || size > 64) {
                throw new IllegalArgumentException("worker pool size must be between 1 and 64");
            }
            result.put(type, Executors.newFixedThreadPool(
                size, Thread.ofPlatform().name("scheduler-" + type.name().toLowerCase()
                    + "-", 0).factory()));
        }
        return result;
    }

    /**
     * 停止接收新 Job，并关闭类型 Worker Pool 与 Lease 心跳线程。
     */
    @Override
    public void close() {
        executors.values().forEach(ExecutorService::shutdown);
        heartbeatExecutor.shutdown();
    }
}
