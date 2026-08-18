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

import static space.refinex.agentark.control.governance.adapter.in.web.GovernanceApiModels.optional;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import space.refinex.agentark.control.governance.adapter.in.web.GovernanceApiModels.*;
import space.refinex.agentark.control.governance.application.GovernanceApplicationService;
import space.refinex.agentark.control.governance.application.GovernanceCommands.*;
import space.refinex.agentark.control.governance.domain.GovernanceModels.*;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.kernel.id.EventId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Instant;
import java.util.*;

/**
 * 暴露 Governance Public 查询/管理 API 与受服务身份保护的 Internal 汇聚命令。
 *
 * @author refinex
 */
@RestController
@PreAuthorize("isAuthenticated()")
public class GovernanceController {

    /**
     * Governance 应用服务。
     */
    private final GovernanceApplicationService service;

    /**
     * 创建 Governance Controller。
     *
     * @param service Governance 应用服务
     */
    public GovernanceController(GovernanceApplicationService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    /**
     * 返回项目过去 24 小时治理概览。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @return 审计、用量、成本、配额和评估计数
     */
    @GetMapping("/api/v1/projects/{projectId}/governance/overview")
    public Map<String, Object> overview(Authentication authentication, @PathVariable String projectId) {
        return service.overview(principal(authentication), ProjectId.parse(projectId));
    }

    /**
     * 严格授权后读取项目 Audit Event。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param before         可选时间上界
     * @param beforeId       可选 UUID 上界
     * @param limit          页大小
     * @return Audit Event
     */
    @GetMapping("/api/v1/projects/{projectId}/governance/audit-events")
    public List<AuditEvent> listAudit(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam(required = false) Instant before,
        @RequestParam(required = false) String beforeId,
        @RequestParam(defaultValue = "50") int limit) {

        return service.listAudit(
            principal(authentication),
            ProjectId.parse(projectId),
            Optional.ofNullable(before),
            Optional.ofNullable(beforeId).map(EventId::parse), limit);
    }

    /**
     * 读取项目 Usage Ledger。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param before         可选时间上界
     * @param limit          页大小
     * @return Usage 明细
     */
    @GetMapping("/api/v1/projects/{projectId}/governance/usage")
    public List<UsageLedgerEntry> listUsage(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam(required = false) Instant before,
        @RequestParam(defaultValue = "50") int limit) {

        return service.listUsage(principal(authentication), ProjectId.parse(projectId), Optional.ofNullable(before), limit);
    }

    /**
     * 读取项目 Usage/Cost 日聚合。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param from           开始时间
     * @param to             结束时间
     * @param limit          页大小
     * @return Usage/Cost 聚合
     */
    @GetMapping("/api/v1/projects/{projectId}/governance/usage:aggregate")
    public List<UsageAggregate> listUsageAggregates(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam Instant from,
        @RequestParam Instant to,
        @RequestParam(defaultValue = "100") int limit) {

        return service.listUsageAggregates(principal(authentication), ProjectId.parse(projectId), from, to, limit);
    }

    /**
     * 创建 Price Table 和不可变 V1。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param request        创建请求
     * @return 初始不可变版本
     */
    @PostMapping("/api/v1/projects/{projectId}/governance/price-tables")
    @ResponseStatus(HttpStatus.CREATED)
    public PriceTableVersion createPriceTable(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody CreatePriceTableRequest request) {

        return service.createPriceTable(
            principal(authentication),
            ProjectId.parse(projectId),
            new PriceTableCommand(
                request.key(),
                request.name(),
                request.currency(),
                request.effectiveFrom(),
                request.entries()
            )
        );
    }

    /**
     * 列出 Price Table。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param limit          页大小
     * @return Price Table
     */
    @GetMapping("/api/v1/projects/{projectId}/governance/price-tables")
    public List<PriceTable> listPriceTables(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam(defaultValue = "50") int limit) {

        return service.listPriceTables(principal(authentication), ProjectId.parse(projectId), limit);
    }

    /**
     * 列出指定 Price Table 的不可变版本。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param priceTableId   Price Table UUIDv7
     * @param limit          页大小
     * @return Price Table Version
     */
    @GetMapping("/api/v1/projects/{projectId}/governance/price-tables/{priceTableId}/versions")
    public List<PriceTableVersion> listPriceTableVersions(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String priceTableId,
        @RequestParam(defaultValue = "50") int limit) {

        return service.listPriceTableVersions(principal(authentication), ProjectId.parse(projectId), uuidV7(priceTableId), limit);
    }

    /**
     * 创建 Quota Policy。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param request        Policy 请求
     * @return Policy
     */
    @PostMapping("/api/v1/projects/{projectId}/governance/quota-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public QuotaPolicy createQuotaPolicy(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody CreateQuotaPolicyRequest request) {

        return service.createQuotaPolicy(
            principal(authentication),
            ProjectId.parse(projectId),
            new QuotaPolicyCommand(
                request.scopeType(),
                request.scopeRef(),
                request.metric(),
                request.enforcement(),
                request.limitValue(),
                Optional.ofNullable(request.windowSeconds()),
                request.budgetAction(),
                request.effectiveFrom(),
                Optional.ofNullable(request.effectiveUntil())
            )
        );
    }

    /**
     * 列出 Quota Policy。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param limit          页大小
     * @return Policy
     */
    @GetMapping("/api/v1/projects/{projectId}/governance/quota-policies")
    public List<QuotaPolicy> listQuotaPolicies(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam(defaultValue = "50") int limit) {

        return service.listQuotaPolicies(principal(authentication), ProjectId.parse(projectId), limit);
    }

    /**
     * 创建 Dataset、V1 和 Test Cases。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param request        Dataset 请求
     * @return Dataset Version
     */
    @PostMapping("/api/v1/projects/{projectId}/governance/evaluation/datasets")
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetVersion createDataset(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody CreateDatasetRequest request) {

        return service.createDataset(
            principal(authentication), ProjectId.parse(projectId),
            new DatasetCommand(
                request.key(),
                request.name(),
                Optional.ofNullable(request.description()),
                request.schema(),
                request.contentHash(),
                request.cases().stream()
                    .map(item -> new EvaluationCaseCommand(
                        item.key(),
                        item.inputObjectUri(),
                        item.inputContentHash(),
                        item.expected(),
                        item.expectedContentHash(),
                        item.weight()))
                    .toList()));
    }

    /**
     * 列出 Dataset。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param limit          页大小
     * @return Dataset
     */
    @GetMapping("/api/v1/projects/{projectId}/governance/evaluation/datasets")
    public List<EvaluationDataset> listDatasets(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam(defaultValue = "50") int limit) {

        return service.listDatasets(principal(authentication), ProjectId.parse(projectId), limit);
    }

    /**
     * 创建 Evaluator 与不可变 V1。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param request        Evaluator 请求
     * @return Evaluator Version
     */
    @PostMapping("/api/v1/projects/{projectId}/governance/evaluation/evaluators")
    @ResponseStatus(HttpStatus.CREATED)
    public EvaluatorVersion createEvaluator(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody CreateEvaluatorRequest request) {

        return service.createEvaluator(
            principal(authentication), ProjectId.parse(projectId),
            new EvaluatorCommand(
                request.key(),
                request.name(),
                request.type(),
                request.config(),
                request.contentHash()
            )
        );
    }

    /**
     * 列出 Evaluator。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param limit          页大小
     * @return Evaluator
     */
    @GetMapping("/api/v1/projects/{projectId}/governance/evaluation/evaluators")
    public List<Evaluator> listEvaluators(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam(defaultValue = "50") int limit) {

        return service.listEvaluators(principal(authentication), ProjectId.parse(projectId), limit);
    }

    /**
     * 执行固定 Hash 的确定性 Evaluation。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param request        Evaluation 请求
     * @return 终态 Run
     */
    @PostMapping("/api/v1/projects/{projectId}/governance/evaluation/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public EvaluationRun evaluate(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody RunEvaluationRequest request) {

        return service.evaluateDeterministically(
            principal(authentication), ProjectId.parse(projectId),
            new EvaluationRunCommand(
                request.candidateRevisionId(),
                request.datasetVersionId(),
                request.evaluatorVersionId(),
                request.threshold(),
                Optional.ofNullable(request.baselineRunId()),
                request.observedHashes()
            )
        );
    }

    /**
     * 列出 Evaluation Run。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param limit          页大小
     * @return Run
     */
    @GetMapping("/api/v1/projects/{projectId}/governance/evaluation/runs")
    public List<EvaluationRun> listEvaluationRuns(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam(defaultValue = "50") int limit) {

        return service.listEvaluationRuns(principal(authentication), ProjectId.parse(projectId), limit);
    }

    /**
     * 新建或更新 Release Gate。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param request        Gate 请求
     * @return Gate
     */
    @PutMapping("/api/v1/projects/{projectId}/governance/evaluation/release-gates")
    public ReleaseGate saveReleaseGate(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody SaveReleaseGateRequest request) {

        return service.saveReleaseGate(principal(authentication), ProjectId.parse(projectId),
            new ReleaseGateCommand(
                Optional.ofNullable(request.id()),
                request.agentId(),
                Optional.ofNullable(request.environmentId()),
                request.datasetVersionId(),
                request.evaluatorVersionId(),
                request.threshold(),
                request.enforcement(),
                request.status(),
                Optional.ofNullable(request.expectedVersion()))
        );
    }

    /**
     * 列出 Release Gate。
     *
     * @param authentication 已认证主体
     * @param projectId      项目 UUIDv7
     * @param limit          页大小
     * @return Gate
     */
    @GetMapping("/api/v1/projects/{projectId}/governance/evaluation/release-gates")
    public List<ReleaseGate> listReleaseGates(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam(defaultValue = "50") int limit) {

        return service.listReleaseGates(principal(authentication), ProjectId.parse(projectId), limit);
    }

    /**
     * 内部服务幂等提交 Audit Event。
     *
     * @param authentication 已认证服务身份
     * @param request        Audit 请求
     * @return 首次接收结果
     */
    @PostMapping("/internal/v1/governance/audit-events")
    public IngestResponse ingestAudit(Authentication authentication, @Valid @RequestBody AuditIngestRequest request) {
        requireService(authentication);
        return new IngestResponse(service.ingestAudit(new AuditCommand(
            request.sourceEventId(),
            request.sourcePlane(),
            optional(request.organizationId()),
            optional(request.projectId()),
            request.principalType(),
            request.principalRef(),
            request.scopeType(),
            request.scopeRef(),
            request.action(),
            request.result(),
            request.resourceType(),
            request.resourceRef(),
            request.diffSummary(),
            optional(request.policyVersion()),
            Optional.ofNullable(request.roleVersion()),
            optional(request.traceId()),
            optional(request.requestId()),
            request.occurredAt())));
    }

    /**
     * 内部服务幂等提交 Usage 明细。
     *
     * @param authentication 已认证服务身份
     * @param request        Usage 请求
     * @return 首次接收结果
     */
    @PostMapping("/internal/v1/governance/usage-records")
    public IngestResponse ingestUsage(Authentication authentication, @Valid @RequestBody UsageIngestRequest request) {
        requireService(authentication);
        return new IngestResponse(service.ingestUsage(new UsageCommand(
            request.sourcePlane(),
            request.sourceRecordId(),
            request.organizationId(),
            request.projectId(),
            optional(request.agentId()),
            optional(request.revisionId()),
            optional(request.deploymentId()),
            optional(request.sessionId()),
            optional(request.turnId()),
            optional(request.runId()),
            request.usageType(),
            request.provider(),
            optional(request.model()),
            optional(request.tool()),
            request.inputTokens(),
            request.outputTokens(),
            request.cachedTokens(),
            request.embeddingTokens(),
            request.toolCalls(),
            request.sandboxDurationMs(),
            request.estimated(),
            optional(request.priceTableVersionId()),
            optional(request.currency()),
            request.costAmount(),
            request.occurredAt())));
    }

    /**
     * 内部服务申请 Quota Reservation。
     *
     * @param authentication 已认证服务身份
     * @param request        Reservation 请求
     * @return Quota 决策
     */
    @PostMapping("/internal/v1/governance/quota-reservations")
    public QuotaDecision reserveQuota(
        Authentication authentication, @Valid @RequestBody QuotaReserveRequest request) {
        requireService(authentication);
        return service.reserveQuota(new QuotaReservationCommand(
            request.organizationId(),
            request.projectId(),
            request.scopeType(),
            request.scopeRef(),
            request.metric(),
            request.idempotencyKey(),
            request.subjectRef(),
            request.amount(),
            request.ttlSeconds()));
    }

    /**
     * 内部服务提交或释放 Reservation。
     *
     * @param authentication 已认证服务身份
     * @param reservationId  Reservation UUIDv7
     * @param request        目标状态
     * @return 转换结果
     */
    @PostMapping("/internal/v1/governance/quota-reservations/{reservationId}:transition")
    public IngestResponse transitionReservation(
        Authentication authentication,
        @PathVariable String reservationId,
        @Valid @RequestBody ReservationTransitionRequest request) {

        requireService(authentication);
        return new IngestResponse(service.transitionReservation(uuidV7(reservationId), request.target()));
    }

    /**
     * 解析 Spring Security Principal。
     *
     * @param authentication 已认证上下文
     * @return AgentArk Principal
     */
    private AgentArkPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AgentArkPrincipal principal)) {
            throw new AccessDeniedException("agentark principal is required");
        }
        return principal;
    }

    /**
     * 校验 Internal API 只能由面向 Control Audience 的服务身份调用。
     *
     * @param authentication 已认证上下文
     */
    private void requireService(Authentication authentication) {
        AgentArkPrincipal principal = principal(authentication);
        if (principal.type() != PrincipalType.SERVICE || principal.serviceIdentity().orElseThrow().audiences().stream()
            .noneMatch(audience -> audience.equals("agentark-control"))) {
            throw new AccessDeniedException("control service identity is required");
        }
    }

    /**
     * 解析规范 UUIDv7 字符串。
     *
     * @param value UUID 字符串
     * @return UUIDv7
     */
    private UUID uuidV7(String value) {
        UUID parsed = UUID.fromString(value);
        if (parsed.version() != 7 || parsed.variant() != 2 || !parsed.toString().equals(value)) {
            throw new IllegalArgumentException("value must be canonical UUIDv7");
        }
        return parsed;
    }
}
