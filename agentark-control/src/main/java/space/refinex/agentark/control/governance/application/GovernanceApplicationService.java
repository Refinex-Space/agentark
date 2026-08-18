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

import org.springframework.transaction.annotation.Transactional;
import space.refinex.agentark.control.governance.application.GovernanceCommands.*;
import space.refinex.agentark.control.governance.application.port.GovernanceRepository;
import space.refinex.agentark.control.governance.domain.GovernanceModels.*;
import space.refinex.agentark.control.iam.application.IamAuthorizationService;
import space.refinex.agentark.control.iam.application.IamConflictException;
import space.refinex.agentark.control.iam.application.IamNotFoundException;
import space.refinex.agentark.control.iam.application.PermissionRegistry;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.iam.domain.Project;
import space.refinex.agentark.control.release.application.port.ReleaseRepository;
import space.refinex.agentark.control.release.domain.ReleaseModels.StoredSnapshot;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 编排严格授权 Audit 查询、Usage/Cost、并发 Quota 与固定版本 Evaluation。
 *
 * @author refinex
 */
public class GovernanceApplicationService {

    /**
     * Public 列表最大页大小。
     */
    private static final int MAX_LIMIT = 100;

    /**
     * Quota Reservation 最短 TTL。
     */
    private static final long MIN_QUOTA_TTL_SECONDS = 5;

    /**
     * Quota Reservation 最长 TTL。
     */
    private static final long MAX_QUOTA_TTL_SECONDS = 3600;

    /**
     * 治理持久化仓储。
     */
    private final GovernanceRepository repository;

    /**
     * 租户目录。
     */
    private final TenantCatalogRepository tenantRepository;

    /**
     * IAM 授权服务。
     */
    private final IamAuthorizationService authorizationService;

