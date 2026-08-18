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

package space.refinex.agentark.control.governance.domain;

import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 集中定义审计、用量成本、配额和版本化评估的语言中立治理模型。
 *
 * @author refinex
 */
public final class GovernanceModels {

    /**
     * 禁止实例化治理模型容器。
     */
    private GovernanceModels() {
    }

    /**
     * 审计事件来源平面。
     *
     * @author refinex
     */
    public enum AuditPlane {
        /**
         * 公共 Gateway。
         */
        GATEWAY,

        /**
         * 控制平面。
         */
        CONTROL,

        /**
         * 运行平面。
         */
        RUNTIME,

        /**
         * 调度平面。
         */
        SCHEDULER
    }

    /**
     * 审计操作主体类型。
     *
     * @author refinex
     */
    public enum AuditPrincipalType {
        /**
         * 外部用户。
         */
        USER,

        /**
         * 服务账号。
         */
        SERVICE_ACCOUNT,

        /**
         * 接口密钥主体。
         */
        API_KEY,

        /**
         * 内部服务身份。
         */
        SERVICE,

        /**
         * 平台后台任务。
         */
        SYSTEM
    }

    /**
     * 审计操作结果。
     *
     * @author refinex
     */
    public enum AuditResult {
        /**
         * 操作成功。
         */
        SUCCEEDED,

        /**
         * 授权拒绝。
         */
        DENIED,

        /**
         * 业务或基础设施失败。
         */
        FAILED
    }

    /**
     * 审计作用域类型。
     *
     * @author refinex
     */
    public enum AuditScopeType {
        /**
         * 平台。
         */
        PLATFORM,

        /**
         * 组织。
         */
        ORGANIZATION,

        /**
         * 项目。
         */
        PROJECT,

        /**
         * 环境。
         */
        ENVIRONMENT,

        /**
         * 运行实例。
         */
        RUN,

        /**
         * 调度任务。
         */
        JOB
    }

    /**
     * 用量计量类型。
     *
     * @author refinex
     */
    public enum UsageType {
        /**
         * 模型调用。
         */
        MODEL,

        /**
         * Embedding 调用。
         */
        EMBEDDING,

        /**
         * Tool 或 MCP 调用。
         */
        TOOL,

        /**
         * Sandbox 执行。
         */
        SANDBOX
    }

    /**
     * 配额作用域类型。
     *
     * @author refinex
     */
    public enum QuotaScopeType {
        /**
         * 组织。
         */
        ORGANIZATION,

        /**
         * 项目。
         */
        PROJECT,

        /**
         * 部署。
         */
        DEPLOYMENT,

        /**
         * 模型。
         */
        MODEL
    }

    /**
     * 配额计量指标。
     *
     * @author refinex
     */
    public enum QuotaMetric {
        /**
         * 请求速率。
         */
        REQUEST_RATE,

        /**
         * 输入 Token。
         */
        INPUT_TOKEN,

        /**
         * 输出 Token。
         */
        OUTPUT_TOKEN,

        /**
         * 成本金额。
         */
        COST,

        /**
         * 并发 Run。
         */
        CONCURRENT_RUN
    }

    /**
     * 配额执行方式。
     *
     * @author refinex
     */
    public enum QuotaEnforcement {
        /**
         * 仅告警。
         */
        SOFT,

        /**
         * 拒绝超限预留。
         */
        HARD
    }

    /**
     * 运行中预算动作。
     *
     * @author refinex
     */
    public enum BudgetAction {
        /**
         * 记录告警并继续。
         */
        WARN,

        /**
         * 请求人工审批。
         */
        REQUIRE_APPROVAL,

        /**
         * 明确停止运行。
         */
        STOP
    }

    /**
     * 配额预留状态。
     *
     * @author refinex
     */
    public enum ReservationStatus {
        /**
         * 正在占用额度。
         */
        HELD,

        /**
         * 已计入最终使用。
         */
        COMMITTED,

        /**
         * 主动释放。
         */
        RELEASED,

        /**
         * 超时回收。
         */
        EXPIRED
    }

