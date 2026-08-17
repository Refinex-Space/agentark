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

package space.refinex.agentark.control.governance.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import space.refinex.agentark.control.governance.domain.GovernanceModels.*;
import space.refinex.agentark.kernel.ref.Checksum;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 定义 Governance Public/Internal API 的显式请求契约。
 *
 * @author refinex
 */
public final class GovernanceApiModels {

    /** 禁止实例化 API 模型容器。 */
    private GovernanceApiModels() {
    }

    /**
     * 创建 Price Table 与 V1 请求。
     *
     * @param key           项目内 Key
     * @param name          名称
     * @param currency      币种
     * @param effectiveFrom 起效时间
     * @param entries       单位价格项
     * @author refinex
     */
    public record CreatePriceTableRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,62}") String key,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull Instant effectiveFrom,
        @NotEmpty Map<@NotBlank String, @DecimalMin("0") BigDecimal> entries) {
    }

    /**
     * 创建 Quota Policy 请求。
     *
     * @param scopeType      Scope 类型
     * @param scopeRef       Scope 引用
     * @param metric         指标
     * @param enforcement    SOFT 或 HARD
     * @param limitValue     上限
     * @param windowSeconds  可选窗口秒数
     * @param budgetAction   预算动作
     * @param effectiveFrom  起效时间
     * @param effectiveUntil 可选失效时间
     * @author refinex
     */
    public record CreateQuotaPolicyRequest(
        @NotNull QuotaScopeType scopeType,
        @NotBlank @Size(max = 255) String scopeRef,
        @NotNull QuotaMetric metric,
        @NotNull QuotaEnforcement enforcement,
        @NotNull @DecimalMin("0") BigDecimal limitValue,
        @Positive Long windowSeconds,
        @NotNull BudgetAction budgetAction,
        @NotNull Instant effectiveFrom,
        Instant effectiveUntil) {
    }

    /**
     * Dataset Test Case 请求。
     *
     * @param key                 用例键
     * @param inputObjectUri      输入 ObjectRef URI
     * @param inputContentHash    输入 Hash
     * @param expected            期望投影
     * @param expectedContentHash 期望 Hash
     * @param weight              权重
     * @author refinex
     */
    public record EvaluationCaseRequest(
        @NotBlank @Size(max = 128) String key,
        @NotBlank @Size(max = 1024) String inputObjectUri,
        @NotNull Checksum inputContentHash,
        @NotNull Map<String, Object> expected,
        @NotNull Checksum expectedContentHash,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal weight) {
    }

    /**
     * 创建 Dataset 与不可变 V1 请求。
     *
     * @param key         数据集键
     * @param name        名称
     * @param description 可选说明
     * @param schema      用例结构定义
     * @param contentHash 内容 Hash
     * @param cases       测试用例列表
     * @author refinex
     */
    public record CreateDatasetRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,62}") String key,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @NotNull Map<String, Object> schema,
        @NotNull Checksum contentHash,
        @NotEmpty List<@Valid EvaluationCaseRequest> cases) {
    }

    /**
     * 创建 Evaluator 与不可变 V1 请求。
     *
     * @param key         评估器键
     * @param name        名称
     * @param type        类型
     * @param config      安全配置
     * @param contentHash 内容 Hash
     * @author refinex
     */
    public record CreateEvaluatorRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,62}") String key,
        @NotBlank @Size(max = 128) String name,
        @NotNull EvaluatorType type,
        @NotNull Map<String, Object> config,
        @NotNull Checksum contentHash) {
    }

    /**
     * 执行确定性 Evaluation 请求。
     *
     * @param candidateRevisionId 候选修订标识
     * @param datasetVersionId    数据集版本标识
     * @param evaluatorVersionId  评估器版本标识
     * @param threshold           通过阈值
     * @param baselineRunId       可选 Baseline Run UUIDv7
     * @param observedHashes      Case Key 到输出 Hash
     * @author refinex
     */
    public record RunEvaluationRequest(
        @NotBlank String candidateRevisionId,
        @NotBlank String datasetVersionId,
        @NotBlank String evaluatorVersionId,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal threshold,
        String baselineRunId,
        @NotEmpty Map<@NotBlank String, @NotNull Checksum> observedHashes) {
    }

    /**
     * 创建或更新 Release Gate 请求。
     *
     * @param id                 可选 Gate UUIDv7
     * @param agentId            代理标识
     * @param environmentId      可选 Environment UUIDv7
     * @param datasetVersionId   数据集版本标识
     * @param evaluatorVersionId 评估器版本标识
     * @param threshold          通过阈值
     * @param enforcement        SOFT 或 HARD
     * @param status             ACTIVE 或 DISABLED
     * @param expectedVersion    更新时预期版本
     * @author refinex
     */
    public record SaveReleaseGateRequest(
        String id,
        @NotBlank String agentId,
        String environmentId,
        @NotBlank String datasetVersionId,
        @NotBlank String evaluatorVersionId,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal threshold,
        @NotNull QuotaEnforcement enforcement,
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
        @PositiveOrZero Long expectedVersion) {
    }

    /**
     * Internal Audit Event 请求。
     *
     * @param sourceEventId  来源事件
     * @param sourcePlane    来源平面
     * @param organizationId 可选组织
     * @param projectId      可选项目
     * @param principalType  主体类型
     * @param principalRef   主体引用
     * @param scopeType      Scope 类型
     * @param scopeRef       Scope 引用
     * @param action         动作
     * @param result         结果
     * @param resourceType   资源类型
     * @param resourceRef    资源引用
     * @param diffSummary    安全差异摘要
     * @param policyVersion  可选策略版本
     * @param roleVersion    可选角色版本
     * @param traceId        可选 Trace ID
     * @param requestId      可选 Request ID
     * @param occurredAt     发生时间
     * @author refinex
     */
    public record AuditIngestRequest(
        @NotBlank @Size(max = 128) String sourceEventId,
        @NotNull AuditPlane sourcePlane,
        String organizationId,
        String projectId,
        @NotNull AuditPrincipalType principalType,
        @NotBlank @Size(max = 255) String principalRef,
        @NotNull AuditScopeType scopeType,
        @NotBlank @Size(max = 128) String scopeRef,
        @NotBlank @Size(max = 128) String action,
        @NotNull AuditResult result,
        @NotBlank @Size(max = 64) String resourceType,
        @NotBlank @Size(max = 128) String resourceRef,
        @NotNull Map<String, Object> diffSummary,
        @Size(max = 128) String policyVersion,
        @PositiveOrZero Long roleVersion,
        @Pattern(regexp = "[0-9a-f]{32}") String traceId,
        @Size(max = 128) String requestId,
        @NotNull Instant occurredAt) {
    }

    /**
     * Internal Usage 汇聚请求。
     *
     * @param sourcePlane         来源平面
     * @param sourceRecordId      来源记录
     * @param organizationId      组织
     * @param projectId           项目
     * @param agentId             可选 Agent
     * @param revisionId          可选 Revision
     * @param deploymentId        可选 Deployment
     * @param sessionId           可选 Session
     * @param turnId              可选 Turn
     * @param runId               可选 Run
     * @param usageType           类型
     * @param provider            供应方标识
     * @param model               可选模型
     * @param tool                可选 Tool
     * @param inputTokens         输入 Token
     * @param outputTokens        输出 Token
     * @param cachedTokens        缓存 Token
     * @param embeddingTokens     嵌入令牌数
     * @param toolCalls           Tool 次数
     * @param sandboxDurationMs   Sandbox 毫秒
     * @param estimated           是否估算
     * @param priceTableVersionId 可选价格版本
     * @param currency            可选币种
     * @param costAmount          成本
     * @param occurredAt          发生时间
     * @author refinex
     */
    public record UsageIngestRequest(
        @NotBlank @Pattern(regexp = "RUNTIME|SCHEDULER|CONTROL") String sourcePlane,
        @NotBlank @Size(max = 128) String sourceRecordId,
        @NotBlank String organizationId,
        @NotBlank String projectId,
        String agentId,
        String revisionId,
        String deploymentId,
        String sessionId,
        String turnId,
        String runId,
        @NotNull UsageType usageType,
        @NotBlank @Size(max = 128) String provider,
        @Size(max = 255) String model,
        @Size(max = 255) String tool,
        @PositiveOrZero long inputTokens,
        @PositiveOrZero long outputTokens,
        @PositiveOrZero long cachedTokens,
        @PositiveOrZero long embeddingTokens,
        @PositiveOrZero long toolCalls,
        @PositiveOrZero long sandboxDurationMs,
        boolean estimated,
        String priceTableVersionId,
        @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull @DecimalMin("0") BigDecimal costAmount,
        @NotNull Instant occurredAt) {
    }

    /**
     * Internal Quota Reservation 请求。
     *
     * @param organizationId 组织
     * @param projectId      项目
     * @param scopeType      Scope 类型
     * @param scopeRef       Scope 引用
     * @param metric         指标
     * @param idempotencyKey 幂等键
     * @param subjectRef     工作引用
     * @param amount         预留量
     * @param ttlSeconds     TTL 秒数
     * @author refinex
     */
    public record QuotaReserveRequest(
        @NotBlank String organizationId,
        @NotBlank String projectId,
        @NotNull QuotaScopeType scopeType,
        @NotBlank @Size(max = 255) String scopeRef,
        @NotNull QuotaMetric metric,
        @NotBlank @Size(max = 128) String idempotencyKey,
        @NotBlank @Size(max = 128) String subjectRef,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @Min(5) @Max(3600) long ttlSeconds) {
    }

    /**
     * Internal Reservation 终态请求。
     *
     * @param target COMMITTED 或 RELEASED
     * @author refinex
     */
    public record ReservationTransitionRequest(
        @NotNull ReservationStatus target) {
    }

    /**
     * Internal 幂等接收结果。
     *
     * @param accepted 是否首次接收
     * @author refinex
     */
    public record IngestResponse(boolean accepted) {
    }

    /** 将可空字符串转换为 Optional。 */
    static Optional<String> optional(String value) {
        return Optional.ofNullable(value);
    }
}
