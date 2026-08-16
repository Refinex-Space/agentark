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

import org.apache.ibatis.annotations.*;
import space.refinex.agentark.runtime.adapter.out.persistence.RuntimePersistenceRows.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 定义 Runtime 所属 Schema 的显式 SQL；所有语句使用未限定表名并由所属 DataSource 隔离。
 *
 * @author refinex
 */
@Mapper
public interface RuntimeMapper {

    /**
     * 插入固定 Snapshot 的 Session。
     *
     * @param row Session 数据库行
     */
    @Insert("""
        INSERT INTO session
            (id, organization_id, project_id, deployment_id, revision_id, snapshot_id,
             snapshot_hash, participant_metadata, channel_metadata, status,
             event_sequence, version, created_at, updated_at)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.deploymentId,jdbcType=BINARY},
             #{row.revisionId,jdbcType=BINARY}, #{row.snapshotId,jdbcType=BINARY},
             #{row.snapshotHash,jdbcType=BINARY}, #{row.participantMetadata},
             #{row.channelMetadata}, #{row.status}, #{row.eventSequence}, #{row.version},
             #{row.createdAt}, #{row.updatedAt})
        """)
    void insertSession(@Param("row") SessionRow row);

    /**
     * 按标识读取 Session。
     *
     * @param id Session UUID
     * @return Session 数据库行
     */
    @Select("""
        SELECT id, organization_id, project_id, deployment_id, revision_id, snapshot_id,
               snapshot_hash, participant_metadata, channel_metadata, status,
               event_sequence, version, created_at, updated_at
        FROM session WHERE id = #{id,jdbcType=BINARY}
        """)
    Optional<SessionRow> findSession(@Param("id") UUID id);

    /**
     * 锁定 Session 行，确保 Turn/Event 序号分配串行化。
     *
     * @param id Session UUID
     * @return 当前 Event 序号
     */
    @Select("SELECT event_sequence FROM session WHERE id = #{id,jdbcType=BINARY} FOR UPDATE")
    Optional<Long> lockSessionSequence(@Param("id") UUID id);

    /**
     * 返回 Session 内下一 Turn 序号；调用前必须锁定 Session 行。
     *
     * @param sessionId Session UUID
     * @return 下一序号
     */
    @Select("""
        SELECT COALESCE(MAX(sequence), 0) + 1 FROM turn
        WHERE session_id = #{sessionId,jdbcType=BINARY}
        """)
    long nextTurnSequence(@Param("sessionId") UUID sessionId);

    /**
     * 原子递增 Session Event 序号。
     *
     * @param id Session UUID
     * @return 更新行数
     */
    @Update("UPDATE session SET event_sequence = event_sequence + 1 WHERE id = #{id,jdbcType=BINARY}")
    int incrementSessionEventSequence(@Param("id") UUID id);

    /**
     * 插入 Turn。
     *
     * @param row Turn 数据库行
     */
    @Insert("""
        INSERT INTO turn
            (id, organization_id, project_id, session_id, sequence, input_storage,
             input_json, input_object_uri, input_object_size, input_media_type,
             input_hash, status, current_run_id, fencing_token, version,
             created_at, updated_at)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.sessionId,jdbcType=BINARY},
             #{row.sequence}, #{row.inputStorage}, #{row.inputJson}, #{row.inputObjectUri},
             #{row.inputObjectSize}, #{row.inputMediaType}, #{row.inputHash,jdbcType=BINARY},
             #{row.status}, #{row.currentRunId,jdbcType=BINARY}, #{row.fencingToken},
             #{row.version}, #{row.createdAt}, #{row.updatedAt})
        """)
    void insertTurn(@Param("row") TurnRow row);

    /**
     * 读取 Turn。
     *
     * @param id Turn UUID
     * @return Turn 数据库行
     */
    @Select("""
        SELECT id, organization_id, project_id, session_id, sequence, input_storage,
               input_json, input_object_uri, input_object_size, input_media_type,
               input_hash, status, current_run_id, fencing_token, version,
               created_at, updated_at
        FROM turn WHERE id = #{id,jdbcType=BINARY}
        """)
    Optional<TurnRow> findTurn(@Param("id") UUID id);

