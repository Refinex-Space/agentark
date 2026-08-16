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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.adapter.out.fake.FakeAgentExecutionEngine;
import space.refinex.agentark.runtime.adapter.out.fake.InMemoryRuntimeStore;
import space.refinex.agentark.runtime.application.RuntimeCommands.*;
import space.refinex.agentark.runtime.domain.RuntimeConflictException;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 使用 Fake Engine 和内存权威 Store 验证 Runtime 核心事务、恢复、并发和幂等行为。
 *
 * @author refinex
 */
class RuntimeApplicationServiceTest {

    /** 固定测试时刻。 */
    private static final Instant NOW = Instant.parse("2026-08-16T06:00:00Z");

    /** Snapshot 内容 Hash。 */
    private static final Checksum SNAPSHOT_HASH = Checksum.sha256("snapshot-v1");

    /** 组织标识。 */
    private OrganizationId organizationId;

    /** 项目标识。 */
    private ProjectId projectId;

    /** Deployment 标识。 */
    private DeploymentId deploymentId;

    /** Revision 标识。 */
    private RevisionId revisionId;

    /** Snapshot 标识。 */
    private SnapshotId snapshotId;

    /** 内存 Runtime Store。 */
    private InMemoryRuntimeStore store;

    /** 返回预设结果的伪执行引擎。 */
    private FakeAgentExecutionEngine engine;

    /** 被测应用服务。 */
    private RuntimeApplicationService service;

    /** 创建 Runtime 应用服务测试实例。 */
    RuntimeApplicationServiceTest() {
    }

