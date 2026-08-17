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

package space.refinex.agentark.scheduling.port;

import space.refinex.agentark.knowledge.port.IngestionPlanSource;
import space.refinex.agentark.knowledge.port.IngestionResultSink;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Instant;
import java.util.Map;

/**
 * 聚合 Scheduler 访问 Control 的版本化 Knowledge 计划与结果命令，不暴露 Control Entity。
 *
 * @author refinex
 */
public interface ControlInternalClient extends IngestionPlanSource, IngestionResultSink {

    /**
     * 幂等提交 Scheduler 高风险管理操作的安全 Audit 投影。
     *
     * @param record 不含 Job Payload、Webhook 正文或凭据的审计投影
     */
    void appendAudit(AuditRecord record);

    /**
     * Scheduler 到 Control 的最小 Audit Wire 投影。
     *
     * @param sourceEventId 来源幂等标识
     * @param organizationId 组织
     * @param projectId 项目
     * @param principalRef 操作主体
     * @param action 稳定动作
     * @param resourceRef Job 引用
     * @param diffSummary 安全差异摘要
     * @param occurredAt 发生时间
     * @author refinex
     */
    record AuditRecord(
        String sourceEventId,
        OrganizationId organizationId,
        ProjectId projectId,
        String principalRef,
        String action,
        String resourceRef,
        Map<String, Object> diffSummary,
        Instant occurredAt) {

        /** 校验 Audit Wire 投影并防御性复制摘要。 */
        public AuditRecord {
            java.util.Objects.requireNonNull(organizationId, "organizationId must not be null");
            java.util.Objects.requireNonNull(projectId, "projectId must not be null");
            diffSummary = Map.copyOf(java.util.Objects.requireNonNull(
                diffSummary, "diffSummary must not be null"));
            java.util.Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            if (sourceEventId == null || sourceEventId.isBlank()
                || principalRef == null || principalRef.isBlank()
                || action == null || !action.matches("[a-z][a-z0-9_.-]{0,126}")
                || resourceRef == null || resourceRef.isBlank()) {
                throw new IllegalArgumentException("scheduler audit projection is invalid");
            }
        }
    }
}