    /**
     * 使用当前状态和 Fencing Token 转换 Turn。
     *
     * @param id           Turn UUID
     * @param current      当前状态
     * @param target       目标状态
     * @param fencingToken 当前令牌
     * @param occurredAt   转换时刻
     * @return 更新行数
     */
    @Update("""
        UPDATE turn SET status = #{target}, version = version + 1, updated_at = #{occurredAt}
        WHERE id = #{id,jdbcType=BINARY} AND status = #{current}
          AND fencing_token = #{fencingToken}
        """)
    int transitionTurn(
        @Param("id") UUID id,
        @Param("current") String current,
        @Param("target") String target,
        @Param("fencingToken") long fencingToken,
        @Param("occurredAt") Instant occurredAt);

    /**
     * 将新 Run 指针和 QUEUED 状态写入可重试 Turn。
     *
     * @param turnId    Turn UUID
     * @param runId     新 Run UUID
     * @param updatedAt 更新时间
     * @return 更新行数
     */
    @Update("""
        UPDATE turn SET current_run_id = #{runId,jdbcType=BINARY}, status = 'QUEUED',
            fencing_token = 0, version = version + 1, updated_at = #{updatedAt}
        WHERE id = #{turnId,jdbcType=BINARY} AND status IN ('FAILED', 'TIMED_OUT')
        """)
    int attachRetryRun(
        @Param("turnId") UUID turnId,
        @Param("runId") UUID runId,
        @Param("updatedAt") Instant updatedAt);

    /**
     * 将失联 Run 的 Turn 指针切到新 Attempt，并从执行态重新进入 QUEUED。
     *
     * @param turnId    Turn UUID
     * @param oldRunId  已被新 Token 接管并放弃的旧 Run UUID
     * @param newRunId  新 Run Attempt UUID
     * @param oldToken  旧 Run 被接管后的当前 Fencing Token
     * @param updatedAt 更新时间
     * @return 更新行数
     */
    @Update("""
        UPDATE turn SET current_run_id = #{newRunId,jdbcType=BINARY}, status = 'QUEUED',
            fencing_token = 0, version = version + 1, updated_at = #{updatedAt}
        WHERE id = #{turnId,jdbcType=BINARY}
          AND current_run_id = #{oldRunId,jdbcType=BINARY}
          AND fencing_token = #{oldToken}
          AND status IN ('QUEUED', 'RUNNING', 'WAITING_APPROVAL')
        """)
    int attachRecoveryRun(
        @Param("turnId") UUID turnId,
        @Param("oldRunId") UUID oldRunId,
        @Param("newRunId") UUID newRunId,
        @Param("oldToken") long oldToken,
        @Param("updatedAt") Instant updatedAt);

    /**
     * 插入 Run Attempt。
     *
     * @param row Run 数据库行
     */
    @Insert("""
        INSERT INTO run
            (id, organization_id, project_id, session_id, turn_id, attempt_number,
             runtime_provider, compiler_version, status, event_sequence, fencing_token,
             started_at, ended_at, error_code, created_at)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.sessionId,jdbcType=BINARY},
             #{row.turnId,jdbcType=BINARY}, #{row.attemptNumber}, #{row.runtimeProvider},
             #{row.compilerVersion}, #{row.status}, #{row.eventSequence},
             #{row.fencingToken}, #{row.startedAt}, #{row.endedAt},
             #{row.errorCode}, #{row.createdAt})
        """)
    void insertRun(@Param("row") RunRow row);

    /**
     * 读取 Run。
     *
     * @param id Run UUID
     * @return Run 数据库行
     */
    @Select("""
        SELECT id, organization_id, project_id, session_id, turn_id, attempt_number,
               runtime_provider, compiler_version, status, event_sequence, fencing_token,
               started_at, ended_at, error_code, created_at
        FROM run WHERE id = #{id,jdbcType=BINARY}
        """)
    Optional<RunRow> findRun(@Param("id") UUID id);

    /**
     * 锁定 Run 行，确保 Event 序号分配和 Fencing 校验串行化。
     *
     * @param id Run UUID
     * @return 当前 Event 序号
     */
    @Select("SELECT event_sequence FROM run WHERE id = #{id,jdbcType=BINARY} FOR UPDATE")
    Optional<Long> lockRunSequence(@Param("id") UUID id);

    /**
     * 原子递增 Run Event 序号。
     *
     * @param id Run UUID
     * @return 更新行数
     */
    @Update("UPDATE run SET event_sequence = event_sequence + 1 WHERE id = #{id,jdbcType=BINARY}")
    int incrementRunEventSequence(@Param("id") UUID id);

