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

package space.refinex.agentark.scheduling.adapter.out.persistence;

import org.apache.ibatis.annotations.*;
import space.refinex.agentark.scheduling.adapter.out.persistence.SchedulerPersistenceRows.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 提供 Scheduler 独占 Schema 的精确 SQL，禁止跨 Schema 名称和共享业务 BaseMapper。
 *
 * @author refinex
 */
@Mapper
public interface SchedulerMapper {

    /**
     * 插入 Trigger 定义。
     *
     * @param row Trigger 行
     */
    @Insert("""
        INSERT INTO trigger_definition
            (id, organization_id, project_id, trigger_key, type, schedule_expression,
             time_zone, config_json, secret_ref, target_contract, target_job_type,
             status, version, created_at, updated_at)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.triggerKey}, #{row.type},
             #{row.scheduleExpression}, #{row.timeZone}, #{row.configJson},
             #{row.secretRef}, #{row.targetContract}, #{row.targetJobType},
             #{row.status}, #{row.version}, #{row.createdAt}, #{row.updatedAt})
        """)
    void insertTrigger(@Param("row") TriggerRow row);

    /**
     * 插入 Cron Cursor。
     *
     * @param row Cursor 行
     */
    @Insert("""
        INSERT INTO trigger_cursor
            (trigger_id, next_fire_at, last_fire_at, last_token, version)
        VALUES
            (#{row.triggerId,jdbcType=BINARY}, #{row.nextFireAt},
             #{row.lastFireAt}, #{row.lastToken}, #{row.version})
        """)
    void insertTriggerCursor(@Param("row") CursorRow row);

    /**
     * 插入 Durable Job。
     *
     * @param row Job 行
     */
    @Insert("""
        INSERT INTO job
            (id, organization_id, project_id, type, business_key, payload_json,
             payload_object_uri, payload_hash, status, priority, available_at,
             retry_policy_json, idempotency_capability, current_attempt,
             current_fencing_token, claimed_by, claim_until, result_ref, error_code,
             created_at, updated_at)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.type}, #{row.businessKey},
             #{row.payloadJson}, #{row.payloadObjectUri}, #{row.payloadHash,jdbcType=BINARY},
             #{row.status}, #{row.priority}, #{row.availableAt}, #{row.retryPolicyJson},
             #{row.idempotencyCapability}, #{row.currentAttempt},
             #{row.currentFencingToken}, #{row.claimedBy}, #{row.claimUntil},
             #{row.resultRef}, #{row.errorCode}, #{row.createdAt}, #{row.updatedAt})
        """)
    void insertJob(@Param("row") JobRow row);

    /**
     * 按类型与业务键读取 Job。
     *
     * @param type        Job 类型
     * @param businessKey 业务键
     * @return Job 行
     */
    @Select("""
        SELECT id, organization_id, project_id, type, business_key, payload_json,
               payload_object_uri, payload_hash, status, priority, available_at,
               retry_policy_json, idempotency_capability, current_attempt,
               current_fencing_token, claimed_by, claim_until, result_ref, error_code,
               created_at, updated_at
        FROM job WHERE type = #{type} AND business_key = #{businessKey}
        """)
    Optional<JobRow> findJobByBusinessKey(
        @Param("type") String type, @Param("businessKey") String businessKey);

    /**
     * 按标识读取 Job。
     *
     * @param id Job UUID
     * @return Job 行
     */
    @Select("""
        SELECT id, organization_id, project_id, type, business_key, payload_json,
               payload_object_uri, payload_hash, status, priority, available_at,
               retry_policy_json, idempotency_capability, current_attempt,
               current_fencing_token, claimed_by, claim_until, result_ref, error_code,
               created_at, updated_at
        FROM job WHERE id = #{id,jdbcType=BINARY}
        """)
    Optional<JobRow> findJob(@Param("id") UUID id);