    /**
     * 评估执行器类型。
     *
     * @author refinex
     */
    public enum EvaluatorType {
        /**
         * 固定规则确定性评估。
         */
        DETERMINISTIC,

        /**
         * 模型评分。
         */
        MODEL,

        /**
         * 人工评分。
         */
        HUMAN
    }

    /**
     * 评估运行状态。
     *
     * @author refinex
     */
    public enum EvaluationStatus {
        /**
         * 等待执行。
         */
        QUEUED,

        /**
         * 正在执行。
         */
        RUNNING,

        /**
         * 通过阈值。
         */
        PASSED,

        /**
         * 未通过阈值。
         */
        FAILED,

        /**
         * 评估器错误。
         */
        ERROR,

        /**
         * 已取消。
         */
        CANCELLED
    }

    /**
     * append-only 审计事实。
     *
     * @param id             审计 UUIDv7
     * @param sourceEventId  来源幂等事件标识
     * @param sourcePlane    来源平面
     * @param organizationId 可选组织
     * @param projectId      可选项目
     * @param principalType  主体类型
     * @param principalRef   主体稳定引用
     * @param scopeType      作用域类型
     * @param scopeRef       作用域引用
     * @param action         动作代码
     * @param result         操作结果
     * @param resourceType   资源类型
     * @param resourceRef    资源引用
     * @param diffSummary    不含正文的差异摘要
     * @param policyVersion  可选策略版本
     * @param roleVersion    可选角色版本
     * @param traceId        可选 W3C Trace ID
     * @param requestId      可选请求 ID
     * @param occurredAt     发生时间
     * @param ingestedAt     Control 接收时间
     * @author refinex
     */
    public record AuditEvent(
        EventId id,
        String sourceEventId,
        AuditPlane sourcePlane,
        Optional<OrganizationId> organizationId,
        Optional<ProjectId> projectId,
        AuditPrincipalType principalType,
        String principalRef,
        AuditScopeType scopeType,
        String scopeRef,
        String action,
        AuditResult result,
        String resourceType,
        String resourceRef,
        Map<String, Object> diffSummary,
        Optional<String> policyVersion,
        Optional<Long> roleVersion,
        Optional<String> traceId,
        Optional<String> requestId,
        Instant occurredAt,
        Instant ingestedAt) {

        /**
         * 校验审计 Scope、关联字段和敏感数据最小化边界。
         */
        public AuditEvent {
            Objects.requireNonNull(id, "id must not be null");
            sourceEventId = text(sourceEventId, "sourceEventId", 128);
            Objects.requireNonNull(sourcePlane, "sourcePlane must not be null");
            organizationId = optional(organizationId, "organizationId");
            projectId = optional(projectId, "projectId");
            if (projectId.isPresent() && organizationId.isEmpty()) {
                throw new IllegalArgumentException("project audit requires organization");
            }
            Objects.requireNonNull(principalType, "principalType must not be null");
            principalRef = text(principalRef, "principalRef", 255);
            Objects.requireNonNull(scopeType, "scopeType must not be null");
            scopeRef = text(scopeRef, "scopeRef", 128);
            action = code(action, "action", 128);
            Objects.requireNonNull(result, "result must not be null");
            resourceType = code(resourceType, "resourceType", 64);
            resourceRef = text(resourceRef, "resourceRef", 128);
            diffSummary = Map.copyOf(Objects.requireNonNull(diffSummary, "diffSummary must not be null"));
            policyVersion = optionalText(policyVersion, "policyVersion", 128);
            roleVersion = optional(roleVersion, "roleVersion");
            roleVersion.ifPresent(value -> {
                if (value < 0) {
                    throw new IllegalArgumentException("roleVersion must not be negative");
                }
            });
            traceId = optionalText(traceId, "traceId", 32);
            traceId.ifPresent(value -> {
                if (!value.matches("[0-9a-f]{32}") || value.matches("0{32}")) {
                    throw new IllegalArgumentException("traceId must be a non-zero W3C trace id");
                }
            });
            requestId = optionalText(requestId, "requestId", 128);
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            Objects.requireNonNull(ingestedAt, "ingestedAt must not be null");
        }
    }