    /**
     * 使用当前状态与令牌转换 Run。
     *
     * @param id           Run UUID
     * @param current      当前状态
     * @param target       目标状态
     * @param fencingToken 当前令牌
     * @param occurredAt   转换时刻
     * @param errorCode    稳定错误码
     * @return 更新行数
     */
    @Update("""
        UPDATE run
        SET status = #{target},
            started_at = CASE
                WHEN #{target} = 'RUNNING' AND started_at IS NULL THEN #{occurredAt}
                ELSE started_at END,
            ended_at = CASE
                WHEN #{target} IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'ABANDONED')
                    THEN #{occurredAt}
                ELSE NULL END,
            error_code = #{errorCode}
        WHERE id = #{id,jdbcType=BINARY} AND status = #{current}
          AND fencing_token = #{fencingToken}
        """)
    int transitionRun(
        @Param("id") UUID id,
        @Param("current") String current,
        @Param("target") String target,
        @Param("fencingToken") long fencingToken,
        @Param("occurredAt") Instant occurredAt,
        @Param("errorCode") String errorCode);

    /**
     * 将递增 Fencing Token 写入 Run。
     *
     * @param id       Run UUID
     * @param newToken 新令牌
     * @return 更新行数
     */
    @Update("""
        UPDATE run SET fencing_token = #{newToken}
        WHERE id = #{id,jdbcType=BINARY} AND fencing_token < #{newToken}
        """)
    int assignRunFencing(@Param("id") UUID id, @Param("newToken") long newToken);

    /**
     * 将递增 Fencing Token 写入当前 Turn。
     *
     * @param id       Turn UUID
     * @param runId    当前 Run UUID
     * @param newToken 新令牌
     * @return 更新行数
     */
    @Update("""
        UPDATE turn SET fencing_token = #{newToken}, version = version + 1
        WHERE id = #{id,jdbcType=BINARY} AND current_run_id = #{runId,jdbcType=BINARY}
          AND fencing_token < #{newToken}
        """)
    int assignTurnFencing(
        @Param("id") UUID id,
        @Param("runId") UUID runId,
        @Param("newToken") long newToken);

    /**
     * 插入 Work Item。
     *
     * @param row Work Item 数据库行
     */
    @Insert("""
        INSERT INTO runtime_work_item
            (id, run_id, status, priority, available_at, claimed_by, claim_until,
             fencing_token, attempt_count, created_at, updated_at)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.runId,jdbcType=BINARY}, #{row.status},
             #{row.priority}, #{row.availableAt}, #{row.claimedBy}, #{row.claimUntil},
             #{row.fencingToken}, #{row.attemptCount}, #{row.createdAt}, #{row.createdAt})
        """)
    void insertWorkItem(@Param("row") WorkItemRow row);

    /**
     * 锁定优先级最高的可 Claim Work Item。
     *
     * @param now 当前时刻
     * @return Work Item 数据库行
     */
    @Select("""
        SELECT id, run_id, status, priority, available_at, claimed_by, claim_until,
               fencing_token, attempt_count, created_at
        FROM runtime_work_item
        WHERE available_at <= #{now}
          AND (status = 'READY' OR (status = 'CLAIMED' AND claim_until <= #{now}))
        ORDER BY priority DESC, available_at, id
        LIMIT 1 FOR UPDATE SKIP LOCKED
        """)
    Optional<WorkItemRow> lockNextWorkItem(@Param("now") Instant now);

    /**
     * 将已锁 Work Item Claim 给新 Owner 并递增令牌。
     *
     * @param id         Work Item UUID
     * @param owner      Owner Key
     * @param claimUntil Lease 到期时刻
     * @param now        Claim 时刻
     * @return 更新行数
     */
    @Update("""
        UPDATE runtime_work_item
        SET status = 'CLAIMED', claimed_by = #{owner}, claim_until = #{claimUntil},
            fencing_token = fencing_token + 1, attempt_count = attempt_count + 1,
            updated_at = #{now}
        WHERE id = #{id,jdbcType=BINARY}
        """)
    int claimWorkItem(
        @Param("id") UUID id,
        @Param("owner") String owner,
        @Param("claimUntil") Instant claimUntil,
        @Param("now") Instant now);

