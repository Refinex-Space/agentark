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

package space.refinex.agentark.runtime.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 集中定义 Runtime Public API 的语言中立请求和响应契约。
 *
 * @author refinex
 */
public final class RuntimeApiModels {

    /**
     * 禁止实例化 API 模型容器。
     */
    private RuntimeApiModels() {
    }

    /**
     * @param organizationId      已授权组织 UUIDv7
     * @param projectId           已授权项目 UUIDv7
     * @param deploymentId        启用的 Deployment UUIDv7
     * @param participantMetadata 非敏感参与者元数据
     * @param channelMetadata     非敏感渠道元数据
     * @author refinex
     */
    public record CreateSessionRequest(
        @NotBlank String organizationId,
        @NotBlank String projectId,
        @NotBlank String deploymentId,
        @Size(max = 64)
        Map<@NotBlank @Size(max = 128) String, @NotBlank @Size(max = 1024) String> participantMetadata,
        @Size(max = 64)
        Map<@NotBlank @Size(max = 128) String, @NotBlank @Size(max = 1024) String> channelMetadata) {
    }

    /**
     * @param organizationId 已授权组织 UUIDv7
     * @param projectId      已授权项目 UUIDv7
     * @param input          不含明文 Secret 和隐藏推理过程的输入 JSON
     * @param priority       队列优先级，数值越大优先级越高
     * @author refinex
     */
    public record CreateTurnRequest(
        @NotBlank String organizationId,
        @NotBlank String projectId,
        @NotNull JsonNode input,
        @Min(-100) @Max(100) int priority) {
    }

    /**
     * @param expectedVersion 调用方读取到的 Approval 乐观锁版本
     * @param decision        APPROVED 或 REJECTED
     * @author refinex
     */
    public record DecideApprovalRequest(
        @Min(0) long expectedVersion,
        @NotBlank String decision) {
    }

    /**
     * @param sessionId      Session UUIDv7 标识
     * @param organizationId 所属组织 UUIDv7
     * @param projectId      所属项目 UUIDv7
     * @param deploymentId   固定 Deployment UUIDv7
     * @param revisionId     固定 Revision UUIDv7
     * @param snapshotId     固定 Snapshot UUIDv7
     * @param snapshotHash   固定 Snapshot SHA-256
     * @param status         ACTIVE、CLOSED 或 FAILED
     * @param createdAt      创建时刻
     * @author refinex
     */
    public record SessionResponse(
        String sessionId,
        String organizationId,
        String projectId,
        String deploymentId,
        String revisionId,
        String snapshotId,
        String snapshotHash,
        String status,
        Instant createdAt) {
    }

    /**
     * @param turnId    Turn UUIDv7 标识
     * @param runId     首个 Run UUIDv7
     * @param sequence  Session 内 Turn 序号
     * @param status    ACCEPTED、QUEUED、RUNNING 或终态
     * @param createdAt 接单时刻
     * @author refinex
     */
    public record TurnResponse(
        String turnId,
        String runId,
        long sequence,
        String status,
        Instant createdAt) {
    }

    /**
     * @param runId           Run UUIDv7 标识
     * @param sessionId       Session UUIDv7 标识
     * @param turnId          Turn UUIDv7 标识
     * @param attemptNumber   Attempt 序号
     * @param status          Run 状态
     * @param runtimeProvider Runtime Provider 标识
     * @param compilerVersion Compiler 版本
     * @param fencingToken    当前 MySQL 权威 Fencing Token
     * @param startedAt       首次开始时刻
     * @param endedAt         终态结束时刻
     * @param errorCode       稳定错误码
     * @author refinex
     */
    public record RunResponse(
        String runId,
        String sessionId,
        String turnId,
        int attemptNumber,
        String status,
        String runtimeProvider,
        String compilerVersion,
        long fencingToken,
        Instant startedAt,
        Instant endedAt,
        String errorCode) {
    }

    /**
     * @param schemaVersion   Event Schema 版本
     * @param eventId         Event UUIDv7 标识
     * @param sessionSequence SSE Last-Event-ID 使用的 Session Sequence
     * @param sequence        Run 内序号
     * @param eventType       稳定 Event 类型
     * @param occurredAt      发生时刻
     * @param organizationId  所属组织 UUIDv7 标识
     * @param projectId       所属项目 UUIDv7 标识
     * @param sessionId       Session UUIDv7 标识
     * @param turnId          Turn UUIDv7 标识
     * @param runId           Run UUIDv7 标识
     * @param traceId         稳定 Trace ID
     * @param fencingToken    Event 写入时的当前 Fencing Token
     * @param payload         小型内联 JSON
     * @param payloadRef      大载荷受控 ObjectRef
     * @author refinex
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventResponse(
        int schemaVersion,
        String eventId,
        long sessionSequence,
        long sequence,
        String eventType,
        Instant occurredAt,
        String organizationId,
        String projectId,
        String sessionId,
        String turnId,
        String runId,
        String traceId,
        long fencingToken,
        JsonNode payload,
        ObjectRefResponse payloadRef) {
    }

    /**
     * @param uri       受控 Object Store URI
     * @param checksum  SHA-256 规范校验和
     * @param size      对象字节数
     * @param mediaType 具体媒体类型
     * @author refinex
     */
    public record ObjectRefResponse(
        String uri,
        String checksum,
        long size,
        String mediaType) {
    }

    /**
     * @param approvalId     Approval UUIDv7 标识
     * @param runId          所属 Run UUIDv7
     * @param organizationId 所属组织 UUIDv7
     * @param projectId      所属项目 UUIDv7
     * @param toolName       Tool 稳定名称
     * @param action         TOOL_EXECUTE 与 Tool Call 标识
     * @param argumentHash   参数 SHA-256
     * @param policyVersion  固定策略版本
     * @param status         PENDING、APPROVED、REJECTED、EXPIRED 或 CANCELLED
     * @param version        乐观锁版本
     * @param expiresAt      到期时刻
     * @param createdAt      创建时刻
     * @author refinex
     */
    public record ApprovalResponse(
        String approvalId,
        String runId,
        String organizationId,
        String projectId,
        String toolName,
        String action,
        String argumentHash,
        String policyVersion,
        String status,
        long version,
        Instant expiresAt,
        Instant createdAt) {
    }

    /**
     * @param items      当前页 Approval
     * @param nextCursor 下一页 UUIDv7 游标；无后续页时为空
     * @author refinex
     */
    public record ApprovalPage(List<ApprovalResponse> items, String nextCursor) {
    }

    /**
     * @param registered      已登记实例数
     * @param healthy         最近一分钟内仍有心跳的实例数
     * @param draining        正在排空或已排空实例数
     * @param lastHeartbeatAt 最近心跳时刻；无实例时为空
     * @param runtimeProvider 当前 Runtime Provider 标识
     * @param compilerVersion 当前 Compiler 版本
     * @param capabilities    不含实例身份的 Provider 能力摘要
     * @author refinex
     */
    public record RuntimeStatusResponse(
        int registered,
        int healthy,
        int draining,
        Instant lastHeartbeatAt,
        String runtimeProvider,
        String compilerVersion,
        Map<String, String> capabilities) {

        /**
         * 防御性复制非敏感 Provider 能力。
         */
        public RuntimeStatusResponse {
            capabilities = Map.copyOf(capabilities);
        }
    }
}
