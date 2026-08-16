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

import java.time.Instant;
import java.util.UUID;

/**
 * 集中定义 Scheduler MyBatis 数据库行，不向 Domain 或 API 暴露。
 *
 * @author refinex
 */
public final class SchedulerPersistenceRows {

    /**
     * 禁止实例化数据库行容器。
     */
    private SchedulerPersistenceRows() {
    }

    /**
     * @param id                    调度任务 UUID
     * @param organizationId        组织 UUID
     * @param projectId             项目 UUID
     * @param type                  Job 类型代码
     * @param businessKey           幂等业务键
     * @param payloadJson           任务载荷 JSON
     * @param payloadObjectUri      可选对象 URI
     * @param payloadHash           SHA-256 原始字节
     * @param status                Job 状态代码
     * @param priority              优先级
     * @param availableAt           最早执行时间
     * @param retryPolicyJson       重试策略 JSON
     * @param idempotencyCapability 幂等能力代码
     * @param currentAttempt        当前 Attempt 数
     * @param currentFencingToken   当前 Fencing Token
     * @param claimedBy             当前 Owner
     * @param claimUntil            Claim 到期时间
     * @param resultRef             结果引用
     * @param errorCode             错误码
     * @param createdAt             创建时间
     * @param updatedAt             更新时间
     * @author refinex
     */
    public record JobRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String type,
        String businessKey,
        String payloadJson,
        String payloadObjectUri,
        byte[] payloadHash,
        String status,
        int priority,
        Instant availableAt,
        String retryPolicyJson,
        String idempotencyCapability,
        int currentAttempt,
        long currentFencingToken,
        String claimedBy,
        Instant claimUntil,
        String resultRef,
        String errorCode,
        Instant createdAt,
        Instant updatedAt) {

        /**
         * 防御性复制 Payload Hash。
         */
        public JobRow {
            payloadHash = payloadHash == null ? null : payloadHash.clone();
        }

        /**
         * 返回防御性复制后的 Payload Hash。
         *
         * @return SHA-256 原始字节
         */
        @Override
        public byte[] payloadHash() {
            return payloadHash == null ? null : payloadHash.clone();
        }
    }

    /**
     * @param id                 触发器 UUID
     * @param organizationId     组织 UUID
     * @param projectId          项目 UUID
     * @param triggerKey         触发器稳定 Key
     * @param type               Trigger 类型
     * @param scheduleExpression Cron 表达式
     * @param timeZone           IANA 时区
     * @param configJson         配置 JSON
     * @param secretRef          外部密钥引用 SecretRef
     * @param targetContract     目标 Contract
     * @param targetJobType      Job 类型
     * @param status             Trigger 状态
     * @param version            乐观锁版本
     * @param createdAt          创建时间
     * @param updatedAt          更新时间
     * @author refinex
     */
    public record TriggerRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String triggerKey,
        String type,
        String scheduleExpression,
        String timeZone,
        String configJson,
        String secretRef,
        String targetContract,
        String targetJobType,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {
    }

    /**
     * @param triggerId  所属触发器 UUID
     * @param nextFireAt 下一点火时间
     * @param lastFireAt 上次点火时间
     * @param lastToken  上次点火 Token
     * @param version    Cursor 版本
     * @author refinex
     */
    public record CursorRow(
        UUID triggerId,
        Instant nextFireAt,
        Instant lastFireAt,
        String lastToken,
        long version) {
    }

    /**
     * @param id                     投递记录 UUID
     * @param jobId                  所属调度任务 UUID
     * @param channelType            Channel 类型
     * @param endpointIdentity       目标身份
     * @param providerIdempotencyKey Provider 幂等键
     * @param status                 Delivery 状态
     * @param providerMessageId      Provider 消息标识
     * @param responseSummary        响应摘要
     * @param createdAt              创建时间
     * @param updatedAt              更新时间
     * @author refinex
     */
    public record DeliveryRow(
        UUID id,
        UUID jobId,
        String channelType,
        String endpointIdentity,
        String providerIdempotencyKey,
        String status,
        String providerMessageId,
        String responseSummary,
        Instant createdAt,
        Instant updatedAt) {
    }

    /**
     * @param id             死信记录 UUID
     * @param jobId          所属调度任务 UUID
     * @param finalAttemptId 最终 Attempt UUID
     * @param reason         失败原因
     * @param redriveCount   Redrive 次数
     * @param status         Dead Letter 状态
     * @param createdAt      创建时间
     * @param updatedAt      更新时间
     * @author refinex
     */
    public record DeadLetterRow(
        UUID id,
        UUID jobId,
        UUID finalAttemptId,
        String reason,
        int redriveCount,
        String status,
        Instant createdAt,
        Instant updatedAt) {
    }
}