    /**
     * 按 Run 读取 Work Item。
     *
     * @param runId Run UUID
     * @return Work Item 数据库行
     */
    @Select("""
        SELECT id, run_id, status, priority, available_at, claimed_by, claim_until,
               fencing_token, attempt_count, created_at
        FROM runtime_work_item WHERE run_id = #{runId,jdbcType=BINARY}
        """)
    Optional<WorkItemRow> findWorkItem(@Param("runId") UUID runId);

    /**
     * 续约当前 Owner 的 Work Item Lease。
     *
     * @param runId      Run UUID
     * @param owner      Owner Key
     * @param token      当前令牌
     * @param now        当前时刻
     * @param claimUntil 新到期时刻
     * @return 更新行数
     */
    @Update("""
        UPDATE runtime_work_item SET claim_until = #{claimUntil}, updated_at = #{now}
        WHERE run_id = #{runId,jdbcType=BINARY} AND status = 'CLAIMED'
          AND claimed_by = #{owner} AND fencing_token = #{token} AND claim_until >= #{now}
        """)
    int renewWorkItem(
        @Param("runId") UUID runId,
        @Param("owner") String owner,
        @Param("token") long token,
        @Param("now") Instant now,
        @Param("claimUntil") Instant claimUntil);

    /**
     * 使用当前令牌完成 Work Item。
     *
     * @param runId  Run UUID
     * @param token  当前令牌
     * @param target 终态
     * @param now    更新时间
     * @return 更新行数
     */
    @Update("""
        UPDATE runtime_work_item
        SET status = #{target}, claimed_by = NULL, claim_until = NULL, updated_at = #{now}
        WHERE run_id = #{runId,jdbcType=BINARY} AND fencing_token = #{token}
          AND status IN ('READY', 'CLAIMED', 'COMPLETED')
        """)
    int completeWorkItem(
        @Param("runId") UUID runId,
        @Param("token") long token,
        @Param("target") String target,
        @Param("now") Instant now);

    /**
     * 将 PAUSED Run 已完成的 Work Item 重新置为 READY，等待新 Lease/Fencing Resume。
     *
     * @param runId       Run UUID
     * @param token       暂停时 Fencing Token
     * @param availableAt 最早 Claim 时刻
     * @return 更新行数
     */
    @Update("""
        UPDATE runtime_work_item
        SET status = 'READY', available_at = #{availableAt}, claimed_by = NULL,
            claim_until = NULL, updated_at = #{availableAt}
        WHERE run_id = #{runId,jdbcType=BINARY} AND status = 'COMPLETED'
          AND fencing_token = #{token}
        """)
    int requeueWorkItem(
        @Param("runId") UUID runId,
        @Param("token") long token,
        @Param("availableAt") Instant availableAt);

    /**
     * 插入 Runtime Event。
     *
     * @param row Event 数据库行
     */
    @Insert("""
        INSERT INTO runtime_event
            (id, organization_id, project_id, session_id, turn_id, run_id,
             session_sequence, run_sequence, type, schema_version,
             trace_id, payload_storage, payload_json, occurred_at, fencing_token)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.sessionId,jdbcType=BINARY},
             #{row.turnId,jdbcType=BINARY}, #{row.runId,jdbcType=BINARY},
             #{row.sessionSequence}, #{row.runSequence}, #{row.type}, #{row.schemaVersion},
             #{row.traceId}, #{row.payloadStorage}, #{row.payloadJson},
             #{row.occurredAt}, #{row.fencingToken})
        """)
    void insertEvent(@Param("row") EventRow row);

    /**
     * 插入大 Event Payload 的 ObjectRef 元数据。
     *
     * @param row Event 数据库行
     */
    @Insert("""
        INSERT INTO runtime_event_payload_ref
            (event_id, object_uri, content_hash, object_size, media_type, encryption_metadata)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.objectUri}, #{row.objectHash,jdbcType=BINARY},
             #{row.objectSize}, #{row.mediaType}, NULL)
        """)
    void insertEventPayloadRef(@Param("row") EventRow row);

