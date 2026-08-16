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

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 集中定义供应商中立的 Runtime 聚合、追加事实、恢复状态、用量和队列模型。
 *
 * @author refinex
 */
public final class RuntimeModels {

    /**
     * 禁止实例化纯领域模型容器。
     */
    private RuntimeModels() {
    }

    /**
     * 定义 Session 生命周期状态。
     *
     * @author refinex
     */
    public enum SessionStatus {
        /**
         * Session 可继续接收 Turn。
         */
        ACTIVE,
        /**
         * Session 已正常关闭，不再接收 Turn。
         */
        CLOSED,
        /**
         * Session 因不可恢复错误关闭。
         */
        FAILED
    }

    /**
     * 定义 Turn 的稳定状态机代码。
     *
     * @author refinex
     */
    public enum TurnStatus {
        /**
         * 请求已持久接收。
         */
        ACCEPTED,
        /**
         * 已创建持久 Work Item，等待 Worker。
         */
        QUEUED,
        /**
         * 当前 Run 正在执行。
         */
        RUNNING,
        /**
         * 当前 Run 等待人工审批。
         */
        WAITING_APPROVAL,
        /**
         * Turn 已成功完成。
         */
        COMPLETED,
        /**
         * Turn 已失败结束。
         */
        FAILED,
        /**
         * Turn 已被调用方取消。
         */
        CANCELLED,
        /**
         * Turn 已超过执行期限。
         */
        TIMED_OUT
    }

    /**
     * 定义每次 Run Attempt 的稳定状态机代码。
     *
     * @author refinex
     */
    public enum RunStatus {
        /**
         * Run 已创建但尚未被 Worker Claim。
         */
        CREATED,
        /**
         * Run 已由持有有效 Lease 的 Worker Claim。
         */
        CLAIMED,
        /**
         * Provider 中立执行引擎正在执行。
         */
        RUNNING,
        /**
         * Run 已持久暂停并可在满足条件后恢复。
         */
        PAUSED,
        /**
         * Run 已成功结束。
         */
        SUCCEEDED,
        /**
         * Run 已失败结束。
         */
        FAILED,
        /**
         * Run 已被取消。
         */
        CANCELLED,
        /**
         * 原 Owner 失联，Run 已放弃且允许新 Attempt。
         */
        ABANDONED
    }

    /**
     * 定义审批请求的稳定状态机代码。
     *
     * @author refinex
     */
    public enum ApprovalStatus {
        /**
         * 等待授权主体决策。
         */
        PENDING,
        /**
         * 授权主体同意执行原始参数。
         */
        APPROVED,
        /**
         * 授权主体拒绝执行。
         */
        REJECTED,
        /**
         * 审批在期限内未完成。
         */
        EXPIRED,
        /**
         * 所属 Run 取消导致审批取消。
         */
        CANCELLED
    }

    /**
     * 定义持久 Work Item 状态。
     *
     * @author refinex
     */
    public enum WorkItemStatus {
        /**
         * 已入队且达到 availableAt 后可 Claim。
         */
        READY,
        /**
         * 已被一个 Runtime Instance Claim。
         */
        CLAIMED,
        /**
         * 对应 Run 已成功完成。
         */
        COMPLETED,
        /**
         * 对应 Run 已失败或放弃。
         */
        FAILED,
        /**
         * 对应 Turn 已取消。
         */
        CANCELLED
    }

    /**
     * 定义 Runtime Instance 排空状态。
     *
     * @author refinex
     */
    public enum DrainStatus {
        /**
         * 正常接收新的 Work Item。
         */
        ACTIVE,
        /**
         * 不再接收新任务，但允许已有任务收尾。
         */
        DRAINING,
        /**
         * 已完成排空。
         */
        DRAINED
    }

    /**
     * 定义幂等记录状态。
     *
     * @author refinex
     */
    public enum IdempotencyStatus {
        /**
         * 首次请求事务正在执行。
         */
        IN_PROGRESS,
        /**
         * 首次请求已成功并保存结果引用。
         */
        COMPLETED,
        /**
         * 首次请求已失败且结果不可复用。
         */
        FAILED
    }

