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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import space.refinex.agentark.foundation.observability.AgentArkTelemetry;
import space.refinex.agentark.kernel.id.EventId;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.RunId;
import space.refinex.agentark.runtime.application.RuntimeCommands.CancellationCommand;
import space.refinex.agentark.runtime.application.RuntimeCommands.DecideApprovalCommand;
import space.refinex.agentark.runtime.domain.RuntimeConflictException;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.domain.RuntimeNotFoundException;
import space.refinex.agentark.runtime.port.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 使用短事务编排 Worker Claim、状态切换、终态写入和 Approval 决策。
 *
 * @author refinex
 */
public class RuntimeExecutionCoordinator {

    /**
     * Approval 决策幂等作用域。
     */
    private static final String APPROVAL_SCOPE = "APPROVAL_DECIDE";

    /**
     * 幂等结果保留期。
     */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(1);

    /**
     * Runtime 聚合仓储。
     */
    private final RuntimeRepository repository;

    /**
     * 持久事件仓储。
     */
    private final RuntimeEventStore eventStore;

    /**
     * 持久 Work Queue。
     */
    private final RuntimeWorkQueue workQueue;

    /**
     * Approval 仓储。
     */
    private final ApprovalRepository approvalRepository;

    /**
     * Checkpoint 仓储。
     */
    private final CheckpointStore checkpointStore;

    /**
     * MySQL Lease 校验端口。
     */
    private final LeaseManager leaseManager;

    /**
     * 事务提交后事件提示端口。
     */
    private final RuntimeEventNotifier eventNotifier;

    /**
     * Control 并发配额 Reservation 生命周期端口。
     */
    private final RuntimeQuotaPort quotaPort;

    /**
     * Control append-only Audit 汇聚端口。
     */
    private final GovernanceAuditClient governanceAuditClient;

    /**
     * 当前 Trace 关联访问器。
     */
    private final AgentArkTelemetry telemetry;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 创建短事务执行协调器。
     *
     * @param repository         Runtime 聚合仓储
     * @param eventStore         Event Store
     * @param workQueue          持久 Work Queue
     * @param approvalRepository Approval 仓储
     * @param checkpointStore    Checkpoint 仓储
     * @param leaseManager       MySQL Lease 校验端口
     * @param eventNotifier      事务后 Event 提示端口
     * @param clock              UTC 时钟
     */
    public RuntimeExecutionCoordinator(
        RuntimeRepository repository,
        RuntimeEventStore eventStore,
        RuntimeWorkQueue workQueue,
        ApprovalRepository approvalRepository,
        CheckpointStore checkpointStore,
        LeaseManager leaseManager,
        RuntimeEventNotifier eventNotifier,
        Clock clock) {
        this(
            repository, eventStore, workQueue, approvalRepository, checkpointStore,
            leaseManager, eventNotifier, RuntimeQuotaPort.noop(),
            GovernanceAuditClient.noop(), AgentArkTelemetry.noop(), clock);
    }

    /**
     * 创建带 Control 并发配额生命周期的短事务执行协调器。
     *
     * @param repository         Runtime 聚合仓储
     * @param eventStore         Event Store
     * @param workQueue          持久 Work Queue
     * @param approvalRepository Approval 仓储
     * @param checkpointStore    Checkpoint 仓储
     * @param leaseManager       MySQL Lease 校验端口
     * @param eventNotifier      事务后 Event 提示端口
     * @param quotaPort          Control 并发配额 Reservation 端口
     * @param clock              UTC 时钟
     */
    public RuntimeExecutionCoordinator(
        RuntimeRepository repository,
        RuntimeEventStore eventStore,
        RuntimeWorkQueue workQueue,
        ApprovalRepository approvalRepository,
        CheckpointStore checkpointStore,
        LeaseManager leaseManager,
        RuntimeEventNotifier eventNotifier,
        RuntimeQuotaPort quotaPort,
        Clock clock) {
        this(
            repository, eventStore, workQueue, approvalRepository, checkpointStore,
            leaseManager, eventNotifier, quotaPort, GovernanceAuditClient.noop(),
            AgentArkTelemetry.noop(), clock);
    }