    /**
     * 按 Session Sequence 增量读取 Event 和可选 ObjectRef。
     *
     * @param sessionId     Session UUID
     * @param afterSequence 已消费序号
     * @param limit         最大数量
     * @return Event 数据库行
     */
    @Select("""
        SELECT e.id, e.organization_id, e.project_id, e.session_id, e.turn_id, e.run_id,
               e.session_sequence, e.run_sequence, e.type, e.schema_version,
               e.trace_id, e.payload_storage, e.payload_json, e.occurred_at, e.fencing_token,
               p.object_uri, p.content_hash AS object_hash, p.object_size, p.media_type
        FROM runtime_event e
        LEFT JOIN runtime_event_payload_ref p ON p.event_id = e.id
        WHERE e.session_id = #{sessionId,jdbcType=BINARY}
          AND e.session_sequence > #{afterSequence}
        ORDER BY e.session_sequence, e.id
        LIMIT #{limit}
        """)
    List<EventRow> listEventsAfter(
        @Param("sessionId") UUID sessionId,
        @Param("afterSequence") long afterSequence,
        @Param("limit") int limit);

    /**
     * 按 Session Sequence 增量读取单个 Run 的 Event。
     *
     * @param runId         Run UUID
     * @param afterSequence 已消费 Session Sequence
     * @param limit         最大数量
     * @return Event 行
     */
    @Select("""
        SELECT e.id, e.organization_id, e.project_id, e.session_id, e.turn_id, e.run_id,
               e.session_sequence, e.run_sequence, e.type, e.schema_version, e.trace_id,
               e.payload_storage, e.payload_json, e.occurred_at, e.fencing_token,
               r.object_uri, r.content_hash AS object_hash, r.object_size, r.media_type
        FROM runtime_event e
        LEFT JOIN runtime_event_payload_ref r ON r.event_id = e.id
        WHERE e.run_id = #{runId,jdbcType=BINARY}
          AND e.session_sequence > #{afterSequence}
        ORDER BY e.session_sequence
        LIMIT #{limit}
        """)
    List<EventRow> listRunEventsAfter(
        @Param("runId") UUID runId,
        @Param("afterSequence") long afterSequence,
        @Param("limit") int limit);

    /**
     * 插入幂等记录。
     *
     * @param row 幂等数据库行
     */
    @Insert("""
        INSERT INTO runtime_idempotency_record
            (scope_type, scope_id, idempotency_key, request_hash, result_ref,
             status, expires_at, created_at)
        VALUES
            (#{row.scopeType}, #{row.scopeId}, #{row.idempotencyKey},
             #{row.requestHash,jdbcType=BINARY}, #{row.resultRef}, #{row.status},
             #{row.expiresAt}, #{row.createdAt})
        """)
    void insertIdempotency(@Param("row") IdempotencyRow row);

    /**
     * 读取幂等记录。
     *
     * @param scopeType 作用域类型
     * @param scopeId   作用域标识
     * @param key       幂等键
     * @return 幂等数据库行
     */
    @Select("""
        SELECT scope_type, scope_id, idempotency_key, request_hash, result_ref,
               status, expires_at, created_at
        FROM runtime_idempotency_record
        WHERE scope_type = #{scopeType} AND scope_id = #{scopeId}
          AND idempotency_key = #{key}
        """)
    Optional<IdempotencyRow> findIdempotency(
        @Param("scopeType") String scopeType,
        @Param("scopeId") String scopeId,
        @Param("key") String key);

    /**
     * 插入内联 Runtime Outbox。
     *
     * @param eventId       Outbox Event UUID
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合标识
     * @param eventType     事件类型
     * @param payloadJson   内联 JSON
     * @param status        投递状态
     * @param availableAt   最早投递时刻
     * @param attempts      尝试次数
     * @param createdAt     创建时刻
     */
    @Insert("""
        INSERT INTO runtime_outbox
            (event_id, aggregate_type, aggregate_id, event_type, payload_storage,
             payload_json, object_uri, object_hash, object_size, media_type,
             status, available_at, attempts, created_at, published_at)
        VALUES
            (#{eventId,jdbcType=BINARY}, #{aggregateType}, #{aggregateId}, #{eventType},
             'INLINE', #{payloadJson}, NULL, NULL, NULL, NULL,
             #{status}, #{availableAt}, #{attempts}, #{createdAt}, NULL)
        """)
    void insertInlineOutbox(
        @Param("eventId") UUID eventId,
        @Param("aggregateType") String aggregateType,
        @Param("aggregateId") String aggregateId,
        @Param("eventType") String eventType,
        @Param("payloadJson") String payloadJson,
        @Param("status") String status,
        @Param("availableAt") Instant availableAt,
        @Param("attempts") int attempts,
        @Param("createdAt") Instant createdAt);

