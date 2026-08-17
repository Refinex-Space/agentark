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

package space.refinex.agentark.control.governance.application;

import space.refinex.agentark.control.governance.domain.GovernanceModels.*;
import space.refinex.agentark.kernel.ref.Checksum;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 定义 Governance Public/Internal API 进入应用层的中立命令。
 *
 * @author refinex
 */
public final class GovernanceCommands {

    /** 禁止实例化命令容器。 */
    private GovernanceCommands() {
    }

    /**
     * 跨平面审计幂等命令。
     *
     * @param sourceEventId  来源事件标识
     * @param sourcePlane    来源平面
     * @param organizationId 可选组织 UUIDv7
     * @param projectId      可选项目 UUIDv7
     * @param principalType  主体类型
     * @param principalRef   主体引用
     * @param scopeType      作用域类型
     * @param scopeRef       作用域引用
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
    public record AuditCommand(
        String sourceEventId,
        AuditPlane sourcePlane,
        Optional<String> organizationId,
        Optional<String> projectId,
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
        Instant occurredAt) {

        /** 校验命令容器和枚举字段非空。 */
        public AuditCommand {
            organizationId = requireOptional(organizationId, "organizationId");
            projectId = requireOptional(projectId, "projectId");
            diffSummary = Map.copyOf(java.util.Objects.requireNonNull(
                diffSummary, "diffSummary must not be null"));
            policyVersion = requireOptional(policyVersion, "policyVersion");
            roleVersion = requireOptional(roleVersion, "roleVersion");
            traceId = requireOptional(traceId, "traceId");
            requestId = requireOptional(requestId, "requestId");
        }
    }

    /**
     * Price Table 与初始不可变版本命令。
     *
     * @param key           项目内 Key
     * @param name          名称
     * @param currency      币种
     * @param effectiveFrom 起效时间
     * @param entries       单位价格项
     * @author refinex
     */
    public record PriceTableCommand(
        String key,
        String name,
        String currency,
        Instant effectiveFrom,
        Map<String, BigDecimal> entries) {

        /** 防御性复制价格项。 */
        public PriceTableCommand {
            entries = Map.copyOf(java.util.Objects.requireNonNull(entries, "entries must not be null"));
        }
    }

    /**
     * Usage 明细幂等汇聚命令。
     *
     * @param sourcePlane         来源平面
     * @param sourceRecordId      来源记录
     * @param organizationId      组织 UUIDv7
     * @param projectId           项目 UUIDv7
     * @param agentId             可选 Agent UUIDv7
     * @param revisionId          可选 Revision UUIDv7
     * @param deploymentId        可选 Deployment UUIDv7
     * @param sessionId           可选 Session UUIDv7
     * @param turnId              可选 Turn UUIDv7
     * @param runId               可选 Run UUIDv7
     * @param usageType           计量类型
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
     * @param priceTableVersionId 可选价格版本 UUIDv7
     * @param currency            可选币种
     * @param costAmount          成本
     * @param occurredAt          发生时间
     * @author refinex
     */
    public record UsageCommand(
        String sourcePlane,
        String sourceRecordId,
        String organizationId,
        String projectId,
        Optional<String> agentId,
        Optional<String> revisionId,
        Optional<String> deploymentId,
        Optional<String> sessionId,
        Optional<String> turnId,
        Optional<String> runId,
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
        Optional<String> priceTableVersionId,
        Optional<String> currency,
        BigDecimal costAmount,
        Instant occurredAt) {

        /** 校验所有 Optional 容器非空。 */
        public UsageCommand {
            agentId = requireOptional(agentId, "agentId");
            revisionId = requireOptional(revisionId, "revisionId");
            deploymentId = requireOptional(deploymentId, "deploymentId");
            sessionId = requireOptional(sessionId, "sessionId");
            turnId = requireOptional(turnId, "turnId");
            runId = requireOptional(runId, "runId");
            model = requireOptional(model, "model");
            tool = requireOptional(tool, "tool");
            priceTableVersionId = requireOptional(priceTableVersionId, "priceTableVersionId");
            currency = requireOptional(currency, "currency");
        }
    }

    /**
     * Quota Policy 创建命令。
     *
     * @param scopeType      Scope 类型
     * @param scopeRef       Scope 引用
     * @param metric         指标
     * @param enforcement    软硬方式
     * @param limitValue     上限
     * @param windowSeconds  可选窗口秒数
     * @param budgetAction   运行中动作
     * @param effectiveFrom  起效时间
     * @param effectiveUntil 可选失效时间
     * @author refinex
     */
    public record QuotaPolicyCommand(
        QuotaScopeType scopeType,
        String scopeRef,
        QuotaMetric metric,
        QuotaEnforcement enforcement,
        BigDecimal limitValue,
        Optional<Long> windowSeconds,
        BudgetAction budgetAction,
        Instant effectiveFrom,
        Optional<Instant> effectiveUntil) {

        /** 校验 Optional 容器非空。 */
        public QuotaPolicyCommand {
            windowSeconds = requireOptional(windowSeconds, "windowSeconds");
            effectiveUntil = requireOptional(effectiveUntil, "effectiveUntil");
        }
    }

    /**
     * Quota Reservation Internal Command。
     *
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param scopeType      Scope 类型
     * @param scopeRef       Scope 引用
     * @param metric         指标
     * @param idempotencyKey 幂等键
     * @param subjectRef     工作引用
     * @param amount         预留量
     * @param ttlSeconds     预留 TTL 秒数
     * @author refinex
     */
    public record QuotaReservationCommand(
        String organizationId,
        String projectId,
        QuotaScopeType scopeType,
        String scopeRef,
        QuotaMetric metric,
        String idempotencyKey,
        String subjectRef,
        BigDecimal amount,
        long ttlSeconds) {
    }

    /**
     * Evaluation Test Case 创建命令。
     *
     * @param key               用例键
     * @param inputObjectUri    输入 ObjectRef URI
     * @param inputContentHash  输入 Hash
     * @param expected          期望投影
     * @param expectedContentHash 期望 Hash
     * @param weight            权重
     * @author refinex
     */
    public record EvaluationCaseCommand(
        String key,
        String inputObjectUri,
        Checksum inputContentHash,
        Map<String, Object> expected,
        Checksum expectedContentHash,
        BigDecimal weight) {

        /** 防御性复制安全期望投影。 */
        public EvaluationCaseCommand {
            expected = Map.copyOf(java.util.Objects.requireNonNull(expected, "expected must not be null"));
        }
    }

    /**
     * Dataset 与首个不可变版本创建命令。
     *
     * @param key         数据集键
     * @param name        名称
     * @param description 可选说明
     * @param schema      用例结构定义
     * @param contentHash 版本内容 Hash
     * @param cases       测试用例列表
     * @author refinex
     */
    public record DatasetCommand(
        String key,
        String name,
        Optional<String> description,
        Map<String, Object> schema,
        Checksum contentHash,
        List<EvaluationCaseCommand> cases) {

        /** 防御性复制 Dataset 版本内容。 */
        public DatasetCommand {
            description = requireOptional(description, "description");
            schema = Map.copyOf(java.util.Objects.requireNonNull(schema, "schema must not be null"));
            cases = List.copyOf(java.util.Objects.requireNonNull(cases, "cases must not be null"));
        }
    }

    /**
     * Evaluator 与首个不可变版本创建命令。
     *
     * @param key         评估器键
     * @param name        名称
     * @param type        类型
     * @param config      安全配置
     * @param contentHash 内容 Hash
     * @author refinex
     */
    public record EvaluatorCommand(
        String key,
        String name,
        EvaluatorType type,
        Map<String, Object> config,
        Checksum contentHash) {

        /** 防御性复制 Evaluator 配置。 */
        public EvaluatorCommand {
            config = Map.copyOf(java.util.Objects.requireNonNull(config, "config must not be null"));
        }
    }

    /**
     * 确定性 Evaluation Run 命令。
     *
     * @param candidateRevisionId 候选修订标识
     * @param datasetVersionId    数据集版本标识
     * @param evaluatorVersionId  评估器版本标识
     * @param threshold           通过阈值
     * @param baselineRunId       可选 Baseline Run UUIDv7
     * @param observedHashes      Case Key 到规范输出 Hash
     * @author refinex
     */
    public record EvaluationRunCommand(
        String candidateRevisionId,
        String datasetVersionId,
        String evaluatorVersionId,
        BigDecimal threshold,
        Optional<String> baselineRunId,
        Map<String, Checksum> observedHashes) {

        /** 防御性复制回归引用和输出 Hash。 */
        public EvaluationRunCommand {
            baselineRunId = requireOptional(baselineRunId, "baselineRunId");
            observedHashes = Map.copyOf(java.util.Objects.requireNonNull(
                observedHashes, "observedHashes must not be null"));
        }
    }

    /**
     * Release Gate 创建或更新命令。
     *
     * @param id                 可选既有 Gate UUIDv7
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
    public record ReleaseGateCommand(
        Optional<String> id,
        String agentId,
        Optional<String> environmentId,
        String datasetVersionId,
        String evaluatorVersionId,
        BigDecimal threshold,
        QuotaEnforcement enforcement,
        String status,
        Optional<Long> expectedVersion) {

        /** 校验所有 Optional 容器非空。 */
        public ReleaseGateCommand {
            id = requireOptional(id, "id");
            environmentId = requireOptional(environmentId, "environmentId");
            expectedVersion = requireOptional(expectedVersion, "expectedVersion");
        }
    }

    /** 校验 Optional 容器非空并原样返回。 */
    private static <T> Optional<T> requireOptional(Optional<T> value, String name) {
        return java.util.Objects.requireNonNull(value, name + " must not be null");
    }
}