    /**
     * 定义 Runtime Outbox 投递状态。
     *
     * @author refinex
     */
    public enum OutboxStatus {
        /**
         * 等待可靠投递。
         */
        PENDING,
        /**
         * 已由后续 Publisher 确认投递。
         */
        PUBLISHED,
        /**
         * 已达到重试策略的失败状态。
         */
        FAILED
    }

    /**
     * 定义 Provider 中立执行结果。
     *
     * @author refinex
     */
    public enum ExecutionOutcome {
        /**
         * 执行成功完成。
         */
        SUCCEEDED,
        /**
         * 执行失败结束。
         */
        FAILED,
        /**
         * 执行已持久暂停，等待恢复命令。
         */
        PAUSED,
        /**
         * 执行观察到取消请求并结束。
         */
        CANCELLED
    }

    /**
     * @param value 由所属 Lease 原子递增的非负栅栏令牌，零表示尚未 Claim
     * @author refinex
     */
    public record FencingToken(long value) {

        /**
         * 校验栅栏令牌不能为负。
         */
        public FencingToken {
            if (value < 0) {
                throw new IllegalArgumentException("fencing token must not be negative");
            }
        }

        /**
         * 返回尚未 Claim 的初始令牌。
         *
         * @return 值为零的令牌
         */
        public static FencingToken unclaimed() {
            return new FencingToken(0);
        }
    }

