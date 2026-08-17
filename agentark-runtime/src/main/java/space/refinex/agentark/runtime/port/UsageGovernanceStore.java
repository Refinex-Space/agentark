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

import space.refinex.agentark.kernel.id.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 定义 Runtime 原始 Usage 的有界 Claim 与治理汇聚确认端口。
 *
 * @author refinex
 */
public interface UsageGovernanceStore {

    /**
     * 短事务 Claim 一批待汇聚 Usage，并在返回前推进重试时间和尝试次数。
     *
     * @param now   当前时间
     * @param limit 最大数量
     * @return 不含 Prompt、输出或 Tool 参数的 Usage 投影
     */
    List<UsageExportRecord> claimUsageForGovernance(Instant now, int limit);

    /**
     * 标记 Control 已幂等接收 Usage。
     *
     * @param id  Usage UUIDv7
     * @param now 确认时间
     */
    void markUsageExported(EventId id, Instant now);

    /**
     * 达到重试预算后标记 Usage 汇聚失败；原始明细仍保留。
     *
     * @param id Usage UUIDv7
     */
    void markUsageExportFailed(EventId id);

    /**
     * Runtime Usage 治理投影。
     *
     * @param id                用量记录标识
     * @param organizationId    组织
     * @param projectId         项目
     * @param sessionId         会话标识
     * @param turnId            轮次标识
     * @param runId             运行标识
     * @param revisionId        修订标识
     * @param deploymentId      部署标识
     * @param usageType         计量类型
     * @param provider          供应方标识
     * @param model             可选模型
     * @param tool              可选 Tool
     * @param inputTokens       输入 Token
     * @param outputTokens      输出 Token
     * @param cachedTokens      缓存 Token
     * @param embeddingTokens   嵌入令牌数
     * @param toolCalls         Tool 次数
     * @param sandboxDurationMs Sandbox 毫秒
     * @param estimated         是否估算
     * @param priceVersion      可选价格版本
     * @param currency          可选币种
     * @param costAmount        成本
     * @param occurredAt        发生时间
     * @param attempts          当前汇聚尝试次数
     * @author refinex
     */
    record UsageExportRecord(
        EventId id,
        OrganizationId organizationId,
        ProjectId projectId,
        SessionId sessionId,
        TurnId turnId,
        RunId runId,
        RevisionId revisionId,
        DeploymentId deploymentId,
        String usageType,
        String provider,
        Optional<String> model,
        Optional<String> tool,
        long inputTokens,
        long outputTokens,
        long cachedTokens,
        long embeddingTokens,
        long toolCalls,
        long sandboxDurationMs,
        boolean estimated,
        Optional<String> priceVersion,
        Optional<String> currency,
        BigDecimal costAmount,
        Instant occurredAt,
        int attempts) {

        /** 校验投影不变量和 Optional 容器。 */
        public UsageExportRecord {
            java.util.Objects.requireNonNull(id, "id must not be null");
            java.util.Objects.requireNonNull(organizationId, "organizationId must not be null");
            java.util.Objects.requireNonNull(projectId, "projectId must not be null");
            java.util.Objects.requireNonNull(sessionId, "sessionId must not be null");
            java.util.Objects.requireNonNull(turnId, "turnId must not be null");
            java.util.Objects.requireNonNull(runId, "runId must not be null");
            java.util.Objects.requireNonNull(revisionId, "revisionId must not be null");
            java.util.Objects.requireNonNull(deploymentId, "deploymentId must not be null");
            model = java.util.Objects.requireNonNull(model, "model must not be null");
            tool = java.util.Objects.requireNonNull(tool, "tool must not be null");
            priceVersion = java.util.Objects.requireNonNull(
                priceVersion, "priceVersion must not be null");
            currency = java.util.Objects.requireNonNull(currency, "currency must not be null");
            java.util.Objects.requireNonNull(costAmount, "costAmount must not be null");
            java.util.Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            if (usageType == null || usageType.isBlank() || provider == null || provider.isBlank()
                || inputTokens < 0 || outputTokens < 0 || cachedTokens < 0
                || embeddingTokens < 0 || toolCalls < 0 || sandboxDurationMs < 0
                || costAmount.signum() < 0 || attempts < 1) {
                throw new IllegalArgumentException("usage export dimensions are invalid");
            }
        }
    }
}
