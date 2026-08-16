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

import org.springframework.transaction.annotation.Transactional;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.application.RuntimeCommands.*;
import space.refinex.agentark.runtime.domain.RuntimeConflictException;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.domain.RuntimeNotFoundException;
import space.refinex.agentark.runtime.domain.RuntimeStateMachine;
import space.refinex.agentark.runtime.port.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 编排 Runtime 权威事务、幂等、队列 Claim、Fencing、Event-first 状态变化和 Fake Engine。
 *
 * @author refinex
 */
public class RuntimeApplicationService {

    /**
     * Session 幂等记录作用域。
     */
    private static final String SESSION_SCOPE = "SESSION_CREATE";

    /**
     * Turn 幂等记录作用域。
     */
    private static final String TURN_SCOPE = "TURN_ACCEPT";

    /**
     * 默认幂等记录保留期。
     */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(1);

    /**
     * Runtime 聚合持久化端口。
     */
    private final RuntimeRepository repository;

    /**
     * 追加式 Event Store。
     */
    private final RuntimeEventStore eventStore;

    /**
     * 持久 Work Queue。
     */
    private final RuntimeWorkQueue workQueue;

    /**
     * Approval 持久化端口。
     */
    private final ApprovalRepository approvalRepository;

    /**
     * 从 Control 内部契约加载固定快照的端口。
     */
    private final SnapshotLoader snapshotLoader;

