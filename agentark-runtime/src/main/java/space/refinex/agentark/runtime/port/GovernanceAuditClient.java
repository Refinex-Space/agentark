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

package space.refinex.agentark.runtime.port;

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 定义 Runtime 向 Control append-only Audit Ledger 提交安全投影的中立端口。
 *
 * @author refinex
 */
public interface GovernanceAuditClient {

    /**
     * 返回显式禁用跨平面审计汇聚时的空实现；Runtime 本地 Event/Outbox 仍保留权威事实。
     *
     * @return 不执行网络请求的端口
     */
    static GovernanceAuditClient noop() {
        return record -> {
            // 显式未组合 Control Governance Client 时只保留 Runtime 权威事实。
        };
    }

    /**
     * 幂等追加 Runtime 高风险操作审计投影；实现不得把远端故障反向覆盖已提交本地终态。
     *
     * @param record 不含正文、参数或凭据的审计投影
     */
    void append(AuditRecord record);

    /**
     * Runtime 高风险操作的最小审计投影。
     *
     * @param sourceEventId 来源幂等标识
     * @param organizationId 组织
     * @param projectId 项目
     * @param principalRef 主体稳定引用
     * @param action 稳定动作名
     * @param result SUCCEEDED、DENIED 或 FAILED
     * @param resourceType 资源类型
     * @param resourceRef 资源引用
     * @param diffSummary 安全差异摘要
     * @param traceId 可选 W3C Trace ID
     * @param occurredAt 发生时间
     * @author refinex
     */
    record AuditRecord(
        String sourceEventId,
        OrganizationId organizationId,
        ProjectId projectId,
        String principalRef,
        String action,
        String result,
        String resourceType,
        String resourceRef,
        Map<String, Object> diffSummary,
        Optional<String> traceId,
        Instant occurredAt) {

        /** 校验审计字段完整并防御性复制差异摘要。 */
        public AuditRecord {
            java.util.Objects.requireNonNull(organizationId, "organizationId must not be null");
            java.util.Objects.requireNonNull(projectId, "projectId must not be null");
            diffSummary = Map.copyOf(java.util.Objects.requireNonNull(
                diffSummary, "diffSummary must not be null"));
            traceId = java.util.Objects.requireNonNull(traceId, "traceId must not be null");
            java.util.Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            if (sourceEventId == null || sourceEventId.isBlank()
                || principalRef == null || principalRef.isBlank()
                || action == null || !action.matches("[a-z][a-z0-9_.-]{0,126}")
                || !java.util.Set.of("SUCCEEDED", "DENIED", "FAILED").contains(result)
                || resourceType == null || resourceType.isBlank()
                || resourceRef == null || resourceRef.isBlank()) {
                throw new IllegalArgumentException("runtime audit projection is invalid");
            }
        }
    }
}
