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

import org.junit.jupiter.api.Test;
import space.refinex.agentark.scheduling.domain.SchedulerModels.JobStatus;
import space.refinex.agentark.scheduling.domain.SchedulerModels.RetryPolicy;

import java.time.Duration;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Scheduler Job 状态机、Retry Budget 与有界退避不变量。
 *
 * @author refinex
 */
class SchedulerStateMachineTest {

    /** 创建状态机测试实例。 */
    SchedulerStateMachineTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明 Dead Letter 只能通过显式授权 Redrive 回到 READY。 */
    @Test
    void requiresExplicitAuthorizationForRedrive() {
        SchedulerStateMachine machine = new SchedulerStateMachine();

        assertThatThrownBy(() -> machine.requireTransition(
            JobStatus.DEAD_LETTERED, JobStatus.READY, false))
            .isInstanceOf(IllegalStateException.class);
        machine.requireTransition(JobStatus.DEAD_LETTERED, JobStatus.READY, true);
    }

    /** 证明终态不能再次 Claim，防止同一副作用不受控重复。 */
    @Test
    void rejectsClaimAfterSuccess() {
        assertThatThrownBy(() -> new SchedulerStateMachine().requireTransition(
            JobStatus.SUCCEEDED, JobStatus.CLAIMED, false))
            .isInstanceOf(IllegalStateException.class);
    }

    /** 证明指数退避达到上限后仍施加有界 Jitter，不突破最大等待时间。 */
    @Test
    void capsExponentialBackoffAndJitter() {
        RetryPolicy policy = new RetryPolicy(
            5, Duration.ofSeconds(10), Duration.ofMinutes(1), 3.0, 0.5,
            Duration.ofMinutes(2));
        RandomGenerator maximumJitter = new FixedRandom(1.0);

        assertThat(policy.delayAfter(5, maximumJitter)).isEqualTo(Duration.ofMinutes(1));
    }

    /**
     * 提供可预测的 Jitter 随机值。
     *
     * @param value 固定随机值
     * @author refinex
     */
    private record FixedRandom(double value) implements RandomGenerator {

        /** 返回固定随机值。 */
        @Override
        public long nextLong() {
            return Double.doubleToLongBits(value);
        }

        /** 返回固定随机值。 */
        @Override
        public double nextDouble() {
            return value;
        }
    }
}