    /**
     * 锁定一个可领取或 Lease 已过期的 Job，支持多实例 SKIP LOCKED。
     *
     * @param type Job 类型
     * @param now  当前时间
     * @return 被当前事务锁定的候选行
     */
    @Select("""
        SELECT id, organization_id, project_id, type, business_key, payload_json,
               payload_object_uri, payload_hash, status, priority, available_at,
               retry_policy_json, idempotency_capability, current_attempt,
               current_fencing_token, claimed_by, claim_until, result_ref, error_code,
               created_at, updated_at
        FROM job
        WHERE type = #{type}
          AND ((status IN ('READY', 'RETRY_WAIT') AND available_at <= #{now})
               OR (status = 'CLAIMED' AND claim_until < #{now}))
        ORDER BY priority DESC, available_at, id
        LIMIT 1 FOR UPDATE SKIP LOCKED
        """)
    Optional<JobRow> lockClaimCandidate(@Param("type") String type, @Param("now") Instant now);

    /**
     * 将锁定候选转换为 CLAIMED 并写入新 Owner、Attempt 与 Token。
     *
     * @param id           Job UUID
     * @param owner        Worker Key
     * @param attempt      Attempt 序号
     * @param fencingToken 新 Token
     * @param claimUntil   Claim 到期时间
     * @param updatedAt    更新时间
     * @return 更新行数
     */
    @Update("""
        UPDATE job SET status = 'CLAIMED', claimed_by = #{owner}, claim_until = #{claimUntil},
            current_attempt = #{attempt}, current_fencing_token = #{fencingToken},
            error_code = NULL, updated_at = #{updatedAt}
        WHERE id = #{id,jdbcType=BINARY}
        """)
    int claimJob(
        @Param("id") UUID id,
        @Param("owner") String owner,
        @Param("attempt") int attempt,
        @Param("fencingToken") long fencingToken,
        @Param("claimUntil") Instant claimUntil,
        @Param("updatedAt") Instant updatedAt);

    /**
     * 把过期 Owner 的 RUNNING Attempt 标记为 ABANDONED。
     *
     * @param jobId         Job UUID
     * @param attemptNumber 旧 Attempt 序号
     * @param endedAt       接管时间
     * @return 更新行数
     */
    @Update("""
        UPDATE job_attempt SET status = 'ABANDONED', ended_at = #{endedAt},
            error_code = 'LEASE_EXPIRED'
        WHERE job_id = #{jobId,jdbcType=BINARY} AND attempt_number = #{attemptNumber}
          AND status = 'RUNNING'
        """)
    int abandonAttempt(
        @Param("jobId") UUID jobId,
        @Param("attemptNumber") int attemptNumber,
        @Param("endedAt") Instant endedAt);

    /**
     * 插入 RUNNING Attempt。
     *
     * @param id            Attempt UUID
     * @param jobId         Job UUID
     * @param attemptNumber Attempt 序号
     * @param owner         Worker Key
     * @param fencingToken  Token
     * @param startedAt     开始时间
     */
    @Insert("""
        INSERT INTO job_attempt
            (id, job_id, attempt_number, owner, fencing_token, status,
             started_at, ended_at, error_code, result_ref)
        VALUES
            (#{id,jdbcType=BINARY}, #{jobId,jdbcType=BINARY}, #{attemptNumber},
             #{owner}, #{fencingToken}, 'RUNNING', #{startedAt}, NULL, NULL, NULL)
        """)
    void insertAttempt(
        @Param("id") UUID id,
        @Param("jobId") UUID jobId,
        @Param("attemptNumber") int attemptNumber,
        @Param("owner") String owner,
        @Param("fencingToken") long fencingToken,
        @Param("startedAt") Instant startedAt);

