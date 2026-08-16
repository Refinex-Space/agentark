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
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.port.ExecutionLeaseCoordinator;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证过期 Owner 的 Checkpoint 恢复、不可恢复新 Attempt 和接单后准备失败可查询性。
 *
 * @author refinex
 */
class RuntimeRecoveryTest {

    /**
     * 证明有 Checkpoint 的孤儿 Run 由新 Fencing Token 在同一 Attempt 恢复。
     */
    @Test
    void recoversOrphanFromCheckpointWithNewOwner() {
        RuntimePhase13TestSupport fixture = new RuntimePhase13TestSupport();
        Turn turn = fixture.acceptTurn(
            fixture.createSession("recover-session"), "recover-turn");
        ClaimedExecution first = fixture.coordinator.claim(
            "runtime-a", Duration.ofSeconds(10)).orElseThrow();
        Checkpoint checkpoint = fixture.checkpoint(first);

        fixture.clock.advance(Duration.ofSeconds(11));
        ClaimedExecution recovered = fixture.coordinator.claim(
            "runtime-b", Duration.ofSeconds(10)).orElseThrow();

        assertThat(recovered.mode()).isEqualTo(ExecutionMode.RECOVER);
        assertThat(recovered.checkpoint()).contains(checkpoint);
        assertThat(recovered.run().id()).isEqualTo(first.run().id());
        assertThat(recovered.run().fencingToken().value())
            .isGreaterThan(first.run().fencingToken().value());
        assertThat(fixture.store.findTurn(turn.id()).orElseThrow().currentRunId())
            .contains(first.run().id());
    }

    /**
     * 证明无 Checkpoint 的孤儿 Run 进入 ABANDONED 并追加新 Attempt。
     */
    @Test
    void replacesNonRecoverableOrphanWithNewAttempt() {
        RuntimePhase13TestSupport fixture = new RuntimePhase13TestSupport();
        Turn turn = fixture.acceptTurn(
            fixture.createSession("replace-session"), "replace-turn");
        ClaimedExecution first = fixture.coordinator.claim(
            "runtime-a", Duration.ofSeconds(10)).orElseThrow();

        fixture.clock.advance(Duration.ofSeconds(11));
        assertThat(fixture.coordinator.claim("runtime-b", Duration.ofSeconds(10))).isEmpty();

        Run abandoned = fixture.store.findRun(first.run().id()).orElseThrow();
        Turn redirected = fixture.store.findTurn(turn.id()).orElseThrow();
        assertThat(abandoned.status()).isEqualTo(RunStatus.ABANDONED);
        assertThat(redirected.currentRunId()).isNotEqualTo(Optional.of(abandoned.id()));
        assertThat(fixture.store.runCount(turn.id())).isEqualTo(2);
    }

    /**
     * 证明 202 接单后的 Snapshot 加载失败会保留 RunId 并形成明确 FAILED 终态。
     */
    @Test
    void persistsFailureWhenSnapshotPreparationFailsAfterAdmission() {
        RuntimePhase13TestSupport fixture = new RuntimePhase13TestSupport();
        Turn turn = fixture.acceptTurn(
            fixture.createSession("failure-session"), "failure-turn");
        ExecutionLeaseCoordinator leaseCoordinator = (workItem, ttl) -> Optional.of(
            new AlwaysCurrentLease());
        RuntimeWorker worker = new RuntimeWorker(
            "runtime-a", Duration.ofSeconds(10), fixture.coordinator, leaseCoordinator,
            ignored -> {
                throw new IllegalStateException("control unavailable");
            }, fixture.engine);

        ExecutionResult result = worker.runOnce().orElseThrow();
        Run run = fixture.store.findRun(turn.currentRunId().orElseThrow()).orElseThrow();

        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.FAILED);
        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.errorCode()).contains("RUNTIME_PREPARATION_FAILED");
        assertThat(fixture.store.listRunAfter(run.id(), 0, 100))
            .extracting(RuntimeEvent::type)
            .contains("run.failed");
    }

    /**
     * 提供不访问外部基础设施的当前 Lease。
     *
     * @author refinex
     */
    private static final class AlwaysCurrentLease
        implements ExecutionLeaseCoordinator.ExecutionLease {

        /**
         * 测试 Lease 始终有效。
         */
        @Override
        public void requireCurrent() {
        }

        /**
         * 返回有效状态。
         *
         * @return 始终为 true
         */
        @Override
        public boolean active() {
            return true;
        }

        /**
         * 测试 Lease 无外部资源需要释放。
         */
        @Override
        public void close() {
        }
    }
}