    /**
     * Revision/Snapshot 查询端口。
     */
    private final ReleaseRepository releaseRepository;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 创建 Governance 应用服务。
     *
     * @param repository           Governance Repository
     * @param tenantRepository     租户目录
     * @param authorizationService IAM 授权服务
     * @param releaseRepository    Revision/Snapshot 查询端口
     * @param clock                UTC 时钟
     */
    public GovernanceApplicationService(GovernanceRepository repository, TenantCatalogRepository tenantRepository,
                                        IamAuthorizationService authorizationService, ReleaseRepository releaseRepository, Clock clock) {

        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository must not be null");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService must not be null");
        this.releaseRepository = Objects.requireNonNull(releaseRepository, "releaseRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 严格授权后读取 append-only Audit Event。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param before    可选时间上界
     * @param beforeId  可选 UUID 上界
     * @param limit     页大小
     * @return 审计事件
     */
    public List<AuditEvent> listAudit(AgentArkPrincipal principal, ProjectId projectId, Optional<Instant> before,
                                      Optional<EventId> beforeId, int limit) {

        Project project = authorize(principal, projectId, PermissionRegistry.AUDIT_READ);
        if (before.isPresent() != beforeId.isPresent()) {
            throw new IllegalArgumentException("audit cursor requires time and id together");
        }
        return repository.listAudit(project.organizationId(), projectId, before, beforeId, limit(limit));
    }

    /**
     * 由受认证内部服务幂等接收跨平面 Audit Event。
     *
     * @param command 审计命令
     * @return 首次接收为 {@code true}
     */
    public boolean ingestAudit(AuditCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = Instant.now(clock);
        return repository.appendAudit(new AuditEvent(
            EventId.generate(),
            command.sourceEventId(),
            command.sourcePlane(),
            command.organizationId().map(OrganizationId::parse),
            command.projectId().map(ProjectId::parse),
            command.principalType(),
            command.principalRef(),
            command.scopeType(),
            command.scopeRef(),
            command.action(),
            command.result(),
            command.resourceType(),
            command.resourceRef(),
            command.diffSummary(),
            command.policyVersion(),
            command.roleVersion(),
            command.traceId(),
            command.requestId(),
            command.occurredAt(),
            now));
    }

    /**
     * 创建稳定 Price Table 与不可变 V1。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param command   创建命令
     * @return 初始版本
     */
    @Transactional
    public PriceTableVersion createPriceTable(AgentArkPrincipal principal, ProjectId projectId, PriceTableCommand command) {
        Project project = authorize(principal, projectId, PermissionRegistry.PRICE_MANAGE);
        Instant now = Instant.now(clock);
        UUID tableId = EventId.generate().value();
        PriceTable table = new PriceTable(tableId, project.organizationId(), projectId, command.key(), command.name(), "ACTIVE", 0, now, now);
        Map<String, BigDecimal> sorted = new TreeMap<>(command.entries());
        String canonical = command.currency() + "\n" + command.effectiveFrom() + "\n" + sorted;
        PriceTableVersion version = new PriceTableVersion(EventId.generate().value(), project.organizationId(), projectId,
            tableId, 1, command.currency(), command.effectiveFrom(), sorted, Checksum.sha256(canonical), now);
        repository.insertPriceTable(table, actor(principal));
        repository.insertPriceTableVersion(version, actor(principal));
        audit("price_table.create", principal, project, "price_table", tableId.toString(), Map.of("versionNumber", 1), now);
        return version;
    }

    /**
     * 列出 Price Table。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param limit     页大小
     * @return 价格表
     */
    public List<PriceTable> listPriceTables(AgentArkPrincipal principal, ProjectId projectId, int limit) {
        authorize(principal, projectId, PermissionRegistry.USAGE_READ);
        return repository.listPriceTables(projectId, limit(limit));
    }

    /**
     * 列出不可变 Price Table Version。
     *
     * @param principal    已认证主体
     * @param projectId    项目
     * @param priceTableId 价格表 UUIDv7
     * @param limit        页大小
     * @return 价格版本
     */
    public List<PriceTableVersion> listPriceTableVersions(AgentArkPrincipal principal, ProjectId projectId, UUID priceTableId, int limit) {
        authorize(principal, projectId, PermissionRegistry.USAGE_READ);
        return repository.listPriceTableVersions(projectId, priceTableId, limit(limit));
    }

    /**
     * 幂等接收不含正文的跨平面 Usage 明细。
     *
     * @param command Usage 命令
     * @return 首次接收为 {@code true}
     */
    public boolean ingestUsage(UsageCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return repository.ingestUsage(new UsageLedgerEntry(
            EventId.generate().value(),
            command.sourcePlane(),
            command.sourceRecordId(),
            OrganizationId.parse(command.organizationId()),
            ProjectId.parse(command.projectId()),
            command.agentId().map(AgentId::parse),
            command.revisionId().map(RevisionId::parse),
            command.deploymentId().map(DeploymentId::parse),
            command.sessionId().map(SessionId::parse),
            command.turnId().map(TurnId::parse),
            command.runId().map(RunId::parse),
            command.usageType(),
            command.provider(),
            command.model(),
            command.tool(),
            command.inputTokens(),
            command.outputTokens(),
            command.cachedTokens(),
            command.embeddingTokens(),
            command.toolCalls(),
            command.sandboxDurationMs(),
            command.estimated(),
            command.priceTableVersionId().map(GovernanceApplicationService::uuidV7),
            command.currency(),
            command.costAmount(),
            command.occurredAt(),
            Instant.now(clock)));
    }

    /**
     * 查询项目 Usage 明细。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param before    可选时间上界
     * @param limit     页大小
     * @return Usage 明细
     */
    public List<UsageLedgerEntry> listUsage(AgentArkPrincipal principal, ProjectId projectId, Optional<Instant> before, int limit) {
        authorize(principal, projectId, PermissionRegistry.USAGE_READ);
        return repository.listUsageLedger(projectId, before, limit(limit));
    }

    /**
     * 查询项目 Usage/Cost 聚合。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param from      开始时间
     * @param to        结束时间
     * @param limit     页大小
     * @return 聚合
     */
    public List<UsageAggregate> listUsageAggregates(AgentArkPrincipal principal, ProjectId projectId, Instant from, Instant to, int limit) {
        authorize(principal, projectId, PermissionRegistry.USAGE_READ);
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0) {
            throw new IllegalArgumentException("usage time range is invalid");
        }
        return repository.listUsageAggregates(projectId, from, to, limit(limit));
    }

    /**
     * 创建 Quota Policy。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param command   Policy 命令
     * @return Policy
     */
    @Transactional
    public QuotaPolicy createQuotaPolicy(AgentArkPrincipal principal, ProjectId projectId, QuotaPolicyCommand command) {
        Project project = authorize(principal, projectId, PermissionRegistry.QUOTA_MANAGE);
        QuotaPolicy policy = new QuotaPolicy(
            EventId.generate().value(),
            project.organizationId(),
            projectId,
            command.scopeType(),
            command.scopeRef(),
            command.metric(),
            command.enforcement(),
            command.limitValue(),
            command.windowSeconds(),
            command.budgetAction(),
            command.effectiveFrom(),
            command.effectiveUntil(),
            "ACTIVE",
            0);
        repository.insertQuotaPolicy(policy, actor(principal));
        audit(
            "quota_policy.create", principal, project, "quota_policy", policy.id().toString(),
            Map.of(
                "metric", policy.metric().name(),
                "enforcement", policy.enforcement().name(),
                "budgetAction", policy.budgetAction().name()),
            Instant.now(clock));
        return policy;
    }