    /**
     * Provider 中立执行引擎。
     */
    private final AgentExecutionEngine executionEngine;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 创建 Runtime 应用服务。
     *
     * @param repository         Runtime 聚合仓储
     * @param eventStore         Event Store
     * @param workQueue          持久 Work Queue
     * @param approvalRepository Approval 仓储
     * @param snapshotLoader     Snapshot Loader
     * @param executionEngine    Provider 中立执行引擎
     * @param clock              UTC 时钟
     */
    public RuntimeApplicationService(
        RuntimeRepository repository,
        RuntimeEventStore eventStore,
        RuntimeWorkQueue workQueue,
        ApprovalRepository approvalRepository,
        SnapshotLoader snapshotLoader,
        AgentExecutionEngine executionEngine,
        Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
        this.workQueue = Objects.requireNonNull(workQueue, "workQueue must not be null");
        this.approvalRepository = Objects.requireNonNull(
            approvalRepository, "approvalRepository must not be null");
        this.snapshotLoader = Objects.requireNonNull(
            snapshotLoader, "snapshotLoader must not be null");
        this.executionEngine = Objects.requireNonNull(
            executionEngine, "executionEngine must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建固定 Deployment、Revision、Snapshot 与 Hash 的 Session；幂等重放返回原 Session。
     *
     * @param command 创建命令
     * @return 新建或幂等复用的 Session
     * @throws RuntimeConflictException 同 Key 不同请求 Hash 时抛出
     */
    public Session createSession(CreateSessionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return createSession(command, snapshotLoader.load(command.revisionId()));
    }

    /**
     * 使用事务外已解析并校验的 Snapshot 原子创建 Session；幂等重放返回原 Session。
     *
     * @param command  创建命令
     * @param snapshot 已解析固定 Snapshot
     * @return 新建或幂等复用的 Session
     */
    @Transactional
    public Session createSession(
        CreateSessionCommand command, SnapshotDescriptor snapshot) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        String scopeId = command.projectId().asString();
        Optional<IdempotencyRecord> existing = repository.findIdempotency(
            SESSION_SCOPE, scopeId, command.idempotencyKey());
        if (existing.isPresent()) {
            return replaySession(existing.orElseThrow(), command.requestHash());
        }
        if (!snapshot.snapshotId().equals(command.snapshotId())
            || !snapshot.contentHash().equals(command.snapshotHash())) {
            throw new RuntimeConflictException("session snapshot identity or hash changed");
        }
        Instant now = Instant.now(clock);
        Session session = new Session(
            SessionId.generate(), command.organizationId(), command.projectId(),
            command.deploymentId(), command.revisionId(), command.snapshotId(),
            command.snapshotHash(), command.participantMetadata(), command.channelMetadata(),
            SessionStatus.ACTIVE, 0, 0, now, now);
        IdempotencyRecord idempotency = completedIdempotency(
            SESSION_SCOPE, scopeId, command.idempotencyKey(), command.requestHash(),
            "session:" + session.id().asString(), now);
        repository.insertSession(session, idempotency);
        return session;
    }

    /**
     * 原子接收 Turn 并创建首个 Run、持久 Work Item、Event、Outbox 和幂等结果。
     *
     * @param command Turn 接收命令
     * @return 已进入 QUEUED 的 Turn
     */
    @Transactional
    public Turn acceptTurn(AcceptTurnCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String scopeId = command.sessionId().asString();
        Optional<IdempotencyRecord> existing = repository.findIdempotency(
            TURN_SCOPE, scopeId, command.idempotencyKey());
        if (existing.isPresent()) {
            return replayTurn(existing.orElseThrow(), command.requestHash());
        }
        Session session = requireSession(command.sessionId());
        if (!session.organizationId().equals(command.organizationId())
            || !session.projectId().equals(command.projectId())
            || session.status() != SessionStatus.ACTIVE) {
            throw new RuntimeNotFoundException("session is not available");
        }
        long sequence = repository.nextTurnSequence(session.id());
        Instant now = Instant.now(clock);
        TurnId turnId = TurnId.generate();
        RunId runId = RunId.generate();
        Turn turn = new Turn(
            turnId, session.organizationId(), session.projectId(), session.id(), sequence,
            command.input(), command.inputHash(), TurnStatus.ACCEPTED, Optional.of(runId),
            FencingToken.unclaimed(), 0, now, now);
        Run run = new Run(
            runId, session.organizationId(), session.projectId(), session.id(), turnId, 1,
            command.runtimeProvider(), command.compilerVersion(), RunStatus.CREATED, 0,
            FencingToken.unclaimed(), Optional.empty(), Optional.empty(), Optional.empty(), now);
        RuntimeWorkItem workItem = new RuntimeWorkItem(
            JobId.generate(), runId, WorkItemStatus.READY, command.priority(), now,
            Optional.empty(), Optional.empty(), FencingToken.unclaimed(), 0, now);
        RuntimeOutboxEvent outbox = outbox(
            "turn", turnId.asString(), "run.accepted",
            "{\"turnId\":\"" + turnId.asString() + "\",\"runId\":\""
                + runId.asString() + "\"}", now);
        IdempotencyRecord idempotency = completedIdempotency(
            TURN_SCOPE, scopeId, command.idempotencyKey(), command.requestHash(),
            "turn:" + turnId.asString(), now);
        repository.insertAcceptedTurn(
            turn, run, workItem, EventId.generate(), now, outbox, idempotency);
        requireUpdated(repository.transitionTurn(
            turnId, TurnStatus.ACCEPTED, TurnStatus.QUEUED,
            FencingToken.unclaimed(), now), "turn queue transition conflicted");
        return requireTurn(turnId);
    }

    /**
     * Claim 下一项持久任务、加载固定 Snapshot 并使用 Engine 执行到一个稳定结果。
     *
     * @param command Worker Claim 命令
     * @return 无任务时为空，否则返回执行结果
     */
    @Transactional
    public Optional<ExecutionResult> executeNext(ExecuteNextCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = Instant.now(clock);
        Optional<RuntimeWorkItem> claimed = workQueue.claimNext(
            command.instanceKey(), now, command.leaseTtl());
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        RuntimeWorkItem item = claimed.orElseThrow();
        Run beforeClaim = requireRun(item.runId());
        Turn turn = requireTurn(beforeClaim.turnId());
        Session session = requireSession(beforeClaim.sessionId());
        repository.assignFencingToken(beforeClaim.id(), turn.id(), item.fencingToken());
        Run claimedRun = requireRun(beforeClaim.id());
        appendEvent(session, turn, claimedRun, "run.claimed", item.fencingToken(), now);
        requireUpdated(repository.transitionRun(
            claimedRun.id(), RunStatus.CREATED, RunStatus.CLAIMED,
            item.fencingToken(), now, Optional.empty()), "run claim transition conflicted");
        Run run = requireRun(claimedRun.id());
        appendEvent(session, turn, run, "run.started", item.fencingToken(), now);
        requireUpdated(repository.transitionRun(
            run.id(), RunStatus.CLAIMED, RunStatus.RUNNING,
            item.fencingToken(), now, Optional.empty()), "run start transition conflicted");
        requireUpdated(repository.transitionTurn(
            turn.id(), TurnStatus.QUEUED, TurnStatus.RUNNING,
            item.fencingToken(), now), "turn start transition conflicted");
        SnapshotDescriptor snapshot = loadFixedSnapshot(session);
        ExecutionResult result = executionEngine.execute(
            session, requireRun(run.id()), snapshot, turn.input());
        applyExecutionResult(session, requireTurn(turn.id()), requireRun(run.id()), result, true);
        return Optional.of(result);
    }

    /**
     * 显式重试失败或超时 Turn，创建新 Run Attempt 而不覆盖旧 Run。
     *
     * @param command 重试命令
     * @return 新 Run Attempt
     */
    @Transactional
    public Run retryTurn(RetryTurnCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Turn turn = requireTurn(command.turnId());
        RuntimeStateMachine.requireRetryable(turn.status());
        Run previous = requireRun(turn.currentRunId().orElseThrow());
        Instant now = Instant.now(clock);
        Run retry = new Run(
            RunId.generate(), turn.organizationId(), turn.projectId(), turn.sessionId(), turn.id(),
            previous.attemptNumber() + 1, command.runtimeProvider(), command.compilerVersion(),
            RunStatus.CREATED, 0, FencingToken.unclaimed(), Optional.empty(), Optional.empty(),
            Optional.empty(), now);
        RuntimeWorkItem workItem = new RuntimeWorkItem(
            JobId.generate(), retry.id(), WorkItemStatus.READY, command.priority(), now,
            Optional.empty(), Optional.empty(), FencingToken.unclaimed(), 0, now);
        RuntimeOutboxEvent outbox = outbox(
            "turn", turn.id().asString(), "run.retried",
            "{\"previousRunId\":\"" + previous.id().asString()
                + "\",\"runId\":\"" + retry.id().asString() + "\"}", now);
        repository.insertRetryRun(turn, retry, workItem, outbox);
        return retry;
    }

    /**
     * 创建参数 Hash 固定的待审批事实；调用方随后必须将 Run 暂停。
     *
     * @param runId         Run 标识
     * @param toolName      Tool 稳定名称
     * @param action        待审批动作
     * @param argumentHash  原始参数 Hash
     * @param policyVersion 固定策略版本
     * @param ttl           审批有效期
     * @return 新 Approval
     */
    @Transactional
    public Approval requestApproval(
        RunId runId,
        String toolName,
        String action,
        Checksum argumentHash,
        String policyVersion,
        Duration ttl) {
        Run run = requireRun(runId);
        if (run.status() != RunStatus.RUNNING && run.status() != RunStatus.PAUSED) {
            throw new RuntimeConflictException("approval requires a running or paused run");
        }
        Instant now = Instant.now(clock);
        Approval approval = new Approval(
            ApprovalId.generate(), run.organizationId(), run.projectId(), run.id(), toolName,
            action, argumentHash, policyVersion, ApprovalStatus.PENDING, 0,
            now.plus(ttl), Optional.empty(), Optional.empty(), now);
        approvalRepository.insert(approval);
        return approval;
    }

    /**
     * 持久取消事实并终止当前 Run/Turn，然后通知 Engine 释放外部资源。
     *
     * @param command 绑定当前 Run 与 Fencing Token 的取消命令
     */
    @Transactional
    public void cancel(CancellationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Turn turn = requireTurn(command.turnId());
        if (!turn.currentRunId().equals(Optional.of(command.expectedRunId()))) {
            throw new RuntimeConflictException("cancellation targets a stale run");
        }
        Run run = requireRun(command.expectedRunId());
        if (!run.fencingToken().equals(command.fencingToken())) {
            throw new RuntimeConflictException("cancellation fencing token is stale");
        }
        Session session = requireSession(run.sessionId());
        Instant now = Instant.now(clock);
        appendEvent(session, turn, run, "run.cancelled", command.fencingToken(), now);
        requireUpdated(repository.transitionRun(
            run.id(), run.status(), RunStatus.CANCELLED, command.fencingToken(), now,
            Optional.of(command.reasonCode())), "run cancellation conflicted");
        requireUpdated(repository.transitionTurn(
            turn.id(), turn.status(), TurnStatus.CANCELLED,
            command.fencingToken(), now), "turn cancellation conflicted");
        workQueue.complete(run.id(), command.fencingToken(), WorkItemStatus.CANCELLED);
        repository.insertOutbox(outbox(
            "run", run.id().asString(), "run.cancelled",
            "{\"reasonCode\":\"" + command.reasonCode() + "\"}", now));
        executionEngine.cancel(command);
    }

    /**
     * 使用已批准且参数 Hash 未变化的 Approval 恢复 PAUSED Run。
     *
     * @param command 恢复命令
     * @return 恢复后的执行结果
     */
    @Transactional
    public ExecutionResult resume(ResumeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Run run = requireRun(command.runId());
        Turn turn = requireTurn(run.turnId());
        List<Approval> approvals = approvalRepository.listForRun(run.id());
        boolean decisionsMatch = !command.decisions().isEmpty()
            && command.decisions().stream().allMatch(decision -> approvals.stream()
            .anyMatch(approval -> approval.id().equals(decision.approvalId())
                && approval.argumentHash().equals(decision.argumentHash())
                && approval.status() == (decision.approved()
                ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED)));
        if (!decisionsMatch
            || run.status() != RunStatus.PAUSED
            || turn.status() != TurnStatus.WAITING_APPROVAL
            || !run.fencingToken().equals(command.fencingToken())) {
            throw new RuntimeConflictException("resume prerequisites are not satisfied");
        }
        Session session = requireSession(run.sessionId());
        Instant now = Instant.now(clock);
        appendEvent(session, turn, run, "run.resumed", command.fencingToken(), now);
        requireUpdated(repository.transitionRun(
            run.id(), RunStatus.PAUSED, RunStatus.RUNNING,
            command.fencingToken(), now, Optional.empty()), "run resume conflicted");
        requireUpdated(repository.transitionTurn(
            turn.id(), TurnStatus.WAITING_APPROVAL, TurnStatus.RUNNING,
            command.fencingToken(), now), "turn resume conflicted");
        ExecutionResult result = executionEngine.resume(
            session, requireRun(run.id()), loadFixedSnapshot(session), command);
        applyExecutionResult(session, requireTurn(turn.id()), requireRun(run.id()), result, false);
        return result;
    }

    /**
     * 将 Engine 结果先追加为 Event，再转换 Run/Turn 和完成 Work Item。
     *
     * @param session      固定 Session
     * @param turn         当前 Turn
     * @param run          当前 Run
     * @param result       Engine 结果
     * @param completeWork 是否需要完成本次 Claim 的 Work Item
     */
    private void applyExecutionResult(
        Session session, Turn turn, Run run, ExecutionResult result, boolean completeWork) {
        Instant now = Instant.now(clock);
        RunStatus runTarget;
        TurnStatus turnTarget;
        WorkItemStatus workTarget;
        String eventType;
        Optional<String> errorCode = result.errorCode();
        switch (result.outcome()) {
            case SUCCEEDED -> {
                runTarget = RunStatus.SUCCEEDED;
                turnTarget = TurnStatus.COMPLETED;
                workTarget = WorkItemStatus.COMPLETED;
                eventType = "run.succeeded";
            }
            case FAILED -> {
                runTarget = RunStatus.FAILED;
                turnTarget = TurnStatus.FAILED;
                workTarget = WorkItemStatus.FAILED;
                eventType = "run.failed";
            }
            case PAUSED -> {
                runTarget = RunStatus.PAUSED;
                turnTarget = TurnStatus.WAITING_APPROVAL;
                workTarget = WorkItemStatus.COMPLETED;
                eventType = "run.paused";
            }
            case CANCELLED -> {
                runTarget = RunStatus.CANCELLED;
                turnTarget = TurnStatus.CANCELLED;
                workTarget = WorkItemStatus.CANCELLED;
                eventType = "run.cancelled";
            }
            case TIMED_OUT -> {
                runTarget = RunStatus.FAILED;
                turnTarget = TurnStatus.TIMED_OUT;
                workTarget = WorkItemStatus.FAILED;
                eventType = "run.timed_out";
            }
            default -> throw new IllegalStateException("unsupported execution outcome");
        }
        appendEvent(session, turn, run, eventType, run.fencingToken(), now);
        requireUpdated(repository.transitionRun(
                run.id(), RunStatus.RUNNING, runTarget, run.fencingToken(), now, errorCode),
            "run terminal transition conflicted");
        requireUpdated(repository.transitionTurn(
                turn.id(), TurnStatus.RUNNING, turnTarget, run.fencingToken(), now),
            "turn terminal transition conflicted");
        if (completeWork) {
            workQueue.complete(run.id(), run.fencingToken(), workTarget);
        }
        repository.insertOutbox(outbox(
            "run", run.id().asString(), eventType,
            "{\"outcome\":\"" + result.outcome().name() + "\"}", now));
    }

    /**
     * 追加不含隐藏推理过程的稳定状态 Event。
     *
     * @param session      Session
     * @param turn         Turn
     * @param run          Run
     * @param type         事件类型
     * @param fencingToken 当前令牌
     * @param occurredAt   发生时刻
     */
    private void appendEvent(
        Session session,
        Turn turn,
        Run run,
        String type,
        FencingToken fencingToken,
        Instant occurredAt) {
        eventStore.append(
            EventId.generate(), session.organizationId(), session.projectId(), session.id(),
            turn.id(), run.id(), type, 1,
            run.id().asString().replace("-", ""),
            RuntimePayload.inline("{\"type\":\"" + type + "\"}"),
            occurredAt, fencingToken);
    }

    /**
     * 加载并核对 Session 创建时固定的 Snapshot。
     *
     * @param session Session
     * @return 固定 Snapshot
     */
    private SnapshotDescriptor loadFixedSnapshot(Session session) {
        SnapshotDescriptor snapshot = snapshotLoader.load(session.revisionId());
        if (!snapshot.snapshotId().equals(session.snapshotId())
            || !snapshot.contentHash().equals(session.snapshotHash())) {
            throw new RuntimeConflictException("loaded snapshot differs from fixed session snapshot");
        }
        return snapshot;
    }

    /**
     * 读取必需 Session。
     *
     * @param sessionId Session 标识
     * @return Session
     */
    private Session requireSession(SessionId sessionId) {
        return repository.findSession(sessionId)
            .orElseThrow(() -> new RuntimeNotFoundException("session is not available"));
    }

    /**
     * 读取必需 Turn。
     *
     * @param turnId Turn 标识
     * @return Turn
     */
    private Turn requireTurn(TurnId turnId) {
        return repository.findTurn(turnId)
            .orElseThrow(() -> new RuntimeNotFoundException("turn is not available"));
    }

    /**
     * 读取必需 Run。
     *
     * @param runId Run 标识
     * @return Run
     */
    private Run requireRun(RunId runId) {
        return repository.findRun(runId)
            .orElseThrow(() -> new RuntimeNotFoundException("run is not available"));
    }

    /**
     * 处理 Session 幂等重放并拒绝同 Key 不同 Hash。
     *
     * @param existing    已有记录
     * @param requestHash 当前请求 Hash
     * @return 原 Session
     */
    private Session replaySession(IdempotencyRecord existing, Checksum requestHash) {
        requireSameRequest(existing, requestHash);
        String result = existing.resultRef().orElseThrow();
        return requireSession(SessionId.parse(requireResult(result, "session:")));
    }

    /**
     * 处理 Turn 幂等重放并拒绝同 Key 不同 Hash。
     *
     * @param existing    已有记录
     * @param requestHash 当前请求 Hash
     * @return 原 Turn
     */
    private Turn replayTurn(IdempotencyRecord existing, Checksum requestHash) {
        requireSameRequest(existing, requestHash);
        String result = existing.resultRef().orElseThrow();
        return requireTurn(TurnId.parse(requireResult(result, "turn:")));
    }

    /**
     * 校验幂等请求 Hash 相同且首次请求已完成。
     *
     * @param existing    已有记录
     * @param requestHash 当前请求 Hash
     */
    private void requireSameRequest(IdempotencyRecord existing, Checksum requestHash) {
        if (!existing.requestHash().equals(requestHash)
            || existing.status() != IdempotencyStatus.COMPLETED) {
            throw new RuntimeConflictException("idempotency key is bound to another request");
        }
    }

    /**
     * 移除并校验幂等结果类型前缀。
     *
     * @param result 结果引用
     * @param prefix 预期前缀
     * @return UUIDv7 字符串
     */
    private String requireResult(String result, String prefix) {
        if (!result.startsWith(prefix)) {
            throw new IllegalStateException("idempotency result type is invalid");
        }
        return result.substring(prefix.length());
    }

    /**
     * 创建完成态幂等记录。
     *
     * @param scopeType   作用域类型
     * @param scopeId     作用域标识
     * @param key         幂等键
     * @param requestHash 请求 Hash
     * @param resultRef   结果引用
     * @param now         创建时刻
     * @return 完成态记录
     */
    private IdempotencyRecord completedIdempotency(
        String scopeType,
        String scopeId,
        String key,
        Checksum requestHash,
        String resultRef,
        Instant now) {
        return new IdempotencyRecord(
            scopeType, scopeId, key, requestHash, Optional.of(resultRef),
            IdempotencyStatus.COMPLETED, now.plus(IDEMPOTENCY_TTL), now);
    }

    /**
     * 创建待投递 Runtime Outbox。
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合标识
     * @param eventType     事件类型
     * @param payloadJson   非敏感载荷
     * @param now           创建时刻
     * @return Outbox Event
     */
    private RuntimeOutboxEvent outbox(
        String aggregateType,
        String aggregateId,
        String eventType,
        String payloadJson,
        Instant now) {
        return new RuntimeOutboxEvent(
            EventId.generate(), aggregateType, aggregateId, eventType,
            RuntimePayload.inline(payloadJson), OutboxStatus.PENDING, now, 0, now);
    }

    /**
     * 将条件更新失败转换为显式并发冲突。
     *
     * @param updated 实际更新行数
     * @param message 不含敏感数据的上下文
     */
    private void requireUpdated(int updated, String message) {
        if (updated != 1) {
            throw new RuntimeConflictException(message);
        }
    }
}
