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

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.domain.RuntimeModels.FencingToken;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimePayload;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * 集中定义创建、接收、取消和恢复 Runtime 聚合的语言中立命令。
 *
 * @author refinex
 */
public final class RuntimeCommands {

    /**
     * 禁止实例化命令容器。
     */
    private RuntimeCommands() {
    }

    /**
     * @param organizationId      所属组织
     * @param projectId           所属项目
     * @param deploymentId        已解析且启用的 Deployment
     * @param revisionId          创建时固定的 Revision
     * @param snapshotId          创建时固定的 Snapshot
     * @param snapshotHash        规范快照内容 Hash
     * @param participantMetadata 非敏感参与者元数据
     * @param channelMetadata     非敏感渠道元数据
     * @param idempotencyKey      调用方幂等键
     * @param requestHash         规范创建请求 Hash
     * @author refinex
     */
    public record CreateSessionCommand(
        OrganizationId organizationId,
        ProjectId projectId,
        DeploymentId deploymentId,
        RevisionId revisionId,
        SnapshotId snapshotId,
        Checksum snapshotHash,
        Map<String, String> participantMetadata,
        Map<String, String> channelMetadata,
        String idempotencyKey,
        Checksum requestHash) {

        /**
         * 校验固定 Revision/Snapshot 和幂等字段。
         */
        public CreateSessionCommand {
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
            Objects.requireNonNull(requestHash, "requestHash must not be null");
            requireKey(idempotencyKey);
        }
    }

    /**
     * @param organizationId  所属组织
     * @param projectId       所属项目
     * @param sessionId       目标 Session
     * @param input           Turn 输入载荷
     * @param inputHash       输入规范 Hash
     * @param runtimeProvider Snapshot 指定 Provider
     * @param compilerVersion Provider Adapter 编译器版本
     * @param priority        Work Item 优先级
     * @param idempotencyKey  调用方幂等键
     * @param requestHash     规范请求 Hash
     * @author refinex
     */
    public record AcceptTurnCommand(
        OrganizationId organizationId,
        ProjectId projectId,
        SessionId sessionId,
        RuntimePayload input,
        Checksum inputHash,
        String runtimeProvider,
        String compilerVersion,
        int priority,
        String idempotencyKey,
        Checksum requestHash) {

        /**
         * 校验 Turn 输入、Provider 和幂等字段。
         */
        public AcceptTurnCommand {
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(input, "input must not be null");
            Objects.requireNonNull(inputHash, "inputHash must not be null");
            Objects.requireNonNull(requestHash, "requestHash must not be null");
            if (runtimeProvider == null || runtimeProvider.isBlank()
                || compilerVersion == null || compilerVersion.isBlank()) {
                throw new IllegalArgumentException("runtime provider metadata is invalid");
            }
            requireKey(idempotencyKey);
        }
    }

    /**
     * @param instanceKey Runtime Instance 稳定 Key
     * @param leaseTtl    Claim Lease 有效期
     * @author refinex
     */
    public record ExecuteNextCommand(String instanceKey, Duration leaseTtl) {

        /**
         * 校验 Worker 身份和 Lease 有效期。
         */
        public ExecuteNextCommand {
            if (instanceKey == null || instanceKey.isBlank()) {
                throw new IllegalArgumentException("instanceKey must not be blank");
            }
            Objects.requireNonNull(leaseTtl, "leaseTtl must not be null");
            if (leaseTtl.isNegative() || leaseTtl.isZero()) {
                throw new IllegalArgumentException("leaseTtl must be positive");
            }
        }
    }

    /**
     * @param turnId        待取消 Turn
     * @param expectedRunId 调用方观察到的当前 Run
     * @param fencingToken  当前 Owner 令牌；未 Claim 时可为零
     * @param reasonCode    不含用户载荷的稳定取消原因
     * @author refinex
     */
    public record CancellationCommand(
        TurnId turnId, RunId expectedRunId, FencingToken fencingToken, String reasonCode) {

        /**
         * 校验取消命令必须绑定当前 Run 与令牌。
         */
        public CancellationCommand {
            Objects.requireNonNull(turnId, "turnId must not be null");
            Objects.requireNonNull(expectedRunId, "expectedRunId must not be null");
            Objects.requireNonNull(fencingToken, "fencingToken must not be null");
            if (reasonCode == null || reasonCode.isBlank()) {
                throw new IllegalArgumentException("reasonCode must not be blank");
            }
        }
    }

    /**
     * @param runId        待恢复的 PAUSED Run
     * @param approvalId   已批准且参数未变化的 Approval
     * @param argumentHash 原始 Tool 参数 Hash
     * @param fencingToken 当前 Owner 令牌
     * @author refinex
     */
    public record ResumeCommand(
        RunId runId, ApprovalId approvalId, Checksum argumentHash, FencingToken fencingToken) {

        /**
         * 校验恢复命令绑定 Approval、参数 Hash 和当前 Owner。
         */
        public ResumeCommand {
            Objects.requireNonNull(runId, "runId must not be null");
            Objects.requireNonNull(approvalId, "approvalId must not be null");
            Objects.requireNonNull(argumentHash, "argumentHash must not be null");
            Objects.requireNonNull(fencingToken, "fencingToken must not be null");
        }
    }

    /**
     * @param turnId          已失败或超时的 Turn
     * @param runtimeProvider 新 Attempt 使用的相同 Runtime Provider
     * @param compilerVersion Provider Adapter 编译器版本
     * @param priority        新 Work Item 优先级
     * @author refinex
     */
    public record RetryTurnCommand(
        TurnId turnId, String runtimeProvider, String compilerVersion, int priority) {

        /**
         * 校验重试命令的 Provider 编译元数据。
         */
        public RetryTurnCommand {
            Objects.requireNonNull(turnId, "turnId must not be null");
            if (runtimeProvider == null || runtimeProvider.isBlank()
                || compilerVersion == null || compilerVersion.isBlank()) {
                throw new IllegalArgumentException("retry provider metadata is invalid");
            }
        }
    }

    /**
     * 校验调用方幂等键长度和非空约束。
     *
     * @param key 幂等键
     */
    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException("idempotency key is invalid");
        }
    }
}