    /**
     * 插入对象引用 Runtime Outbox。
     *
     * @param eventId       Outbox Event UUID
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合标识
     * @param eventType     事件类型
     * @param uri           对象 URI
     * @param hash          SHA-256 字节
     * @param size          对象字节数
     * @param mediaType     媒体类型
     * @param status        投递状态
     * @param availableAt   最早投递时刻
     * @param attempts      尝试次数
     * @param createdAt     创建时刻
     */
    @Insert("""
        INSERT INTO runtime_outbox
            (event_id, aggregate_type, aggregate_id, event_type, payload_storage,
             payload_json, object_uri, object_hash, object_size, media_type,
             status, available_at, attempts, created_at, published_at)
        VALUES
            (#{eventId,jdbcType=BINARY}, #{aggregateType}, #{aggregateId}, #{eventType},
             'OBJECT', NULL, #{uri}, #{hash,jdbcType=BINARY}, #{size}, #{mediaType},
             #{status}, #{availableAt}, #{attempts}, #{createdAt}, NULL)
        """)
    void insertObjectOutbox(
        @Param("eventId") UUID eventId,
        @Param("aggregateType") String aggregateType,
        @Param("aggregateId") String aggregateId,
        @Param("eventType") String eventType,
        @Param("uri") String uri,
        @Param("hash") byte[] hash,
        @Param("size") long size,
        @Param("mediaType") String mediaType,
        @Param("status") String status,
        @Param("availableAt") Instant availableAt,
        @Param("attempts") int attempts,
        @Param("createdAt") Instant createdAt);

    /**
     * 插入 Approval。
     *
     * @param row Approval 数据库行
     */
    @Insert("""
        INSERT INTO approval
            (id, organization_id, project_id, run_id, tool_name, action_code,
             argument_hash, policy_version, status, version, expires_at,
             decision_by, decision_at, created_at)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.runId,jdbcType=BINARY},
             #{row.toolName}, #{row.actionCode}, #{row.argumentHash,jdbcType=BINARY},
             #{row.policyVersion}, #{row.status}, #{row.version}, #{row.expiresAt},
             #{row.decisionBy}, #{row.decisionAt}, #{row.createdAt})
        """)
    void insertApproval(@Param("row") ApprovalRow row);

    /**
     * 读取 Approval。
     *
     * @param id Approval UUID
     * @return Approval 数据库行
     */
    @Select("""
        SELECT id, organization_id, project_id, run_id, tool_name, action_code,
               argument_hash, policy_version, status, version, expires_at,
               decision_by, decision_at, created_at
        FROM approval WHERE id = #{id,jdbcType=BINARY}
        """)
    Optional<ApprovalRow> findApproval(@Param("id") UUID id);

    /**
     * 使用乐观锁写入审批终态。
     *
     * @param id              Approval UUID
     * @param expectedVersion 预期版本
     * @param target          目标状态
     * @param decisionBy      决策主体
     * @param decisionAt      决策时刻
     * @return 更新行数
     */
    @Update("""
        UPDATE approval SET status = #{target}, version = version + 1,
            decision_by = #{decisionBy}, decision_at = #{decisionAt}
        WHERE id = #{id,jdbcType=BINARY} AND status = 'PENDING'
          AND version = #{expectedVersion}
        """)
    int decideApproval(
        @Param("id") UUID id,
        @Param("expectedVersion") long expectedVersion,
        @Param("target") String target,
        @Param("decisionBy") String decisionBy,
        @Param("decisionAt") Instant decisionAt);

    /**
     * 读取同一 Run 的全部 Approval。
     *
     * @param runId Run UUID
     * @return Approval 行
     */
    @Select("""
        SELECT id, organization_id, project_id, run_id, tool_name, action_code,
               argument_hash, policy_version, status, version, expires_at,
               decision_by, decision_at, created_at
        FROM approval WHERE run_id = #{runId,jdbcType=BINARY}
        ORDER BY created_at, id
        """)
    List<ApprovalRow> listApprovalsForRun(@Param("runId") UUID runId);

