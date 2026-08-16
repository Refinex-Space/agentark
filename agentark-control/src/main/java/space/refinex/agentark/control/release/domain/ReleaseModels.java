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

package space.refinex.agentark.control.release.domain;

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 集中定义发布、不可变修订版本、部署指针、历史和 Outbox 的语言中立领域模型。
 *
 * @author refinex
 */
public final class ReleaseModels {

    /**
     * 禁止实例化领域模型容器。
     */
    private ReleaseModels() {
    }

    /**
     * @param id             Agent 稳定身份
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param key            项目内稳定 Key
     * @param name           展示名称
     * @param description    用途说明
     * @param version        乐观锁版本
     * @param createdAt      创建时刻
     * @author refinex
     */
    public record Agent(
        AgentId id,
        OrganizationId organizationId,
        ProjectId projectId,
        String key,
        String name,
        String description,
        long version,
        Instant createdAt) {
        /**
         * 校验 Agent 稳定身份和租户归属。
         */
        public Agent {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            if (key == null || key.isBlank() || name == null || name.isBlank()
                || version < 0) {
                throw new IllegalArgumentException("agent fields are invalid");
            }
            description = description == null ? "" : description;
            Objects.requireNonNull(createdAt, "createdAt must not be null");
        }
    }

    /**
     * @param agentId        Agent 稳定身份，同时作为 Draft 主键
     * @param organizationId 所属组织
     * @param projectId      所属项目
     * @param spec           当前强类型 Draft 规范
     * @param version        乐观锁版本
     * @param updatedAt      最近更新时间
     * @author refinex
     */
    public record AgentDraft(
        AgentId agentId,
        OrganizationId organizationId,
        ProjectId projectId,
        AgentDraftSpec spec,
        long version,
        Instant updatedAt) {
        /**
         * 校验 Draft 租户归属与乐观锁版本。
         */
        public AgentDraft {
            Objects.requireNonNull(agentId, "agentId must not be null");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(spec, "spec must not be null");
            Objects.requireNonNull(updatedAt, "updatedAt must not be null");
            if (version < 0) {
                throw new IllegalArgumentException("version must not be negative");
            }
        }
    }

    /**
     * 定义 Draft 校验发现的稳定严重程度。
     *
     * @author refinex
     */
    public enum ValidationSeverity {
        /**
         * 阻止发布。
         */
        ERROR,
        /**
         * 不阻止发布但需要显式展示。
         */
        WARNING
    }

    /**
     * @param path     Draft 中的问题路径
     * @param code     稳定问题代码
     * @param severity 严重程度：ERROR、WARNING
     * @param message  面向调用方的中文说明
     * @author refinex
     */
    public record ValidationFinding(
        String path, String code, ValidationSeverity severity, String message) {
        /**
         * 校验问题字段完整且不携带资产内容。
         */
        public ValidationFinding {
            if (path == null || path.isBlank() || code == null || code.isBlank()
                || message == null || message.isBlank()) {
                throw new IllegalArgumentException("validation finding fields must not be blank");
            }
            Objects.requireNonNull(severity, "severity must not be null");
        }
    }

    /**
     * @param id           校验报告标识
     * @param agentId      Agent 标识
     * @param draftVersion 被校验 Draft 乐观锁版本
     * @param valid        是否允许发布
     * @param findings     问题列表
     * @param createdAt    创建时刻
     * @author refinex
     */
    public record ValidationReport(
        EventId id,
        AgentId agentId,
        long draftVersion,
        boolean valid,
        List<ValidationFinding> findings,
        Instant createdAt) {
        /**
         * 校验报告并冻结问题列表。
         */
        public ValidationReport {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(agentId, "agentId must not be null");
            findings = List.copyOf(Objects.requireNonNull(findings, "findings must not be null"));
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            if (draftVersion < 0 || valid == findings.stream()
                .anyMatch(item -> item.severity() == ValidationSeverity.ERROR)) {
                throw new IllegalArgumentException("validation report status is inconsistent");
            }
        }
    }

