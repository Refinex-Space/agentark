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

package space.refinex.agentark.runtime.domain;

import org.junit.jupiter.api.Test;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.RunId;
import space.refinex.agentark.runtime.domain.RuntimeModels.ApprovalStatus;
import space.refinex.agentark.runtime.domain.RuntimeModels.FencingToken;
import space.refinex.agentark.runtime.domain.RuntimeModels.RunStatus;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimeWorkItem;
import space.refinex.agentark.runtime.domain.RuntimeModels.TurnStatus;
import space.refinex.agentark.runtime.domain.RuntimeModels.WorkItemStatus;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Runtime 三套状态机拒绝跳步、终态重开和非失败重试。
 *
 * @author refinex
 */
class RuntimeStateMachineTest {

    /** 创建状态机测试实例。 */
    RuntimeStateMachineTest() {
    }

    /** 证明 Turn、Run 与 Approval 的正常路径可执行。 */
    @Test
    void acceptsDocumentedTransitions() {
        assertThatCode(() -> RuntimeStateMachine.requireTurnTransition(
            TurnStatus.RUNNING, TurnStatus.WAITING_APPROVAL)).doesNotThrowAnyException();
        assertThatCode(() -> RuntimeStateMachine.requireRunTransition(
            RunStatus.PAUSED, RunStatus.RUNNING)).doesNotThrowAnyException();
        assertThatCode(() -> RuntimeStateMachine.requireApprovalTransition(
            ApprovalStatus.PENDING, ApprovalStatus.APPROVED)).doesNotThrowAnyException();
        assertThatCode(() -> RuntimeStateMachine.requireRetryable(TurnStatus.FAILED))
            .doesNotThrowAnyException();
    }

    /** 证明不能跳过 Claim、恢复已成功 Run 或重试正常 Turn。 */
    @Test
    void rejectsIllegalTransitionsAndRetry() {
        assertThatThrownBy(() -> RuntimeStateMachine.requireRunTransition(
            RunStatus.CREATED, RunStatus.SUCCEEDED))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RuntimeStateMachine.requireRunTransition(
            RunStatus.SUCCEEDED, RunStatus.RUNNING))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RuntimeStateMachine.requireRetryable(TurnStatus.COMPLETED))
            .isInstanceOf(IllegalStateException.class);
    }

    /** 证明已 Claim Work Item 可形成显式 Lease，零令牌不能伪装为有效 Owner。 */
    @Test
    void projectsClaimedWorkItemToValidatedLease() {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        RuntimeWorkItem claimed = new RuntimeWorkItem(
            JobId.generate(), RunId.generate(), WorkItemStatus.CLAIMED, 0, now,
            Optional.of("runtime-1"), Optional.of(now.plusSeconds(30)),
            new FencingToken(1), 1, now);

        assertThat(claimed.currentLease()).hasValueSatisfying(lease -> {
            assertThat(lease.owner()).isEqualTo("runtime-1");
            assertThat(lease.isActiveAt(now.plusSeconds(29))).isTrue();
            assertThat(lease.isActiveAt(now.plusSeconds(30))).isFalse();
        });
        assertThatThrownBy(() -> new RuntimeWorkItem(
            JobId.generate(), RunId.generate(), WorkItemStatus.CLAIMED, 0, now,
            Optional.of("runtime-1"), Optional.of(now.plusSeconds(30)),
            FencingToken.unclaimed(), 1, now))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