    /**
     * 新建或替换锁定 Job 的 Lease。
     *
     * @param jobId        Job UUID
     * @param owner        Worker Key
     * @param fencingToken Token
     * @param leaseUntil   到期时间
     */
    @Insert("""
        INSERT INTO job_lease (job_id, owner, fencing_token, lease_until, version)
        VALUES (#{jobId,jdbcType=BINARY}, #{owner}, #{fencingToken}, #{leaseUntil}, 0)
        ON DUPLICATE KEY UPDATE owner = VALUES(owner), fencing_token = VALUES(fencing_token),
            lease_until = VALUES(lease_until), version = version + 1
        """)
    void upsertLease(
        @Param("jobId") UUID jobId,
        @Param("owner") String owner,
        @Param("fencingToken") long fencingToken,
        @Param("leaseUntil") Instant leaseUntil);

    /**
     * 使用 Owner 与 Token 续租 Lease。
     *
     * @param jobId        Job UUID
     * @param owner        Worker Key
     * @param fencingToken Token
     * @param leaseUntil   新到期时间
     * @return 更新行数
     */
    @Update("""
        UPDATE job_lease SET lease_until = #{leaseUntil}, version = version + 1
        WHERE job_id = #{jobId,jdbcType=BINARY} AND owner = #{owner}
          AND fencing_token = #{fencingToken}
        """)
    int renewLease(
        @Param("jobId") UUID jobId,
        @Param("owner") String owner,
        @Param("fencingToken") long fencingToken,
        @Param("leaseUntil") Instant leaseUntil);

    /**
     * 同步 Job 冗余 Claim 到期时间。
     *
     * @param jobId        Job UUID
     * @param owner        Worker Key
     * @param fencingToken Token
     * @param leaseUntil   新到期时间
     * @return 更新行数
     */
    @Update("""
        UPDATE job SET claim_until = #{leaseUntil}, updated_at = UTC_TIMESTAMP(6)
        WHERE id = #{jobId,jdbcType=BINARY} AND status = 'CLAIMED'
          AND claimed_by = #{owner} AND current_fencing_token = #{fencingToken}
        """)
    int renewJobClaim(
        @Param("jobId") UUID jobId,
        @Param("owner") String owner,
        @Param("fencingToken") long fencingToken,
        @Param("leaseUntil") Instant leaseUntil);

    /**
     * 使用当前 Token 完成 Attempt。
     *
     * @param attemptId    Attempt UUID
     * @param fencingToken Token
     * @param status       Attempt 终态
     * @param errorCode    错误码
     * @param resultRef    结果引用
     * @param endedAt      完成时间
     * @return 更新行数
     */
    @Update("""
        UPDATE job_attempt SET status = #{status}, error_code = #{errorCode},
            result_ref = #{resultRef}, ended_at = #{endedAt}
        WHERE id = #{attemptId,jdbcType=BINARY} AND status = 'RUNNING'
          AND fencing_token = #{fencingToken}
        """)
    int completeAttempt(
        @Param("attemptId") UUID attemptId,
        @Param("fencingToken") long fencingToken,
        @Param("status") String status,
        @Param("errorCode") String errorCode,
        @Param("resultRef") String resultRef,
        @Param("endedAt") Instant endedAt);

    /**
     * 使用当前 Owner 与 Token 完成 Job 或安排退避/死信。
     *
     * @param jobId        Job UUID
     * @param owner        Worker Key
     * @param fencingToken Token
     * @param status       Job 目标状态
     * @param availableAt  下次时间
     * @param errorCode    错误码
     * @param resultRef    结果引用
     * @param updatedAt    更新时间
     * @return 更新行数
     */
    @Update("""
        UPDATE job SET status = #{status}, available_at = #{availableAt},
            claimed_by = NULL, claim_until = NULL, error_code = #{errorCode},
            result_ref = #{resultRef}, updated_at = #{updatedAt}
        WHERE id = #{jobId,jdbcType=BINARY} AND status = 'CLAIMED'
          AND claimed_by = #{owner} AND current_fencing_token = #{fencingToken}
        """)
    int completeJob(
        @Param("jobId") UUID jobId,
        @Param("owner") String owner,
        @Param("fencingToken") long fencingToken,
        @Param("status") String status,
        @Param("availableAt") Instant availableAt,
        @Param("errorCode") String errorCode,
        @Param("resultRef") String resultRef,
        @Param("updatedAt") Instant updatedAt);

