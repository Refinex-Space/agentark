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

package space.refinex.agentark.runtime.application;

import org.junit.jupiter.api.Test;
import space.refinex.agentark.foundation.redis.DistributedLeaseManager;
import space.refinex.agentark.foundation.redis.Lease;
import space.refinex.agentark.kernel.id.EventId;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.RunId;
import space.refinex.agentark.runtime.adapter.out.coordination.RedisRuntimeLeaseCoordinator;
import space.refinex.agentark.runtime.domain.RuntimeConflictException;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.port.LeaseManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证多实例 Lease 竞争和 MySQL 权威 Fencing 对陈旧写的拒绝。
 *
 * @author refinex
 */
class RuntimeLeaseFencingTest {

    /**
     * 证明未过期 Work Item 只有一个 Owner，过期重领后旧 Token 不能写 Event 或终态。
     */
    @Test
    void allowsOneOwnerAndRejectsStaleWritesAfterReclaim() {
        RuntimePhase13TestSupport fixture = new RuntimePhase13TestSupport();
        Session session = fixture.createSession("lease-session");
        Turn turn = fixture.acceptTurn(session, "lease-turn");
        RuntimeWorkItem first = fixture.store.claimNext(
            "runtime-a", fixture.clock.instant(), Duration.ofSeconds(10)).orElseThrow();
        fixture.store.assignFencingToken(first.runId(), turn.id(), first.fencingToken());

        assertThat(fixture.store.claimNext(
            "runtime-b", fixture.clock.instant(), Duration.ofSeconds(10))).isEmpty();

        fixture.clock.advance(Duration.ofSeconds(11));
        RuntimeWorkItem second = fixture.store.claimNext(
            "runtime-b", fixture.clock.instant(), Duration.ofSeconds(10)).orElseThrow();
        fixture.store.assignFencingToken(second.runId(), turn.id(), second.fencingToken());

        assertThat(second.fencingToken().value()).isGreaterThan(first.fencingToken().value());
        assertThatThrownBy(() -> fixture.store.append(
            EventId.generate(), fixture.organizationId, fixture.projectId, session.id(),
            turn.id(), first.runId(), "run.stale", 1,
            first.runId().asString().replace("-", ""), RuntimePayload.inline("{}"),
            fixture.clock.instant(), first.fencingToken()))
            .isInstanceOf(RuntimeConflictException.class);
        assertThatThrownBy(() -> fixture.store.complete(
            first.runId(), first.fencingToken(), WorkItemStatus.FAILED))
            .isInstanceOf(RuntimeConflictException.class);
    }

    /**
     * 证明 Redis 续约失败会使执行 Lease 失效并触发一次 Provider 中断回调。
     *
     * @throws InterruptedException 等待后台续约回调被中断时抛出
     */
    @Test
    void notifiesExecutionWhenRedisLeaseIsLost() throws InterruptedException {
        DistributedLeaseManager redis = mock(DistributedLeaseManager.class);
        LeaseManager database = mock(LeaseManager.class);
        Instant now = Instant.parse("2026-08-16T08:00:00Z");
        RunId runId = RunId.generate();
        Lease redisLease = new Lease(
            "runtime-execution", runId.asString(), "runtime-a", 1, now.plusSeconds(1));
        when(redis.tryAcquire(anyString(), anyString(), anyString(), any(Duration.class)))
            .thenReturn(Optional.of(redisLease));
        when(redis.renew(any(Lease.class), any(Duration.class))).thenReturn(false);
        RuntimeWorkItem workItem = new RuntimeWorkItem(
            JobId.generate(), runId, WorkItemStatus.CLAIMED, 10, now,
            Optional.of("runtime-a"), Optional.of(now.plusSeconds(1)),
            new FencingToken(1), 1, now);
        CountDownLatch lost = new CountDownLatch(1);

        try (RedisRuntimeLeaseCoordinator coordinator = new RedisRuntimeLeaseCoordinator(
            redis, database, Clock.systemUTC());
            var lease = coordinator.activate(workItem, Duration.ofMillis(300)).orElseThrow()) {
            lease.onLost(lost::countDown);

            assertThat(lost.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(lease.active()).isFalse();
        }
    }
}