    /**
     * @param runId        Lease 所属 Run
     * @param owner        当前 Runtime Instance Key
     * @param fencingToken 本次 Claim 获得的单调栅栏令牌
     * @param expiresAt    Lease 到期时刻
     * @author refinex
     */
    public record Lease(
        RunId runId,
        String owner,
        FencingToken fencingToken,
        Instant expiresAt) {

        /**
         * 校验 Lease 必须属于已 Claim 的 Run 且具有有效 Owner 和正令牌。
         */
        public Lease {
            Objects.requireNonNull(runId, "runId must not be null");
            Objects.requireNonNull(fencingToken, "fencingToken must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
            if (owner == null || owner.isBlank() || fencingToken.value() == 0) {
                throw new IllegalArgumentException("claimed lease metadata is invalid");
            }
        }

        /**
         * 判断指定时刻是否仍在 Lease 有效期内。
         *
         * @param instant 待校验时刻
         * @return 严格早于到期时刻时返回 true
         */
        public boolean isActiveAt(Instant instant) {
            return Objects.requireNonNull(instant, "instant must not be null").isBefore(expiresAt);
        }
    }

    /**
     * @param inlineJson 小载荷的规范 JSON；大载荷时为空
     * @param objectRef  大载荷的受控对象引用；小载荷时为空
     * @author refinex
     */
    public record RuntimePayload(Optional<String> inlineJson, Optional<ObjectRef> objectRef) {

        /**
         * 校验内联 JSON 与 ObjectRef 必须且只能存在一个。
         */
        public RuntimePayload {
            inlineJson = Objects.requireNonNull(inlineJson, "inlineJson must not be null");
            objectRef = Objects.requireNonNull(objectRef, "objectRef must not be null");
            if (inlineJson.isPresent() == objectRef.isPresent()) {
                throw new IllegalArgumentException("exactly one payload representation is required");
            }
            inlineJson.ifPresent(value -> {
                if (value.isBlank()) {
                    throw new IllegalArgumentException("inlineJson must not be blank");
                }
            });
        }

        /**
         * 创建小型内联 JSON 载荷。
         *
         * @param json 不含 Secret 或隐藏推理过程的规范 JSON
         * @return 内联载荷
         */
        public static RuntimePayload inline(String json) {
            return new RuntimePayload(Optional.of(json), Optional.empty());
        }

        /**
         * 创建大载荷对象引用。
         *
         * @param objectRef 已校验 Hash、Size 与媒体类型的对象引用
         * @return 对象载荷
         */
        public static RuntimePayload external(ObjectRef objectRef) {
            return new RuntimePayload(Optional.empty(), Optional.of(objectRef));
        }
    }

    /**
     * @param id                  Session 标识
     * @param organizationId      所属组织
     * @param projectId           所属项目
     * @param deploymentId        创建时解析的 Deployment 标识
     * @param revisionId          创建后固定的 Revision 标识
     * @param snapshotId          创建后固定的 Snapshot 标识
     * @param snapshotHash        创建后固定的 Snapshot 内容 Hash
     * @param participantMetadata 非敏感参与者元数据
     * @param channelMetadata     非敏感渠道元数据
     * @param status              Session 状态：ACTIVE、CLOSED、FAILED
     * @param eventSequence       已分配的最大 Session Event 序号
     * @param version             乐观锁版本
     * @param createdAt           创建时刻
     * @param updatedAt           最近更新时间
     * @author refinex
     */
    public record Session(
        SessionId id,
        OrganizationId organizationId,
        ProjectId projectId,
        DeploymentId deploymentId,
        RevisionId revisionId,
        SnapshotId snapshotId,
        Checksum snapshotHash,
        Map<String, String> participantMetadata,
        Map<String, String> channelMetadata,
        SessionStatus status,
        long eventSequence,
        long version,
        Instant createdAt,
        Instant updatedAt) {

        /**
         * 校验 Session 固定快照、租户与版本字段。
         */
        public Session {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(deploymentId, "deploymentId must not be null");
            Objects.requireNonNull(revisionId, "revisionId must not be null");
            Objects.requireNonNull(snapshotId, "snapshotId must not be null");
            Objects.requireNonNull(snapshotHash, "snapshotHash must not be null");
            participantMetadata = Map.copyOf(Objects.requireNonNull(
                participantMetadata, "participantMetadata must not be null"));
            channelMetadata = Map.copyOf(Objects.requireNonNull(
                channelMetadata, "channelMetadata must not be null"));
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(updatedAt, "updatedAt must not be null");
            if (eventSequence < 0 || version < 0 || updatedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("session counters or timestamps are invalid");
            }
        }
    }

    /**
     * @param id             Turn 标识
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param sessionId      所属 Session
     * @param sequence       Session 内从一开始的 Turn 序号
     * @param input          不含 Secret 和隐藏推理过程的输入载荷
     * @param inputHash      输入载荷规范 Hash
     * @param status         Turn 状态
     * @param currentRunId   当前 Attempt 的 Run 标识
     * @param fencingToken   当前有效栅栏令牌
     * @param version        乐观锁版本
     * @param createdAt      创建时刻
     * @param updatedAt      最近更新时间
     * @author refinex
     */
    public record Turn(
        TurnId id,
        OrganizationId organizationId,
        ProjectId projectId,
        SessionId sessionId,
        long sequence,
        RuntimePayload input,
        Checksum inputHash,
        TurnStatus status,
        Optional<RunId> currentRunId,
        FencingToken fencingToken,
        long version,
        Instant createdAt,
        Instant updatedAt) {

        /**
         * 校验 Turn 序号、输入和状态关联。
         */
        public Turn {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(input, "input must not be null");
            Objects.requireNonNull(inputHash, "inputHash must not be null");
            Objects.requireNonNull(status, "status must not be null");
            currentRunId = Objects.requireNonNull(currentRunId, "currentRunId must not be null");
            Objects.requireNonNull(fencingToken, "fencingToken must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(updatedAt, "updatedAt must not be null");
            if (sequence < 1 || version < 0 || updatedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("turn sequence, version or timestamps are invalid");
            }
        }
    }

    /**
     * @param id              Run 标识
     * @param organizationId  所属组织
     * @param projectId       所属项目
     * @param sessionId       所属 Session
     * @param runId           产生该状态版本的 Run
     * @param turnId          所属 Turn
     * @param attemptNumber   Turn 内从一开始的 Attempt 序号
     * @param runtimeProvider Snapshot 指定的 Runtime Provider
     * @param compilerVersion Provider Adapter 编译器版本
     * @param status          Run 状态
     * @param eventSequence   已分配的最大 Run Event 序号
     * @param fencingToken    当前有效栅栏令牌
     * @param startedAt       首次开始执行时刻
     * @param endedAt         终态结束时刻
     * @param errorCode       失败时的稳定错误码
     * @param createdAt       创建时刻
     * @author refinex
     */
    public record Run(
        RunId id,
        OrganizationId organizationId,
        ProjectId projectId,
        SessionId sessionId,
        TurnId turnId,
        int attemptNumber,
        String runtimeProvider,
        String compilerVersion,
        RunStatus status,
        long eventSequence,
        FencingToken fencingToken,
        Optional<Instant> startedAt,
        Optional<Instant> endedAt,
        Optional<String> errorCode,
        Instant createdAt) {

        /**
         * 校验 Run Attempt、Provider 和终态字段一致性。
         */
        public Run {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(turnId, "turnId must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(fencingToken, "fencingToken must not be null");
            startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
            endedAt = Objects.requireNonNull(endedAt, "endedAt must not be null");
            errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            if (attemptNumber < 1 || runtimeProvider == null || runtimeProvider.isBlank()
                || compilerVersion == null || compilerVersion.isBlank() || eventSequence < 0) {
                throw new IllegalArgumentException("run attempt or provider metadata is invalid");
            }
            if (endedAt.isPresent() != RuntimeStateMachine.isTerminal(status)) {
                throw new IllegalArgumentException("run terminal timestamp is inconsistent");
            }
        }
    }

    /**
     * @param id              全局唯一 Event 标识
     * @param organizationId  所属组织
     * @param projectId       所属项目
     * @param sessionId       所属 Session
     * @param turnId          所属 Turn
     * @param runId           所属 Run
     * @param sessionSequence Session 内单调序号
     * @param runSequence     Run 内单调序号
     * @param type            稳定事件类型
     * @param schemaVersion   Event Schema 正整数版本
     * @param traceId         同一 Run 使用的 16 字节小写十六进制追踪标识
     * @param payload         内联 JSON 或受控 ObjectRef
     * @param occurredAt      事件事实发生时刻
     * @param fencingToken    写入时的有效栅栏令牌
     * @author refinex
     */
    public record RuntimeEvent(
        EventId id,
        OrganizationId organizationId,
        ProjectId projectId,
        SessionId sessionId,
        TurnId turnId,
        RunId runId,
        long sessionSequence,
        long runSequence,
        String type,
        int schemaVersion,
        String traceId,
        RuntimePayload payload,
        Instant occurredAt,
        FencingToken fencingToken) {

        /**
         * 校验追加 Event 的关联、序号和 Schema 版本。
         */
        public RuntimeEvent {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(turnId, "turnId must not be null");
            Objects.requireNonNull(runId, "runId must not be null");
            Objects.requireNonNull(payload, "payload must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            Objects.requireNonNull(fencingToken, "fencingToken must not be null");
            if (sessionSequence < 1 || runSequence < 1 || schemaVersion < 1
                || type == null || !type.matches("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+")) {
                throw new IllegalArgumentException("runtime event metadata is invalid");
            }
            if (traceId == null || !traceId.matches("[0-9a-f]{32}")) {
                throw new IllegalArgumentException("traceId must contain 32 lowercase hex digits");
            }
        }
    }

    /**
     * @param id              Approval 标识
     * @param organizationId  所属组织
     * @param projectId       所属项目
     * @param runId           所属 Run
     * @param toolName        待执行 Tool 稳定名称
     * @param action          待审批动作
     * @param argumentHash    原始参数 Hash，审批后不可替换
     * @param policyVersion   决策时使用的固定策略版本
     * @param status          审批状态
     * @param expectedVersion 乐观锁版本
     * @param expiresAt       审批到期时刻
     * @param decisionBy      决策主体；PENDING 时为空
     * @param decisionAt      决策时刻；PENDING 时为空
     * @param createdAt       创建时刻
     * @author refinex
     */
    public record Approval(
        ApprovalId id,
        OrganizationId organizationId,
        ProjectId projectId,
        RunId runId,
        String toolName,
        String action,
        Checksum argumentHash,
        String policyVersion,
        ApprovalStatus status,
        long expectedVersion,
        Instant expiresAt,
        Optional<String> decisionBy,
        Optional<Instant> decisionAt,
        Instant createdAt) {

        /**
         * 校验审批参数绑定、期限和决策字段一致性。
         */
        public Approval {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(runId, "runId must not be null");
            Objects.requireNonNull(argumentHash, "argumentHash must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
            decisionBy = Objects.requireNonNull(decisionBy, "decisionBy must not be null");
            decisionAt = Objects.requireNonNull(decisionAt, "decisionAt must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            if (toolName == null || toolName.isBlank() || action == null || action.isBlank()
                || policyVersion == null || policyVersion.isBlank() || expectedVersion < 0
                || !expiresAt.isAfter(createdAt)) {
                throw new IllegalArgumentException("approval metadata is invalid");
            }
            if ((status == ApprovalStatus.PENDING) != (decisionBy.isEmpty() && decisionAt.isEmpty())) {
                throw new IllegalArgumentException("approval decision metadata is inconsistent");
            }
        }
    }

    /**
     * @param id           Runtime Instance 标识
     * @param instanceKey  部署范围内稳定实例 Key
     * @param startedAt    启动时刻
     * @param heartbeatAt  最近心跳时刻
     * @param capabilities 低基数能力集合
     * @param drainStatus  排空状态
     * @author refinex
     */
    public record RuntimeInstance(
        JobId id,
        String instanceKey,
        Instant startedAt,
        Instant heartbeatAt,
        Map<String, String> capabilities,
        DrainStatus drainStatus) {

        /**
         * 校验 Runtime Instance 心跳和能力元数据。
         */
        public RuntimeInstance {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(startedAt, "startedAt must not be null");
            Objects.requireNonNull(heartbeatAt, "heartbeatAt must not be null");
            capabilities = Map.copyOf(Objects.requireNonNull(
                capabilities, "capabilities must not be null"));
            Objects.requireNonNull(drainStatus, "drainStatus must not be null");
            if (instanceKey == null || instanceKey.isBlank() || heartbeatAt.isBefore(startedAt)) {
                throw new IllegalArgumentException("runtime instance metadata is invalid");
            }
        }
    }

    /**
     * @param id           Work Item 标识
     * @param runId        唯一关联 Run
     * @param status       队列状态
     * @param priority     数值越大优先级越高
     * @param availableAt  最早 Claim 时刻
     * @param claimedBy    当前 Owner Instance Key
     * @param claimUntil   当前 Lease 到期时刻
     * @param fencingToken 每次 Claim 原子递增的令牌
     * @param attemptCount Claim 次数
     * @param createdAt    创建时刻
     * @author refinex
     */
    public record RuntimeWorkItem(
        JobId id,
        RunId runId,
        WorkItemStatus status,
        int priority,
        Instant availableAt,
        Optional<String> claimedBy,
        Optional<Instant> claimUntil,
        FencingToken fencingToken,
        int attemptCount,
        Instant createdAt) {

        /**
         * 校验 Claim Owner、期限和计数一致性。
         */
        public RuntimeWorkItem {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(runId, "runId must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(availableAt, "availableAt must not be null");
            claimedBy = Objects.requireNonNull(claimedBy, "claimedBy must not be null");
            claimUntil = Objects.requireNonNull(claimUntil, "claimUntil must not be null");
            Objects.requireNonNull(fencingToken, "fencingToken must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            if (attemptCount < 0 || (claimedBy.isPresent() != claimUntil.isPresent())
                || (status == WorkItemStatus.CLAIMED) != claimedBy.isPresent()
                || (status == WorkItemStatus.CLAIMED && fencingToken.value() == 0)) {
                throw new IllegalArgumentException("work item claim metadata is inconsistent");
            }
        }

        /**
         * 将已 Claim 的 Work Item 投影为 Provider 中立 Lease；非 Claim 状态返回空。
         *
         * @return 当前 Lease
         */
        public Optional<Lease> currentLease() {
            if (status != WorkItemStatus.CLAIMED) {
                return Optional.empty();
            }
            return Optional.of(new Lease(
                runId, claimedBy.orElseThrow(), fencingToken, claimUntil.orElseThrow()));
        }
    }

    /**
     * @param id             State Version 行标识
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param sessionId      所属 Session
     * @param runId          产生该状态版本的运行尝试
     * @param agentKey       Snapshot 内 Agent 稳定 Key
     * @param stateKey       Provider 中立状态 Key
     * @param itemIndex      列表状态元素下标，标量使用零
     * @param stateVersion   相同 State Key 下从一开始的版本
     * @param payload        内联 JSON 或 ObjectRef
     * @param contentHash    状态内容 Hash
     * @param committed      是否已对恢复流程可见
     * @param fencingToken   写入时有效令牌
     * @param createdAt      创建时刻
     * @author refinex
     */
    public record AgentStateVersion(
        JobId id,
        OrganizationId organizationId,
        ProjectId projectId,
        SessionId sessionId,
        RunId runId,
        String agentKey,
        String stateKey,
        int itemIndex,
        long stateVersion,
        RuntimePayload payload,
        Checksum contentHash,
        boolean committed,
        FencingToken fencingToken,
        Instant createdAt) {

        /**
         * 校验 State Version 的稳定键、版本和内容引用。
         */
        public AgentStateVersion {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(runId, "runId must not be null");
            Objects.requireNonNull(payload, "payload must not be null");
            Objects.requireNonNull(contentHash, "contentHash must not be null");
            Objects.requireNonNull(fencingToken, "fencingToken must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            if (agentKey == null || agentKey.isBlank() || stateKey == null || stateKey.isBlank()
                || itemIndex < 0 || stateVersion < 1) {
                throw new IllegalArgumentException("agent state identity or version is invalid");
            }
        }
    }

    /**
     * @param id                Checkpoint 标识
     * @param runId             所属 Run
     * @param sequence          Run 内从一开始的 Checkpoint 序号
     * @param agentStateId      已写入 State Version 行标识
     * @param agentStateVersion 被引用状态版本
     * @param eventSequence     恢复后继续消费的 Run Event 序号
     * @param contentHash       Checkpoint 描述 Hash
     * @param recoverable       是否通过完整性校验并可恢复
     * @param fencingToken      写入时有效令牌
     * @param createdAt         创建时刻
     * @author refinex
     */
    public record Checkpoint(
        JobId id,
        RunId runId,
        long sequence,
        JobId agentStateId,
        long agentStateVersion,
        long eventSequence,
        Checksum contentHash,
        boolean recoverable,
        FencingToken fencingToken,
        Instant createdAt) {

        /**
         * 校验 Checkpoint 只能引用正版本 State 和已分配 Event。
         */
        public Checkpoint {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(runId, "runId must not be null");
            Objects.requireNonNull(agentStateId, "agentStateId must not be null");
            Objects.requireNonNull(contentHash, "contentHash must not be null");
            Objects.requireNonNull(fencingToken, "fencingToken must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            if (sequence < 1 || agentStateVersion < 1 || eventSequence < 1) {
                throw new IllegalArgumentException("checkpoint sequence or state version is invalid");
            }
        }
    }

    /**
     * @param id                Usage 记录标识
     * @param runId             所属 Run
     * @param eventId           证明本次用量的 Runtime Event
     * @param provider          Provider 稳定标识
     * @param model             可选模型标识
     * @param tool              可选 Tool 标识
     * @param providerRequestId 可选 Provider 请求去重标识
     * @param inputUnits        输入计量单位数
     * @param outputUnits       输出计量单位数
     * @param durationMillis    持续时间，单位毫秒
     * @param estimated         是否为估算值
     * @param priceVersion      可选价格版本
     * @param occurredAt        计量发生时刻
     * @author refinex
     */
    public record UsageRecord(
        EventId id,
        RunId runId,
        EventId eventId,
        String provider,
        Optional<String> model,
        Optional<String> tool,
        Optional<String> providerRequestId,
        long inputUnits,
        long outputUnits,
        long durationMillis,
        boolean estimated,
        Optional<String> priceVersion,
        Instant occurredAt) {

        /**
         * 校验用量非负且 Provider 请求元数据不为空串。
         */
        public UsageRecord {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(runId, "runId must not be null");
            Objects.requireNonNull(eventId, "eventId must not be null");
            model = requireOptionalText(model, "model");
            tool = requireOptionalText(tool, "tool");
            providerRequestId = requireOptionalText(providerRequestId, "providerRequestId");
            priceVersion = requireOptionalText(priceVersion, "priceVersion");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            if (provider == null || provider.isBlank() || inputUnits < 0 || outputUnits < 0
                || durationMillis < 0) {
                throw new IllegalArgumentException("usage dimensions are invalid");
            }
        }
    }

    /**
     * @param scopeType      幂等作用域类型
     * @param scopeId        作用域稳定标识
     * @param idempotencyKey 调用方幂等键
     * @param requestHash    规范请求 Hash
     * @param resultRef      成功结果的类型化引用
     * @param status         幂等状态
     * @param expiresAt      清理时刻，不改变已创建资源
     * @param createdAt      创建时刻
     * @author refinex
     */
    public record IdempotencyRecord(
        String scopeType,
        String scopeId,
        String idempotencyKey,
        Checksum requestHash,
        Optional<String> resultRef,
        IdempotencyStatus status,
        Instant expiresAt,
        Instant createdAt) {

        /**
         * 校验幂等键、请求 Hash 与结果状态一致。
         */
        public IdempotencyRecord {
            Objects.requireNonNull(requestHash, "requestHash must not be null");
            resultRef = requireOptionalText(resultRef, "resultRef");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            if (scopeType == null || scopeType.isBlank() || scopeId == null || scopeId.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128 || !expiresAt.isAfter(createdAt)
                || (status == IdempotencyStatus.COMPLETED) != resultRef.isPresent()) {
                throw new IllegalArgumentException("idempotency record is invalid");
            }
        }
    }

    /**
     * @param id            Outbox Event 标识
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合稳定标识
     * @param eventType     稳定事件类型
     * @param payload       非敏感事件载荷
     * @param status        投递状态
     * @param availableAt   最早投递时刻
     * @param attempts      投递尝试次数
     * @param createdAt     创建时刻
     * @author refinex
     */
    public record RuntimeOutboxEvent(
        EventId id,
        String aggregateType,
        String aggregateId,
        String eventType,
        RuntimePayload payload,
        OutboxStatus status,
        Instant availableAt,
        int attempts,
        Instant createdAt) {

        /**
         * 校验 Outbox 类型、载荷和重试计数。
         */
        public RuntimeOutboxEvent {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(payload, "payload must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(availableAt, "availableAt must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            if (aggregateType == null || aggregateType.isBlank()
                || aggregateId == null || aggregateId.isBlank()
                || eventType == null || eventType.isBlank() || attempts < 0) {
                throw new IllegalArgumentException("runtime outbox metadata is invalid");
            }
        }
    }

    /**
     * @param revisionId      固定 Revision 标识
     * @param snapshotId      固定 Snapshot 标识
     * @param contentHash     规范快照内容 Hash
     * @param schemaVersion   Snapshot Schema 版本
     * @param runtimeProvider Runtime Provider 标识
     * @param canonicalJson   不含明文 Secret 的 Canonical Snapshot JSON
     * @author refinex
     */
    public record SnapshotDescriptor(
        RevisionId revisionId,
        SnapshotId snapshotId,
        Checksum contentHash,
        int schemaVersion,
        String runtimeProvider,
        String canonicalJson) {

        /**
         * 校验从 Internal Contract 加载的固定 Snapshot。
         */
        public SnapshotDescriptor {
            Objects.requireNonNull(revisionId, "revisionId must not be null");
            Objects.requireNonNull(snapshotId, "snapshotId must not be null");
            Objects.requireNonNull(contentHash, "contentHash must not be null");
            if (schemaVersion < 1 || runtimeProvider == null || runtimeProvider.isBlank()
                || canonicalJson == null || canonicalJson.isBlank()) {
                throw new IllegalArgumentException("snapshot descriptor is invalid");
            }
        }
    }

    /**
     * @param outcome   Provider 中立执行结果
     * @param errorCode 失败时稳定错误码
     * @param detail    不含 Secret、Prompt 或隐藏推理过程的诊断摘要
     * @author refinex
     */
    public record ExecutionResult(
        ExecutionOutcome outcome, Optional<String> errorCode, Optional<String> detail) {

        /**
         * 校验结果与错误码一致，避免失败原因静默丢失。
         */
        public ExecutionResult {
            Objects.requireNonNull(outcome, "outcome must not be null");
            errorCode = requireOptionalText(errorCode, "errorCode");
            detail = requireOptionalText(detail, "detail");
            if ((outcome == ExecutionOutcome.FAILED) != errorCode.isPresent()) {
                throw new IllegalArgumentException("execution outcome and error code are inconsistent");
            }
        }
    }

    /**
     * 校验可选文本不存在空白值。
     *
     * @param value 可选文本
     * @param name  异常上下文字段名
     * @return 原可选文本
     */
    private static Optional<String> requireOptionalText(Optional<String> value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        value.ifPresent(text -> {
            if (text.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
        });
        return value;
    }
}