    /**
     * 使用当前 Owner 与 Token 删除完成后的 Lease。
     *
     * @param jobId        Job UUID
     * @param owner        Worker Key
     * @param fencingToken Token
     * @return 删除行数
     */
    @Delete("""
        DELETE FROM job_lease WHERE job_id = #{jobId,jdbcType=BINARY}
          AND owner = #{owner} AND fencing_token = #{fencingToken}
        """)
    int deleteLease(
        @Param("jobId") UUID jobId,
        @Param("owner") String owner,
        @Param("fencingToken") long fencingToken);

    /**
     * 插入 Dead Letter。
     *
     * @param row Dead Letter 行
     */
    @Insert("""
        INSERT INTO dead_letter
            (id, job_id, final_attempt_id, reason, redrive_count, status,
             redriven_by, redrive_reason, redriven_at, created_at, updated_at)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.jobId,jdbcType=BINARY},
             #{row.finalAttemptId,jdbcType=BINARY}, #{row.reason}, #{row.redriveCount},
             #{row.status}, NULL, NULL, NULL, #{row.createdAt}, #{row.updatedAt})
        ON DUPLICATE KEY UPDATE final_attempt_id = VALUES(final_attempt_id),
            reason = VALUES(reason), status = 'OPEN', redriven_by = NULL,
            redrive_reason = NULL, redriven_at = NULL, updated_at = VALUES(updated_at)
        """)
    void insertDeadLetter(@Param("row") DeadLetterRow row);

    /**
     * 插入 Scheduler Outbox。
     *
     * @param eventId       Event UUID
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 UUID
     * @param type          事件类型
     * @param payloadJson   Payload JSON
     * @param status        Outbox 状态
     * @param availableAt   可投递时间
     * @param attempts      已尝试次数
     * @param createdAt     创建时间
     */
    @Insert("""
        INSERT INTO scheduler_outbox
            (event_id, aggregate_type, aggregate_id, type, payload_json,
             payload_object_uri, status, available_at, attempts,
             claimed_by, claim_until, created_at)
        VALUES
            (#{eventId,jdbcType=BINARY}, #{aggregateType}, #{aggregateId,jdbcType=BINARY},
             #{type}, #{payloadJson}, NULL, #{status}, #{availableAt}, #{attempts},
             NULL, NULL, #{createdAt})
        """)
    void insertOutbox(
        @Param("eventId") UUID eventId,
        @Param("aggregateType") String aggregateType,
        @Param("aggregateId") UUID aggregateId,
        @Param("type") String type,
        @Param("payloadJson") String payloadJson,
        @Param("status") String status,
        @Param("availableAt") Instant availableAt,
        @Param("attempts") int attempts,
        @Param("createdAt") Instant createdAt);

    /**
     * 取消租户内非终态 Job。
     *
     * @param jobId          Job UUID
     * @param organizationId 组织 UUID
     * @param projectId      项目 UUID
     * @param updatedAt      取消时间
     * @return 更新行数
     */
    @Update("""
        UPDATE job SET status = 'CANCELLED', claimed_by = NULL, claim_until = NULL,
            error_code = 'JOB_CANCELLED', updated_at = #{updatedAt}
        WHERE id = #{jobId,jdbcType=BINARY}
          AND organization_id = #{organizationId,jdbcType=BINARY}
          AND project_id = #{projectId,jdbcType=BINARY}
          AND status IN ('READY', 'CLAIMED', 'RETRY_WAIT')
        """)
    int cancelJob(
        @Param("jobId") UUID jobId,
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("updatedAt") Instant updatedAt);