    /**
     * 创建带 Control 配额、审计汇聚和 Trace 关联的短事务执行协调器。
     *
     * @param repository         Runtime 聚合仓储
     * @param eventStore         Event Store
     * @param workQueue          持久 Work Queue
     * @param approvalRepository Approval 仓储
     * @param checkpointStore    Checkpoint 仓储
     * @param leaseManager       MySQL Lease 校验端口
     * @param eventNotifier      事务后 Event 提示端口
     * @param quotaPort          Control 并发配额 Reservation 端口
     * @param governanceAuditClient Control append-only Audit 汇聚端口
     * @param telemetry          Trace 关联访问器
     * @param clock              UTC 时钟
     */
    public RuntimeExecutionCoordinator(
        RuntimeRepository repository,
        RuntimeEventStore eventStore,
        RuntimeWorkQueue workQueue,
        ApprovalRepository approvalRepository,
        CheckpointStore checkpointStore,
        LeaseManager leaseManager,
        RuntimeEventNotifier eventNotifier,
        RuntimeQuotaPort quotaPort,
        GovernanceAuditClient governanceAuditClient,
        AgentArkTelemetry telemetry,
        Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
        this.workQueue = Objects.requireNonNull(workQueue, "workQueue must not be null");
        this.approvalRepository = Objects.requireNonNull(
            approvalRepository, "approvalRepository must not be null");
        this.checkpointStore = Objects.requireNonNull(
            checkpointStore, "checkpointStore must not be null");
        this.leaseManager = Objects.requireNonNull(leaseManager, "leaseManager must not be null");
        this.eventNotifier = Objects.requireNonNull(
            eventNotifier, "eventNotifier must not be null");
        this.quotaPort = Objects.requireNonNull(quotaPort, "quotaPort must not be null");
        this.governanceAuditClient = Objects.requireNonNull(
            governanceAuditClient, "governanceAuditClient must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Claim 一项 Work Item，并在短事务内决定首次执行、Approval 恢复或 Checkpoint 恢复。
     *
     * @param instanceKey Runtime Instance Key
     * @param leaseTtl    Lease 有效期
     * @return 可执行上下文；不可恢复孤儿会创建新 Attempt 并返回空
     */
    @Transactional
    public Optional<ClaimedExecution> claim(String instanceKey, Duration leaseTtl) {
        Instant now = Instant.now(clock);
        Optional<RuntimeWorkItem> claimed = workQueue.claimNext(instanceKey, now, leaseTtl);
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        RuntimeWorkItem workItem = claimed.orElseThrow();
        Run previous = requireRun(workItem.runId());
        Turn turn = requireTurn(previous.turnId());
        Session session = requireSession(previous.sessionId());
        repository.assignFencingToken(previous.id(), turn.id(), workItem.fencingToken());
        Run run = requireRun(previous.id());
        Turn currentTurn = requireTurn(turn.id());
        return switch (run.status()) {
            case CREATED -> Optional.of(startInitial(
                session, currentTurn, run, workItem, now));
            case PAUSED -> Optional.of(startApprovalResume(
                session, currentTurn, run, workItem, now));
            case CLAIMED, RUNNING -> recoverOrReplace(
                session, currentTurn, run, workItem, now);
            case SUCCEEDED, FAILED, CANCELLED, ABANDONED -> {
                workQueue.complete(run.id(), run.fencingToken(), WorkItemStatus.FAILED);
                yield Optional.empty();
            }
        };
    }

    /**
     * 使用当前 Owner 和 Fencing Token 原子持久化 Provider 执行结果。
     *
     * @param claimed 已 Claim 执行上下文
     * @param result  Provider 中立结果
     */
    @Transactional
    public void complete(ClaimedExecution claimed, ExecutionResult result) {
        Objects.requireNonNull(claimed, "claimed must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Instant now = Instant.now(clock);
        RuntimeWorkItem workItem = claimed.workItem();
        leaseManager.requireCurrent(
            claimed.run().id(), workItem.claimedBy().orElseThrow(),
            workItem.fencingToken(), now);
        Run run = requireRun(claimed.run().id());
        Turn turn = requireTurn(run.turnId());
        Session session = requireSession(run.sessionId());
        ExecutionResult checked = requirePauseEvidence(run, result);
        TerminalProjection projection = TerminalProjection.from(checked);
        appendEvent(session, turn, run, projection.eventType(), now);
        requireUpdated(repository.transitionRun(
            run.id(), RunStatus.RUNNING, projection.runStatus(), run.fencingToken(), now,
            checked.errorCode()), "run terminal transition conflicted");
        requireUpdated(repository.transitionTurn(
                turn.id(), TurnStatus.RUNNING, projection.turnStatus(), run.fencingToken(), now),
            "turn terminal transition conflicted");
        workQueue.complete(run.id(), run.fencingToken(), projection.workItemStatus());
        if (projection.runStatus() == RunStatus.CANCELLED) {
            cancelPendingApprovals(session, turn, run, "runtime-system", now);
        }
        repository.insertOutbox(outbox(
            "run", run.id().asString(), projection.eventType(),
            "{\"outcome\":\"" + checked.outcome().name() + "\"}", now));
        if (space.refinex.agentark.runtime.domain.RuntimeStateMachine.isTerminal(
            projection.runStatus())) {
            releaseQuotaAfterCommit(turn.id());
        }
    }

    /**
     * 对 Approval 做租户外已完成授权后的幂等乐观锁决策；全部决策完成时重新入队。
     *
     * @param command Approval 决策命令
     * @return 已决 Approval
     */
    @Transactional
    public Approval decideApproval(DecideApprovalCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String scopeId = command.approvalId().asString();
        Optional<IdempotencyRecord> existing = repository.findIdempotency(
            APPROVAL_SCOPE, scopeId, command.idempotencyKey());
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.orElseThrow();
            if (!record.requestHash().equals(command.requestHash())) {
                throw new RuntimeConflictException(
                    "idempotency key was reused with a different request hash");
            }
            return requireApproval(command.approvalId());
        }
        Approval approval = requireApproval(command.approvalId());
        Instant now = Instant.now(clock);
        if (!approval.expiresAt().isAfter(now)) {
            requireUpdated(approvalRepository.decide(
                approval.id(), approval.expectedVersion(), ApprovalStatus.EXPIRED,
                "runtime-system", now), "approval expiration conflicted");
            Approval expired = requireApproval(approval.id());
            repository.insertIdempotency(new IdempotencyRecord(
                APPROVAL_SCOPE, scopeId, command.idempotencyKey(), command.requestHash(),
                Optional.of("approval:" + expired.id().asString()),
                IdempotencyStatus.COMPLETED, now.plus(IDEMPOTENCY_TTL), now));
            recordApprovalDecision(expired, now);
            requeueIfApprovalReady(expired, now);
            return expired;
        }
        requireUpdated(approvalRepository.decide(
            approval.id(), command.expectedVersion(), command.target(),
            command.decisionBy(), now), "approval decision conflicted");
        Approval decided = requireApproval(approval.id());
        repository.insertIdempotency(new IdempotencyRecord(
            APPROVAL_SCOPE, scopeId, command.idempotencyKey(), command.requestHash(),
            Optional.of("approval:" + decided.id().asString()), IdempotencyStatus.COMPLETED,
            now.plus(IDEMPOTENCY_TTL), now));
        recordApprovalDecision(decided, now);
        requeueIfApprovalReady(decided, now);
        return decided;
    }

    /**
     * 幂等持久化取消事实、终止 Run/Turn/Work Item，并返回事务后 Provider 取消命令。
     *
     * @param runId      Run 标识
     * @param reasonCode 稳定取消原因
     * @return 首次取消时返回 Provider 命令；已处于终态时为空
     */
    @Transactional
    public Optional<CancellationCommand> cancel(RunId runId, String reasonCode) {
        return cancel(runId, reasonCode, "runtime-system");
    }

    /**
     * 幂等持久化带已认证主体的取消事实，并在提交后汇聚 Audit。
     *
     * @param runId       Run 标识
     * @param reasonCode  稳定取消原因
     * @param principalRef 已认证主体稳定引用
     * @return 首次取消时返回 Provider 命令；已处于终态时为空
     */
    @Transactional
    public Optional<CancellationCommand> cancel(
        RunId runId, String reasonCode, String principalRef) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        if (principalRef == null || principalRef.isBlank()) {
            throw new IllegalArgumentException("principalRef must not be blank");
        }
        Run run = requireRun(runId);
        if (space.refinex.agentark.runtime.domain.RuntimeStateMachine.isTerminal(run.status())) {
            return Optional.empty();
        }
        Turn turn = requireTurn(run.turnId());
        if (!turn.currentRunId().equals(Optional.of(run.id()))) {
            throw new RuntimeConflictException("cancellation targets a stale run");
        }
        Session session = requireSession(run.sessionId());
        Instant now = Instant.now(clock);
        appendEvent(session, turn, run, "run.cancelled", now);
        requireUpdated(repository.transitionRun(
            run.id(), run.status(), RunStatus.CANCELLED, run.fencingToken(), now,
            Optional.of(reasonCode)), "run cancellation conflicted");
        requireUpdated(repository.transitionTurn(
                turn.id(), turn.status(), TurnStatus.CANCELLED, run.fencingToken(), now),
            "turn cancellation conflicted");
        workQueue.complete(run.id(), run.fencingToken(), WorkItemStatus.CANCELLED);
        cancelPendingApprovals(session, turn, run, "runtime-system", now);
        repository.insertOutbox(outbox(
            "run", run.id().asString(), "run.cancelled",
            "{\"reasonCode\":\"" + reasonCode + "\"}", now));
        auditAfterCommit(
            run.id().asString() + ":cancelled", session, principalRef,
            "runtime.run.cancel", "run", run.id().asString(),
            java.util.Map.of("reasonCode", reasonCode), now);
        releaseQuotaAfterCommit(turn.id());
        return Optional.of(new CancellationCommand(
            turn.id(), run.id(), run.fencingToken(), reasonCode));
    }

    /**
     * 将首次执行从 CREATED 推进到 RUNNING。
     *
     * @param session  Session
     * @param turn     Turn
     * @param run      Run
     * @param workItem Work Item
     * @param now      当前时刻
     * @return 首次执行上下文
     */
    private ClaimedExecution startInitial(
        Session session, Turn turn, Run run, RuntimeWorkItem workItem, Instant now) {
        appendEvent(session, turn, run, "run.claimed", now);
        requireUpdated(repository.transitionRun(
            run.id(), RunStatus.CREATED, RunStatus.CLAIMED, run.fencingToken(), now,
            Optional.empty()), "run claim transition conflicted");
        Run claimedRun = requireRun(run.id());
        appendEvent(session, turn, claimedRun, "run.started", now);
        requireUpdated(repository.transitionRun(
            run.id(), RunStatus.CLAIMED, RunStatus.RUNNING, run.fencingToken(), now,
            Optional.empty()), "run start transition conflicted");
        requireUpdated(repository.transitionTurn(
                turn.id(), TurnStatus.QUEUED, TurnStatus.RUNNING, run.fencingToken(), now),
            "turn start transition conflicted");
        return new ClaimedExecution(
            session, requireTurn(turn.id()), requireRun(run.id()), workItem,
            ExecutionMode.INITIAL, Optional.empty(), List.of());
    }

    /**
     * 校验同一暂停点全部 Approval 已决，并以新 Fencing Token 恢复 RUNNING。
     *
     * @param session  Session
     * @param turn     Turn
     * @param run      暂停 Run
     * @param workItem 新 Claim Work Item
     * @param now      当前时刻
     * @return Approval 恢复上下文
     */
    private ClaimedExecution startApprovalResume(
        Session session, Turn turn, Run run, RuntimeWorkItem workItem, Instant now) {
        List<Approval> approvals = approvalRepository.listForRun(run.id());
        expireApprovals(session, turn, run, approvals, now);
        approvals = approvalRepository.listForRun(run.id());
        if (approvals.isEmpty()
            || approvals.stream().anyMatch(item -> item.status() == ApprovalStatus.PENDING)) {
            throw new RuntimeConflictException("approval resume is not ready");
        }
        Checkpoint checkpoint = checkpointStore.findLatestRecoverable(run.id())
            .orElseThrow(() -> new RuntimeConflictException(
                "approval resume requires a recoverable checkpoint"));
        List<ApprovalDecision> decisions = approvals.stream()
            .map(this::decision)
            .toList();
        appendEvent(session, turn, run, "run.resumed", now);
        requireUpdated(repository.transitionRun(
            run.id(), RunStatus.PAUSED, RunStatus.RUNNING, run.fencingToken(), now,
            Optional.empty()), "run resume transition conflicted");
        requireUpdated(repository.transitionTurn(
            turn.id(), TurnStatus.WAITING_APPROVAL, TurnStatus.RUNNING,
            run.fencingToken(), now), "turn resume transition conflicted");
        return new ClaimedExecution(
            session, requireTurn(turn.id()), requireRun(run.id()), workItem,
            ExecutionMode.RESUME, Optional.empty(), decisions);
    }

    /**
     * 从 Checkpoint 接管孤儿 Run；无可恢复状态时原子创建新 Attempt。
     *
     * @param session  Session
     * @param turn     Turn
     * @param run      被新令牌接管的 Run
     * @param workItem 新 Claim Work Item
     * @param now      当前时刻
     * @return 可恢复上下文或空
     */
    private Optional<ClaimedExecution> recoverOrReplace(
        Session session, Turn turn, Run run, RuntimeWorkItem workItem, Instant now) {
        Optional<Checkpoint> checkpoint = checkpointStore.findLatestRecoverable(run.id());
        if (checkpoint.isPresent() && run.status() == RunStatus.RUNNING) {
            appendEvent(session, turn, run, "run.recovered", now);
            return Optional.of(new ClaimedExecution(
                session, turn, run, workItem, ExecutionMode.RECOVER,
                checkpoint, List.of()));
        }
        appendEvent(session, turn, run, "run.abandoned", now);
        Run retry = new Run(
            RunId.generate(), run.organizationId(), run.projectId(), run.sessionId(), run.turnId(),
            run.attemptNumber() + 1, run.runtimeProvider(), run.compilerVersion(),
            RunStatus.CREATED, 0, FencingToken.unclaimed(), Optional.empty(), Optional.empty(),
            Optional.empty(), now);
        RuntimeWorkItem retryWork = new RuntimeWorkItem(
            JobId.generate(), retry.id(), WorkItemStatus.READY, workItem.priority(), now,
            Optional.empty(), Optional.empty(), FencingToken.unclaimed(), 0, now);
        repository.replaceOrphanRun(
            turn, run, retry, retryWork,
            outbox("run", run.id().asString(), "run.abandoned",
                "{\"retryRunId\":\"" + retry.id().asString() + "\"}", now),
            now);
        return Optional.empty();
    }

    /**
     * 对到期的待决 Approval 写入 EXPIRED 终态。
     *
     * @param session   Session
     * @param turn      Turn
     * @param run       暂停 Run
     * @param approvals Approval 集合
     * @param now       当前时刻
     */
    private void expireApprovals(
        Session session, Turn turn, Run run, List<Approval> approvals, Instant now) {
        approvals.stream()
            .filter(approval -> approval.status() == ApprovalStatus.PENDING
                && !approval.expiresAt().isAfter(now))
            .forEach(approval -> {
                requireUpdated(approvalRepository.decide(
                    approval.id(), approval.expectedVersion(), ApprovalStatus.EXPIRED,
                    "runtime-system", now), "approval expiration conflicted");
                recordApprovalDecision(
                    session, turn, run, requireApproval(approval.id()), now);
            });
    }

    /**
     * 追加 Approval 决策 Event 与 Outbox，载荷只包含标识和状态，不包含 Tool 参数。
     *
     * @param approval 已持久化决策
     * @param now      决策时刻
     */
    private void recordApprovalDecision(Approval approval, Instant now) {
        Run run = requireRun(approval.runId());
        Turn turn = requireTurn(run.turnId());
        Session session = requireSession(run.sessionId());
        recordApprovalDecision(session, turn, run, approval, now);
    }

    /**
     * 使用已读取的运行上下文追加 Approval 审计事实。
     *
     * @param session  Session
     * @param turn     Turn
     * @param run      Run
     * @param approval 已持久化决策
     * @param now      决策时刻
     */
    private void recordApprovalDecision(
        Session session, Turn turn, Run run, Approval approval, Instant now) {
        String eventType = "approval." + approval.status().name().toLowerCase(java.util.Locale.ROOT);
        appendEvent(session, turn, run, eventType, now);
        repository.insertOutbox(outbox(
            "approval", approval.id().asString(), eventType,
            "{\"approvalId\":\"" + approval.id().asString()
                + "\",\"status\":\"" + approval.status().name() + "\"}", now));
        auditAfterCommit(
            approval.id().asString() + ":" + approval.status().name()
                + ":" + approval.expectedVersion(),
            session,
            approval.decisionBy().orElse("runtime-system"),
            "runtime.approval." + approval.status().name().toLowerCase(java.util.Locale.ROOT),
            "approval",
            approval.id().asString(),
            java.util.Map.of("status", approval.status().name()),
            now);
    }

    /**
     * 当同一 Run 已无待决审批时，在可恢复 Checkpoint 保护下重新入队。
     *
     * @param approval 最后完成决策的 Approval
     * @param now      当前时刻
     */
    private void requeueIfApprovalReady(Approval approval, Instant now) {
        List<Approval> runApprovals = approvalRepository.listForRun(approval.runId());
        if (runApprovals.stream().anyMatch(item -> item.status() == ApprovalStatus.PENDING)) {
            return;
        }
        Run run = requireRun(approval.runId());
        if (run.status() != RunStatus.PAUSED) {
            return;
        }
        checkpointStore.findLatestRecoverable(run.id()).orElseThrow(() ->
            new RuntimeConflictException("approval resume requires a recoverable checkpoint"));
        workQueue.requeueForResume(run.id(), run.fencingToken(), now);
    }

    /**
     * 取消 Run 的全部待决 Approval，并为每项状态变化追加审计事实。
     *
     * @param session    Session
     * @param turn       Turn
     * @param run        被取消 Run
     * @param decisionBy 系统或调用主体
     * @param now        取消时刻
     */
    private void cancelPendingApprovals(
        Session session, Turn turn, Run run, String decisionBy, Instant now) {
        List<Approval> pending = approvalRepository.listForRun(run.id()).stream()
            .filter(approval -> approval.status() == ApprovalStatus.PENDING)
            .toList();
        int cancelled = approvalRepository.cancelPending(run.id(), decisionBy, now);
        if (cancelled != pending.size()) {
            throw new RuntimeConflictException("approval cancellation conflicted");
        }
        pending.stream()
            .map(approval -> requireApproval(approval.id()))
            .forEach(approval -> recordApprovalDecision(session, turn, run, approval, now));
    }

    /**
     * 将 Approval 映射为 Provider 恢复决策。
     *
     * @param approval 已决 Approval
     * @return 绑定 Tool Call 与参数 Hash 的决策
     */
    private ApprovalDecision decision(Approval approval) {
        String prefix = "TOOL_EXECUTE:";
        if (!approval.action().startsWith(prefix)) {
            throw new RuntimeConflictException("approval action is not a tool execution");
        }
        return new ApprovalDecision(
            approval.id(), approval.action().substring(prefix.length()), approval.argumentHash(),
            approval.status() == ApprovalStatus.APPROVED);
    }

    /**
     * 暂停结果必须已经持久化 Approval 与可恢复 Checkpoint，否则降级为明确失败。
     *
     * @param run    当前 Run
     * @param result 原始结果
     * @return 已验证或降级后的结果
     */
    private ExecutionResult requirePauseEvidence(Run run, ExecutionResult result) {
        if (result.outcome() != ExecutionOutcome.PAUSED) {
            return result;
        }
        boolean hasPendingApproval = approvalRepository.listForRun(run.id()).stream()
            .anyMatch(approval -> approval.status() == ApprovalStatus.PENDING);
        boolean hasCheckpoint = checkpointStore.findLatestRecoverable(run.id()).isPresent();
        if (hasPendingApproval && hasCheckpoint) {
            return result;
        }
        return new ExecutionResult(
            ExecutionOutcome.FAILED, Optional.of("RUNTIME_PAUSE_EVIDENCE_MISSING"),
            Optional.of("Provider pause did not persist approval and checkpoint evidence"));
    }

    /**
     * 追加 Event，并安排在事务提交后发送实时提示。
     *
     * @param session Session
     * @param turn    Turn
     * @param run     Run
     * @param type    事件类型
     * @param now     发生时刻
     */
    private void appendEvent(Session session, Turn turn, Run run, String type, Instant now) {
        RuntimeEvent event = eventStore.append(
            EventId.generate(), session.organizationId(), session.projectId(), session.id(),
            turn.id(), run.id(), type, 1, run.id().asString().replace("-", ""),
            RuntimePayload.inline("{\"type\":\"" + type + "\"}"), now,
            run.fencingToken());
        afterCommit(() -> eventNotifier.publish(event.sessionId(), event.sessionSequence()));
    }

    /**
     * 在本地终态事务提交后幂等释放 Turn 绑定的 Control 并发配额 Reservation。
     *
     * <p>远端释放失败由 Control TTL 最终回收，且不能回滚已经持久化的 Runtime 终态。
     *
     * @param turnId Turn 标识
     */
    private void releaseQuotaAfterCommit(space.refinex.agentark.kernel.id.TurnId turnId) {
        repository.findQuotaReservation(turnId)
            .ifPresent(reservationId -> afterCommit(() -> quotaPort.release(reservationId)));
    }

    /**
     * 在 Runtime 本地事务提交后提交安全 Audit 投影，远端失败不得覆盖本地权威事实。
     *
     * @param sourceEventId 来源幂等标识
     * @param session       固定租户 Session
     * @param principalRef  主体稳定引用
     * @param action        稳定动作
     * @param resourceType  资源类型
     * @param resourceRef   资源引用
     * @param diff          不含正文的差异摘要
     * @param now           发生时间
     */
    private void auditAfterCommit(
        String sourceEventId,
        Session session,
        String principalRef,
        String action,
        String resourceType,
        String resourceRef,
        java.util.Map<String, Object> diff,
        Instant now) {
        Optional<String> traceId = telemetry.currentTraceId();
        afterCommit(() -> governanceAuditClient.append(new GovernanceAuditClient.AuditRecord(
            sourceEventId, session.organizationId(), session.projectId(), principalRef,
            action, "SUCCEEDED", resourceType, resourceRef, diff, traceId, now)));
    }

    /**
     * 在存在 Spring 事务时延迟动作到提交后，否则立即执行。
     *
     * @param action 提交后动作
     */
    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 事务成功提交后发布可丢失提示。
             */
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /**
     * 创建 Runtime Outbox 事实。
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合标识
     * @param eventType     事件类型
     * @param payload       非敏感内联 JSON
     * @param now           发生时刻
     * @return Outbox 事实
     */
    private RuntimeOutboxEvent outbox(
        String aggregateType, String aggregateId, String eventType,
        String payload, Instant now) {
        return new RuntimeOutboxEvent(
            EventId.generate(), aggregateType, aggregateId, eventType,
            RuntimePayload.inline(payload), OutboxStatus.PENDING, now, 0, now);
    }

    /**
     * 读取必需 Session。
     *
     * @param id Session 标识
     * @return Session
     */
    private Session requireSession(space.refinex.agentark.kernel.id.SessionId id) {
        return repository.findSession(id)
            .orElseThrow(() -> new RuntimeNotFoundException("session is not available"));
    }

    /**
     * 读取必需 Turn。
     *
     * @param id Turn 标识
     * @return Turn
     */
    private Turn requireTurn(space.refinex.agentark.kernel.id.TurnId id) {
        return repository.findTurn(id)
            .orElseThrow(() -> new RuntimeNotFoundException("turn is not available"));
    }

    /**
     * 读取必需 Run。
     *
     * @param id Run 标识
     * @return Run
     */
    private Run requireRun(RunId id) {
        return repository.findRun(id)
            .orElseThrow(() -> new RuntimeNotFoundException("run is not available"));
    }

    /**
     * 读取必需 Approval。
     *
     * @param id Approval 标识
     * @return Approval
     */
    private Approval requireApproval(space.refinex.agentark.kernel.id.ApprovalId id) {
        return approvalRepository.find(id)
            .orElseThrow(() -> new RuntimeNotFoundException("approval is not available"));
    }

    /**
     * 要求数据库条件更新命中一行。
     *
     * @param updated 更新行数
     * @param message 冲突消息
     */
    private void requireUpdated(int updated, String message) {
        if (updated != 1) {
            throw new RuntimeConflictException(message);
        }
    }

    /**
     * 表达 Provider 结果到 Run、Turn、Work Item 与 Event 的稳定投影。
     *
     * @param runStatus      Run 目标状态
     * @param turnStatus     Turn 目标状态
     * @param workItemStatus Work Item 目标状态
     * @param eventType      稳定 Event 类型
     * @author refinex
     */
    private record TerminalProjection(
        RunStatus runStatus,
        TurnStatus turnStatus,
        WorkItemStatus workItemStatus,
        String eventType) {

        /**
         * 将 Provider 中立结果映射为权威终态。
         *
         * @param result 执行结果
         * @return 终态投影
         */
        private static TerminalProjection from(ExecutionResult result) {
            return switch (result.outcome()) {
                case SUCCEEDED -> new TerminalProjection(
                    RunStatus.SUCCEEDED, TurnStatus.COMPLETED,
                    WorkItemStatus.COMPLETED, "run.succeeded");
                case FAILED -> new TerminalProjection(
                    RunStatus.FAILED, TurnStatus.FAILED,
                    WorkItemStatus.FAILED, "run.failed");
                case PAUSED -> new TerminalProjection(
                    RunStatus.PAUSED, TurnStatus.WAITING_APPROVAL,
                    WorkItemStatus.COMPLETED, "run.paused");
                case CANCELLED -> new TerminalProjection(
                    RunStatus.CANCELLED, TurnStatus.CANCELLED,
                    WorkItemStatus.CANCELLED, "run.cancelled");
                case TIMED_OUT -> new TerminalProjection(
                    RunStatus.FAILED, TurnStatus.TIMED_OUT,
                    WorkItemStatus.FAILED, "run.timed_out");
            };
        }
    }
}
