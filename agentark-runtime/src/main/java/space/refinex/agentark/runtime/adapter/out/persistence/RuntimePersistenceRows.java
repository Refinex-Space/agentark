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

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 集中定义 MyBatis 与 Runtime 领域模型之间隔离的数据库行结构。
 *
 * @author refinex
 */
public final class RuntimePersistenceRows {

    /**
     * 禁止实例化数据库行容器。
     */
    private RuntimePersistenceRows() {
    }

    /**
     * @param id                  会话主键 UUID
     * @param organizationId      组织 UUID
     * @param projectId           项目 UUID
     * @param deploymentId        固定 Deployment UUID
     * @param revisionId          固定 Revision UUID
     * @param snapshotId          固定 Snapshot UUID
     * @param snapshotHash        Snapshot SHA-256 字节
     * @param participantMetadata 参与者元数据 JSON
     * @param channelMetadata     渠道元数据 JSON
     * @param status              Session 状态
     * @param eventSequence       Event 计数器
     * @param version             乐观锁版本
     * @param createdAt           创建时刻
     * @param updatedAt           更新时间
     * @author refinex
     */
    public record SessionRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        UUID deploymentId,
        UUID revisionId,
        UUID snapshotId,
        byte[] snapshotHash,
        String participantMetadata,
        String channelMetadata,
        String status,
        long eventSequence,
        long version,
        Instant createdAt,
        Instant updatedAt) {
    }

    /**
     * @param id              轮次主键 UUID
     * @param organizationId  组织 UUID
     * @param projectId       项目 UUID
     * @param sessionId       所属会话 UUID
     * @param sequence        Turn 序号
     * @param inputStorage    输入保存方式
     * @param inputJson       内联 JSON
     * @param inputObjectUri  对象 URI
     * @param inputObjectSize 对象字节数
     * @param inputMediaType  对象媒体类型
     * @param inputHash       输入 SHA-256 字节
     * @param status          Turn 状态
     * @param currentRunId    当前 Run UUID
     * @param fencingToken    栅栏令牌
     * @param version         乐观锁版本
     * @param createdAt       创建时刻
     * @param updatedAt       更新时间
     * @author refinex
     */
    public record TurnRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        UUID sessionId,
        long sequence,
        String inputStorage,
        String inputJson,
        String inputObjectUri,
        Long inputObjectSize,
        String inputMediaType,
        byte[] inputHash,
        String status,
        UUID currentRunId,
        long fencingToken,
        long version,
        Instant createdAt,
        Instant updatedAt) {
    }

    /**
     * @param id              运行尝试主键 UUID
     * @param organizationId  组织 UUID
     * @param projectId       项目 UUID
     * @param sessionId       所属会话 UUID
     * @param turnId          所属轮次 UUID
     * @param attemptNumber   Attempt 序号
     * @param runtimeProvider 运行时供应方标识
     * @param compilerVersion 编译器版本
     * @param status          Run 状态
     * @param eventSequence   Event 计数器
     * @param fencingToken    栅栏令牌
     * @param startedAt       开始时刻
     * @param endedAt         终态时刻
     * @param errorCode       稳定错误码
     * @param createdAt       创建时刻
     * @author refinex
     */
    public record RunRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        UUID sessionId,
        UUID turnId,
        int attemptNumber,
        String runtimeProvider,
        String compilerVersion,
        String status,
        long eventSequence,
        long fencingToken,
        Instant startedAt,
        Instant endedAt,
        String errorCode,
        Instant createdAt) {
    }

    /**
     * @param id           工作项主键 UUID
     * @param runId        所属运行尝试 UUID
     * @param status       队列状态
     * @param priority     优先级
     * @param availableAt  最早 Claim 时刻
     * @param claimedBy    当前持有者实例 Key
     * @param claimUntil   Lease 到期时刻
     * @param fencingToken 栅栏令牌
     * @param attemptCount Claim 次数
     * @param createdAt    创建时刻
     * @author refinex
     */
    public record WorkItemRow(
        UUID id,
        UUID runId,
        String status,
        int priority,
        Instant availableAt,
        String claimedBy,
        Instant claimUntil,
        long fencingToken,
        int attemptCount,
        Instant createdAt) {
    }

    /**
     * @param id           Runtime Instance UUID 标识
     * @param instanceKey  部署范围内稳定实例 Key
     * @param startedAt    本次进程启动时刻
     * @param heartbeatAt  最近心跳时刻
     * @param capabilities 不含秘密的能力 JSON
     * @param drainStatus  ACTIVE、DRAINING 或 DRAINED
     * @author refinex
     */
    public record RuntimeInstanceRow(
        UUID id,
        String instanceKey,
        Instant startedAt,
        Instant heartbeatAt,
        String capabilities,
        String drainStatus) {

        /**
         * 校验 Runtime Instance 数据库行包含完整身份、心跳和能力字段。
         */
        public RuntimeInstanceRow {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(startedAt, "startedAt must not be null");
            Objects.requireNonNull(heartbeatAt, "heartbeatAt must not be null");
            if (instanceKey == null || instanceKey.isBlank()
                || capabilities == null || capabilities.isBlank()
                || drainStatus == null || drainStatus.isBlank()) {
                throw new IllegalArgumentException("runtime instance row is invalid");
            }
        }
    }

    /**
     * @param id              事件主键 UUID
     * @param organizationId  组织 UUID
     * @param projectId       项目 UUID
     * @param sessionId       所属会话 UUID
     * @param turnId          所属轮次 UUID
     * @param runId           所属运行尝试 UUID
     * @param sessionSequence Session Event 序号
     * @param runSequence     Run Event 序号
     * @param type            Event 类型
     * @param schemaVersion   Schema 版本
     * @param traceId         追踪标识
     * @param payloadStorage  载荷保存方式
     * @param payloadJson     内联 JSON
     * @param occurredAt      发生时刻
     * @param fencingToken    栅栏令牌
     * @param objectUri       对象 URI
     * @param objectHash      对象 SHA-256 字节
     * @param objectSize      对象字节数
     * @param mediaType       对象媒体类型
     * @author refinex
     */
    public record EventRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        UUID sessionId,
        UUID turnId,
        UUID runId,
        long sessionSequence,
        long runSequence,
        String type,
        int schemaVersion,
        String traceId,
        String payloadStorage,
        String payloadJson,
        Instant occurredAt,
        long fencingToken,
        String objectUri,
        byte[] objectHash,
        Long objectSize,
        String mediaType) {
    }

    /**
     * @param scopeType      作用域类型
     * @param scopeId        作用域标识
     * @param idempotencyKey 幂等键
     * @param requestHash    请求 SHA-256 字节
     * @param resultRef      结果引用
     * @param status         幂等状态
     * @param expiresAt      到期时刻
     * @param createdAt      创建时刻
     * @author refinex
     */
    public record IdempotencyRow(
        String scopeType,
        String scopeId,
        String idempotencyKey,
        byte[] requestHash,
        String resultRef,
        String status,
        Instant expiresAt,
        Instant createdAt) {
    }

    /**
     * @param id             审批主键 UUID
     * @param organizationId 组织 UUID
     * @param projectId      项目 UUID
     * @param runId          所属运行尝试 UUID
     * @param toolName       Tool 名称
     * @param actionCode     动作代码
     * @param argumentHash   参数 SHA-256 字节
     * @param policyVersion  策略版本
     * @param status         审批状态
     * @param version        乐观锁版本
     * @param expiresAt      到期时刻
     * @param decisionBy     决策主体
     * @param decisionAt     决策时刻
     * @param createdAt      创建时刻
     * @author refinex
     */
    public record ApprovalRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        UUID runId,
        String toolName,
        String actionCode,
        byte[] argumentHash,
        String policyVersion,
        String status,
        long version,
        Instant expiresAt,
        String decisionBy,
        Instant decisionAt,
        Instant createdAt) {
    }

    /**
     * @param id             状态版本主键 UUID
     * @param organizationId 组织 UUID
     * @param projectId      项目 UUID
     * @param sessionId      所属会话 UUID
     * @param runId          所属运行尝试 UUID
     * @param agentKey       智能体稳定 Key
     * @param stateKey       状态稳定 Key
     * @param itemIndex      元素下标
     * @param stateVersion   State 版本
     * @param stateStorage   保存方式
     * @param stateJson      内联 JSON
     * @param objectUri      对象 URI
     * @param objectSize     对象字节数
     * @param mediaType      对象媒体类型
     * @param contentHash    内容 SHA-256 字节
     * @param committed      是否已提交
     * @param fencingToken   栅栏令牌
     * @param createdAt      创建时刻
     * @author refinex
     */
    public record StateRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        UUID sessionId,
        UUID runId,
        String agentKey,
        String stateKey,
        int itemIndex,
        long stateVersion,
        String stateStorage,
        String stateJson,
        String objectUri,
        Long objectSize,
        String mediaType,
        byte[] contentHash,
        boolean committed,
        long fencingToken,
        Instant createdAt) {
    }

    /**
     * @param id                检查点主键 UUID
     * @param runId             所属运行尝试 UUID
     * @param sequence          Checkpoint 序号
     * @param agentStateId      状态版本行 UUID
     * @param agentStateVersion State 版本
     * @param eventSequence     Event 序号
     * @param contentHash       内容 SHA-256 字节
     * @param recoverable       是否可恢复
     * @param fencingToken      栅栏令牌
     * @param createdAt         创建时刻
     * @author refinex
     */
    public record CheckpointRow(
        UUID id,
        UUID runId,
        long sequence,
        UUID agentStateId,
        long agentStateVersion,
        long eventSequence,
        byte[] contentHash,
        boolean recoverable,
        long fencingToken,
        Instant createdAt) {
    }

    /**
     * @param storage   载荷保存方式
     * @param json      内联 JSON
     * @param uri       对象 URI
     * @param hash      对象 SHA-256 字节
     * @param size      对象字节数
     * @param mediaType 对象媒体类型
     * @author refinex
     */
    public record PayloadColumns(
        String storage, String json, String uri, byte[] hash, Long size, String mediaType) {
    }
}