    /** 为每项测试创建隔离的租户、Snapshot、Store 和 Engine。 */
    @BeforeEach
    void setUp() {
        organizationId = OrganizationId.generate();
        projectId = ProjectId.generate();
        deploymentId = DeploymentId.generate();
        revisionId = RevisionId.generate();
        snapshotId = SnapshotId.generate();
        store = new InMemoryRuntimeStore();
        engine = new FakeAgentExecutionEngine();
        service = new RuntimeApplicationService(
            store, store, store, store,
            ignored -> new SnapshotDescriptor(
                revisionId, snapshotId, SNAPSHOT_HASH, 1,
                "agentscope-java-2", "{\"schemaVersion\":1}"),
            engine, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** 证明 Session 创建后固定 Revision/Snapshot，成功执行产生有序 Event 与 Outbox。 */
    @Test
    void fixesSnapshotAndCompletesSuccessfulTurn() {
        Session session = createSession("session-success", Checksum.sha256("session-success"));
        Turn turn = acceptTurn(session, "turn-success", Checksum.sha256("turn-success"));

        ExecutionResult result = service.executeNext(
            new ExecuteNextCommand("runtime-1", Duration.ofSeconds(30))).orElseThrow();

        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.SUCCEEDED);
        assertThat(store.findSession(session.id()).orElseThrow().revisionId()).isEqualTo(revisionId);
        assertThat(store.findSession(session.id()).orElseThrow().snapshotId()).isEqualTo(snapshotId);
        assertThat(store.findTurn(turn.id()).orElseThrow().status()).isEqualTo(TurnStatus.COMPLETED);
        assertThat(store.listAfter(session.id(), 0, 100))
            .extracting(RuntimeEvent::sessionSequence)
            .containsExactly(1L, 2L, 3L, 4L);
        assertThat(store.outboxCount()).isEqualTo(2);
    }

    /** 证明相同幂等请求返回原资源，同 Key 不同 Hash 显式冲突。 */
    @Test
    void enforcesIdempotencyRequestHash() {
        Checksum requestHash = Checksum.sha256("same-request");
        Session first = createSession("same-key", requestHash);
        Session replay = createSession("same-key", requestHash);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThatThrownBy(() -> createSession("same-key", Checksum.sha256("different-request")))
            .isInstanceOf(RuntimeConflictException.class)
            .hasMessageContaining("another request");
    }

    /** 证明失败 Turn 的显式重试创建新 Run Attempt，不覆盖旧 Run。 */
    @Test
    void createsNewRunAttemptWhenRetryingFailure() {
        engine.enqueue(new ExecutionResult(
            ExecutionOutcome.FAILED, Optional.of("MODEL_UNAVAILABLE"), Optional.empty()));
        Session session = createSession("session-failure", Checksum.sha256("session-failure"));
        Turn turn = acceptTurn(session, "turn-failure", Checksum.sha256("turn-failure"));
        RunId firstRunId = turn.currentRunId().orElseThrow();
        service.executeNext(new ExecuteNextCommand("runtime-1", Duration.ofSeconds(30)));

        Run retry = service.retryTurn(new RetryTurnCommand(
            turn.id(), "agentscope-java-2", "compiler-v1", 10));

        assertThat(retry.attemptNumber()).isEqualTo(2);
        assertThat(retry.id()).isNotEqualTo(firstRunId);
        assertThat(store.runCount(turn.id())).isEqualTo(2);
        assertThat(store.findRun(firstRunId)).isPresent();
    }

    /** 证明 PAUSED Run 只有在 Approval 批准且参数 Hash 未变化时才能恢复成功。 */
    @Test
    void pausesAndResumesWithApprovedArgumentHash() {
        engine.enqueue(new ExecutionResult(
            ExecutionOutcome.PAUSED, Optional.empty(), Optional.of("approval-required")));
        engine.enqueue(new ExecutionResult(
            ExecutionOutcome.SUCCEEDED, Optional.empty(), Optional.empty()));
        Session session = createSession("session-pause", Checksum.sha256("session-pause"));
        Turn turn = acceptTurn(session, "turn-pause", Checksum.sha256("turn-pause"));
        service.executeNext(new ExecuteNextCommand("runtime-1", Duration.ofSeconds(30)));
        Run paused = store.findRun(turn.currentRunId().orElseThrow()).orElseThrow();
        Checksum argumentHash = Checksum.sha256("tool-arguments");
        Approval approval = service.requestApproval(
            paused.id(), "filesystem.read", "READ", argumentHash, "policy-v1",
            Duration.ofMinutes(5));
        assertThat(store.decide(
            approval.id(), 0, ApprovalStatus.APPROVED, "user:test", NOW)).isEqualTo(1);

        ExecutionResult resumed = service.resume(new ResumeCommand(
            paused.id(), approval.id(), argumentHash, paused.fencingToken()));

        assertThat(resumed.outcome()).isEqualTo(ExecutionOutcome.SUCCEEDED);
        assertThat(store.findRun(paused.id()).orElseThrow().status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(store.findTurn(turn.id()).orElseThrow().status()).isEqualTo(TurnStatus.COMPLETED);
    }

    /** 证明取消 PAUSED Run 会先追加取消事实并通知 Fake Engine。 */
    @Test
    void cancelsPausedRunAndRecordsEngineCommand() {
        engine.enqueue(new ExecutionResult(
            ExecutionOutcome.PAUSED, Optional.empty(), Optional.of("manual-pause")));
        Session session = createSession("session-cancel", Checksum.sha256("session-cancel"));
        Turn turn = acceptTurn(session, "turn-cancel", Checksum.sha256("turn-cancel"));
        service.executeNext(new ExecuteNextCommand("runtime-1", Duration.ofSeconds(30)));
        Run paused = store.findRun(turn.currentRunId().orElseThrow()).orElseThrow();
        CancellationCommand command = new CancellationCommand(
            turn.id(), paused.id(), paused.fencingToken(), "USER_CANCELLED");

        service.cancel(command);

        assertThat(store.findRun(paused.id()).orElseThrow().status()).isEqualTo(RunStatus.CANCELLED);
        assertThat(store.findTurn(turn.id()).orElseThrow().status()).isEqualTo(TurnStatus.CANCELLED);
        assertThat(engine.lastCancellation()).isEqualTo(command);
        assertThat(store.listAfter(session.id(), 0, 100).getLast().type())
            .isEqualTo("run.cancelled");
    }

    /** 证明并发 Event 追加在 Session 和 Run 两个维度均保持唯一单调。 */
    @Test
    void allocatesMonotonicEventSequencesConcurrently() throws Exception {
        engine.enqueue(new ExecutionResult(
            ExecutionOutcome.PAUSED, Optional.empty(), Optional.of("hold")));
        Session session = createSession("session-events", Checksum.sha256("session-events"));
        Turn turn = acceptTurn(session, "turn-events", Checksum.sha256("turn-events"));
        service.executeNext(new ExecuteNextCommand("runtime-1", Duration.ofMinutes(1)));
        Run run = store.findRun(turn.currentRunId().orElseThrow()).orElseThrow();
        int additions = 24;
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < additions; index++) {
                futures.add(executor.submit(() -> store.append(
                    EventId.generate(), organizationId, projectId, session.id(), turn.id(), run.id(),
                    "run.observed", 1, run.id().asString().replace("-", ""),
                    RuntimePayload.inline("{}"), NOW, run.fencingToken())));
            }
            for (var future : futures) {
                future.get();
            }
        }

        List<RuntimeEvent> events = store.listAfter(session.id(), 0, 100);
        assertThat(events).hasSize(4 + additions);
        assertThat(events).extracting(RuntimeEvent::sessionSequence).doesNotHaveDuplicates();
        assertThat(events).extracting(RuntimeEvent::runSequence).doesNotHaveDuplicates();
        assertThat(events.getLast().sessionSequence()).isEqualTo(4L + additions);
    }

