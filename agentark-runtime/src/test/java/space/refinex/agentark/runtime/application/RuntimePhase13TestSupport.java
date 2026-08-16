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

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.adapter.out.fake.FakeAgentExecutionEngine;
import space.refinex.agentark.runtime.adapter.out.fake.InMemoryRuntimeStore;
import space.refinex.agentark.runtime.adapter.out.event.InMemoryRuntimeEventNotifier;
import space.refinex.agentark.runtime.application.RuntimeCommands.AcceptTurnCommand;
import space.refinex.agentark.runtime.application.RuntimeCommands.CreateSessionCommand;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;

import java.time.*;
import java.util.Map;

/**
 * 为 Phase 13 并发、恢复、SSE 与 Approval 测试提供隔离的 Runtime Fixture。
 *
 * @author refinex
 */
final class RuntimePhase13TestSupport {

    /**
     * 初始测试时刻。
     */
    static final Instant INITIAL_TIME = Instant.parse("2026-08-16T08:00:00Z");

    /**
     * 测试组织。
     */
    final OrganizationId organizationId = OrganizationId.generate();

    /**
     * 测试项目。
     */
    final ProjectId projectId = ProjectId.generate();

    /**
     * 测试 Deployment。
     */
    final DeploymentId deploymentId = DeploymentId.generate();

    /**
     * 固定 Revision。
     */
    final RevisionId revisionId = RevisionId.generate();

    /**
     * 固定 Snapshot。
     */
    final SnapshotId snapshotId = SnapshotId.generate();

    /**
     * 固定 Snapshot Hash。
     */
    final Checksum snapshotHash = Checksum.sha256("phase-13-snapshot");

    /**
     * 可推进测试时钟。
     */
    final MutableClock clock = new MutableClock(INITIAL_TIME);

    /**
     * 线程安全内存权威 Store。
     */
    final InMemoryRuntimeStore store = new InMemoryRuntimeStore();

    /**
     * 可丢失实时通知器。
     */
    final InMemoryRuntimeEventNotifier notifier = new InMemoryRuntimeEventNotifier();

    /**
     * 用于状态机验证的虚拟 Provider 执行引擎。
     */
    final FakeAgentExecutionEngine engine = new FakeAgentExecutionEngine();

    /**
     * 固定 Snapshot 描述。
     */
    final SnapshotDescriptor snapshot = new SnapshotDescriptor(
        revisionId, snapshotId, snapshotHash, 1,
        "agentscope-java-2", "{\"schemaVersion\":1}");

    /**
     * Runtime 权威接单服务。
     */
    final RuntimeApplicationService application = new RuntimeApplicationService(
        store, store, store, store, ignored -> snapshot, engine, clock);

    /**
     * Phase 13 短事务执行协调器。
     */
    final RuntimeExecutionCoordinator coordinator = new RuntimeExecutionCoordinator(
        store, store, store, store, store, store, notifier, clock);

    /**
     * 创建固定 Snapshot 的测试 Session。
     *
     * @param key 幂等键
     * @return Session
     */
    Session createSession(String key) {
        return application.createSession(new CreateSessionCommand(
            organizationId, projectId, deploymentId, revisionId, snapshotId, snapshotHash,
            Map.of("actor", "phase-13-test"), Map.of("channel", "api"), key,
            Checksum.sha256("session:" + key)));
    }

    /**
     * 为 Session 原子接收一个 Turn。
     *
     * @param session Session
     * @param key     幂等键
     * @return 已排队 Turn
     */
    Turn acceptTurn(Session session, String key) {
        return application.acceptTurn(new AcceptTurnCommand(
            organizationId, projectId, session.id(),
            RuntimePayload.inline("{\"text\":\"hello\"}"), Checksum.sha256("hello"),
            "agentscope-java-2", "compiler-v1", 10, key,
            Checksum.sha256("turn:" + key)));
    }

    /**
     * 写入当前 Run 可恢复的已提交 State 与 Checkpoint。
     *
     * @param claimed 已 Claim 的执行上下文
     * @return 可恢复 Checkpoint
     */
    Checkpoint checkpoint(ClaimedExecution claimed) {
        JobId stateId = JobId.generate();
        AgentStateVersion state = new AgentStateVersion(
            stateId, organizationId, projectId, claimed.session().id(), claimed.run().id(),
            "main", "agent_state", 0, 1, RuntimePayload.inline("{\"state\":\"ready\"}"),
            Checksum.sha256("state"), false, claimed.run().fencingToken(), clock.instant());
        store.append(state);
        store.commit(state, claimed.run().fencingToken());
        Checkpoint checkpoint = new Checkpoint(
            JobId.generate(), claimed.run().id(), 1, stateId, 1,
            claimed.run().eventSequence(), Checksum.sha256("checkpoint"), true,
            claimed.run().fencingToken(), clock.instant());
        store.append(checkpoint);
        return checkpoint;
    }

    /**
     * 提供可由测试显式推进的 UTC Clock。
     *
     * @author refinex
     */
    static final class MutableClock extends Clock {

        /**
         * 当前时刻。
         */
        private Instant current;

        /**
         * 创建可变 UTC 时钟。
         *
         * @param initial 初始时刻
         */
        MutableClock(Instant initial) {
            this.current = initial;
        }

        /**
         * 推进当前时刻。
         *
         * @param duration 推进时长
         */
        void advance(Duration duration) {
            current = current.plus(duration);
        }

        /**
         * 返回 UTC 时区。
         *
         * @return UTC
         */
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /**
         * 返回具有相同时刻的新时区 Clock；测试统一固定为 UTC。
         *
         * @param zone 目标时区
         * @return 当前 Clock
         */
        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("phase 13 test clock only supports UTC");
            }
            return this;
        }

        /**
         * 返回当前测试时刻。
         *
         * @return 当前时刻
         */
        @Override
        public Instant instant() {
            return current;
        }
    }
}