    /**
     * 稳定价格表身份。
     *
     * @param id             价格表 UUIDv7
     * @param organizationId 组织
     * @param projectId      项目
     * @param key            项目内 Key
     * @param name           显示名称
     * @param status         ACTIVE 或 ARCHIVED
     * @param version        乐观锁版本
     * @param createdAt      创建时间
     * @param updatedAt      更新时间
     * @author refinex
     */
    public record PriceTable(
        UUID id,
        OrganizationId organizationId,
        ProjectId projectId,
        String key,
        String name,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

        /**
         * 校验价格表稳定身份。
         */
        public PriceTable {
            uuidV7(id, "id");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            key = GovernanceModels.key(key, "key");
            name = text(name, "name", 128);
            oneOf(status, "status", "ACTIVE", "ARCHIVED");
            nonNegative(version, "version");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        }
    }

    /**
     * 不可变价格版本。
     *
     * @param id             版本 UUIDv7
     * @param organizationId 组织
     * @param projectId      项目
     * @param priceTableId   稳定价格表 UUIDv7
     * @param versionNumber  从 1 开始的版本号
     * @param currency       ISO 4217 币种
     * @param effectiveFrom  起效时间
     * @param entries        价格项
     * @param contentHash    规范内容 Hash
     * @param createdAt      创建时间
     * @author refinex
     */
    public record PriceTableVersion(
        UUID id,
        OrganizationId organizationId,
        ProjectId projectId,
        UUID priceTableId,
        long versionNumber,
        String currency,
        Instant effectiveFrom,
        Map<String, BigDecimal> entries,
        Checksum contentHash,
        Instant createdAt) {

        /**
         * 校验不可变价格版本。
         */
        public PriceTableVersion {
            uuidV7(id, "id");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            uuidV7(priceTableId, "priceTableId");
            positive(versionNumber, "versionNumber");
            currency = GovernanceModels.currency(currency);
            Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
            entries = Map.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
            if (entries.isEmpty() || entries.values().stream().anyMatch(value -> value == null || value.signum() < 0)) {
                throw new IllegalArgumentException("price entries must be non-empty and non-negative");
            }
            Objects.requireNonNull(contentHash, "contentHash must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
        }
    }

    /**
     * 治理用量明细，不包含 Prompt、输出或 Tool 参数。
     *
     * @param id                  明细 UUIDv7
     * @param sourcePlane         来源平面
     * @param sourceRecordId      来源幂等标识
     * @param organizationId      组织
     * @param projectId           项目
     * @param agentId             可选 Agent
     * @param revisionId          可选 Revision
     * @param deploymentId        可选 Deployment
     * @param sessionId           可选 Session
     * @param turnId              可选 Turn
     * @param runId               可选 Run
     * @param usageType           计量类型
     * @param provider            供应方标识
     * @param model               可选模型
     * @param tool                可选 Tool
     * @param inputTokens         输入 Token
     * @param outputTokens        输出 Token
     * @param cachedTokens        缓存 Token
     * @param embeddingTokens     嵌入令牌数
     * @param toolCalls           Tool 调用次数
     * @param sandboxDurationMs   Sandbox 毫秒数
     * @param estimated           是否估算
     * @param priceTableVersionId 可选价格版本
     * @param currency            可选币种
     * @param costAmount          成本金额
     * @param occurredAt          发生时间
     * @param ingestedAt          接收时间
     * @author refinex
     */
    public record UsageLedgerEntry(
        UUID id,
        String sourcePlane,
        String sourceRecordId,
        OrganizationId organizationId,
        ProjectId projectId,
        Optional<AgentId> agentId,
        Optional<RevisionId> revisionId,
        Optional<DeploymentId> deploymentId,
        Optional<SessionId> sessionId,
        Optional<TurnId> turnId,
        Optional<RunId> runId,
        UsageType usageType,
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
        Optional<UUID> priceTableVersionId,
        Optional<String> currency,
        BigDecimal costAmount,
        Instant occurredAt,
        Instant ingestedAt) {

        /**
         * 校验计量维度、金额和价格版本一致。
         */
        public UsageLedgerEntry {
            uuidV7(id, "id");
            oneOf(sourcePlane, "sourcePlane", "RUNTIME", "SCHEDULER", "CONTROL");
            sourceRecordId = text(sourceRecordId, "sourceRecordId", 128);
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            agentId = optional(agentId, "agentId");
            revisionId = optional(revisionId, "revisionId");
            deploymentId = optional(deploymentId, "deploymentId");
            sessionId = optional(sessionId, "sessionId");
            turnId = optional(turnId, "turnId");
            runId = optional(runId, "runId");
            Objects.requireNonNull(usageType, "usageType must not be null");
            provider = text(provider, "provider", 128);
            model = optionalText(model, "model", 255);
            tool = optionalText(tool, "tool", 255);
            nonNegative(inputTokens, "inputTokens");
            nonNegative(outputTokens, "outputTokens");
            nonNegative(cachedTokens, "cachedTokens");
            nonNegative(embeddingTokens, "embeddingTokens");
            nonNegative(toolCalls, "toolCalls");
            nonNegative(sandboxDurationMs, "sandboxDurationMs");
            priceTableVersionId = optional(priceTableVersionId, "priceTableVersionId");
            currency = optionalText(currency, "currency", 3);
            currency.ifPresent(GovernanceModels::currency);
            Objects.requireNonNull(costAmount, "costAmount must not be null");
            if (costAmount.signum() < 0 || costAmount.signum() > 0 && (priceTableVersionId.isEmpty() || currency.isEmpty())) {
                throw new IllegalArgumentException("positive cost requires price version and currency");
            }
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            Objects.requireNonNull(ingestedAt, "ingestedAt must not be null");
        }
    }

    /**
     * Usage 与 Cost 查询聚合。
     *
     * @param periodStart       窗口开始
     * @param periodEnd         窗口结束
     * @param dimensionType     聚合维度类型
     * @param dimensionRef      维度引用
     * @param provider          供应方标识
     * @param model             模型或 none
     * @param inputTokens       输入 Token
     * @param outputTokens      输出 Token
     * @param cachedTokens      缓存 Token
     * @param embeddingTokens   嵌入令牌数
     * @param toolCalls         Tool 调用次数
     * @param sandboxDurationMs Sandbox 毫秒数
     * @param estimatedRecords  估算记录数
     * @param sourceRecords     明细数
     * @param costAmount        成本
     * @param currency          币种
     * @author refinex
     */
    public record UsageAggregate(
        Instant periodStart,
        Instant periodEnd,
        String dimensionType,
        String dimensionRef,
        String provider,
        String model,
        long inputTokens,
        long outputTokens,
        long cachedTokens,
        long embeddingTokens,
        long toolCalls,
        long sandboxDurationMs,
        long estimatedRecords,
        long sourceRecords,
        BigDecimal costAmount,
        String currency) {

        /**
         * 校验聚合窗口和非负计量。
         */
        public UsageAggregate {
            Objects.requireNonNull(periodStart, "periodStart must not be null");
            Objects.requireNonNull(periodEnd, "periodEnd must not be null");
            if (!periodEnd.isAfter(periodStart)) {
                throw new IllegalArgumentException("periodEnd must be after periodStart");
            }
            oneOf(dimensionType, "dimensionType", "PROJECT", "AGENT", "REVISION", "MODEL");
            dimensionRef = text(dimensionRef, "dimensionRef", 255);
            provider = text(provider, "provider", 128);
            model = text(model, "model", 255);
            nonNegative(inputTokens, "inputTokens");
            nonNegative(outputTokens, "outputTokens");
            nonNegative(cachedTokens, "cachedTokens");
            nonNegative(embeddingTokens, "embeddingTokens");
            nonNegative(toolCalls, "toolCalls");
            nonNegative(sandboxDurationMs, "sandboxDurationMs");
            nonNegative(estimatedRecords, "estimatedRecords");
            nonNegative(sourceRecords, "sourceRecords");
            Objects.requireNonNull(costAmount, "costAmount must not be null");
            if (costAmount.signum() < 0) {
                throw new IllegalArgumentException("costAmount must not be negative");
            }
            currency = GovernanceModels.currency(currency);
        }
    }

    /**
     * 版本化 Quota Policy。
     *
     * @param id             配额策略标识
     * @param organizationId 组织
     * @param projectId      项目
     * @param scopeType      作用域类型
     * @param scopeRef       作用域引用
     * @param metric         配额指标
     * @param enforcement    软硬方式
     * @param limitValue     上限
     * @param windowSeconds  可选窗口秒数
     * @param budgetAction   运行中动作
     * @param effectiveFrom  起效时间
     * @param effectiveUntil 可选失效时间
     * @param status         ACTIVE 或 DISABLED
     * @param version        乐观锁版本
     * @author refinex
     */
    public record QuotaPolicy(
        UUID id,
        OrganizationId organizationId,
        ProjectId projectId,
        QuotaScopeType scopeType,
        String scopeRef,
        QuotaMetric metric,
        QuotaEnforcement enforcement,
        BigDecimal limitValue,
        Optional<Long> windowSeconds,
        BudgetAction budgetAction,
        Instant effectiveFrom,
        Optional<Instant> effectiveUntil,
        String status,
        long version) {

        /**
         * 校验 Quota Policy 的窗口、上限和有效期。
         */
        public QuotaPolicy {
            uuidV7(id, "id");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(scopeType, "scopeType must not be null");
            scopeRef = text(scopeRef, "scopeRef", 255);
            Objects.requireNonNull(metric, "metric must not be null");
            Objects.requireNonNull(enforcement, "enforcement must not be null");
            Objects.requireNonNull(limitValue, "limitValue must not be null");
            if (limitValue.signum() < 0) {
                throw new IllegalArgumentException("limitValue must not be negative");
            }
            windowSeconds = optional(windowSeconds, "windowSeconds");
            if (metric == QuotaMetric.CONCURRENT_RUN && windowSeconds.isPresent()
                || metric != QuotaMetric.CONCURRENT_RUN
                && windowSeconds.filter(value -> value > 0).isEmpty()) {
                throw new IllegalArgumentException("quota window does not match metric");
            }
            Objects.requireNonNull(budgetAction, "budgetAction must not be null");
            Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
            effectiveUntil = optional(effectiveUntil, "effectiveUntil");
            effectiveUntil.ifPresent(value -> {
                if (!value.isAfter(effectiveFrom)) {
                    throw new IllegalArgumentException("effectiveUntil must be after effectiveFrom");
                }
            });
            oneOf(status, "status", "ACTIVE", "DISABLED");
            nonNegative(version, "version");
        }
    }

    /**
     * Quota 预留结果。
     *
     * @param reservationId 可选 Reservation UUIDv7；无匹配 Policy 时为空
     * @param allowed       是否允许继续
     * @param softExceeded  是否超过软限额
     * @param action        可选预算动作
     * @param policyVersion 可选 Policy 版本
     * @param remaining     可选剩余额度
     * @param expiresAt     可选预留到期时间
     * @author refinex
     */
    public record QuotaDecision(
        Optional<UUID> reservationId,
        boolean allowed,
        boolean softExceeded,
        Optional<BudgetAction> action,
        Optional<Long> policyVersion,
        Optional<BigDecimal> remaining,
        Optional<Instant> expiresAt) {

        /**
         * 校验预留结果字段组合。
         */
        public QuotaDecision {
            reservationId = optional(reservationId, "reservationId");
            reservationId.ifPresent(value -> uuidV7(value, "reservationId"));
            action = optional(action, "action");
            policyVersion = optional(policyVersion, "policyVersion");
            remaining = optional(remaining, "remaining");
            expiresAt = optional(expiresAt, "expiresAt");
            if (!allowed && reservationId.isPresent()) {
                throw new IllegalArgumentException("denied quota must not create reservation");
            }
        }
    }

    /**
     * Evaluation Dataset 稳定身份。
     *
     * @param id          数据集标识
     * @param key         项目内 Key
     * @param name        名称
     * @param description 可选说明
     * @param status      ACTIVE 或 ARCHIVED
     * @param version     乐观锁版本
     * @author refinex
     */
    public record EvaluationDataset(
        UUID id,
        String key,
        String name,
        Optional<String> description,
        String status,
        long version) {

        /**
         * 校验 Dataset 稳定身份。
         */
        public EvaluationDataset {
            uuidV7(id, "id");
            key = GovernanceModels.key(key, "key");
            name = text(name, "name", 128);
            description = optionalText(description, "description", 512);
            oneOf(status, "status", "ACTIVE", "ARCHIVED");
            nonNegative(version, "version");
        }
    }

    /**
     * 不可变 Dataset Version。
     *
     * @param id            数据集版本标识
     * @param datasetId     数据集标识
     * @param versionNumber 版本号
     * @param schema        用例结构定义
     * @param contentHash   内容 Hash
     * @author refinex
     */
    public record DatasetVersion(
        UUID id,
        UUID datasetId,
        long versionNumber,
        Map<String, Object> schema,
        Checksum contentHash) {

        /**
         * 校验不可变 Dataset Version。
         */
        public DatasetVersion {
            uuidV7(id, "id");
            uuidV7(datasetId, "datasetId");
            positive(versionNumber, "versionNumber");
            schema = Map.copyOf(Objects.requireNonNull(schema, "schema must not be null"));
            Objects.requireNonNull(contentHash, "contentHash must not be null");
        }
    }

    /**
     * 固定 Dataset Version 下的 Test Case。
     *
     * @param id                  测试用例标识
     * @param datasetVersionId    数据集版本标识
     * @param key                 用例键
     * @param inputObjectUri      输入 ObjectRef URI
     * @param inputContentHash    输入 Hash
     * @param expected            安全期望投影
     * @param expectedContentHash 期望 Hash
     * @param weight              权重
     * @author refinex
     */
    public record EvaluationTestCase(
        UUID id,
        UUID datasetVersionId,
        String key,
        String inputObjectUri,
        Checksum inputContentHash,
        Map<String, Object> expected,
        Checksum expectedContentHash,
        BigDecimal weight) {

        /**
         * 校验 Test Case ObjectRef、Hash 和权重。
         */
        public EvaluationTestCase {
            uuidV7(id, "id");
            uuidV7(datasetVersionId, "datasetVersionId");
            key = code(key, "key", 128);
            inputObjectUri = text(inputObjectUri, "inputObjectUri", 1024);
            Objects.requireNonNull(inputContentHash, "inputContentHash must not be null");
            expected = Map.copyOf(Objects.requireNonNull(expected, "expected must not be null"));
            Objects.requireNonNull(expectedContentHash, "expectedContentHash must not be null");
            Objects.requireNonNull(weight, "weight must not be null");
            if (weight.signum() <= 0) {
                throw new IllegalArgumentException("weight must be positive");
            }
        }
    }

    /**
     * Evaluator 稳定身份。
     *
     * @param id      评估器标识
     * @param key     项目内 Key
     * @param name    名称
     * @param status  ACTIVE 或 ARCHIVED
     * @param version 乐观锁版本
     * @author refinex
     */
    public record Evaluator(UUID id, String key, String name, String status, long version) {

        /**
         * 校验 Evaluator 稳定身份。
         */
        public Evaluator {
            uuidV7(id, "id");
            key = GovernanceModels.key(key, "key");
            name = text(name, "name", 128);
            oneOf(status, "status", "ACTIVE", "ARCHIVED");
            nonNegative(version, "version");
        }
    }

    /**
     * 不可变 Evaluator Version。
     *
     * @param id            评估器版本标识
     * @param evaluatorId   评估器标识
     * @param versionNumber 版本号
     * @param type          Evaluator 类型
     * @param config        安全配置
     * @param contentHash   内容 Hash
     * @author refinex
     */
    public record EvaluatorVersion(
        UUID id,
        UUID evaluatorId,
        long versionNumber,
        EvaluatorType type,
        Map<String, Object> config,
        Checksum contentHash) {

        /**
         * 校验不可变 Evaluator Version。
         */
        public EvaluatorVersion {
            uuidV7(id, "id");
            uuidV7(evaluatorId, "evaluatorId");
            positive(versionNumber, "versionNumber");
            Objects.requireNonNull(type, "type must not be null");
            config = Map.copyOf(Objects.requireNonNull(config, "config must not be null"));
            Objects.requireNonNull(contentHash, "contentHash must not be null");
        }
    }

    /**
     * 固定所有版本引用的 Evaluation Run。
     *
     * @param id                  评估运行标识
     * @param candidateRevisionId 候选修订标识
     * @param candidateSnapshotId 候选快照标识
     * @param datasetVersionId    数据集版本标识
     * @param evaluatorVersionId  评估器版本标识
     * @param provider            供应方标识
     * @param model               可选模型
     * @param threshold           通过阈值
     * @param baselineRunId       可选基准 Run
     * @param status              状态
     * @param totalScore          可选总分
     * @param regressionDelta     可选回归差异
     * @param createdAt           创建时间
     * @param completedAt         可选完成时间
     * @author refinex
     */
    public record EvaluationRun(
        UUID id,
        RevisionId candidateRevisionId,
        SnapshotId candidateSnapshotId,
        UUID datasetVersionId,
        UUID evaluatorVersionId,
        String provider,
        Optional<String> model,
        BigDecimal threshold,
        Optional<UUID> baselineRunId,
        EvaluationStatus status,
        Optional<BigDecimal> totalScore,
        Optional<BigDecimal> regressionDelta,
        Instant createdAt,
        Optional<Instant> completedAt) {

        /**
         * 校验 Evaluation Run 固定引用、阈值和终态字段。
         */
        public EvaluationRun {
            uuidV7(id, "id");
            Objects.requireNonNull(candidateRevisionId, "candidateRevisionId must not be null");
            Objects.requireNonNull(candidateSnapshotId, "candidateSnapshotId must not be null");
            uuidV7(datasetVersionId, "datasetVersionId");
            uuidV7(evaluatorVersionId, "evaluatorVersionId");
            provider = text(provider, "provider", 128);
            model = optionalText(model, "model", 255);
            ratio(threshold, "threshold");
            baselineRunId = optional(baselineRunId, "baselineRunId");
            baselineRunId.ifPresent(value -> uuidV7(value, "baselineRunId"));
            Objects.requireNonNull(status, "status must not be null");
            totalScore = optional(totalScore, "totalScore");
            totalScore.ifPresent(value -> ratio(value, "totalScore"));
            regressionDelta = optional(regressionDelta, "regressionDelta");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            completedAt = optional(completedAt, "completedAt");
            if ((status == EvaluationStatus.QUEUED || status == EvaluationStatus.RUNNING) == completedAt.isPresent()) {
                throw new IllegalArgumentException("evaluation terminal time does not match status");
            }
        }
    }

    /**
     * 单个 Test Case 的只追加评分。
     *
     * @param id         评分标识
     * @param runId      评估运行标识
     * @param testCaseId 测试用例标识
     * @param metricKey  指标 Key
     * @param score      0 到 1 分数
     * @param passed     是否通过
     * @param details    安全详情
     * @author refinex
     */
    public record EvaluationScore(
        UUID id,
        UUID runId,
        UUID testCaseId,
        String metricKey,
        BigDecimal score,
        boolean passed,
        Map<String, Object> details) {

        /**
         * 校验评分范围和详情最小化。
         */
        public EvaluationScore {
            uuidV7(id, "id");
            uuidV7(runId, "runId");
            uuidV7(testCaseId, "testCaseId");
            metricKey = code(metricKey, "metricKey", 128);
            ratio(score, "score");
            details = Map.copyOf(Objects.requireNonNull(details, "details must not be null"));
        }
    }

    /**
     * 以固定 Dataset/Evaluator 约束 Promote 的 Release Gate。
     *
     * @param id                 发布门禁标识
     * @param organizationId     组织
     * @param projectId          项目
     * @param agentId            代理标识
     * @param environmentId      可选环境
     * @param datasetVersionId   数据集版本标识
     * @param evaluatorVersionId 评估器版本标识
     * @param threshold          通过阈值
     * @param enforcement        软硬方式
     * @param status             ACTIVE 或 DISABLED
     * @param version            乐观锁版本
     * @author refinex
     */
    public record ReleaseGate(
        UUID id,
        OrganizationId organizationId,
        ProjectId projectId,
        AgentId agentId,
        Optional<EnvironmentId> environmentId,
        UUID datasetVersionId,
        UUID evaluatorVersionId,
        BigDecimal threshold,
        QuotaEnforcement enforcement,
        String status,
        long version) {

        /**
         * 校验 Gate 作用域与固定版本。
         */
        public ReleaseGate {
            uuidV7(id, "id");
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            Objects.requireNonNull(agentId, "agentId must not be null");
            environmentId = optional(environmentId, "environmentId");
            uuidV7(datasetVersionId, "datasetVersionId");
            uuidV7(evaluatorVersionId, "evaluatorVersionId");
            ratio(threshold, "threshold");
            Objects.requireNonNull(enforcement, "enforcement must not be null");
            oneOf(status, "status", "ACTIVE", "DISABLED");
            nonNegative(version, "version");
        }
    }

    /**
     * Release Gate 决策。
     *
     * @param allowed       是否允许 Promote
     * @param softFailed    是否仅软失败
     * @param gateId        可选 Gate UUIDv7
     * @param evaluationRun 可选证明 Run UUIDv7
     * @param reason        稳定原因代码
     * @author refinex
     */
    public record ReleaseGateDecision(
        boolean allowed,
        boolean softFailed,
        Optional<UUID> gateId,
        Optional<UUID> evaluationRun,
        String reason) {

        /**
         * 校验 Gate 决策关联字段。
         */
        public ReleaseGateDecision {
            gateId = optional(gateId, "gateId");
            gateId.ifPresent(value -> uuidV7(value, "gateId"));
            evaluationRun = optional(evaluationRun, "evaluationRun");
            evaluationRun.ifPresent(value -> uuidV7(value, "evaluationRun"));
            reason = code(reason, "reason", 128);
            if (!allowed && softFailed) {
                throw new IllegalArgumentException("soft failure must remain allowed");
            }
        }
    }

    /**
     * 校验字符串为非空短文本。
     */
    private static String text(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " has invalid text");
        }
        return value;
    }

    /**
     * 校验字符串为稳定代码。
     */
    private static String code(String value, String name, int maxLength) {
        text(value, name, maxLength);
        if (!value.matches("[a-z][a-z0-9_.:-]{0," + (maxLength - 1) + "}")) {
            throw new IllegalArgumentException(name + " must be a stable code");
        }
        return value;
    }

    /**
     * 校验项目内资源 Key。
     */
    private static String key(String value, String name) {
        if (value == null || !value.matches("[a-z][a-z0-9-]{0,62}")) {
            throw new IllegalArgumentException(name + " must be a normalized key");
        }
        return value;
    }

    /**
     * 校验枚举字符串来自给定集合。
     */
    private static void oneOf(String value, String name, String... allowed) {
        if (value == null || Stream.of(allowed).noneMatch(value::equals)) {
            throw new IllegalArgumentException(name + " has unsupported value");
        }
    }

    /**
     * 校验 RFC 9562 UUIDv7。
     */
    private static void uuidV7(UUID value, String name) {
        if (value == null || value.version() != 7 || value.variant() != 2) {
            throw new IllegalArgumentException(name + " must be UUIDv7");
        }
    }

    /**
     * 校验非负长整数。
     */
    private static void nonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    /**
     * 校验正长整数。
     */
    private static void positive(long value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    /**
     * 校验 0 到 1 的比例。
     */
    private static void ratio(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " must be between zero and one");
        }
    }

    /**
     * 校验 ISO 4217 三字符币种。
     */
    private static String currency(String value) {
        if (value == null || !value.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must use ISO 4217 format");
        }
        return value;
    }

    /**
     * 校验 Optional 容器本身非空。
     */
    private static <T> Optional<T> optional(Optional<T> value, String name) {
        return Objects.requireNonNull(value, name + " must not be null");
    }

    /**
     * 校验 Optional 文本存在时满足长度。
     */
    private static Optional<String> optionalText(
        Optional<String> value, String name, int maxLength) {
        Optional<String> checked = optional(value, name);
        checked.ifPresent(item -> text(item, name, maxLength));
        return checked;
    }
}