    /** 证明 Lease 过期后旧 Owner 和旧 Fencing Token 均被拒绝。 */
    @Test
    void rejectsExpiredLeaseOwner() {
        Session session = createSession("session-lease", Checksum.sha256("session-lease"));
        Turn turn = acceptTurn(session, "turn-lease", Checksum.sha256("turn-lease"));
        RuntimeWorkItem claimed = store.claimNext(
            "runtime-old", NOW, Duration.ofSeconds(1)).orElseThrow();
        store.assignFencingToken(claimed.runId(), turn.id(), claimed.fencingToken());

        assertThatThrownBy(() -> store.requireCurrent(
            claimed.runId(), "runtime-old", claimed.fencingToken(), NOW.plusSeconds(2)))
            .isInstanceOf(RuntimeConflictException.class);
        RuntimeWorkItem reclaimed = store.claimNext(
            "runtime-new", NOW.plusSeconds(2), Duration.ofSeconds(30)).orElseThrow();
        assertThat(reclaimed.fencingToken().value()).isGreaterThan(claimed.fencingToken().value());
        assertThatThrownBy(() -> store.assignFencingToken(
            claimed.runId(), turn.id(), claimed.fencingToken()))
            .isInstanceOf(RuntimeConflictException.class);
    }

    /**
     * 创建测试 Session。
     *
     * @param key         幂等键
     * @param requestHash 请求 Hash
     * @return Session
     */
    private Session createSession(String key, Checksum requestHash) {
        return service.createSession(new CreateSessionCommand(
            organizationId, projectId, deploymentId, revisionId, snapshotId, SNAPSHOT_HASH,
            Map.of("actor", "test"), Map.of("channel", "api"), key, requestHash));
    }

    /**
     * 创建测试 Turn。
     *
     * @param session     所属 Session
     * @param key         幂等键
     * @param requestHash 请求 Hash
     * @return QUEUED Turn
     */
    private Turn acceptTurn(Session session, String key, Checksum requestHash) {
        return service.acceptTurn(new AcceptTurnCommand(
            organizationId, projectId, session.id(), RuntimePayload.inline("{\"text\":\"hello\"}"),
            Checksum.sha256("hello"), "agentscope-java-2", "compiler-v1", 10, key, requestHash));
    }
}
