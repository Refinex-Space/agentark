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

package space.refinex.agentark.runtime.adapter.out.coordination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.refinex.agentark.foundation.redis.DistributedLeaseManager;
import space.refinex.agentark.foundation.redis.Lease;
import space.refinex.agentark.runtime.domain.RuntimeConflictException;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimeWorkItem;
import space.refinex.agentark.runtime.port.ExecutionLeaseCoordinator;
import space.refinex.agentark.runtime.port.LeaseManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 使用 Redis 做快速互斥、MySQL 做权威续约和陈旧写拒绝的双层执行 Lease。
 *
 * @author refinex
 */
public final class RedisRuntimeLeaseCoordinator
    implements ExecutionLeaseCoordinator, AutoCloseable {

    /**
     * 记录不含凭据和业务载荷的 Lease 诊断。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(
        RedisRuntimeLeaseCoordinator.class);

    /**
     * Redis Lease 命名空间。
     */
    private static final String NAMESPACE = "runtime-execution";

    /**
     * Redis 分布式 Lease。
     */
    private final DistributedLeaseManager redisLeases;

    /**
     * MySQL 权威 Lease。
     */
    private final LeaseManager databaseLeases;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 共享单线程续约调度器。
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
        runnable -> Thread.ofPlatform().name("agentark-runtime-lease-renewal").daemon(true)
            .unstarted(runnable));

    /**
     * 创建 Redis 与 MySQL 双层 Lease 协调器。
     *
     * @param redisLeases    Redis Lease Manager
     * @param databaseLeases MySQL Lease Manager
     * @param clock          UTC 时钟
     */
    public RedisRuntimeLeaseCoordinator(
        DistributedLeaseManager redisLeases, LeaseManager databaseLeases, Clock clock) {
        this.redisLeases = Objects.requireNonNull(redisLeases, "redisLeases must not be null");
        this.databaseLeases = Objects.requireNonNull(
            databaseLeases, "databaseLeases must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 为已 Claim Work Item 获取 Redis Lease，并启动双层续约。
     *
     * @param workItem 已 Claim Work Item
     * @param ttl      Lease 有效期
     * @return 执行 Lease 或空
     */
    @Override
    public Optional<ExecutionLease> activate(RuntimeWorkItem workItem, Duration ttl) {
        Objects.requireNonNull(workItem, "workItem must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        String owner = workItem.claimedBy().orElseThrow(() ->
            new IllegalArgumentException("workItem must be claimed"));
        Optional<Lease> acquired = redisLeases.tryAcquire(
            NAMESPACE, workItem.runId().asString(), owner, ttl);
        return acquired.map(lease -> new ActiveLease(workItem, lease, ttl));
    }

    /**
     * 关闭进程级 Lease 续约调度器。
     */
    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    /**
     * 维护单个 Run 的 Redis/MySQL 双层 Lease。
     *
     * @author refinex
     */
    private final class ActiveLease implements ExecutionLease {

        /**
         * MySQL Claim 上下文。
         */
        private final RuntimeWorkItem workItem;

        /**
         * Redis Lease 身份。
         */
        private final Lease redisLease;

        /**
         * Lease 续约周期使用的 TTL。
         */
        private final Duration ttl;

        /**
         * 后台续约有效标记。
         */
        private final AtomicBoolean active = new AtomicBoolean(true);

        /**
         * Lease 是否已经因续约或权威校验失败而丢失。
         */
        private final AtomicBoolean lost = new AtomicBoolean(false);

        /**
         * 只执行一次的 Lease 丢失回调。
         */
        private final AtomicReference<Runnable> lossHandler = new AtomicReference<>();

        /**
         * 后台续约任务。
         */
        private final ScheduledFuture<?> renewal;

        /**
         * 创建并启动单个 Run 的续约任务。
         *
         * @param workItem   已 Claim Work Item
         * @param redisLease Redis Lease
         * @param ttl        Lease TTL
         */
        private ActiveLease(RuntimeWorkItem workItem, Lease redisLease, Duration ttl) {
            this.workItem = workItem;
            this.redisLease = redisLease;
            this.ttl = ttl;
            long periodMillis = Math.max(100, ttl.toMillis() / 3);
            this.renewal = scheduler.scheduleWithFixedDelay(
                this::renew, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
        }

        /**
         * 校验后台 Redis 续约与 MySQL 权威 Lease。
         */
        @Override
        public void requireCurrent() {
            if (!active.get()) {
                throw new RuntimeConflictException("runtime execution lease was lost");
            }
            try {
                databaseLeases.requireCurrent(
                    workItem.runId(), workItem.claimedBy().orElseThrow(),
                    workItem.fencingToken(), Instant.now(clock));
            } catch (RuntimeException exception) {
                lose(exception);
                throw exception;
            }
        }

        /**
         * 返回后台续约有效状态。
         *
         * @return 仍有效时为 true
         */
        @Override
        public boolean active() {
            return active.get();
        }

        /**
         * 注册一次性丢失回调；若 Lease 已丢失则立即执行。
         *
         * @param action Lease 丢失后调用 Provider Cancel 的动作
         */
        @Override
        public void onLost(Runnable action) {
            Objects.requireNonNull(action, "action must not be null");
            if (!lossHandler.compareAndSet(null, action)) {
                throw new IllegalStateException("runtime lease loss handler is already registered");
            }
            if (lost.get()) {
                runLossHandler();
            }
        }

        /**
         * 停止续约并条件释放 Redis Lease。
         */
        @Override
        public void close() {
            if (active.getAndSet(false)) {
                renewal.cancel(false);
                redisLeases.release(redisLease);
            }
        }

        /**
         * 先续约 Redis，再续约 MySQL；任一失败即永久失效。
         */
        private void renew() {
            if (!active.get()) {
                return;
            }
            try {
                boolean redisCurrent = redisLeases.renew(redisLease, ttl);
                boolean databaseCurrent = redisCurrent && databaseLeases.renew(
                    workItem.runId(), workItem.claimedBy().orElseThrow(),
                    workItem.fencingToken(), Instant.now(clock), ttl);
                if (!databaseCurrent) {
                    lose(null);
                }
            } catch (RuntimeException exception) {
                lose(exception);
            }
        }

        /**
         * 原子标记 Lease 丢失并执行一次 Provider 中断回调。
         *
         * @param cause 可选续约异常；不含业务载荷
         */
        private void lose(RuntimeException cause) {
            if (!lost.compareAndSet(false, true)) {
                return;
            }
            active.set(false);
            renewal.cancel(false);
            if (cause != null) {
                LOGGER.warn(
                    "Runtime execution lease renewal failed for runId={} owner={}",
                    workItem.runId().asString(), workItem.claimedBy().orElseThrow(), cause);
            } else {
                LOGGER.warn(
                    "Runtime execution lease was lost for runId={} owner={}",
                    workItem.runId().asString(), workItem.claimedBy().orElseThrow());
            }
            runLossHandler();
        }

        /**
         * 获取并执行一次丢失回调，回调异常只记录脱敏诊断。
         */
        private void runLossHandler() {
            Runnable action = lossHandler.getAndSet(null);
            if (action == null) {
                return;
            }
            try {
                action.run();
            } catch (RuntimeException exception) {
                LOGGER.warn(
                    "Runtime provider cancellation failed after lease loss for runId={}",
                    workItem.runId().asString(), exception);
            }
        }
    }
}