    /**
     * 列出 Quota Policy。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param limit     页大小
     * @return Policy
     */
    public List<QuotaPolicy> listQuotaPolicies(AgentArkPrincipal principal, ProjectId projectId, int limit) {
        authorize(principal, projectId, PermissionRegistry.QUOTA_READ);
        return repository.listQuotaPolicies(projectId, limit(limit));
    }

    /**
     * 内部服务执行 Quota Reservation。
     *
     * @param command 预留命令
     * @return Quota 决策
     */
    public QuotaDecision reserveQuota(QuotaReservationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.ttlSeconds() < MIN_QUOTA_TTL_SECONDS || command.ttlSeconds() > MAX_QUOTA_TTL_SECONDS) {
            throw new IllegalArgumentException("quota ttl must be between 5 and 3600 seconds");
        }

        return repository.reserveQuota(
            OrganizationId.parse(command.organizationId()),
            ProjectId.parse(command.projectId()),
            command.scopeType(),
            command.scopeRef(),
            command.metric(),
            command.idempotencyKey(),
            command.subjectRef(),
            command.amount(),
            Duration.ofSeconds(command.ttlSeconds()),
            Instant.now(clock));
    }

    /**
     * 内部服务提交或释放 Quota Reservation。
     *
     * @param reservationId Reservation UUIDv7
     * @param target        COMMITTED 或 RELEASED
     * @return 幂等转换是否成功
     */
    public boolean transitionReservation(UUID reservationId, ReservationStatus target) {
        return repository.transitionReservation(reservationId, target, Instant.now(clock));
    }

    /**
     * 创建 Dataset、不可变 V1 和 Test Cases。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param command   Dataset 命令
     * @return Dataset Version
     */
    @Transactional
    public DatasetVersion createDataset(AgentArkPrincipal principal, ProjectId projectId, DatasetCommand command) {
        Project project = authorize(principal, projectId, PermissionRegistry.EVALUATION_MANAGE);
        if (command.cases().isEmpty()) {
            throw new IllegalArgumentException("evaluation dataset requires test cases");
        }

        UUID datasetId = EventId.generate().value();
        UUID versionId = EventId.generate().value();
        EvaluationDataset dataset = new EvaluationDataset(datasetId, command.key(), command.name(), command.description(), "ACTIVE", 0);
        DatasetVersion version = new DatasetVersion(versionId, datasetId, 1, command.schema(), command.contentHash());
        List<EvaluationTestCase> cases = command.cases().stream()
            .map(item -> new EvaluationTestCase(
                EventId.generate().value(),
                versionId,
                item.key(),
                item.inputObjectUri(),
                item.inputContentHash(),
                item.expected(),
                item.expectedContentHash(),
                item.weight()))
            .toList();

        repository.insertDataset(dataset, version, cases, project.organizationId(), projectId, actor(principal));
        audit("evaluation_dataset.create", principal, project, "evaluation_dataset", datasetId.toString(),
            Map.of("versionNumber", 1, "caseCount", cases.size()), Instant.now(clock));
        return version;
    }

    /**
     * 列出 Dataset。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param limit     页大小
     * @return Dataset
     */
    public List<EvaluationDataset> listDatasets(AgentArkPrincipal principal, ProjectId projectId, int limit) {
        authorize(principal, projectId, PermissionRegistry.EVALUATION_READ);
        return repository.listDatasets(projectId, limit(limit));
    }

    /**
     * 创建 Evaluator 与不可变 V1。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param command   Evaluator 命令
     * @return Evaluator Version
     */
    @Transactional
    public EvaluatorVersion createEvaluator(AgentArkPrincipal principal, ProjectId projectId, EvaluatorCommand command) {
        Project project = authorize(principal, projectId, PermissionRegistry.EVALUATION_MANAGE);
        UUID evaluatorId = EventId.generate().value();
        Evaluator evaluator = new Evaluator(evaluatorId, command.key(), command.name(), "ACTIVE", 0);
        EvaluatorVersion version = new EvaluatorVersion(EventId.generate().value(), evaluatorId, 1,
            command.type(), command.config(), command.contentHash());
        repository.insertEvaluator(evaluator, version, project.organizationId(), projectId, actor(principal));
        audit("evaluator.create", principal, project, "evaluator", evaluatorId.toString(),
            Map.of("versionNumber", 1, "type", command.type().name()), Instant.now(clock));
        return version;
    }

    /**
     * 列出 Evaluator。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param limit     页大小
     * @return Evaluator
     */
    public List<Evaluator> listEvaluators(
        AgentArkPrincipal principal, ProjectId projectId, int limit) {
        authorize(principal, projectId, PermissionRegistry.EVALUATION_READ);
        return repository.listEvaluators(projectId, limit(limit));
    }

    /**
     * 使用固定 Hash 执行 Fake/Deterministic Evaluation 并持久化终态 Run 与 Scores。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param command   Evaluation 命令
     * @return 终态 Evaluation Run
     */
    @Transactional
    public EvaluationRun evaluateDeterministically(AgentArkPrincipal principal, ProjectId projectId, EvaluationRunCommand command) {
        Project project = authorize(principal, projectId, PermissionRegistry.EVALUATION_MANAGE);
        RevisionId revisionId = RevisionId.parse(command.candidateRevisionId());
        StoredSnapshot snapshot = releaseRepository.findSnapshot(projectId, revisionId)
            .orElseThrow(() -> new IamNotFoundException("candidate revision is not visible"));
        UUID datasetVersionId = uuidV7(command.datasetVersionId());
        UUID evaluatorVersionId = uuidV7(command.evaluatorVersionId());
        repository.findDatasetVersion(projectId, datasetVersionId)
            .orElseThrow(() -> new IamNotFoundException("dataset version is not visible"));

        EvaluatorVersion evaluator = repository.findEvaluatorVersion(projectId, evaluatorVersionId)
            .orElseThrow(() -> new IamNotFoundException("evaluator version is not visible"));
        if (evaluator.type() != EvaluatorType.DETERMINISTIC) {
            throw new IamConflictException("only deterministic evaluator is executable in phase 19");
        }

        List<EvaluationTestCase> cases = repository.listTestCases(projectId, datasetVersionId);
        if (!command.observedHashes().keySet().equals(
            cases.stream().map(EvaluationTestCase::key).collect(java.util.stream.Collectors.toSet()))) {
            throw new IamConflictException("observed output hashes must exactly cover dataset cases");
        }

        UUID runId = EventId.generate().value();
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        List<EvaluationScore> scores = new ArrayList<>();

        for (EvaluationTestCase item : cases) {
            boolean matched = item.expectedContentHash().equals(command.observedHashes().get(item.key()));
            BigDecimal score = matched ? BigDecimal.ONE : BigDecimal.ZERO;
            weighted = weighted.add(score.multiply(item.weight()));
            totalWeight = totalWeight.add(item.weight());
            scores.add(new EvaluationScore(EventId.generate().value(), runId, item.id(), "exact_match", score,
                matched, Map.of("hashMatched", matched)));
        }

        BigDecimal totalScore = weighted.divide(totalWeight, MathContext.DECIMAL64);
        Optional<UUID> baselineId = command.baselineRunId().map(GovernanceApplicationService::uuidV7);
        Optional<BigDecimal> regressionDelta = baselineId.map(id -> repository
            .findEvaluationRun(projectId, id)
            .flatMap(EvaluationRun::totalScore)
            .map(totalScore::subtract)
            .orElseThrow(() -> new IamNotFoundException("baseline run is not visible")));
        Instant now = Instant.now(clock);

        EvaluationRun run = new EvaluationRun(
            runId,
            revisionId,
            snapshot.revision().snapshotId(),
            datasetVersionId,
            evaluatorVersionId,
            "agentark",
            Optional.empty(),
            command.threshold(),
            baselineId,
            totalScore.compareTo(command.threshold()) >= 0 ? EvaluationStatus.PASSED : EvaluationStatus.FAILED,
            Optional.of(totalScore),
            regressionDelta,
            now,
            Optional.of(now));

        repository.insertEvaluationRun(run, scores, project.organizationId(), projectId, actor(principal));
        audit("evaluation.run.complete", principal, project, "evaluation_run", runId.toString(),
            Map.of("status", run.status().name(), "score", totalScore), now);
        return run;
    }

    /**
     * 列出 Evaluation Run。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param limit     页大小
     * @return Run
     */
    public List<EvaluationRun> listEvaluationRuns(AgentArkPrincipal principal, ProjectId projectId, int limit) {
        authorize(principal, projectId, PermissionRegistry.EVALUATION_READ);
        return repository.listEvaluationRuns(projectId, limit(limit));
    }

    /**
     * 新建或乐观锁更新 Release Gate。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param command   Gate 命令
     * @return Gate
     */
    @Transactional
    public ReleaseGate saveReleaseGate(AgentArkPrincipal principal, ProjectId projectId, ReleaseGateCommand command) {
        Project project = authorize(principal, projectId, PermissionRegistry.EVALUATION_MANAGE);
        ReleaseGate gate = new ReleaseGate(
            command.id()
                .map(GovernanceApplicationService::uuidV7)
                .orElseGet(() -> EventId.generate().value()),
            project.organizationId(), projectId, AgentId.parse(command.agentId()),
            command.environmentId().map(EnvironmentId::parse),
            uuidV7(command.datasetVersionId()),
            uuidV7(command.evaluatorVersionId()),
            command.threshold(),
            command.enforcement(),
            command.status(),
            command.expectedVersion().map(value -> value + 1).orElse(0L));
        ReleaseGate saved = repository.saveReleaseGate(gate, command.expectedVersion(), actor(principal), Instant.now(clock));
        audit("release_gate.save", principal, project, "release_gate", saved.id().toString(),
            Map.of("status", saved.status(), "enforcement", saved.enforcement().name()), Instant.now(clock));
        return saved;
    }

    /**
     * 列出 Release Gate。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @param limit     页大小
     * @return Gate
     */
    public List<ReleaseGate> listReleaseGates(AgentArkPrincipal principal, ProjectId projectId, int limit) {
        authorize(principal, projectId, PermissionRegistry.EVALUATION_READ);
        return repository.listReleaseGates(projectId, limit(limit));
    }

    /**
     * 返回过去 24 小时治理概览。
     *
     * @param principal 已认证主体
     * @param projectId 项目
     * @return 概览
     */
    public Map<String, Object> overview(AgentArkPrincipal principal, ProjectId projectId) {
        authorize(principal, projectId, PermissionRegistry.USAGE_READ);
        return repository.overview(projectId, Instant.now(clock).minus(Duration.ofHours(24)));
    }

    /**
     * 授权项目资源并返回项目 Owner。
     *
     * @param principal  已认证主体
     * @param projectId  项目
     * @param permission 权限
     * @return 项目
     */
    private Project authorize(AgentArkPrincipal principal, ProjectId projectId, String permission) {
        Project project = tenantRepository.findProject(projectId)
            .orElseThrow(() -> new IamNotFoundException("project is not visible"));
        authorizationService.requirePermission(principal, project.organizationId(), Optional.of(projectId), Optional.empty(), permission);
        return project;
    }

    /**
     * 同一 Control 本地事务内追加 Governance Audit。
     *
     * @param action    动作
     * @param principal 主体
     * @param project   项目
     * @param type      资源类型
     * @param ref       资源引用
     * @param diff      安全差异摘要
     * @param now       时间
     */
    private void audit(String action, AgentArkPrincipal principal, Project project, String type, String ref, Map<String, Object> diff, Instant now) {
        repository.appendAudit(new AuditEvent(
            EventId.generate(),
            EventId.generate().asString(),
            AuditPlane.CONTROL,
            Optional.of(project.organizationId()),
            Optional.of(project.id()),
            AuditPrincipalType.valueOf(principal.type().name()),
            actor(principal),
            AuditScopeType.PROJECT,
            project.id().asString(),
            action,
            AuditResult.SUCCEEDED,
            type,
            ref,
            diff,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            now,
            now));
    }

    /**
     * 返回稳定非秘密主体引用。
     *
     * @param principal 主体
     * @return Issuer 与 Subject 引用
     */
    private String actor(AgentArkPrincipal principal) {
        return principal.issuer() + ":" + principal.subject();
    }

    /**
     * 限制列表页大小。
     *
     * @param value 请求值
     * @return 1 到 100
     */
    private int limit(int value) {
        if (value < 1 || value > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return value;
    }

    /**
     * 解析并校验 UUIDv7。
     *
     * @param value 规范 UUID 字符串
     * @return UUIDv7
     */
    private static UUID uuidV7(String value) {
        UUID parsed = UUID.fromString(value);
        if (parsed.version() != 7 || parsed.variant() != 2 || !parsed.toString().equals(value)) {
            throw new IllegalArgumentException("value must be canonical UUIDv7");
        }
        return parsed;
    }
}