    /**
     * 增量读取项目内 Approval。
     *
     * @param projectId 项目 UUID
     * @param status    可选状态
     * @param afterId   可选 UUIDv7 游标
     * @param limit     最大数量
     * @return Approval 行
     */
    @Select("""
        <script>
        SELECT id, organization_id, project_id, run_id, tool_name, action_code,
               argument_hash, policy_version, status, version, expires_at,
               decision_by, decision_at, created_at
        FROM approval
        WHERE project_id = #{projectId,jdbcType=BINARY}
        <if test="status != null">AND status = #{status}</if>
        <if test="afterId != null">AND id &gt; #{afterId,jdbcType=BINARY}</if>
        ORDER BY id LIMIT #{limit}
        </script>
        """)
    List<ApprovalRow> listApprovals(
        @Param("projectId") UUID projectId,
        @Param("status") String status,
        @Param("afterId") UUID afterId,
        @Param("limit") int limit);

    /**
     * 取消 Run 下全部待决 Approval。
     *
     * @param runId      Run UUID
     * @param decisionBy 系统或调用主体
     * @param decisionAt 取消时刻
     * @return 更新数量
     */
    @Update("""
        UPDATE approval SET status = 'CANCELLED', version = version + 1,
            decision_by = #{decisionBy}, decision_at = #{decisionAt}
        WHERE run_id = #{runId,jdbcType=BINARY} AND status = 'PENDING'
        """)
    int cancelPendingApprovals(
        @Param("runId") UUID runId,
        @Param("decisionBy") String decisionBy,
        @Param("decisionAt") Instant decisionAt);

    /**
     * 注册 Runtime Instance；同 Key 重启时替换实例 UUID 和能力并恢复 ACTIVE。
     *
     * @param row Runtime Instance 行
     */
    @Insert("""
        INSERT INTO runtime_instance
            (id, instance_key, started_at, heartbeat_at, capabilities, drain_status)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.instanceKey}, #{row.startedAt},
             #{row.heartbeatAt}, #{row.capabilities}, #{row.drainStatus})
        ON DUPLICATE KEY UPDATE id = VALUES(id), started_at = VALUES(started_at),
            heartbeat_at = VALUES(heartbeat_at), capabilities = VALUES(capabilities),
            drain_status = VALUES(drain_status)
        """)
    void upsertRuntimeInstance(@Param("row") RuntimeInstanceRow row);

    /**
     * 刷新 Runtime Instance 心跳。
     *
     * @param instanceKey 实例 Key
     * @param heartbeatAt 当前时刻
     * @return 更新行数
     */
    @Update("""
        UPDATE runtime_instance SET heartbeat_at = #{heartbeatAt}
        WHERE instance_key = #{instanceKey}
        """)
    int heartbeatRuntimeInstance(
        @Param("instanceKey") String instanceKey,
        @Param("heartbeatAt") Instant heartbeatAt);

    /**
     * 更新 Runtime Instance 排空状态。
     *
     * @param instanceKey 实例 Key
     * @param status      排空状态
     * @param occurredAt  更新时间
     * @return 更新行数
     */
    @Update("""
        UPDATE runtime_instance SET drain_status = #{status}, heartbeat_at = #{occurredAt}
        WHERE instance_key = #{instanceKey}
        """)
    int updateRuntimeInstanceDrain(
        @Param("instanceKey") String instanceKey,
        @Param("status") String status,
        @Param("occurredAt") Instant occurredAt);

    /**
     * 插入 Agent State Version。
     *
     * @param row State 数据库行
     */
    @Insert("""
        INSERT INTO runtime_agent_state
            (id, organization_id, project_id, session_id, run_id, agent_key, state_key,
             item_index, state_version, state_storage, state_json, object_uri,
             object_size, media_type, content_hash, committed, fencing_token, created_at)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.sessionId,jdbcType=BINARY},
             #{row.runId,jdbcType=BINARY}, #{row.agentKey}, #{row.stateKey},
             #{row.itemIndex}, #{row.stateVersion}, #{row.stateStorage}, #{row.stateJson},
             #{row.objectUri}, #{row.objectSize}, #{row.mediaType},
             #{row.contentHash,jdbcType=BINARY}, #{row.committed},
             #{row.fencingToken}, #{row.createdAt})
        """)
    void insertState(@Param("row") StateRow row);