    /**
     * 终结取消 Job 的 RUNNING Attempt。
     *
     * @param jobId   Job UUID
     * @param endedAt 取消时间
     * @return 更新行数
     */
    @Update("""
        UPDATE job_attempt SET status = 'FAILED', ended_at = #{endedAt},
            error_code = 'JOB_CANCELLED'
        WHERE job_id = #{jobId,jdbcType=BINARY} AND status = 'RUNNING'
        """)
    int cancelRunningAttempt(@Param("jobId") UUID jobId, @Param("endedAt") Instant endedAt);

    /**
     * 删除取消 Job 的 Lease。
     *
     * @param jobId Job UUID
     * @return 删除行数
     */
    @Delete("DELETE FROM job_lease WHERE job_id = #{jobId,jdbcType=BINARY}")
    int deleteLeaseByJob(@Param("jobId") UUID jobId);

    /**
     * 将 OPEN Dead Letter 标记为 REDRIVEN。
     *
     * @param jobId      Job UUID
     * @param actor      操作者
     * @param reason     原因
     * @param redrivenAt 时间
     * @return 更新行数
     */
    @Update("""
        UPDATE dead_letter SET status = 'REDRIVEN', redrive_count = redrive_count + 1,
            redriven_by = #{actor}, redrive_reason = #{reason}, redriven_at = #{redrivenAt},
            updated_at = #{redrivenAt}
        WHERE job_id = #{jobId,jdbcType=BINARY} AND status = 'OPEN'
        """)
    int redriveDeadLetter(
        @Param("jobId") UUID jobId,
        @Param("actor") String actor,
        @Param("reason") String reason,
        @Param("redrivenAt") Instant redrivenAt);

    /**
     * 将租户 DEAD_LETTERED Job 重新置为 READY。
     *
     * @param jobId          Job UUID
     * @param organizationId 组织 UUID
     * @param projectId      项目 UUID
     * @param availableAt    可执行时间
     * @return 更新行数
     */
    @Update("""
        UPDATE job SET status = 'READY', available_at = #{availableAt},
            error_code = NULL, result_ref = NULL, updated_at = #{availableAt}
        WHERE id = #{jobId,jdbcType=BINARY}
          AND organization_id = #{organizationId,jdbcType=BINARY}
          AND project_id = #{projectId,jdbcType=BINARY}
          AND status = 'DEAD_LETTERED'
        """)
    int reactivateJob(
        @Param("jobId") UUID jobId,
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("availableAt") Instant availableAt);

    /**
     * 列出租户 OPEN Dead Letter。
     *
     * @param organizationId 组织 UUID
     * @param projectId      项目 UUID
     * @param limit          最大数量
     * @return Dead Letter 行
     */
    @Select("""
        SELECT d.id, d.job_id, d.final_attempt_id, d.reason, d.redrive_count,
               d.status, d.created_at, d.updated_at
        FROM dead_letter d JOIN job j ON j.id = d.job_id
        WHERE j.organization_id = #{organizationId,jdbcType=BINARY}
          AND j.project_id = #{projectId,jdbcType=BINARY} AND d.status = 'OPEN'
        ORDER BY d.created_at, d.id LIMIT #{limit}
        """)
    List<DeadLetterRow> listOpenDeadLetters(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("limit") int limit);

    /**
     * 统计指定类型到期队列深度。
     *
     * @param type Job 类型
     * @param now  当前时间
     * @return Job 数量
     */
    @Select("""
        SELECT COUNT(*) FROM job WHERE type = #{type}
          AND status IN ('READY', 'RETRY_WAIT') AND available_at <= #{now}
        """)
    long countDue(@Param("type") String type, @Param("now") Instant now);

