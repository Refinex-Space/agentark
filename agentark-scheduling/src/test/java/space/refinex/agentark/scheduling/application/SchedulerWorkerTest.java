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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;
import space.refinex.agentark.scheduling.domain.SchedulerUuidV7;
import space.refinex.agentark.scheduling.port.JobHandler;
import space.refinex.agentark.scheduling.port.SchedulerRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证 Worker 的 Retry Budget、无幂等写默认不重试和 Dead Letter 行为。
 *
 * @author refinex
 */
class SchedulerWorkerTest {

    /** 固定测试时间。 */
    private static final Instant NOW = Instant.parse("2026-08-16T10:00:00Z");

    /** 创建 Worker 测试实例。 */
    SchedulerWorkerTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明无幂等声明的写操作即使 Handler 标记可重试也直接进入 Dead Letter。 */
    @Test
    void doesNotRetryWriteWithoutIdempotencyDeclaration() {
        assertFailureTarget(
            IdempotencyCapability.NONE, 1, JobStatus.DEAD_LETTERED, true);
    }

    /** 证明带 Provider 幂等键且预算未耗尽的失败进入 RETRY_WAIT。 */
    @Test
    void retriesIdempotentHandlerWithinBudget() {
        assertFailureTarget(
            IdempotencyCapability.PROVIDER_KEY, 1, JobStatus.RETRY_WAIT, false);
    }

    /** 证明带幂等键的 Handler 达到最大 Attempt 后也必须进入 Dead Letter。 */
    @Test
    void deadLettersIdempotentHandlerWhenRetryBudgetIsExhausted() {
        assertFailureTarget(
            IdempotencyCapability.PROVIDER_KEY, 3, JobStatus.DEAD_LETTERED, true);
    }

    /**
     * 执行一个失败 Claim 并验证目标状态与 Dead Letter 形态。
     *
     * @param idempotency       Handler 幂等能力
     * @param attemptNumber     当前 Attempt 序号
     * @param expectedStatus    预期 Job 目标状态
     * @param expectsDeadLetter 是否预期创建 Dead Letter
     */
    private void assertFailureTarget(
        IdempotencyCapability idempotency,
        int attemptNumber,
        JobStatus expectedStatus,
        boolean expectsDeadLetter) {
        SchedulerRepository repository = mock(SchedulerRepository.class);
        ClaimedJob claim = claim(idempotency, attemptNumber);
        when(repository.claim(
            eq(JobType.OUTBOUND_WEBHOOK), anyString(), eq(NOW), any(), any()))
            .thenReturn(Optional.of(claim));
        JobHandler handler = new FailingHandler(idempotency);
        try (SchedulerWorker worker = new SchedulerWorker(
            repository, List.of(handler), Map.of(JobType.OUTBOUND_WEBHOOK, 1),
            Clock.fixed(NOW, ZoneOffset.UTC), RandomGenerator.getDefault(),
            "scheduler-test-1", Duration.ofSeconds(30))) {
            assertThat(worker.tick(JobType.OUTBOUND_WEBHOOK)).isTrue();

            ArgumentCaptor<JobStatus> status = ArgumentCaptor.forClass(JobStatus.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Optional<DeadLetter>> deadLetter =
                ArgumentCaptor.forClass(Optional.class);
            verify(repository, timeout(2_000)).fail(
                eq(claim), eq(AttemptStatus.FAILED), eq("WRITE_FAILED"), status.capture(),
                any(), deadLetter.capture(), any(), eq(NOW));
            assertThat(status.getValue()).isEqualTo(expectedStatus);
            assertThat(deadLetter.getValue().isPresent()).isEqualTo(expectsDeadLetter);
        }
    }

    /**
     * 创建与指定幂等能力和 Attempt 序号匹配的 Claim。
     *
     * @param idempotency Handler 幂等能力
     * @param attemptNumber 当前 Attempt 序号
     * @return Claim
     */
    private ClaimedJob claim(
        IdempotencyCapability idempotency, int attemptNumber) {
        RetryPolicy retryPolicy = new RetryPolicy(
            3, Duration.ofSeconds(1), Duration.ofSeconds(10), 2.0, 0.0,
            Duration.ofSeconds(5));
        Job job = new Job(
            JobId.generate(), OrganizationId.generate(), ProjectId.generate(),
            JobType.OUTBOUND_WEBHOOK, "worker-fixture", "{}", Checksum.sha256("{}"),
            JobStatus.CLAIMED, 0, NOW, retryPolicy, idempotency,
            attemptNumber, attemptNumber, NOW, NOW);
        return new ClaimedJob(
            job, SchedulerUuidV7.generate(NOW), attemptNumber, "scheduler-test-1",
            attemptNumber,
            NOW.plusSeconds(30));
    }

    /**
     * 返回可重试失败的固定 Handler。
     *
     * @author refinex
     */
    private static final class FailingHandler implements JobHandler {

        /** Handler 幂等能力。 */
        private final IdempotencyCapability idempotency;

        /**
         * 创建固定失败 Handler。
         *
         * @param idempotency 幂等能力
         */
        private FailingHandler(IdempotencyCapability idempotency) {
            this.idempotency = idempotency;
        }

        /** 返回外发 Webhook 类型。 */
        @Override
        public JobType type() {
            return JobType.OUTBOUND_WEBHOOK;
        }

        /** 返回测试指定幂等能力。 */
        @Override
        public IdempotencyCapability idempotencyCapability() {
            return idempotency;
        }

        /** 返回立即完成的可重试失败。 */
        @Override
        public CompletionStage<HandlerResult> handle(ClaimedJob claim) {
            return CompletableFuture.completedFuture(
                HandlerResult.failure("WRITE_FAILED", true));
        }
    }
}