    /**
     * 使用当前令牌一次性提交 State Version。
     *
     * @param id    State UUID
     * @param token 当前令牌
     * @return 更新行数
     */
    @Update("""
        UPDATE runtime_agent_state SET committed = TRUE
        WHERE id = #{id,jdbcType=BINARY} AND committed = FALSE AND fencing_token = #{token}
        """)
    int commitState(@Param("id") UUID id, @Param("token") long token);

    /**
     * 读取最新已提交 State Version。
     *
     * @param sessionId Session UUID
     * @param agentKey  Agent Key
     * @param stateKey  State Key
     * @param itemIndex 元素下标
     * @return State 数据库行
     */
    @Select("""
        SELECT id, organization_id, project_id, session_id, run_id, agent_key, state_key,
               item_index, state_version, state_storage, state_json, object_uri,
               object_size, media_type, content_hash, committed, fencing_token, created_at
        FROM runtime_agent_state
        WHERE session_id = #{sessionId,jdbcType=BINARY} AND agent_key = #{agentKey}
          AND state_key = #{stateKey} AND item_index = #{itemIndex} AND committed = TRUE
        ORDER BY state_version DESC, id DESC LIMIT 1
        """)
    Optional<StateRow> findLatestCommittedState(
        @Param("sessionId") UUID sessionId,
        @Param("agentKey") String agentKey,
        @Param("stateKey") String stateKey,
        @Param("itemIndex") int itemIndex);

    /**
     * 插入 Checkpoint。
     *
     * @param row Checkpoint 数据库行
     */
    @Insert("""
        INSERT INTO runtime_checkpoint
            (id, run_id, sequence, agent_state_id, agent_state_version,
             event_sequence, content_hash, recoverable, fencing_token, created_at)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.runId,jdbcType=BINARY}, #{row.sequence},
             #{row.agentStateId,jdbcType=BINARY}, #{row.agentStateVersion},
             #{row.eventSequence}, #{row.contentHash,jdbcType=BINARY}, #{row.recoverable},
             #{row.fencingToken}, #{row.createdAt})
        """)
    void insertCheckpoint(@Param("row") CheckpointRow row);

    /**
     * 读取最新可恢复 Checkpoint。
     *
     * @param runId Run UUID
     * @return Checkpoint 数据库行
     */
    @Select("""
        SELECT id, run_id, sequence, agent_state_id, agent_state_version,
               event_sequence, content_hash, recoverable, fencing_token, created_at
        FROM runtime_checkpoint
        WHERE run_id = #{runId,jdbcType=BINARY} AND recoverable = TRUE
        ORDER BY sequence DESC, id DESC LIMIT 1
        """)
    Optional<CheckpointRow> findLatestCheckpoint(@Param("runId") UUID runId);

    /**
     * 幂等插入 Usage 事实。
     *
     * @param id                Usage UUID
     * @param runId             Run UUID
     * @param eventId           Event UUID
     * @param provider          Provider 标识
     * @param model             模型标识
     * @param tool              Tool 标识
     * @param providerRequestId Provider 请求标识
     * @param inputUnits        输入单位数
     * @param outputUnits       输出单位数
     * @param durationMillis    持续毫秒数
     * @param estimated         是否估算
     * @param priceVersion      价格版本
     * @param occurredAt        发生时刻
     */
    @Insert("""
        INSERT INTO usage_record
            (id, run_id, event_id, provider, model, tool, provider_request_id,
             input_units, output_units, duration_millis, estimated, price_version, occurred_at)
        VALUES
            (#{id,jdbcType=BINARY}, #{runId,jdbcType=BINARY}, #{eventId,jdbcType=BINARY},
             #{provider}, #{model}, #{tool}, #{providerRequestId}, #{inputUnits},
             #{outputUnits}, #{durationMillis}, #{estimated}, #{priceVersion}, #{occurredAt})
        ON DUPLICATE KEY UPDATE id = id
        """)
    void insertUsage(
        @Param("id") UUID id,
        @Param("runId") UUID runId,
        @Param("eventId") UUID eventId,
        @Param("provider") String provider,
        @Param("model") String model,
        @Param("tool") String tool,
        @Param("providerRequestId") String providerRequestId,
        @Param("inputUnits") long inputUnits,
        @Param("outputUnits") long outputUnits,
        @Param("durationMillis") long durationMillis,
        @Param("estimated") boolean estimated,
        @Param("priceVersion") String priceVersion,
        @Param("occurredAt") Instant occurredAt);
}