    /**
     * 查询指定类型最老到期时间。
     *
     * @param type Job 类型
     * @param now  当前时间
     * @return 最老时间
     */
    @Select("""
        SELECT MIN(available_at) FROM job WHERE type = #{type}
          AND status IN ('READY', 'RETRY_WAIT') AND available_at <= #{now}
        """)
    Optional<Instant> oldestDue(@Param("type") String type, @Param("now") Instant now);

    /**
     * 按 Provider 幂等键读取 Delivery。
     *
     * @param providerKey Provider Key
     * @return Delivery 行
     */
    @Select("""
        SELECT id, job_id, channel_type, endpoint_identity, provider_idempotency_key,
               status, provider_message_id, response_summary, created_at, updated_at
        FROM delivery WHERE provider_idempotency_key = #{providerKey}
        """)
    Optional<DeliveryRow> findDeliveryByProviderKey(@Param("providerKey") String providerKey);

    /**
     * 插入 Delivery。
     *
     * @param row Delivery 行
     */
    @Insert("""
        INSERT INTO delivery
            (id, job_id, channel_type, endpoint_identity, provider_idempotency_key,
             status, provider_message_id, response_summary, created_at, updated_at)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.jobId,jdbcType=BINARY}, #{row.channelType},
             #{row.endpointIdentity}, #{row.providerIdempotencyKey}, #{row.status},
             #{row.providerMessageId}, #{row.responseSummary}, #{row.createdAt}, #{row.updatedAt})
        """)
    void insertDelivery(@Param("row") DeliveryRow row);

    /**
     * 通过所属 Job 当前 Fencing Token 转换 Delivery。
     *
     * @param deliveryId        Delivery UUID
     * @param jobId             Job UUID
     * @param fencingToken      当前 Token
     * @param current           当前状态
     * @param target            目标状态
     * @param providerMessageId Provider 消息 ID
     * @param responseSummary   摘要
     * @param updatedAt         更新时间
     * @return 更新行数
     */
    @Update("""
        UPDATE delivery d JOIN job j ON j.id = d.job_id
        SET d.status = #{target}, d.provider_message_id = #{providerMessageId},
            d.response_summary = #{responseSummary}, d.updated_at = #{updatedAt}
        WHERE d.id = #{deliveryId,jdbcType=BINARY} AND d.job_id = #{jobId,jdbcType=BINARY}
          AND d.status = #{current} AND j.status = 'CLAIMED'
          AND j.current_fencing_token = #{fencingToken}
        """)
    int transitionDelivery(
        @Param("deliveryId") UUID deliveryId,
        @Param("jobId") UUID jobId,
        @Param("fencingToken") long fencingToken,
        @Param("current") String current,
        @Param("target") String target,
        @Param("providerMessageId") String providerMessageId,
        @Param("responseSummary") String responseSummary,
        @Param("updatedAt") Instant updatedAt);

    /**
     * 读取 Trigger。
     *
     * @param id Trigger UUID
     * @return Trigger 行
     */
    @Select("""
        SELECT id, organization_id, project_id, trigger_key, type, schedule_expression,
               time_zone, config_json, secret_ref, target_contract, target_job_type,
               status, version, created_at, updated_at
        FROM trigger_definition WHERE id = #{id,jdbcType=BINARY}
        """)
    Optional<TriggerRow> findTrigger(@Param("id") UUID id);

    /**
     * 按租户和稳定 Key 读取 Trigger。
     *
     * @param organizationId 组织 UUID
     * @param projectId      项目 UUID
     * @param key            Trigger Key
     * @return Trigger 行
     */
    @Select("""
        SELECT id, organization_id, project_id, trigger_key, type, schedule_expression,
               time_zone, config_json, secret_ref, target_contract, target_job_type,
               status, version, created_at, updated_at
        FROM trigger_definition
        WHERE organization_id = #{organizationId,jdbcType=BINARY}
          AND project_id = #{projectId,jdbcType=BINARY} AND trigger_key = #{key}
        """)
    Optional<TriggerRow> findTriggerByKey(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("key") String key);