    /**
     * @param id                   修订版本标识
     * @param organizationId       所属组织
     * @param projectId            所属项目
     * @param agentId              Agent 稳定身份
     * @param snapshotId           Snapshot 标识
     * @param revisionNumber       Agent 内单调递增序号
     * @param schemaVersion        Snapshot Schema 版本
     * @param runtimeProvider      Runtime Provider 标识
     * @param contentHash          Snapshot 内容 Hash
     * @param requiredCapabilities Runtime 必需能力
     * @param createdAt            发布时间
     * @author refinex
     */
    public record AgentRevision(
        RevisionId id,
        OrganizationId organizationId,
        ProjectId projectId,
        AgentId agentId,
        SnapshotId snapshotId,
        long revisionNumber,
        int schemaVersion,
        String runtimeProvider,
        Checksum contentHash,
        List<String> requiredCapabilities,
        Instant createdAt) {
        /**
         * 校验修订版本不可变元数据。
         */
        public AgentRevision {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(agentId, "agentId must not be null");
            Objects.requireNonNull(snapshotId, "snapshotId must not be null");
            Objects.requireNonNull(contentHash, "contentHash must not be null");
            requiredCapabilities = List.copyOf(Objects.requireNonNull(
                requiredCapabilities, "requiredCapabilities must not be null"));
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            if (revisionNumber < 1 || schemaVersion < 1 || runtimeProvider == null
                || runtimeProvider.isBlank()) {
                throw new IllegalArgumentException("revision metadata is invalid");
            }
        }
    }

    /**
     * @param revision      修订版本元数据
     * @param canonicalJson 完整规范 Snapshot JSON
     * @author refinex
     */
    public record StoredSnapshot(AgentRevision revision, String canonicalJson) {
        /**
         * 校验 Snapshot JSON 非空。
         */
        public StoredSnapshot {
            Objects.requireNonNull(revision, "revision must not be null");
            if (canonicalJson == null || canonicalJson.isBlank()) {
                throw new IllegalArgumentException("canonicalJson must not be blank");
            }
        }
    }

    /**
     * @param previousRevisionId 首次发布时为空，否则为比较基准 Revision
     * @param changedSections    发生变化的 Snapshot 顶层区段稳定名称
     * @author refinex
     */
    public record RevisionDiffSummary(
        Optional<RevisionId> previousRevisionId, List<String> changedSections) {
        /**
         * 校验差异摘要只包含非秘密稳定区段名称。
         */
        public RevisionDiffSummary {
            previousRevisionId = Objects.requireNonNull(
                previousRevisionId, "previousRevisionId must not be null");
            changedSections = List.copyOf(Objects.requireNonNull(
                changedSections, "changedSections must not be null"));
            if (changedSections.stream().anyMatch(
                section -> section == null || !section.matches("[a-z][a-zA-Z0-9]*"))) {
                throw new IllegalArgumentException("changed sections must be stable names");
            }
        }
    }

    /**
     * 定义发布操作终态。
     *
     * @author refinex
     */
    public enum PublishStatus {
        /**
         * 发布事务完整成功。
         */
        SUCCEEDED,
        /**
         * 发布校验失败且未产生 Revision。
         */
        FAILED
    }

