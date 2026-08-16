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
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.application.RuntimeCommands.DecideApprovalCommand;
import space.refinex.agentark.runtime.domain.RuntimeConflictException;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 HITL 参数 Hash、决策幂等、到期和新 Lease Resume 语义。
 *
 * @author refinex
 */
class RuntimeApprovalTest {

    /**
     * 证明同一审批决策可幂等重放，并在新 Token 下恢复同一 Run。
     */
    @Test
    void decidesIdempotentlyAndResumesWithNewFencingToken() {
        RuntimePhase13TestSupport fixture = new RuntimePhase13TestSupport();
        fixture.acceptTurn(fixture.createSession("approval-session"), "approval-turn");
        ClaimedExecution claimed = fixture.coordinator.claim(
            "runtime-a", Duration.ofSeconds(30)).orElseThrow();
        fixture.checkpoint(claimed);
        Checksum argumentHash = Checksum.sha256("tool-arguments");
        Approval approval = fixture.application.requestApproval(
            claimed.run().id(), "repository.read", "TOOL_EXECUTE:tool-1",
            argumentHash, "policy-v1", Duration.ofMinutes(5));
        fixture.coordinator.complete(claimed, new ExecutionResult(
            ExecutionOutcome.PAUSED, java.util.Optional.empty(),
            java.util.Optional.of("approval-required")));
        int eventCountBeforeDecision = fixture.store.eventCount();
        int outboxCountBeforeDecision = fixture.store.outboxCount();
        DecideApprovalCommand command = new DecideApprovalCommand(
            approval.id(), 0, ApprovalStatus.APPROVED, "user:reviewer", "decision-1",
            Checksum.sha256("approve"));

        Approval first = fixture.coordinator.decideApproval(command);
        Approval replay = fixture.coordinator.decideApproval(command);
        ClaimedExecution resumed = fixture.coordinator.claim(
            "runtime-b", Duration.ofSeconds(30)).orElseThrow();

        assertThat(first.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(replay).isEqualTo(first);
        assertThat(fixture.store.eventCount()).isEqualTo(eventCountBeforeDecision + 2);
        assertThat(fixture.store.outboxCount()).isEqualTo(outboxCountBeforeDecision + 1);
        assertThat(resumed.mode()).isEqualTo(ExecutionMode.RESUME);
        assertThat(resumed.decisions()).containsExactly(new ApprovalDecision(
            approval.id(), "tool-1", argumentHash, true));
        assertThat(resumed.run().fencingToken().value())
            .isGreaterThan(claimed.run().fencingToken().value());
        assertThatThrownBy(() -> fixture.coordinator.decideApproval(
            new DecideApprovalCommand(
                approval.id(), 0, ApprovalStatus.APPROVED, "user:reviewer", "decision-1",
                Checksum.sha256("different"))))
            .isInstanceOf(RuntimeConflictException.class);
    }

    /**
     * 证明到期 Approval 不能被批准，并持久化 EXPIRED 终态。
     */
    @Test
    void expiresApprovalBeforeLateDecision() {
        RuntimePhase13TestSupport fixture = new RuntimePhase13TestSupport();
        fixture.acceptTurn(fixture.createSession("expiry-session"), "expiry-turn");
        ClaimedExecution claimed = fixture.coordinator.claim(
            "runtime-a", Duration.ofMinutes(2)).orElseThrow();
        Approval approval = fixture.application.requestApproval(
            claimed.run().id(), "repository.write", "TOOL_EXECUTE:tool-2",
            Checksum.sha256("write-arguments"), "policy-v1", Duration.ofSeconds(30));
        fixture.checkpoint(claimed);
        fixture.coordinator.complete(claimed, new ExecutionResult(
            ExecutionOutcome.PAUSED, java.util.Optional.empty(),
            java.util.Optional.of("approval-required")));
        int eventCountBeforeDecision = fixture.store.eventCount();
        int outboxCountBeforeDecision = fixture.store.outboxCount();
        fixture.clock.advance(Duration.ofSeconds(31));

        Approval expired = fixture.coordinator.decideApproval(new DecideApprovalCommand(
            approval.id(), 0, ApprovalStatus.APPROVED, "user:reviewer", "late-decision",
            Checksum.sha256("late")));

        assertThat(expired.status()).isEqualTo(ApprovalStatus.EXPIRED);
        assertThat(fixture.store.find(approval.id()).orElseThrow()).isEqualTo(expired);
        assertThat(fixture.store.eventCount()).isEqualTo(eventCountBeforeDecision + 1);
        assertThat(fixture.store.outboxCount()).isEqualTo(outboxCountBeforeDecision + 1);
        ClaimedExecution resumed = fixture.coordinator.claim(
            "runtime-b", Duration.ofMinutes(2)).orElseThrow();
        assertThat(resumed.mode()).isEqualTo(ExecutionMode.RESUME);
        assertThat(resumed.decisions()).containsExactly(new ApprovalDecision(
            approval.id(), "tool-2", approval.argumentHash(), false));
    }
}
