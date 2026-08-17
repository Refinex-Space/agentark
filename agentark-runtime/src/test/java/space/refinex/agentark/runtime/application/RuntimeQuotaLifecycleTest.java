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
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.application.RuntimeCommands.AcceptTurnCommand;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.port.RuntimeProviderCatalog;
import space.refinex.agentark.runtime.port.RuntimeQuotaPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Runtime 并发配额 Reservation 与接单事务、Run 终态的生命周期一致性。
 *
 * @author refinex
 */
class RuntimeQuotaLifecycleTest {

    /**
     * 创建 Runtime 配额生命周期测试实例。
     */
    RuntimeQuotaLifecycleTest() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /**
     * 证明接单原子绑定 Reservation，Run 成功终态后再幂等释放远端名额。
     */
    @Test
    void bindsReservationToTurnAndReleasesAfterTerminalCommit() {
        RuntimePhase13TestSupport fixture = new RuntimePhase13TestSupport();
        Session session = fixture.createSession("quota-session");
        RecordingQuotaPort quotaPort = new RecordingQuotaPort("quota-reservation-1");
        RuntimeProviderCatalog providerCatalog = () -> new RuntimeProviderMetadata(
            "agentscope-java-2", "compiler-v1", Set.of(1), Set.of("streaming"));
        RuntimeAdmissionService admission = new RuntimeAdmissionService(
            ignored -> {
                throw new AssertionError("acceptTurn must not resolve deployment");
            },
            ignored -> fixture.snapshot,
            providerCatalog,
            fixture.application,
            quotaPort);

        Turn turn = admission.acceptTurn(new AcceptTurnCommand(
            fixture.organizationId,
            fixture.projectId,
            session.id(),
            RuntimePayload.inline("{\"text\":\"quota\"}"),
            Checksum.sha256("quota-input"),
            "agentscope-java-2",
            "compiler-v1",
            10,
            "quota-turn",
            Checksum.sha256("quota-request")));

        assertThat(fixture.store.findQuotaReservation(turn.id()))
            .contains("quota-reservation-1");
        RuntimeExecutionCoordinator coordinator = new RuntimeExecutionCoordinator(
            fixture.store,
            fixture.store,
            fixture.store,
            fixture.store,
            fixture.store,
            fixture.store,
            fixture.notifier,
            quotaPort,
            fixture.clock);
        ClaimedExecution claimed = coordinator.claim("runtime-quota", Duration.ofSeconds(30))
            .orElseThrow();
        coordinator.complete(claimed, new ExecutionResult(
            ExecutionOutcome.SUCCEEDED, Optional.empty(), Optional.empty()));

        assertThat(quotaPort.released()).containsExactly("quota-reservation-1");
    }

    /**
     * 记录测试 Reservation 和释放调用，不访问 Control 网络。
     *
     * @author refinex
     */
    private static final class RecordingQuotaPort implements RuntimeQuotaPort {

        /**
         * 固定 Reservation 引用。
         */
        private final String reservationId;

        /**
         * 已释放 Reservation 顺序。
         */
        private final List<String> released = new ArrayList<>();

        /**
         * 创建记录型配额端口。
         *
         * @param reservationId 固定 Reservation 引用
         */
        private RecordingQuotaPort(String reservationId) {
            this.reservationId = reservationId;
        }

        /**
         * 返回固定允许结果，模拟 Control 原子 Reservation。
         *
         * @param organizationId 组织
         * @param projectId      项目
         * @param idempotencyKey Turn 接单幂等键
         * @param subjectRef     Session 引用
         * @param ttl            Reservation TTL
         * @return 固定允许结果
         */
        @Override
        public Reservation reserveConcurrentRun(
            OrganizationId organizationId,
            ProjectId projectId,
            String idempotencyKey,
            String subjectRef,
            Duration ttl) {
            return new Reservation(
                true, Optional.of(reservationId), Optional.of("STOP"));
        }

        /**
         * 记录终态提交后的释放调用。
         *
         * @param reservationId Reservation 引用
         */
        @Override
        public void release(String reservationId) {
            released.add(reservationId);
        }

        /**
         * 返回不可变释放记录。
         *
         * @return 已释放 Reservation
         */
        private List<String> released() {
            return List.copyOf(released);
        }
    }
}
