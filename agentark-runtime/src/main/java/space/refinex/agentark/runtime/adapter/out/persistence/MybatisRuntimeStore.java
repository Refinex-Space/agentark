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

package space.refinex.agentark.runtime.adapter.out.persistence;

import org.springframework.transaction.annotation.Transactional;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.runtime.adapter.out.persistence.RuntimePersistenceRows.*;
import space.refinex.agentark.runtime.domain.RuntimeConflictException;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.domain.RuntimeStateMachine;
import space.refinex.agentark.runtime.port.*;
import space.refinex.agentark.runtime.port.UsageGovernanceStore.UsageExportRecord;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 使用显式 MyBatis SQL 实现 Runtime 聚合、Event、队列、状态、审批、恢复和用量端口。
 *
 * @author refinex
 */
public class MybatisRuntimeStore implements
    RuntimeRepository,
    RuntimeEventStore,
    CheckpointStore,
    AgentStateStore,
    ApprovalRepository,
    LeaseManager,
    RuntimeWorkQueue,
    UsageRecorder,
    UsageGovernanceStore,
    RuntimeMetricsRepository,
    RuntimeInstanceRepository {

    /**
     * 运行时所属 MyBatis 数据库映射器。
     */
    private final RuntimeMapper mapper;

    /**
     * 非敏感元数据 JSON 映射器。
     */
    private final JsonMapper jsonMapper;

    /**
     * 创建 MyBatis Runtime Store。
     *
     * @param mapper     Runtime Mapper
     * @param jsonMapper JSON 映射器
     */
    public MybatisRuntimeStore(RuntimeMapper mapper, JsonMapper jsonMapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * 原子创建固定 Snapshot 的 Session 与幂等结果。
     *
     * @param session     新 Session
     * @param idempotency 完成态幂等记录
     */
    @Override
    @Transactional
    public void insertSession(Session session, IdempotencyRecord idempotency) {
        mapper.insertSession(sessionRow(session));
        mapper.insertIdempotency(idempotencyRow(idempotency));
    }

    /**
     * 读取 Session。
     *
     * @param sessionId Session 标识
     * @return Session
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Session> findSession(SessionId sessionId) {
        return mapper.findSession(sessionId.value()).map(this::session);
    }

    /**
     * 锁定 Session 并分配下一 Turn 序号。
     *
     * @param sessionId Session 标识
     * @return 下一 Turn 序号
     */
    @Override
    @Transactional
    public long nextTurnSequence(SessionId sessionId) {
        mapper.lockSessionSequence(sessionId.value())
            .orElseThrow(() -> new RuntimeConflictException("session does not exist"));
        return mapper.nextTurnSequence(sessionId.value());
    }

    /**
     * 原子创建 Turn、Run、Work、接受 Event、Outbox 和幂等结果。
     *
     * @param turn        新 Turn
     * @param run         首个 Run
     * @param workItem    Work Item
     * @param eventId     接受 Event 标识
     * @param occurredAt  接受时刻
     * @param outbox      Outbox
     * @param idempotency 幂等结果
     * @param quotaReservationRef Control 并发配额 Reservation 引用
     * @return 持久接受 Event
     */
    @Override
    @Transactional
    public RuntimeEvent insertAcceptedTurn(
        Turn turn,
        Run run,
        RuntimeWorkItem workItem,
        EventId eventId,
        Instant occurredAt,
        RuntimeOutboxEvent outbox,
        IdempotencyRecord idempotency,
        Optional<String> quotaReservationRef) {
        mapper.insertTurn(turnRow(turn));
        mapper.insertRun(runRow(run), quotaReservationRef.orElse(null));
        mapper.insertWorkItem(workItemRow(workItem));
        RuntimeEvent accepted = append(
            eventId, turn.organizationId(), turn.projectId(), turn.sessionId(), turn.id(), run.id(),
            "run.accepted", 1, run.id().asString().replace("-", ""),
            RuntimePayload.inline("{\"status\":\"ACCEPTED\"}"),
            occurredAt, FencingToken.unclaimed());
        insertOutbox(outbox);
        mapper.insertIdempotency(idempotencyRow(idempotency));
        return accepted;
    }

    /**
     * 读取 Turn 级 Control 并发配额 Reservation 引用。
     *
     * @param turnId Turn 标识
     * @return 可选 Reservation 引用
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<String> findQuotaReservation(TurnId turnId) {
        return mapper.findQuotaReservation(turnId.value());
    }

    /**
     * 追加 Retry Run 和 Work Item，并只移动 Turn 当前指针。
     *
     * @param turn     当前 Turn
     * @param run      新 Run
     * @param workItem 新 Work Item
     * @param outbox   重试 Outbox
     */
    @Override
    @Transactional
    public void insertRetryRun(
        Turn turn, Run run, RuntimeWorkItem workItem, RuntimeOutboxEvent outbox) {
        RuntimeStateMachine.requireRetryable(turn.status());
        mapper.insertRun(runRow(run), null);
        mapper.insertWorkItem(workItemRow(workItem));
        requireOne(mapper.attachRetryRun(turn.id().value(), run.id().value(), run.createdAt()),
            "retry turn pointer conflicted");
        insertOutbox(outbox);
    }

    /**
     * 读取 Turn。
     *
     * @param turnId Turn 标识
     * @return Turn
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Turn> findTurn(TurnId turnId) {
        return mapper.findTurn(turnId.value()).map(this::turn);
    }

    /**
     * 读取 Run。
     *
     * @param runId Run 标识
     * @return Run
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Run> findRun(RunId runId) {
        return mapper.findRun(runId.value()).map(this::run);
    }

    /**
     * 使用当前状态和 Fencing Token 转换 Run。
     *
     * @param runId        Run 标识
     * @param current      当前状态
     * @param target       目标状态
     * @param fencingToken 当前令牌
     * @param occurredAt   转换时刻
     * @param errorCode    错误码
     * @return 更新行数
     */
    @Override
    public int transitionRun(
        RunId runId,
        RunStatus current,
        RunStatus target,
        FencingToken fencingToken,
        Instant occurredAt,
        Optional<String> errorCode) {
        RuntimeStateMachine.requireRunTransition(current, target);
        return mapper.transitionRun(
            runId.value(), current.name(), target.name(), fencingToken.value(), occurredAt,
            errorCode.orElse(null));
    }

    /**
     * 使用当前状态和 Fencing Token 转换 Turn。
     *
     * @param turnId       Turn 标识
     * @param current      当前状态
     * @param target       目标状态
     * @param fencingToken 当前令牌
     * @param occurredAt   转换时刻
     * @return 更新行数
     */
    @Override
    public int transitionTurn(
        TurnId turnId,
        TurnStatus current,
        TurnStatus target,
        FencingToken fencingToken,
        Instant occurredAt) {
        RuntimeStateMachine.requireTurnTransition(current, target);
        return mapper.transitionTurn(
            turnId.value(), current.name(), target.name(), fencingToken.value(), occurredAt);
    }

    /**
     * 原子将递增令牌写入 Run 与当前 Turn。
     *
     * @param runId        Run 标识
     * @param turnId       Turn 标识
     * @param fencingToken 新令牌
     */
    @Override
    @Transactional
    public void assignFencingToken(
        RunId runId, TurnId turnId, FencingToken fencingToken) {
        requireOne(mapper.assignRunFencing(runId.value(), fencingToken.value()),
            "run fencing token is stale");
        requireOne(mapper.assignTurnFencing(
                turnId.value(), runId.value(), fencingToken.value()),
            "turn fencing token is stale");
    }

    /**
     * 追加 Runtime Outbox，内联 JSON 与 ObjectRef 均保持单一表示。
     *
     * @param outboxEvent Outbox Event
     */
    @Override
    public void insertOutbox(RuntimeOutboxEvent outboxEvent) {
        PayloadColumns payload = payloadColumns(outboxEvent.payload());
        if ("INLINE".equals(payload.storage())) {
            mapper.insertInlineOutbox(
                outboxEvent.id().value(), outboxEvent.aggregateType(), outboxEvent.aggregateId(),
                outboxEvent.eventType(), payload.json(), outboxEvent.status().name(),
                outboxEvent.availableAt(), outboxEvent.attempts(), outboxEvent.createdAt());
        } else {
            mapper.insertObjectOutbox(
                outboxEvent.id().value(), outboxEvent.aggregateType(), outboxEvent.aggregateId(),
                outboxEvent.eventType(), payload.uri(), payload.hash(), payload.size(),
                payload.mediaType(), outboxEvent.status().name(), outboxEvent.availableAt(),
                outboxEvent.attempts(), outboxEvent.createdAt());
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
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findIdempotency(
        String scopeType, String scopeId, String idempotencyKey) {
        return mapper.findIdempotency(scopeType, scopeId, idempotencyKey)
            .map(this::idempotency);
    }

    /**
     * 插入独立命令幂等记录，并由外层应用事务与状态变化共同提交。
     *
     * @param idempotency 幂等记录
     */
    @Override
    public void insertIdempotency(IdempotencyRecord idempotency) {
        mapper.insertIdempotency(idempotencyRow(idempotency));
    }

    /**
     * 原子放弃失联旧 Run，创建新 Attempt、Work Item 并移动 Turn 指针。
     *
     * @param turn        当前 Turn
     * @param abandoned   已使用新 Fencing Token 接管的旧 Run
     * @param retry       新 Run Attempt
     * @param workItem    新 Work Item
     * @param outboxEvent 恢复诊断 Outbox
     * @param occurredAt  状态转换时刻
     */
    @Override
    @Transactional
    public void replaceOrphanRun(
        Turn turn,
        Run abandoned,
        Run retry,
        RuntimeWorkItem workItem,
        RuntimeOutboxEvent outboxEvent,
        Instant occurredAt) {
        requireOne(mapper.transitionRun(
                abandoned.id().value(), abandoned.status().name(), RunStatus.ABANDONED.name(),
                abandoned.fencingToken().value(), occurredAt, "RUNTIME_OWNER_LOST"),
            "orphan run transition conflicted");
        requireOne(mapper.completeWorkItem(
                abandoned.id().value(), abandoned.fencingToken().value(),
                WorkItemStatus.FAILED.name(), occurredAt),
            "orphan work item completion conflicted");
        mapper.insertRun(runRow(retry), null);
        mapper.insertWorkItem(workItemRow(workItem));
        requireOne(mapper.attachRecoveryRun(
                turn.id().value(), abandoned.id().value(), retry.id().value(),
                abandoned.fencingToken().value(), occurredAt),
            "orphan turn recovery pointer conflicted");
        insertOutbox(outboxEvent);
    }

    /**
     * 锁定 Session/Run 计数器、分别递增一次并追加 Event 与可选 ObjectRef。
     *
     * @param eventId        Event 标识
     * @param organizationId 组织标识
     * @param projectId      项目标识
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
    @Transactional
    public RuntimeEvent append(
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
        long sessionSequence = mapper.lockSessionSequence(sessionId.value())
            .orElseThrow(() -> new RuntimeConflictException("session does not exist")) + 1;
        long runSequence = mapper.lockRunSequence(runId.value())
            .orElseThrow(() -> new RuntimeConflictException("run does not exist")) + 1;
        requireOne(mapper.incrementSessionEventSequence(sessionId.value()),
            "session event sequence conflicted");
        requireOne(mapper.incrementRunEventSequence(runId.value()),
            "run event sequence conflicted");
        PayloadColumns columns = payloadColumns(payload);
        EventRow row = new EventRow(
            eventId.value(), organizationId.value(), projectId.value(), sessionId.value(),
            turnId.value(), runId.value(), sessionSequence, runSequence, type, schemaVersion,
            traceId, columns.storage(), columns.json(), occurredAt, fencingToken.value(),
            columns.uri(), columns.hash(), columns.size(), columns.mediaType());
        mapper.insertEvent(row);
        if ("OBJECT".equals(columns.storage())) {
            mapper.insertEventPayloadRef(row);
        }
        return event(row);
    }

    /**
     * 增量读取 Session Event。
     *
     * @param sessionId     Session 标识
     * @param afterSequence 已消费序号
     * @param limit         最大数量
     * @return 有序 Event
     */
    @Override
    @Transactional(readOnly = true)
    public List<RuntimeEvent> listAfter(SessionId sessionId, long afterSequence, int limit) {
        if (afterSequence < 0 || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("event cursor or limit is invalid");
        }
        return mapper.listEventsAfter(sessionId.value(), afterSequence, limit).stream()
            .map(this::event)
            .toList();
    }

    /**
     * 按 Session Sequence 增量读取单个 Run 的已提交 Event。
     *
     * @param runId         Run 标识
     * @param afterSequence 已消费 Session Sequence
     * @param limit         最大数量
     * @return 有序 Event
     */
    @Override
    @Transactional(readOnly = true)
    public List<RuntimeEvent> listRunAfter(RunId runId, long afterSequence, int limit) {
        if (afterSequence < 0 || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("event cursor or limit is invalid");
        }
        return mapper.listRunEventsAfter(runId.value(), afterSequence, limit).stream()
            .map(this::event)
            .toList();
    }

    /**
     * 按全局 Event 标识读取单条持久事实。
     *
     * @param eventId Event 标识
     * @return Event
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<RuntimeEvent> find(EventId eventId) {
        return mapper.findEvent(eventId.value()).map(this::event);
    }

    /**
     * 追加 Checkpoint，数据库触发器验证 State 可见性与当前令牌。
     *
     * @param checkpoint Checkpoint
     */
    @Override
    public void append(Checkpoint checkpoint) {
        mapper.insertCheckpoint(checkpointRow(checkpoint));
    }

    /**
     * 读取最新可恢复 Checkpoint。
     *
     * @param runId Run 标识
     * @return Checkpoint
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Checkpoint> findLatestRecoverable(RunId runId) {
        return mapper.findLatestCheckpoint(runId.value()).map(this::checkpoint);
    }

    /**
     * 追加 Agent State Version，数据库触发器验证当前令牌。
     *
     * @param stateVersion State Version
     */
    @Override
    public void append(AgentStateVersion stateVersion) {
        mapper.insertState(stateRow(stateVersion));
    }

    /**
     * 一次性提交 Agent State Version。
     *
     * @param stateVersion State Version
     * @param fencingToken 当前令牌
     */
    @Override
    public void commit(AgentStateVersion stateVersion, FencingToken fencingToken) {
        requireOne(mapper.commitState(stateVersion.id().value(), fencingToken.value()),
            "agent state commit conflicted");
    }

    /**
     * 读取最新已提交 State Version。
     *
     * @param sessionId Session 标识
     * @param agentKey  Agent Key
     * @param stateKey  State Key
     * @param itemIndex 元素下标
     * @return State Version
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AgentStateVersion> findLatestCommitted(
        SessionId sessionId, String agentKey, String stateKey, int itemIndex) {
        return mapper.findLatestCommittedState(
            sessionId.value(), agentKey, stateKey, itemIndex).map(this::state);
    }

    /**
     * 插入 Approval。
     *
     * @param approval Approval
     */
    @Override
    public void insert(Approval approval) {
        mapper.insertApproval(approvalRow(approval));
    }

    /**
     * 读取 Approval。
     *
     * @param approvalId Approval 标识
     * @return Approval
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Approval> find(ApprovalId approvalId) {
        return mapper.findApproval(approvalId.value()).map(this::approval);
    }

    /**
     * 使用乐观锁写入 Approval 终态。
     *
     * @param approvalId      Approval 标识
     * @param expectedVersion 预期版本
     * @param target          目标状态
     * @param decisionBy      决策主体
     * @param decisionAt      决策时刻
     * @return 更新行数
     */
    @Override
    public int decide(
        ApprovalId approvalId,
        long expectedVersion,
        ApprovalStatus target,
        String decisionBy,
        Instant decisionAt) {
        RuntimeStateMachine.requireApprovalTransition(ApprovalStatus.PENDING, target);
        return mapper.decideApproval(
            approvalId.value(), expectedVersion, target.name(), decisionBy, decisionAt);
    }

    /**
     * 读取同一 Run 的全部 Approval。
     *
     * @param runId Run 标识
     * @return 创建顺序稳定的 Approval
     */
    @Override
    @Transactional(readOnly = true)
    public List<Approval> listForRun(RunId runId) {
        return mapper.listApprovalsForRun(runId.value()).stream().map(this::approval).toList();
    }

    /**
     * 增量读取项目内 Approval。
     *
     * @param projectId 项目标识
     * @param status    可选状态
     * @param afterId   可选 UUIDv7 游标
     * @param limit     最大数量
     * @return Approval 列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<Approval> list(
        ProjectId projectId,
        Optional<ApprovalStatus> status,
        Optional<ApprovalId> afterId,
        int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("approval page limit must be between 1 and 100");
        }
        return mapper.listApprovals(
                projectId.value(), status.map(Enum::name).orElse(null),
                afterId.map(ApprovalId::value).orElse(null), limit).stream()
            .map(this::approval)
            .toList();
    }

    /**
     * 取消 Run 下仍待决的 Approval。
     *
     * @param runId      Run 标识
     * @param decisionBy 系统或调用主体
     * @param decisionAt 取消时刻
     * @return 更新数量
     */
    @Override
    public int cancelPending(RunId runId, String decisionBy, Instant decisionAt) {
        return mapper.cancelPendingApprovals(runId.value(), decisionBy, decisionAt);
    }

    /**
     * 续约当前 Owner Lease。
     *
     * @param runId        Run 标识
     * @param owner        Owner Key
     * @param fencingToken 当前令牌
     * @param now          当前时刻
     * @param ttl          有效期
     * @return 是否成功
     */
    @Override
    public boolean renew(
        RunId runId, String owner, FencingToken fencingToken, Instant now, Duration ttl) {
        return mapper.renewWorkItem(
            runId.value(), owner, fencingToken.value(), now, now.plus(ttl)) == 1;
    }

    /**
     * 校验当前 Owner、令牌和 Lease 未过期。
     *
     * @param runId        Run 标识
     * @param owner        Owner Key
     * @param fencingToken 当前令牌
     * @param now          当前时刻
     */
    @Override
    @Transactional(readOnly = true)
    public void requireCurrent(
        RunId runId, String owner, FencingToken fencingToken, Instant now) {
        RuntimeWorkItem item = mapper.findWorkItem(runId.value()).map(this::workItem)
            .orElseThrow(() -> new RuntimeConflictException("work item does not exist"));
        if (item.status() != WorkItemStatus.CLAIMED
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
    public void enqueue(RuntimeWorkItem item) {
        mapper.insertWorkItem(workItemRow(item));
    }

    /**
     * 使用 SKIP LOCKED Claim 下一 Work Item 并递增 Fencing Token。
     *
     * @param instanceKey Owner Key
     * @param now         当前时刻
     * @param ttl         Lease 有效期
     * @return Claim 后 Work Item
     */
    @Override
    @Transactional
    public Optional<RuntimeWorkItem> claimNext(String instanceKey, Instant now, Duration ttl) {
        Optional<WorkItemRow> selected = mapper.lockNextWorkItem(now);
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        WorkItemRow row = selected.orElseThrow();
        requireOne(mapper.claimWorkItem(row.id(), instanceKey, now.plus(ttl), now),
            "work item claim conflicted");
        return mapper.findWorkItem(row.runId()).map(this::workItem);
    }

    /**
     * 使用当前令牌完成 Work Item。
     *
     * @param runId        Run 标识
     * @param fencingToken 当前令牌
     * @param terminal     终态
     */
    @Override
    public void complete(
        RunId runId, FencingToken fencingToken, WorkItemStatus terminal) {
        if (terminal != WorkItemStatus.COMPLETED && terminal != WorkItemStatus.FAILED
            && terminal != WorkItemStatus.CANCELLED) {
            throw new IllegalArgumentException("work item target must be terminal");
        }
        requireOne(mapper.completeWorkItem(
                runId.value(), fencingToken.value(), terminal.name(), Instant.now()),
            "work item owner is stale");
    }

    /**
     * 将已暂停 Run 的 Work Item 重新置为 READY，下一次 Claim 将产生新 Token。
     *
     * @param runId        Run 标识
     * @param fencingToken 暂停时令牌
     * @param availableAt  最早 Claim 时刻
     */
    @Override
    public void requeueForResume(
        RunId runId, FencingToken fencingToken, Instant availableAt) {
        requireOne(mapper.requeueWorkItem(
                runId.value(), fencingToken.value(), availableAt),
            "approval resume work item conflicted");
    }

    /**
     * 注册或刷新 Runtime Instance。
     *
     * @param instance Runtime Instance
     */
    @Override
    public void register(RuntimeInstance instance) {
        mapper.upsertRuntimeInstance(new RuntimeInstanceRow(
            instance.id().value(), instance.instanceKey(), instance.startedAt(),
            instance.heartbeatAt(), write(instance.capabilities()),
            instance.drainStatus().name()));
    }

    /**
     * 刷新 Runtime Instance 心跳。
     *
     * @param instanceKey 实例 Key
     * @param heartbeatAt 当前时刻
     * @return 实例存在时为 true
     */
    @Override
    public boolean heartbeat(String instanceKey, Instant heartbeatAt) {
        return mapper.heartbeatRuntimeInstance(instanceKey, heartbeatAt) == 1;
    }

    /**
     * 更新 Runtime Instance 排空状态。
     *
     * @param instanceKey 实例 Key
     * @param status      排空状态
     * @param occurredAt  更新时间
     * @return 实例存在时为 true
     */
    @Override
    public boolean updateDrainStatus(
        String instanceKey, DrainStatus status, Instant occurredAt) {
        return mapper.updateRuntimeInstanceDrain(
            instanceKey, status.name(), occurredAt) == 1;
    }

    /**
     * 按最近心跳倒序列出 Runtime Instance。
     *
     * @param limit 最大读取数量
     * @return Runtime Instance 列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<RuntimeInstance> list(int limit) {
        return mapper.listRuntimeInstances(limit).stream()
            .map(row -> new RuntimeInstance(
                new JobId(row.id()), row.instanceKey(), row.startedAt(), row.heartbeatAt(),
                readStringMap(row.capabilities()), DrainStatus.valueOf(row.drainStatus())))
            .toList();
    }

    /**
     * 幂等追加 Usage 事实。
     *
     * @param usageRecord Usage 记录
     */
    @Override
    public void record(UsageRecord usageRecord) {
        mapper.insertUsage(
            usageRecord.id().value(), usageRecord.runId().value(), usageRecord.eventId().value(),
            usageRecord.provider(), usageRecord.model().orElse(null),
            usageRecord.tool().orElse(null), usageRecord.providerRequestId().orElse(null),
            usageRecord.inputUnits(), usageRecord.outputUnits(), usageRecord.durationMillis(),
            usageRecord.estimated(), usageRecord.priceVersion().orElse(null),
            usageRecord.occurredAt());
    }

    /**
     * 短事务 Claim 待汇聚 Usage，并把下一次重试时间推进三十秒。
     *
     * @param now   当前时间
     * @param limit 最大数量
     * @return 已 Claim Usage 投影
     */
    @Override
    @Transactional
    public List<UsageExportRecord> claimUsageForGovernance(Instant now, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("usage governance limit must be between 1 and 100");
        }
        List<RuntimeMapper.UsageGovernanceRow> rows = mapper.lockUsageForGovernance(now, limit);
        Instant retryAt = now.plusSeconds(30);
        List<UsageExportRecord> result = new ArrayList<>(rows.size());
        for (RuntimeMapper.UsageGovernanceRow row : rows) {
            if (mapper.claimUsageForGovernance(row.id(), retryAt) != 1) {
                throw new RuntimeConflictException("usage governance claim conflicted");
            }
            result.add(new UsageExportRecord(
                new EventId(row.id()), new OrganizationId(row.organizationId()),
                new ProjectId(row.projectId()), new SessionId(row.sessionId()),
                new TurnId(row.turnId()), new RunId(row.runId()),
                new RevisionId(row.revisionId()), new DeploymentId(row.deploymentId()),
                row.usageType(), row.provider(), Optional.ofNullable(row.model()),
                Optional.ofNullable(row.tool()), row.inputUnits(), row.outputUnits(),
                row.cachedTokens(), row.embeddingTokens(), row.toolCalls(),
                row.sandboxDurationMillis(), row.estimated(),
                Optional.ofNullable(row.priceVersion()), Optional.ofNullable(row.currency()),
                row.costAmount(), row.occurredAt(), row.governanceAttempts() + 1));
        }
        return List.copyOf(result);
    }

    /**
     * 确认 Usage 已由 Control 接收。
     *
     * @param id  Usage UUIDv7
     * @param now 确认时间
     */
    @Override
    public void markUsageExported(EventId id, Instant now) {
        if (mapper.markUsageExported(id.value(), now) != 1) {
            throw new RuntimeConflictException("usage governance confirmation conflicted");
        }
    }

    /**
     * 标记 Usage 汇聚达到重试终态。
     *
     * @param id Usage UUIDv7
     */
    @Override
    public void markUsageExportFailed(EventId id) {
        if (mapper.markUsageExportFailed(id.value()) != 1) {
            throw new RuntimeConflictException("usage governance failure transition conflicted");
        }
    }

    /** 返回活跃 Session 数量。 */
    @Override
    public long activeSessions() {
        return mapper.countActiveSessions();
    }

    /** 返回活跃 Run 数量。 */
    @Override
    public long activeRuns() {
        return mapper.countActiveRuns();
    }

    /** 返回待审批数量。 */
    @Override
    public long pendingApprovals() {
        return mapper.countPendingApprovals();
    }

    /** 返回最老 Runtime Outbox 积压秒数。 */
    @Override
    public long outboxLagSeconds(Instant now) {
        return mapper.oldestPendingOutbox()
            .map(value -> Math.max(0L, Duration.between(value, now).toSeconds()))
            .orElse(0L);
    }

    /**
     * 将 Session 领域模型转换为数据库行。
     *
     * @param value Session
     * @return 数据库行
     */
    private SessionRow sessionRow(Session value) {
        return new SessionRow(
            value.id().value(), value.organizationId().value(), value.projectId().value(),
            value.deploymentId().value(), value.revisionId().value(), value.snapshotId().value(),
            hash(value.snapshotHash()), write(value.participantMetadata()),
            write(value.channelMetadata()), value.status().name(), value.eventSequence(),
            value.version(), value.createdAt(), value.updatedAt());
    }

    /**
     * 将 Session 数据库行转换为领域模型。
     *
     * @param row 数据库行
     * @return Session
     */
    private Session session(SessionRow row) {
        return new Session(
            new SessionId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new DeploymentId(row.deploymentId()),
            new RevisionId(row.revisionId()), new SnapshotId(row.snapshotId()),
            checksum(row.snapshotHash()), readStringMap(row.participantMetadata()),
            readStringMap(row.channelMetadata()), SessionStatus.valueOf(row.status()),
            row.eventSequence(), row.version(), row.createdAt(), row.updatedAt());
    }

    /**
     * 将 Turn 转换为数据库行。
     *
     * @param value Turn
     * @return 数据库行
     */
    private TurnRow turnRow(Turn value) {
        PayloadColumns payload = payloadColumns(value.input());
        return new TurnRow(
            value.id().value(), value.organizationId().value(), value.projectId().value(),
            value.sessionId().value(), value.sequence(), payload.storage(), payload.json(),
            payload.uri(), payload.size(), payload.mediaType(), hash(value.inputHash()),
            value.status().name(), value.currentRunId().map(RunId::value).orElse(null),
            value.fencingToken().value(), value.version(), value.createdAt(), value.updatedAt());
    }

    /**
     * 将 Turn 数据库行转换为领域模型。
     *
     * @param row 数据库行
     * @return Turn
     */
    private Turn turn(TurnRow row) {
        return new Turn(
            new TurnId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new SessionId(row.sessionId()), row.sequence(),
            payload(row.inputStorage(), row.inputJson(), row.inputObjectUri(),
                row.inputHash(), row.inputObjectSize(), row.inputMediaType()),
            checksum(row.inputHash()), TurnStatus.valueOf(row.status()),
            Optional.ofNullable(row.currentRunId()).map(RunId::new),
            new FencingToken(row.fencingToken()), row.version(), row.createdAt(), row.updatedAt());
    }

    /**
     * 将 Run 转换为数据库行。
     *
     * @param value Run
     * @return 数据库行
     */
    private RunRow runRow(Run value) {
        return new RunRow(
            value.id().value(), value.organizationId().value(), value.projectId().value(),
            value.sessionId().value(), value.turnId().value(), value.attemptNumber(),
            value.runtimeProvider(), value.compilerVersion(), value.status().name(),
            value.eventSequence(), value.fencingToken().value(), value.startedAt().orElse(null),
            value.endedAt().orElse(null), value.errorCode().orElse(null), value.createdAt());
    }

    /**
     * 将 Run 数据库行转换为领域模型。
     *
     * @param row 数据库行
     * @return Run
     */
    private Run run(RunRow row) {
        return new Run(
            new RunId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new SessionId(row.sessionId()),
            new TurnId(row.turnId()), row.attemptNumber(), row.runtimeProvider(),
            row.compilerVersion(), RunStatus.valueOf(row.status()), row.eventSequence(),
            new FencingToken(row.fencingToken()), Optional.ofNullable(row.startedAt()),
            Optional.ofNullable(row.endedAt()), Optional.ofNullable(row.errorCode()), row.createdAt());
    }

    /**
     * 将 Work Item 转换为数据库行。
     *
     * @param value Work Item
     * @return 数据库行
     */
    private WorkItemRow workItemRow(RuntimeWorkItem value) {
        return new WorkItemRow(
            value.id().value(), value.runId().value(), value.status().name(), value.priority(),
            value.availableAt(), value.claimedBy().orElse(null), value.claimUntil().orElse(null),
            value.fencingToken().value(), value.attemptCount(), value.createdAt());
    }

    /**
     * 将 Work Item 数据库行转换为领域模型。
     *
     * @param row 数据库行
     * @return Work Item
     */
    private RuntimeWorkItem workItem(WorkItemRow row) {
        return new RuntimeWorkItem(
            new JobId(row.id()), new RunId(row.runId()), WorkItemStatus.valueOf(row.status()),
            row.priority(), row.availableAt(), Optional.ofNullable(row.claimedBy()),
            Optional.ofNullable(row.claimUntil()), new FencingToken(row.fencingToken()),
            row.attemptCount(), row.createdAt());
    }

    /**
     * 将 Event 数据库行转换为领域模型。
     *
     * @param row 数据库行
     * @return Event
     */
    private RuntimeEvent event(EventRow row) {
        return new RuntimeEvent(
            new EventId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new SessionId(row.sessionId()),
            new TurnId(row.turnId()), new RunId(row.runId()), row.sessionSequence(),
            row.runSequence(), row.type(), row.schemaVersion(),
            row.traceId(),
            payload(row.payloadStorage(), row.payloadJson(), row.objectUri(),
                row.objectHash(), row.objectSize(), row.mediaType()),
            row.occurredAt(), new FencingToken(row.fencingToken()));
    }

    /**
     * 将幂等记录转换为数据库行。
     *
     * @param value 幂等记录
     * @return 数据库行
     */
    private IdempotencyRow idempotencyRow(IdempotencyRecord value) {
        return new IdempotencyRow(
            value.scopeType(), value.scopeId(), value.idempotencyKey(), hash(value.requestHash()),
            value.resultRef().orElse(null), value.status().name(), value.expiresAt(), value.createdAt());
    }

    /**
     * 将幂等数据库行转换为领域模型。
     *
     * @param row 数据库行
     * @return 幂等记录
     */
    private IdempotencyRecord idempotency(IdempotencyRow row) {
        return new IdempotencyRecord(
            row.scopeType(), row.scopeId(), row.idempotencyKey(), checksum(row.requestHash()),
            Optional.ofNullable(row.resultRef()), IdempotencyStatus.valueOf(row.status()),
            row.expiresAt(), row.createdAt());
    }

    /**
     * 将 Approval 转换为数据库行。
     *
     * @param value Approval
     * @return 数据库行
     */
    private ApprovalRow approvalRow(Approval value) {
        return new ApprovalRow(
            value.id().value(), value.organizationId().value(), value.projectId().value(),
            value.runId().value(), value.toolName(), value.action(), hash(value.argumentHash()),
            value.policyVersion(), value.status().name(), value.expectedVersion(), value.expiresAt(),
            value.decisionBy().orElse(null), value.decisionAt().orElse(null), value.createdAt());
    }

    /**
     * 将 Approval 数据库行转换为领域模型。
     *
     * @param row 数据库行
     * @return Approval
     */
    private Approval approval(ApprovalRow row) {
        return new Approval(
            new ApprovalId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new RunId(row.runId()), row.toolName(),
            row.actionCode(), checksum(row.argumentHash()), row.policyVersion(),
            ApprovalStatus.valueOf(row.status()), row.version(), row.expiresAt(),
            Optional.ofNullable(row.decisionBy()), Optional.ofNullable(row.decisionAt()),
            row.createdAt());
    }

    /**
     * 将 State Version 转换为数据库行。
     *
     * @param value State Version
     * @return 数据库行
     */
    private StateRow stateRow(AgentStateVersion value) {
        PayloadColumns payload = payloadColumns(value.payload());
        return new StateRow(
            value.id().value(), value.organizationId().value(), value.projectId().value(),
            value.sessionId().value(), value.runId().value(), value.agentKey(), value.stateKey(),
            value.itemIndex(), value.stateVersion(), payload.storage(), payload.json(),
            payload.uri(), payload.size(), payload.mediaType(), hash(value.contentHash()),
            value.committed(), value.fencingToken().value(), value.createdAt());
    }

    /**
     * 将 State 数据库行转换为领域模型。
     *
     * @param row 数据库行
     * @return State Version
     */
    private AgentStateVersion state(StateRow row) {
        return new AgentStateVersion(
            new JobId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new SessionId(row.sessionId()), new RunId(row.runId()),
            row.agentKey(), row.stateKey(), row.itemIndex(), row.stateVersion(),
            payload(row.stateStorage(), row.stateJson(), row.objectUri(), row.contentHash(),
                row.objectSize(), row.mediaType()),
            checksum(row.contentHash()), row.committed(), new FencingToken(row.fencingToken()),
            row.createdAt());
    }

    /**
     * 将 Checkpoint 转换为数据库行。
     *
     * @param value Checkpoint
     * @return 数据库行
     */
    private CheckpointRow checkpointRow(Checkpoint value) {
        return new CheckpointRow(
            value.id().value(), value.runId().value(), value.sequence(),
            value.agentStateId().value(), value.agentStateVersion(), value.eventSequence(),
            hash(value.contentHash()), value.recoverable(), value.fencingToken().value(),
            value.createdAt());
    }

    /**
     * 将 Checkpoint 数据库行转换为领域模型。
     *
     * @param row 数据库行
     * @return Checkpoint
     */
    private Checkpoint checkpoint(CheckpointRow row) {
        return new Checkpoint(
            new JobId(row.id()), new RunId(row.runId()), row.sequence(),
            new JobId(row.agentStateId()), row.agentStateVersion(), row.eventSequence(),
            checksum(row.contentHash()), row.recoverable(), new FencingToken(row.fencingToken()),
            row.createdAt());
    }

    /**
     * 将二选一 Runtime Payload 转换为数据库列。
     *
     * @param payload Runtime Payload
     * @return 数据库列
     */
    private PayloadColumns payloadColumns(RuntimePayload payload) {
        if (payload.inlineJson().isPresent()) {
            return new PayloadColumns(
                "INLINE", payload.inlineJson().orElseThrow(), null, null, null, null);
        }
        ObjectRef reference = payload.objectRef().orElseThrow();
        return new PayloadColumns(
            "OBJECT", null, reference.uri().toString(), hash(reference.checksum()),
            reference.size(), reference.mediaType());
    }

    /**
     * 将数据库二选一载荷列转换为 Runtime Payload。
     *
     * @param storage   保存方式
     * @param json      内联 JSON
     * @param uri       对象 URI
     * @param hashBytes SHA-256 字节
     * @param size      对象字节数
     * @param mediaType 媒体类型
     * @return Runtime Payload
     */
    private RuntimePayload payload(
        String storage,
        String json,
        String uri,
        byte[] hashBytes,
        Long size,
        String mediaType) {
        if ("INLINE".equals(storage)) {
            return RuntimePayload.inline(json);
        }
        return RuntimePayload.external(new ObjectRef(
            URI.create(uri), checksum(hashBytes), size, mediaType));
    }

    /**
     * 将 Checksum 转换为 MySQL BINARY(32)。
     *
     * @param checksum Checksum
     * @return 32 字节摘要
     */
    private byte[] hash(Checksum checksum) {
        return HexFormat.of().parseHex(checksum.hex());
    }

    /**
     * 将 MySQL BINARY(32) 转换为规范 Checksum。
     *
     * @param bytes 32 字节摘要
     * @return Checksum
     */
    private Checksum checksum(byte[] bytes) {
        return new Checksum("sha256:" + HexFormat.of().formatHex(bytes));
    }

    /**
     * 序列化非敏感元数据 JSON。
     *
     * @param value 元数据对象
     * @return JSON
     */
    private String write(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("runtime metadata serialization failed", exception);
        }
    }

    /**
     * 反序列化字符串 Map 元数据。
     *
     * @param value JSON
     * @return 不可变字符串 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> readStringMap(String value) {
        try {
            return Map.copyOf(jsonMapper.readValue(value, Map.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored runtime metadata is invalid", exception);
        }
    }

    /**
     * 将条件更新数量转换为显式冲突。
     *
     * @param updated 实际更新行数
     * @param message 冲突上下文
     */
    private void requireOne(int updated, String message) {
        if (updated != 1) {
            throw new RuntimeConflictException(message);
        }
    }
}
