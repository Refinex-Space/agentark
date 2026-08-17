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

package space.refinex.agentark.runtime.adapter.out.fake;

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.runtime.domain.RuntimeConflictException;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.domain.RuntimeStateMachine;
import space.refinex.agentark.runtime.port.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 提供线程安全的 Runtime 权威端口内存实现，用于在接入 Provider 和 MySQL 前验证状态机。
 *
 * @author refinex
 */
public final class InMemoryRuntimeStore implements
    RuntimeRepository,
    RuntimeEventStore,
    CheckpointStore,
    AgentStateStore,
    ApprovalRepository,
    LeaseManager,
    RuntimeWorkQueue,
    UsageRecorder,
    RuntimeInstanceRepository {

    /**
     * Session 存储。
     */
    private final Map<SessionId, Session> sessions = new HashMap<>();

    /**
     * Turn 存储。
     */
    private final Map<TurnId, Turn> turns = new HashMap<>();

    /**
     * Run 存储。
     */
    private final Map<RunId, Run> runs = new HashMap<>();

    /**
     * Work Item 存储。
     */
    private final Map<RunId, RuntimeWorkItem> workItems = new HashMap<>();

    /**
     * Event 存储。
     */
    private final Map<EventId, RuntimeEvent> events = new HashMap<>();

    /**
     * Runtime Instance 存储。
     */
    private final Map<String, RuntimeInstance> instances = new HashMap<>();

    /**
     * Approval 存储。
     */
    private final Map<ApprovalId, Approval> approvals = new HashMap<>();

    /**
     * Agent State Version 存储。
     */
    private final Map<JobId, AgentStateVersion> stateVersions = new HashMap<>();

    /**
     * Checkpoint 存储。
     */
    private final Map<JobId, Checkpoint> checkpoints = new HashMap<>();

    /**
     * 幂等记录存储。
     */
    private final Map<String, IdempotencyRecord> idempotencyRecords = new HashMap<>();

    /**
     * Runtime Outbox 存储。
     */
    private final Map<EventId, RuntimeOutboxEvent> outboxEvents = new HashMap<>();

    /**
     * Usage 存储。
     */
    private final Map<EventId, UsageRecord> usageRecords = new HashMap<>();

    /**
     * Provider Request ID 去重索引。
     */
    private final Set<String> providerRequestIds = new HashSet<>();

    /**
     * 创建空的线程安全内存 Runtime Store。
     */
    public InMemoryRuntimeStore() {
    }

    /**
     * 原子创建固定 Snapshot 的 Session 与幂等结果。
     *
     * @param session     新 Session
     * @param idempotency 完成态幂等记录
     */
    @Override
    public synchronized void insertSession(Session session, IdempotencyRecord idempotency) {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(idempotency, "idempotency must not be null");
        if (sessions.containsKey(session.id())
            || idempotencyRecords.containsKey(idempotencyKey(idempotency))) {
            throw new RuntimeConflictException("session or idempotency record already exists");
        }
        sessions.put(session.id(), session);
        idempotencyRecords.put(idempotencyKey(idempotency), idempotency);
    }

    /**
     * 读取 Session。
     *
     * @param sessionId Session 标识
     * @return Session
     */
    @Override
    public synchronized Optional<Session> findSession(SessionId sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * 在内存锁保护下分配 Turn 序号。
     *
     * @param sessionId Session 标识
     * @return 下一个 Turn 序号
     */
    @Override
    public synchronized long nextTurnSequence(SessionId sessionId) {
        return turns.values().stream()
            .filter(turn -> turn.sessionId().equals(sessionId))
            .mapToLong(Turn::sequence)
            .max()
            .orElse(0) + 1;
    }

    /**
     * 原子创建 Turn、Run、Work Item、接受 Event、Outbox 和幂等结果。
     *
     * @param turn        新 Turn
     * @param run         首个 Run
     * @param workItem    Work Item
     * @param eventId     Event 标识
     * @param occurredAt  接受时刻
     * @param outbox      Outbox
     * @param idempotency 幂等结果
     * @return 已分配双重序号的接受 Event
     */
    @Override
    public synchronized RuntimeEvent insertAcceptedTurn(
        Turn turn,
        Run run,
        RuntimeWorkItem workItem,
        EventId eventId,
        Instant occurredAt,
        RuntimeOutboxEvent outbox,
        IdempotencyRecord idempotency) {
        Session session = requireSession(turn.sessionId());
        if (session.status() != SessionStatus.ACTIVE || !run.turnId().equals(turn.id())
            || !workItem.runId().equals(run.id()) || turns.containsKey(turn.id())
            || runs.containsKey(run.id()) || workItems.containsKey(run.id())
            || idempotencyRecords.containsKey(idempotencyKey(idempotency))) {
            throw new RuntimeConflictException("accepted turn aggregate is inconsistent");
        }
        long sessionSequence = session.eventSequence() + 1;
        RuntimeEvent accepted = new RuntimeEvent(
            eventId,
            turn.organizationId(),
            turn.projectId(),
            turn.sessionId(),
            turn.id(),
            run.id(),
            sessionSequence,
            1,
            "run.accepted",
            1,
            run.id().asString().replace("-", ""),
            RuntimePayload.inline("{\"status\":\"ACCEPTED\"}"),
            occurredAt,
            FencingToken.unclaimed());
        sessions.put(session.id(), copySession(session, sessionSequence));
        turns.put(turn.id(), turn);
        runs.put(run.id(), copyRunSequence(run, 1));
        workItems.put(run.id(), workItem);
        events.put(accepted.id(), accepted);
        outboxEvents.put(outbox.id(), outbox);
        idempotencyRecords.put(idempotencyKey(idempotency), idempotency);
        return accepted;
    }

    /**
     * 为失败或超时 Turn 追加新 Run Attempt，保留旧 Run。
     *
     * @param turn     当前 Turn
     * @param run      新 Run
     * @param workItem 新 Work Item
     * @param outbox   重试 Outbox
     */
    @Override
    public synchronized void insertRetryRun(
        Turn turn, Run run, RuntimeWorkItem workItem, RuntimeOutboxEvent outbox) {
        Turn stored = requireTurn(turn.id());
        RuntimeStateMachine.requireRetryable(stored.status());
        if (runs.containsKey(run.id()) || workItems.containsKey(run.id())
            || !run.turnId().equals(turn.id())) {
            throw new RuntimeConflictException("retry run already exists or belongs to another turn");
        }
        long expectedAttempt = runs.values().stream()
            .filter(item -> item.turnId().equals(turn.id()))
            .mapToInt(Run::attemptNumber)
            .max()
            .orElse(0) + 1L;
        if (run.attemptNumber() != expectedAttempt) {
            throw new RuntimeConflictException("retry attempt number is not monotonic");
        }
        runs.put(run.id(), run);
        workItems.put(run.id(), workItem);
        turns.put(turn.id(), new Turn(
            stored.id(), stored.organizationId(), stored.projectId(), stored.sessionId(),
            stored.sequence(), stored.input(), stored.inputHash(), TurnStatus.QUEUED,
            Optional.of(run.id()), FencingToken.unclaimed(), stored.version() + 1,
            stored.createdAt(), run.createdAt()));
        outboxEvents.put(outbox.id(), outbox);
    }

    /**
     * 读取 Turn。
     *
     * @param turnId Turn 标识
     * @return Turn
     */
    @Override
    public synchronized Optional<Turn> findTurn(TurnId turnId) {
        return Optional.ofNullable(turns.get(turnId));
    }

    /**
     * 读取 Run。
     *
     * @param runId Run 标识
     * @return Run
     */
    @Override
    public synchronized Optional<Run> findRun(RunId runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    /**
     * 插入独立命令幂等记录。
     *
     * @param idempotency 幂等记录
     */
    @Override
    public synchronized void insertIdempotency(IdempotencyRecord idempotency) {
        if (idempotencyRecords.putIfAbsent(idempotencyKey(idempotency), idempotency) != null) {
            throw new RuntimeConflictException("idempotency record already exists");
        }
    }

    /**
     * 原子放弃不可恢复的旧 Run，并追加新的恢复 Attempt。
     *
     * @param turn        当前 Turn
     * @param abandoned   旧 Run
     * @param retry       新 Run Attempt
     * @param workItem    新 Work Item
     * @param outboxEvent 恢复诊断 Outbox
     * @param occurredAt  状态转换时刻
     */
    @Override
    public synchronized void replaceOrphanRun(
        Turn turn,
        Run abandoned,
        Run retry,
        RuntimeWorkItem workItem,
        RuntimeOutboxEvent outboxEvent,
        Instant occurredAt) {
        Run stored = requireRun(abandoned.id());
        Turn storedTurn = requireTurn(turn.id());
        RuntimeStateMachine.requireRunTransition(stored.status(), RunStatus.ABANDONED);
        if (!stored.fencingToken().equals(abandoned.fencingToken())
            || !storedTurn.currentRunId().equals(Optional.of(stored.id()))
            || runs.containsKey(retry.id()) || workItems.containsKey(retry.id())) {
            throw new RuntimeConflictException("orphan recovery state conflicts");
        }
        runs.put(stored.id(), new Run(
            stored.id(), stored.organizationId(), stored.projectId(), stored.sessionId(),
            stored.turnId(), stored.attemptNumber(), stored.runtimeProvider(),
            stored.compilerVersion(), RunStatus.ABANDONED, stored.eventSequence(),
            stored.fencingToken(), stored.startedAt(), Optional.of(occurredAt),
            Optional.of("RUNTIME_OWNER_LOST"), stored.createdAt()));
        complete(stored.id(), stored.fencingToken(), WorkItemStatus.FAILED);
        runs.put(retry.id(), retry);
        workItems.put(retry.id(), workItem);
        turns.put(storedTurn.id(), new Turn(
            storedTurn.id(), storedTurn.organizationId(), storedTurn.projectId(),
            storedTurn.sessionId(), storedTurn.sequence(), storedTurn.input(),
            storedTurn.inputHash(), TurnStatus.QUEUED, Optional.of(retry.id()),
            FencingToken.unclaimed(), storedTurn.version() + 1,
            storedTurn.createdAt(), occurredAt));
        outboxEvents.put(outboxEvent.id(), outboxEvent);
    }

    /**
     * 使用当前令牌转换 Run 状态。
     *
     * @param runId        Run 标识
     * @param current      预期状态
     * @param target       目标状态
     * @param fencingToken 当前令牌
     * @param occurredAt   转换时刻
     * @param errorCode    失败错误码
     * @return 成功时为一，否则为零
     */
    @Override
    public synchronized int transitionRun(
        RunId runId,
        RunStatus current,
        RunStatus target,
        FencingToken fencingToken,
        Instant occurredAt,
        Optional<String> errorCode) {
        Run run = runs.get(runId);
        if (run == null || run.status() != current || !run.fencingToken().equals(fencingToken)) {
            return 0;
        }
        RuntimeStateMachine.requireRunTransition(current, target);
        Optional<Instant> startedAt = run.startedAt();
        if (target == RunStatus.RUNNING && startedAt.isEmpty()) {
            startedAt = Optional.of(occurredAt);
        }
        Optional<Instant> endedAt = RuntimeStateMachine.isTerminal(target)
            ? Optional.of(occurredAt) : Optional.empty();
        runs.put(runId, new Run(
            run.id(), run.organizationId(), run.projectId(), run.sessionId(), run.turnId(),
            run.attemptNumber(), run.runtimeProvider(), run.compilerVersion(), target,
            run.eventSequence(), fencingToken, startedAt, endedAt, errorCode, run.createdAt()));
        return 1;
    }

    /**
     * 使用当前令牌转换 Turn 状态。
     *
     * @param turnId       Turn 标识
     * @param current      预期状态
     * @param target       目标状态
     * @param fencingToken 当前令牌
     * @param occurredAt   转换时刻
     * @return 成功时为一，否则为零
     */
    @Override
    public synchronized int transitionTurn(
        TurnId turnId,
        TurnStatus current,
        TurnStatus target,
        FencingToken fencingToken,
        Instant occurredAt) {
        Turn turn = turns.get(turnId);
        if (turn == null || turn.status() != current || !turn.fencingToken().equals(fencingToken)) {
            return 0;
        }
        RuntimeStateMachine.requireTurnTransition(current, target);
        turns.put(turnId, new Turn(
            turn.id(), turn.organizationId(), turn.projectId(), turn.sessionId(), turn.sequence(),
            turn.input(), turn.inputHash(), target, turn.currentRunId(), fencingToken,
            turn.version() + 1, turn.createdAt(), occurredAt));
        return 1;
    }

    /**
     * 将递增令牌同步到 Run 与 Turn，拒绝倒退或跳过当前 Run。
     *
     * @param runId        Run 标识
     * @param turnId       Turn 标识
     * @param fencingToken 新令牌
     */
    @Override
    public synchronized void assignFencingToken(
        RunId runId, TurnId turnId, FencingToken fencingToken) {
        Run run = requireRun(runId);
        Turn turn = requireTurn(turnId);
        if (!turn.currentRunId().equals(Optional.of(runId))
            || fencingToken.value() <= run.fencingToken().value()
            || fencingToken.value() <= turn.fencingToken().value()) {
            throw new RuntimeConflictException("fencing token is stale or run is no longer current");
        }
        runs.put(runId, new Run(
            run.id(), run.organizationId(), run.projectId(), run.sessionId(), run.turnId(),
            run.attemptNumber(), run.runtimeProvider(), run.compilerVersion(), run.status(),
            run.eventSequence(), fencingToken, run.startedAt(), run.endedAt(),
            run.errorCode(), run.createdAt()));
        turns.put(turnId, new Turn(
            turn.id(), turn.organizationId(), turn.projectId(), turn.sessionId(), turn.sequence(),
            turn.input(), turn.inputHash(), turn.status(), turn.currentRunId(), fencingToken,
            turn.version() + 1, turn.createdAt(), turn.updatedAt()));
    }

    /**
     * 追加 Runtime Outbox 事实。
     *
     * @param outboxEvent Outbox 事件
     */
    @Override
    public synchronized void insertOutbox(RuntimeOutboxEvent outboxEvent) {
        if (outboxEvents.putIfAbsent(outboxEvent.id(), outboxEvent) != null) {
            throw new RuntimeConflictException("runtime outbox event already exists");
        }
    }

    /**
     * 读取幂等记录。
     *
     * @param scopeType      作用域类型
     * @param scopeId        作用域标识
     * @param idempotencyKey 幂等键
     * @return 幂等记录
     */
    @Override
    public synchronized Optional<IdempotencyRecord> findIdempotency(
        String scopeType, String scopeId, String idempotencyKey) {
        return Optional.ofNullable(idempotencyRecords.get(
            idempotencyKey(scopeType, scopeId, idempotencyKey)));
    }

    /**
     * 分配 Session/Run 双重序号并追加不可变 Event。
     *
     * @param eventId        Event 标识
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param sessionId      Session 标识
     * @param turnId         Turn 标识
     * @param runId          Run 标识
     * @param type           事件类型
     * @param schemaVersion  Schema 版本
     * @param traceId        追踪标识
     * @param payload        载荷
     * @param occurredAt     发生时刻
     * @param fencingToken   当前令牌
     * @return 持久 Event
     */
    @Override
    public synchronized RuntimeEvent append(
        EventId eventId,
        OrganizationId organizationId,
        ProjectId projectId,
        SessionId sessionId,
        TurnId turnId,
        RunId runId,
        String type,
        int schemaVersion,
        String traceId,
        RuntimePayload payload,
        Instant occurredAt,
        FencingToken fencingToken) {
        Session session = requireSession(sessionId);
        Run run = requireRun(runId);
        if (events.containsKey(eventId) || !run.turnId().equals(turnId)
            || !run.fencingToken().equals(fencingToken)) {
            throw new RuntimeConflictException("event id or fencing token is invalid");
        }
        long sessionSequence = session.eventSequence() + 1;
        long runSequence = run.eventSequence() + 1;
        RuntimeEvent event = new RuntimeEvent(
            eventId, organizationId, projectId, sessionId, turnId, runId,
            sessionSequence, runSequence, type, schemaVersion, traceId, payload,
            occurredAt, fencingToken);
        sessions.put(sessionId, copySession(session, sessionSequence));
        runs.put(runId, copyRunSequence(run, runSequence));
        events.put(eventId, event);
        return event;
    }

    /**
     * 按 Session Sequence 增量读取 Event。
     *
     * @param sessionId     Session 标识
     * @param afterSequence 已消费序号
     * @param limit         最大数量
     * @return 有序 Event
     */
    @Override
    public synchronized List<RuntimeEvent> listAfter(
        SessionId sessionId, long afterSequence, int limit) {
        if (afterSequence < 0 || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("event cursor or limit is invalid");
        }
        return events.values().stream()
            .filter(event -> event.sessionId().equals(sessionId)
                && event.sessionSequence() > afterSequence)
            .sorted(Comparator.comparingLong(RuntimeEvent::sessionSequence))
            .limit(limit)
            .toList();
    }

    /**
     * 按 Session Sequence 增量读取单个 Run 的 Event。
     *
     * @param runId         Run 标识
     * @param afterSequence 已消费 Session Sequence
     * @param limit         最大数量
     * @return 有序 Event
     */
    @Override
    public synchronized List<RuntimeEvent> listRunAfter(
        RunId runId, long afterSequence, int limit) {
        if (afterSequence < 0 || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("event cursor or limit is invalid");
        }
        return events.values().stream()
            .filter(event -> event.runId().equals(runId)
                && event.sessionSequence() > afterSequence)
            .sorted(Comparator.comparingLong(RuntimeEvent::sessionSequence))
            .limit(limit)
            .toList();
    }

    /**
     * 按 Event 标识读取单条事实。
     *
     * @param eventId Event 标识
     * @return Event
     */
    @Override
    public synchronized Optional<RuntimeEvent> find(EventId eventId) {
        return Optional.ofNullable(events.get(eventId));
    }

    /**
     * 注册或替换同 Key Runtime Instance。
     *
     * @param instance Runtime Instance
     */
    @Override
    public synchronized void register(RuntimeInstance instance) {
        instances.put(instance.instanceKey(), instance);
    }

    /**
     * 刷新 Runtime Instance 心跳。
     *
     * @param instanceKey 实例稳定 Key
     * @param heartbeatAt 当前 UTC 时刻
     * @return 实例存在时为 true
     */
    @Override
    public synchronized boolean heartbeat(String instanceKey, Instant heartbeatAt) {
        RuntimeInstance current = instances.get(instanceKey);
        if (current == null) {
            return false;
        }
        instances.put(instanceKey, new RuntimeInstance(
            current.id(), current.instanceKey(), current.startedAt(), heartbeatAt,
            current.capabilities(), current.drainStatus()));
        return true;
    }

    /**
     * 更新 Runtime Instance 排空状态和心跳。
     *
     * @param instanceKey 实例稳定 Key
     * @param status      目标排空状态
     * @param occurredAt  状态变化时刻
     * @return 实例存在时为 true
     */
    @Override
    public synchronized boolean updateDrainStatus(
        String instanceKey, DrainStatus status, Instant occurredAt) {
        RuntimeInstance current = instances.get(instanceKey);
        if (current == null) {
            return false;
        }
        instances.put(instanceKey, new RuntimeInstance(
            current.id(), current.instanceKey(), current.startedAt(), occurredAt,
            current.capabilities(), status));
        return true;
    }

    /**
     * 按最近心跳倒序列出 Runtime Instance。
     *
     * @param limit 最大读取数量
     * @return Runtime Instance 列表
     */
    @Override
    public synchronized List<RuntimeInstance> list(int limit) {
        return instances.values().stream()
            .sorted(Comparator.comparing(RuntimeInstance::heartbeatAt).reversed())
            .limit(limit)
            .toList();
    }

    /**
     * 追加引用已提交 State 的 Checkpoint。
     *
     * @param checkpoint Checkpoint
     */
    @Override
    public synchronized void append(Checkpoint checkpoint) {
        AgentStateVersion state = stateVersions.get(checkpoint.agentStateId());
        Run run = requireRun(checkpoint.runId());
        if (state == null || !state.committed() || !state.runId().equals(run.id())
            || state.stateVersion() != checkpoint.agentStateVersion()
            || !run.fencingToken().equals(checkpoint.fencingToken())
            || checkpoints.putIfAbsent(checkpoint.id(), checkpoint) != null) {
            throw new RuntimeConflictException("checkpoint does not reference visible current state");
        }
    }

    /**
     * 查找最新可恢复 Checkpoint。
     *
     * @param runId Run 标识
     * @return 最新 Checkpoint
     */
    @Override
    public synchronized Optional<Checkpoint> findLatestRecoverable(RunId runId) {
        return checkpoints.values().stream()
            .filter(item -> item.runId().equals(runId) && item.recoverable())
            .max(Comparator.comparingLong(Checkpoint::sequence));
    }

    /**
     * 追加 Agent State Version。
     *
     * @param stateVersion 新状态版本
     */
    @Override
    public synchronized void append(AgentStateVersion stateVersion) {
        Run run = requireRun(stateVersion.runId());
        if (!run.fencingToken().equals(stateVersion.fencingToken())
            || stateVersions.containsKey(stateVersion.id())
            || stateVersions.values().stream().anyMatch(existing ->
            existing.sessionId().equals(stateVersion.sessionId())
                && existing.agentKey().equals(stateVersion.agentKey())
                && existing.stateKey().equals(stateVersion.stateKey())
                && existing.itemIndex() == stateVersion.itemIndex()
                && existing.stateVersion() == stateVersion.stateVersion())) {
            throw new RuntimeConflictException("state version or fencing token conflicts");
        }
        stateVersions.put(stateVersion.id(), stateVersion);
    }

    /**
     * 将 State Version 标记为可恢复可见。
     *
     * @param stateVersion State Version
     * @param fencingToken 当前令牌
     */
    @Override
    public synchronized void commit(
        AgentStateVersion stateVersion, FencingToken fencingToken) {
        AgentStateVersion stored = stateVersions.get(stateVersion.id());
        Run run = requireRun(stateVersion.runId());
        if (stored == null || stored.committed() || !run.fencingToken().equals(fencingToken)
            || !stored.fencingToken().equals(fencingToken)) {
            throw new RuntimeConflictException("state version cannot be committed");
        }
        stateVersions.put(stored.id(), new AgentStateVersion(
            stored.id(), stored.organizationId(), stored.projectId(), stored.sessionId(),
            stored.runId(), stored.agentKey(), stored.stateKey(), stored.itemIndex(),
            stored.stateVersion(), stored.payload(), stored.contentHash(), true,
            stored.fencingToken(), stored.createdAt()));
    }

    /**
     * 查找稳定 State Key 的最新已提交版本。
     *
     * @param sessionId Session 标识
     * @param agentKey  Agent Key
     * @param stateKey  State Key
     * @param itemIndex 元素下标
     * @return 最新已提交状态
     */
    @Override
    public synchronized Optional<AgentStateVersion> findLatestCommitted(
        SessionId sessionId, String agentKey, String stateKey, int itemIndex) {
        return stateVersions.values().stream()
            .filter(item -> item.sessionId().equals(sessionId)
                && item.agentKey().equals(agentKey) && item.stateKey().equals(stateKey)
                && item.itemIndex() == itemIndex && item.committed())
            .max(Comparator.comparingLong(AgentStateVersion::stateVersion));
    }

    /**
     * 创建待审批记录。
     *
     * @param approval Approval
     */
    @Override
    public synchronized void insert(Approval approval) {
        if (approvals.putIfAbsent(approval.id(), approval) != null) {
            throw new RuntimeConflictException("approval already exists");
        }
    }

    /**
     * 读取 Approval。
     *
     * @param approvalId Approval 标识
     * @return Approval
     */
    @Override
    public synchronized Optional<Approval> find(ApprovalId approvalId) {
        return Optional.ofNullable(approvals.get(approvalId));
    }

    /**
     * 使用乐观锁写入审批决策。
     *
     * @param approvalId      Approval 标识
     * @param expectedVersion 预期版本
     * @param target          目标状态
     * @param decisionBy      决策主体
     * @param decisionAt      决策时刻
     * @return 更新行数
     */
    @Override
    public synchronized int decide(
        ApprovalId approvalId,
        long expectedVersion,
        ApprovalStatus target,
        String decisionBy,
        Instant decisionAt) {
        Approval approval = approvals.get(approvalId);
        if (approval == null || approval.expectedVersion() != expectedVersion) {
            return 0;
        }
        RuntimeStateMachine.requireApprovalTransition(approval.status(), target);
        approvals.put(approvalId, new Approval(
            approval.id(), approval.organizationId(), approval.projectId(), approval.runId(),
            approval.toolName(), approval.action(), approval.argumentHash(), approval.policyVersion(),
            target, expectedVersion + 1, approval.expiresAt(), Optional.of(decisionBy),
            Optional.of(decisionAt), approval.createdAt()));
        return 1;
    }

    /**
     * 读取同一 Run 的全部 Approval。
     *
     * @param runId Run 标识
     * @return 创建时间有序的 Approval
     */
    @Override
    public synchronized List<Approval> listForRun(RunId runId) {
        return approvals.values().stream()
            .filter(approval -> approval.runId().equals(runId))
            .sorted(Comparator.comparing(Approval::createdAt)
                .thenComparing(approval -> approval.id().asString()))
            .toList();
    }

    /**
     * 按项目、状态和 UUIDv7 游标读取 Approval。
     *
     * @param projectId 项目标识
     * @param status    可选状态
     * @param afterId   可选游标
     * @param limit     最大数量
     * @return Approval 列表
     */
    @Override
    public synchronized List<Approval> list(
        ProjectId projectId,
        Optional<ApprovalStatus> status,
        Optional<ApprovalId> afterId,
        int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("approval page limit must be between 1 and 100");
        }
        return approvals.values().stream()
            .filter(approval -> approval.projectId().equals(projectId))
            .filter(approval -> status.map(value -> approval.status() == value).orElse(true))
            .filter(approval -> afterId
                .map(value -> approval.id().asString().compareTo(value.asString()) > 0)
                .orElse(true))
            .sorted(Comparator.comparing(approval -> approval.id().asString()))
            .limit(limit)
            .toList();
    }

    /**
     * 取消 Run 下仍待决的 Approval。
     *
     * @param runId      Run 标识
     * @param decisionBy 决策主体
     * @param decisionAt 决策时刻
     * @return 更新数量
     */
    @Override
    public synchronized int cancelPending(
        RunId runId, String decisionBy, Instant decisionAt) {
        List<Approval> pending = approvals.values().stream()
            .filter(approval -> approval.runId().equals(runId)
                && approval.status() == ApprovalStatus.PENDING)
            .toList();
        pending.forEach(approval -> decide(
            approval.id(), approval.expectedVersion(), ApprovalStatus.CANCELLED,
            decisionBy, decisionAt));
        return pending.size();
    }

    /**
     * 续约当前 Work Item Lease。
     *
     * @param runId        Run 标识
     * @param owner        Owner Key
     * @param fencingToken 当前令牌
     * @param now          当前时刻
     * @param ttl          有效期
     * @return 是否成功
     */
    @Override
    public synchronized boolean renew(
        RunId runId, String owner, FencingToken fencingToken, Instant now, Duration ttl) {
        RuntimeWorkItem item = workItems.get(runId);
        if (item == null || item.status() != WorkItemStatus.CLAIMED
            || !item.claimedBy().equals(Optional.of(owner))
            || !item.fencingToken().equals(fencingToken)
            || item.claimUntil().orElseThrow().isBefore(now)) {
            return false;
        }
        workItems.put(runId, new RuntimeWorkItem(
            item.id(), item.runId(), item.status(), item.priority(), item.availableAt(),
            item.claimedBy(), Optional.of(now.plus(ttl)), item.fencingToken(),
            item.attemptCount(), item.createdAt()));
        return true;
    }

    /**
     * 校验当前 Owner、令牌和 Lease 期限。
     *
     * @param runId        Run 标识
     * @param owner        Owner Key
     * @param fencingToken 当前令牌
     * @param now          当前时刻
     */
    @Override
    public synchronized void requireCurrent(
        RunId runId, String owner, FencingToken fencingToken, Instant now) {
        RuntimeWorkItem item = workItems.get(runId);
        if (item == null || item.status() != WorkItemStatus.CLAIMED
            || !item.claimedBy().equals(Optional.of(owner))
            || !item.fencingToken().equals(fencingToken)
            || !item.claimUntil().orElseThrow().isAfter(now)) {
            throw new RuntimeConflictException("lease owner or fencing token is stale");
        }
    }

    /**
     * 持久入队 Work Item。
     *
     * @param item Work Item
     */
    @Override
    public synchronized void enqueue(RuntimeWorkItem item) {
        if (workItems.putIfAbsent(item.runId(), item) != null) {
            throw new RuntimeConflictException("work item already exists for run");
        }
    }

    /**
     * Claim 一个可执行或 Lease 已过期的 Work Item。
     *
     * @param instanceKey Owner Key
     * @param now         当前时刻
     * @param ttl         Lease 有效期
     * @return Claim 后 Work Item
     */
    @Override
    public synchronized Optional<RuntimeWorkItem> claimNext(
        String instanceKey, Instant now, Duration ttl) {
        Optional<RuntimeWorkItem> selected = workItems.values().stream()
            .filter(item -> item.availableAt().compareTo(now) <= 0)
            .filter(item -> item.status() == WorkItemStatus.READY
                || (item.status() == WorkItemStatus.CLAIMED
                && !item.claimUntil().orElseThrow().isAfter(now)))
            .sorted(Comparator.comparingInt(RuntimeWorkItem::priority).reversed()
                .thenComparing(RuntimeWorkItem::availableAt)
                .thenComparing(item -> item.id().asString()))
            .findFirst();
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        RuntimeWorkItem item = selected.orElseThrow();
        RuntimeWorkItem claimed = new RuntimeWorkItem(
            item.id(), item.runId(), WorkItemStatus.CLAIMED, item.priority(), item.availableAt(),
            Optional.of(instanceKey), Optional.of(now.plus(ttl)),
            new FencingToken(item.fencingToken().value() + 1),
            item.attemptCount() + 1, item.createdAt());
        workItems.put(item.runId(), claimed);
        return Optional.of(claimed);
    }

    /**
     * 使用当前令牌完成 Work Item。
     *
     * @param runId        Run 标识
     * @param fencingToken 当前令牌
     * @param terminal     终态
     */
    @Override
    public synchronized void complete(
        RunId runId, FencingToken fencingToken, WorkItemStatus terminal) {
        if (terminal != WorkItemStatus.COMPLETED && terminal != WorkItemStatus.FAILED
            && terminal != WorkItemStatus.CANCELLED) {
            throw new IllegalArgumentException("work item target must be terminal");
        }
        RuntimeWorkItem item = workItems.get(runId);
        if (item == null || (item.status() != WorkItemStatus.CLAIMED
            && item.status() != WorkItemStatus.READY
            && item.status() != WorkItemStatus.COMPLETED)
            || !item.fencingToken().equals(fencingToken)) {
            throw new RuntimeConflictException("work item owner is stale");
        }
        workItems.put(runId, new RuntimeWorkItem(
            item.id(), item.runId(), terminal, item.priority(), item.availableAt(),
            Optional.empty(), Optional.empty(), item.fencingToken(),
            item.attemptCount(), item.createdAt()));
    }

    /**
     * 将暂停 Run 的已完成 Work Item 重新置为 READY。
     *
     * @param runId        Run 标识
     * @param fencingToken 暂停时令牌
     * @param availableAt  最早 Claim 时刻
     */
    @Override
    public synchronized void requeueForResume(
        RunId runId, FencingToken fencingToken, Instant availableAt) {
        RuntimeWorkItem item = workItems.get(runId);
        if (item == null || item.status() != WorkItemStatus.COMPLETED
            || !item.fencingToken().equals(fencingToken)) {
            throw new RuntimeConflictException("approval resume work item conflicts");
        }
        workItems.put(runId, new RuntimeWorkItem(
            item.id(), item.runId(), WorkItemStatus.READY, item.priority(), availableAt,
            Optional.empty(), Optional.empty(), item.fencingToken(), item.attemptCount(),
            item.createdAt()));
    }

    /**
     * 追加 Provider 去重的 Usage 事实。
     *
     * @param usageRecord Usage 记录
     */
    @Override
    public synchronized void record(UsageRecord usageRecord) {
        if (usageRecords.containsKey(usageRecord.id())) {
            return;
        }
        if (usageRecord.providerRequestId().isPresent()
            && !providerRequestIds.add(usageRecord.providerRequestId().orElseThrow())) {
            return;
        }
        usageRecords.put(usageRecord.id(), usageRecord);
    }

    /**
     * 返回当前已追加 Event 数量，供测试证明先持久化语义。
     *
     * @return Event 数量
     */
    public synchronized int eventCount() {
        return events.size();
    }

    /**
     * 返回当前 Outbox 数量，供事务编排测试。
     *
     * @return Outbox 数量
     */
    public synchronized int outboxCount() {
        return outboxEvents.size();
    }

    /**
     * 返回指定 Turn 的全部 Run Attempt 数量。
     *
     * @param turnId Turn 标识
     * @return Run Attempt 数量
     */
    public synchronized long runCount(TurnId turnId) {
        return runs.values().stream().filter(run -> run.turnId().equals(turnId)).count();
    }

    /**
     * 要求 Session 存在。
     *
     * @param sessionId Session 标识
     * @return Session
     */
    private Session requireSession(SessionId sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new RuntimeConflictException("session does not exist");
        }
        return session;
    }

    /**
     * 要求 Turn 存在。
     *
     * @param turnId Turn 标识
     * @return Turn
     */
    private Turn requireTurn(TurnId turnId) {
        Turn turn = turns.get(turnId);
        if (turn == null) {
            throw new RuntimeConflictException("turn does not exist");
        }
        return turn;
    }

    /**
     * 要求 Run 存在。
     *
     * @param runId Run 标识
     * @return Run
     */
    private Run requireRun(RunId runId) {
        Run run = runs.get(runId);
        if (run == null) {
            throw new RuntimeConflictException("run does not exist");
        }
        return run;
    }

    /**
     * 复制 Session 并更新 Event Sequence。
     *
     * @param session       原 Session
     * @param eventSequence 新序号
     * @return 更新后的 Session
     */
    private Session copySession(Session session, long eventSequence) {
        return new Session(
            session.id(), session.organizationId(), session.projectId(), session.deploymentId(),
            session.revisionId(), session.snapshotId(), session.snapshotHash(),
            session.participantMetadata(), session.channelMetadata(), session.status(),
            eventSequence, session.version(), session.createdAt(), session.updatedAt());
    }

    /**
     * 复制 Run 并更新 Event Sequence。
     *
     * @param run           原 Run
     * @param eventSequence 新序号
     * @return 更新后的 Run
     */
    private Run copyRunSequence(Run run, long eventSequence) {
        return new Run(
            run.id(), run.organizationId(), run.projectId(), run.sessionId(), run.turnId(),
            run.attemptNumber(), run.runtimeProvider(), run.compilerVersion(), run.status(),
            eventSequence, run.fencingToken(), run.startedAt(), run.endedAt(),
            run.errorCode(), run.createdAt());
    }

    /**
     * 构造幂等记录复合 Key。
     *
     * @param record 幂等记录
     * @return 复合 Key
     */
    private String idempotencyKey(IdempotencyRecord record) {
        return idempotencyKey(record.scopeType(), record.scopeId(), record.idempotencyKey());
    }

    /**
     * 构造幂等记录复合 Key。
     *
     * @param scopeType 作用域类型
     * @param scopeId   作用域标识
     * @param key       幂等键
     * @return 无歧义复合 Key
     */
    private String idempotencyKey(String scopeType, String scopeId, String key) {
        return scopeType + "\u0000" + scopeId + "\u0000" + key;
    }
}