    /**
     * @param id             发布操作标识
     * @param projectId      项目标识
     * @param agentId        Agent 标识
     * @param idempotencyKey 调用方幂等键
     * @param draftVersion   发布使用的 Draft 版本
     * @param status         终态：SUCCEEDED、FAILED
     * @param revisionId     成功时关联 Revision
     * @param createdAt      创建时刻
     * @author refinex
     */
    public record PublishOperation(
        JobId id,
        ProjectId projectId,
        AgentId agentId,
        String idempotencyKey,
        long draftVersion,
        PublishStatus status,
        Optional<RevisionId> revisionId,
        Instant createdAt) {
        /**
         * 校验幂等键和终态关联一致。
         */
        public PublishOperation {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(agentId, "agentId must not be null");
            if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128
                || draftVersion < 0) {
                throw new IllegalArgumentException("publish operation input is invalid");
            }
            Objects.requireNonNull(status, "status must not be null");
            revisionId = Objects.requireNonNull(revisionId, "revisionId must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            if ((status == PublishStatus.SUCCEEDED) != revisionId.isPresent()) {
                throw new IllegalArgumentException("publish status and revision must match");
            }
        }
    }

    /**
     * 定义 Deployment 期望状态。
     *
     * @author refinex
     */
    public enum DeploymentStatus {
        /**
         * Runtime 可以创建新 Session。
         */
        ENABLED,
        /**
         * Runtime 必须拒绝创建新 Session。
         */
        DISABLED
    }

    /**
     * 定义流量策略类型；Phase 10 仅执行 FULL。
     *
     * @author refinex
     */
    public enum TrafficPolicyType {
        /**
         * 全量切换至 desiredRevisionId。
         */
        FULL,
        /**
         * 预留按百分比分流语义，本阶段拒绝执行。
         */
        CANARY
    }

    /**
     * @param type          策略类型：FULL、CANARY
     * @param canaryPercent CANARY 百分比；FULL 必须为零
     * @author refinex
     */
    public record TrafficPolicy(TrafficPolicyType type, int canaryPercent) {
        /**
         * 校验策略参数；执行层仍会拒绝 CANARY。
         */
        public TrafficPolicy {
            Objects.requireNonNull(type, "type must not be null");
            if ((type == TrafficPolicyType.FULL && canaryPercent != 0)
                || (type == TrafficPolicyType.CANARY
                && (canaryPercent < 1 || canaryPercent > 99))) {
                throw new IllegalArgumentException("traffic policy percentage is invalid");
            }
        }

        /**
         * @return Phase 10 全量切换策略
         */
        public static TrafficPolicy full() {
            return new TrafficPolicy(TrafficPolicyType.FULL, 0);
        }
    }

    /**
     * @param id                Deployment 稳定身份
     * @param organizationId    所属组织
     * @param projectId         所属项目
     * @param environmentId     所属环境
     * @param agentId           Agent 稳定身份
     * @param desiredRevisionId Runtime 应使用的 Revision 指针
     * @param status            期望状态：ENABLED、DISABLED
     * @param trafficPolicy     流量策略
     * @param version           乐观锁版本
     * @param createdAt         创建时刻
     * @param updatedAt         更新时间
     * @author refinex
     */
    public record Deployment(
        DeploymentId id,
        OrganizationId organizationId,
        ProjectId projectId,
        EnvironmentId environmentId,
        AgentId agentId,
        RevisionId desiredRevisionId,
        DeploymentStatus status,
        TrafficPolicy trafficPolicy,
        long version,
        Instant createdAt,
        Instant updatedAt) {
        /**
         * 校验部署租户链路和乐观锁元数据。
         */
        public Deployment {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(environmentId, "environmentId must not be null");
            Objects.requireNonNull(agentId, "agentId must not be null");
            Objects.requireNonNull(desiredRevisionId, "desiredRevisionId must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(trafficPolicy, "trafficPolicy must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(updatedAt, "updatedAt must not be null");
            if (version < 0 || updatedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("deployment version or timestamps are invalid");
            }
        }
    }

    /**
     * 定义 Deployment 指针变更行为。
     *
     * @author refinex
     */
    public enum DeploymentAction {
        /**
         * 首次创建部署。
         */
        CREATE,
        /**
         * 前进到新 Revision。
         */
        PROMOTE,
        /**
         * 回退到历史 Revision。
         */
        ROLLBACK,
        /**
         * 启用新 Session。
         */
        ENABLE,
        /**
         * 禁用新 Session。
         */
        DISABLE
    }

    /**
     * @param id             历史事件标识
     * @param deploymentId   Deployment 标识
     * @param action         动作：CREATE、PROMOTE、ROLLBACK、ENABLE、DISABLE
     * @param fromRevisionId 变更前 Revision
     * @param toRevisionId   变更后 Revision
     * @param createdAt      发生时刻
     * @author refinex
     */
    public record DeploymentRevision(
        EventId id,
        DeploymentId deploymentId,
        DeploymentAction action,
        Optional<RevisionId> fromRevisionId,
        RevisionId toRevisionId,
        Instant createdAt) {
        /**
         * 校验部署历史事件完整。
         */
        public DeploymentRevision {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(deploymentId, "deploymentId must not be null");
            Objects.requireNonNull(action, "action must not be null");
            fromRevisionId = Objects.requireNonNull(fromRevisionId, "fromRevisionId must not be null");
            Objects.requireNonNull(toRevisionId, "toRevisionId must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
        }
    }

    /**
     * @param id            Outbox 事件标识
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合标识
     * @param eventType     稳定事件类型
     * @param payloadJson   不含秘密的语言中立 JSON
     * @param createdAt     创建时刻
     * @author refinex
     */
    public record OutboxEvent(
        EventId id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payloadJson,
        Instant createdAt) {
        /**
         * 校验 Outbox 事件最小信封。
         */
        public OutboxEvent {
            Objects.requireNonNull(id, "id must not be null");
            if (aggregateType == null || aggregateType.isBlank()
                || aggregateId == null || aggregateId.isBlank()
                || eventType == null || eventType.isBlank()
                || payloadJson == null || payloadJson.isBlank()) {
                throw new IllegalArgumentException("outbox event fields must not be blank");
            }
            Objects.requireNonNull(createdAt, "createdAt must not be null");
        }
    }
}