    /**
     * 列出到期 Cron Trigger。
     *
     * @param now   当前时间
     * @param limit 最大数量
     * @return Trigger 行
     */
    @Select("""
        SELECT t.id, t.organization_id, t.project_id, t.trigger_key, t.type,
               t.schedule_expression, t.time_zone, t.config_json, t.secret_ref,
               t.target_contract, t.target_job_type, t.status, t.version,
               t.created_at, t.updated_at
        FROM trigger_definition t JOIN trigger_cursor c ON c.trigger_id = t.id
        WHERE t.type = 'CRON' AND t.status = 'ENABLED' AND c.next_fire_at <= #{now}
        ORDER BY c.next_fire_at, t.id LIMIT #{limit}
        """)
    List<TriggerRow> findDueTriggers(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * 读取 Trigger Cursor。
     *
     * @param triggerId Trigger UUID
     * @return Cursor 行
     */
    @Select("""
        SELECT trigger_id, next_fire_at, last_fire_at, last_token, version
        FROM trigger_cursor WHERE trigger_id = #{triggerId,jdbcType=BINARY}
        """)
    Optional<CursorRow> findCursor(@Param("triggerId") UUID triggerId);

    /**
     * 以版本条件推进 Cursor。
     *
     * @param triggerId       Trigger UUID
     * @param expectedVersion 预期版本
     * @param scheduledAt     当前计划时间
     * @param nextFireAt      下次时间
     * @param fireToken       点火 Token
     * @return 更新行数
     */
    @Update("""
        UPDATE trigger_cursor SET next_fire_at = #{nextFireAt},
            last_fire_at = #{scheduledAt}, last_token = #{fireToken}, version = version + 1
        WHERE trigger_id = #{triggerId,jdbcType=BINARY} AND version = #{expectedVersion}
          AND next_fire_at = #{scheduledAt}
        """)
    int advanceCursor(
        @Param("triggerId") UUID triggerId,
        @Param("expectedVersion") long expectedVersion,
        @Param("scheduledAt") Instant scheduledAt,
        @Param("nextFireAt") Instant nextFireAt,
        @Param("fireToken") String fireToken);

    /**
     * 查询 Webhook Nonce 是否已存在。
     *
     * @param triggerId Trigger UUID
     * @param nonce     Nonce
     * @return 记录数
     */
    @Select("""
        SELECT COUNT(*) FROM scheduler_idempotency_record
        WHERE scope_type = 'WEBHOOK_NONCE' AND scope_id = #{triggerId,jdbcType=BINARY}
          AND idempotency_key = #{nonce}
        """)
    long countWebhookNonce(@Param("triggerId") UUID triggerId, @Param("nonce") String nonce);

    /**
     * 插入 Webhook Nonce Replay Protection 记录。
     *
     * @param id          UUID
     * @param triggerId   Trigger UUID
     * @param nonce       Nonce
     * @param requestHash 请求 Hash 原始字节
     * @param expiresAt   到期时间
     * @param createdAt   创建时间
     */
    @Insert("""
        INSERT INTO scheduler_idempotency_record
            (id, scope_type, scope_id, idempotency_key, request_hash, result_ref,
             status, expires_at, created_at, updated_at)
        VALUES
            (#{id,jdbcType=BINARY}, 'WEBHOOK_NONCE', #{triggerId,jdbcType=BINARY},
             #{nonce}, #{requestHash,jdbcType=BINARY}, NULL, 'COMPLETED',
             #{expiresAt}, #{createdAt}, #{createdAt})
        """)
    int insertWebhookNonce(
        @Param("id") UUID id,
        @Param("triggerId") UUID triggerId,
        @Param("nonce") String nonce,
        @Param("requestHash") byte[] requestHash,
        @Param("expiresAt") Instant expiresAt,
        @Param("createdAt") Instant createdAt);
}
