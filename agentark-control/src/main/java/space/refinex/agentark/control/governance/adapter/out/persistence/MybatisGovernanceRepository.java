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

package space.refinex.agentark.control.governance.adapter.out.persistence;

import org.springframework.transaction.annotation.Transactional;
import space.refinex.agentark.control.governance.adapter.out.persistence.GovernanceMapper.*;
import space.refinex.agentark.control.governance.application.port.GovernanceRepository;
import space.refinex.agentark.control.governance.domain.GovernanceModels.*;
import space.refinex.agentark.control.iam.application.IamConflictException;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * 使用 MyBatis 实现 Control/Governance 持久化、幂等汇聚和行锁配额预留。
 *
 * @author refinex
 */
public class MybatisGovernanceRepository implements GovernanceRepository {

    /**
     * 治理数据库映射器。
     */
    private final GovernanceMapper mapper;

    /**
     * Jackson 3 JSON 映射器。
     */
    private final JsonMapper jsonMapper;

    /**
     * 创建 Governance Repository。
     *
     * @param mapper     MyBatis Mapper
     * @param jsonMapper JSON 映射器
     */
    public MybatisGovernanceRepository(GovernanceMapper mapper, JsonMapper jsonMapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * 幂等追加 Audit Event。
     */
    @Override
    public boolean appendAudit(AuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        return mapper.insertAudit(auditRow(event)) == 1;
    }

    /**
     * 按严格项目 Scope 倒序读取 Audit Event。
     */
    @Override
    @Transactional(readOnly = true)
    public List<AuditEvent> listAudit(OrganizationId organizationId, ProjectId projectId, Optional<Instant> before, Optional<EventId> beforeId,
                                      int limit) {
        return mapper
            .listAudit(organizationId.value(), projectId.value(), before.orElse(null), beforeId.map(EventId::value).orElse(null), limit)
            .stream()
            .map(this::audit)
            .toList();
    }

    /**
     * 插入稳定 Price Table。
     */
    @Override
    public void insertPriceTable(PriceTable table, String actor) {
        mapper.insertPriceTable(
            new PriceTableRow(
                table.id(),
                table.organizationId().value(),
                table.projectId().value(),
                table.key(),
                table.name(),
                table.status(),
                table.version(),
                table.createdAt(),
                table.updatedAt()),
            actor);
    }

    /**
     * 插入不可变 Price Table Version。
     */
    @Override
    public void insertPriceTableVersion(PriceTableVersion version, String actor) {
        mapper.insertPriceVersion(priceVersionRow(version), actor);
    }

    /**
     * 列出项目 Price Table。
     */
    @Override
    @Transactional(readOnly = true)
    public List<PriceTable> listPriceTables(ProjectId projectId, int limit) {
        return mapper.listPriceTables(projectId.value(), limit).stream()
            .map(this::priceTable).toList();
    }

    /**
     * 列出不可变 Price Table Version。
     */
    @Override
    @Transactional(readOnly = true)
    public List<PriceTableVersion> listPriceTableVersions(
        ProjectId projectId, UUID priceTableId, int limit) {
        return mapper.listPriceVersions(projectId.value(), priceTableId, limit).stream()
            .map(this::priceVersion).toList();
    }

    /**
     * 幂等写入 Usage Ledger，并只在首次写入时更新日聚合。
     */
    @Override
    @Transactional
    public boolean ingestUsage(UsageLedgerEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        if (mapper.insertUsage(usageRow(entry)) == 0) {
            return false;
        }

        Instant start = entry.occurredAt()
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = start.plus(Duration.ofDays(1));

        mapper.upsertUsageAggregate(new UsageAggregateDelta(
            EventId.generate().value(),
            entry.organizationId().value(),
            entry.projectId().value(),
            start,
            end,
            entry.projectId().asString(),
            entry.provider(),
            entry.model().orElse("none"),
            entry.inputTokens(),
            entry.outputTokens(),
            entry.cachedTokens(),
            entry.embeddingTokens(),
            entry.toolCalls(),
            entry.sandboxDurationMs(),
            entry.estimated() ? 1 : 0,
            entry.costAmount(),
            entry.currency().orElse("XXX"),
            entry.priceTableVersionId().orElse(null),
            entry.ingestedAt()));

        return true;
    }

    /**
     * 列出项目 Usage Ledger。
     */
    @Override
    @Transactional(readOnly = true)
    public List<UsageLedgerEntry> listUsageLedger(ProjectId projectId, Optional<Instant> before, int limit) {
        return mapper.listUsage(projectId.value(), before.orElse(null), limit).stream()
            .map(this::usage).toList();
    }

    /**
     * 列出项目 Usage Aggregate。
     */
    @Override
    @Transactional(readOnly = true)
    public List<UsageAggregate> listUsageAggregates(ProjectId projectId, Instant from, Instant to, int limit) {
        return mapper.listUsageAggregates(projectId.value(), from, to, limit).stream()
            .map(row -> new UsageAggregate(
                row.periodStart(),
                row.periodEnd(),
                row.dimensionType(),
                row.dimensionRef(),
                row.provider(),
                row.modelKey(),
                row.inputTokens(),
                row.outputTokens(),
                row.cachedTokens(),
                row.embeddingTokens(),
                row.toolCalls(),
                row.sandboxDurationMs(),
                row.estimatedRecords(),
                row.sourceRecords(),
                row.costAmount(),
                row.currency()))
            .toList();
    }

    /**
     * 插入 Quota Policy。
     */
    @Override
    public void insertQuotaPolicy(QuotaPolicy policy, String actor) {
        mapper.insertQuotaPolicy(quotaPolicyRow(policy, Instant.now()), actor);
    }

    /**
     * 列出项目 Quota Policy。
     */
    @Override
    @Transactional(readOnly = true)
    public List<QuotaPolicy> listQuotaPolicies(ProjectId projectId, int limit) {
        return mapper.listQuotaPolicies(projectId.value(), limit).stream()
            .map(this::quotaPolicy).toList();
    }

    /**
     * 在活动 Policy 行锁内执行幂等且并发安全的 Quota Reservation。
     */
    @Override
    @Transactional
    public QuotaDecision reserveQuota(OrganizationId organizationId, ProjectId projectId, QuotaScopeType scopeType,
                                      String scopeRef, QuotaMetric metric, String idempotencyKey, String subjectRef,
                                      BigDecimal amount, Duration ttl, Instant now) {

        Optional<QuotaPolicyRow> locked = mapper.lockQuotaPolicy(organizationId.value(), projectId.value(),
            scopeType.name(), scopeRef, metric.name(), now);
        if (locked.isEmpty()) {
            return new QuotaDecision(Optional.empty(), true, false, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
        }
        QuotaPolicyRow policy = locked.orElseThrow();
        Optional<ReservationRow> existing = mapper.findReservationByKey(policy.id(), idempotencyKey);
        if (existing.isPresent()) {
            ReservationRow row = existing.orElseThrow();
            if (row.amount().compareTo(amount) != 0) {
                throw new IamConflictException("quota idempotency key is bound to another amount");
            }

            boolean active = row.status().equals("HELD") || row.status().equals("COMMITTED");
            return new QuotaDecision(
                Optional.of(row.id()),
                active,
                false,
                Optional.of(BudgetAction.valueOf(policy.budgetAction())),
                Optional.of(policy.version()),
                Optional.empty(),
                Optional.of(row.expiresAt()));
        }

        mapper.expireReservations(policy.id(), now);
        BigDecimal used = metric == QuotaMetric.CONCURRENT_RUN
            ? BigDecimal.ZERO
            : mapper.sumWindowUsage(
            projectId.value(),
            scopeType.name(),
            scopeRef,
            metric.name(),
            now.minusSeconds(policy.windowSeconds()),
            now
        );
        BigDecimal held = mapper.sumHeldReservations(policy.id(), now);
        BigDecimal remaining = policy.limitValue().subtract(used).subtract(held);
        boolean exceeded = amount.compareTo(remaining.max(BigDecimal.ZERO)) > 0;
        if (exceeded && policy.enforcement().equals("HARD")) {
            return new QuotaDecision(
                Optional.empty(),
                false,
                false,
                Optional.of(BudgetAction.valueOf(policy.budgetAction())),
                Optional.of(policy.version()),
                Optional.of(remaining.max(BigDecimal.ZERO)),
                Optional.empty());
        }
        UUID reservationId = EventId.generate().value();
        Instant expiresAt = now.plus(ttl);
        mapper.insertReservation(new ReservationInsert(reservationId, organizationId.value(), projectId.value(), policy.id(),
            idempotencyKey, subjectRef, amount, expiresAt, now));
        return new QuotaDecision(
            Optional.of(reservationId),
            true, exceeded,
            Optional.of(BudgetAction.valueOf(policy.budgetAction())),
            Optional.of(policy.version()),
            Optional.of(remaining.subtract(amount).max(BigDecimal.ZERO)),
            Optional.of(expiresAt));
    }

    /**
     * 幂等提交或释放 HELD Reservation。
     */
    @Override
    @Transactional
    public boolean transitionReservation(UUID reservationId, ReservationStatus target, Instant now) {
        if (target != ReservationStatus.COMMITTED && target != ReservationStatus.RELEASED) {
            throw new IllegalArgumentException("reservation target must be COMMITTED or RELEASED");
        }
        if (mapper.transitionReservation(reservationId, target.name(), now) == 1) {
            return true;
        }
        return mapper.findReservation(reservationId)
            .map(row -> row.status().equals(target.name()))
            .orElse(false);
    }

    /**
     * 原子插入 Dataset、Version 和不可变 Test Cases。
     */
    @Override
    @Transactional
    public void insertDataset(EvaluationDataset dataset, DatasetVersion version, List<EvaluationTestCase> cases,
                              OrganizationId organizationId, ProjectId projectId, String actor) {

        Instant now = Instant.now();
        mapper.insertDataset(
            new DatasetRow(
                dataset.id(),
                organizationId.value(),
                projectId.value(),
                dataset.key(),
                dataset.name(),
                dataset.description().orElse(null),
                dataset.status(),
                dataset.version(),
                now
            ),
            actor
        );
        mapper.insertDatasetVersion(
            new DatasetVersionRow(
                version.id(),
                organizationId.value(),
                projectId.value(),
                dataset.id(),
                version.versionNumber(),
                json(version.schema()),
                bytes(version.contentHash()),
                now
            ),
            actor
        );

        for (EvaluationTestCase item : cases) {
            mapper.insertTestCase(new TestCaseRow(
                item.id(),
                organizationId.value(),
                projectId.value(),
                version.id(),
                item.key(),
                item.inputObjectUri(),
                bytes(item.inputContentHash()),
                json(item.expected()),
                bytes(item.expectedContentHash()),
                item.weight(),
                now), actor);
        }
    }

    /**
     * 列出 Dataset。
     */
    @Override
    @Transactional(readOnly = true)
    public List<EvaluationDataset> listDatasets(ProjectId projectId, int limit) {
        return mapper.listDatasets(projectId.value(), limit).stream()
            .map(row -> new EvaluationDataset(
                row.id(),
                row.datasetKey(),
                row.name(),
                Optional.ofNullable(row.description()),
                row.status(),
                row.version()))
            .toList();
    }

    /**
     * 读取固定 Dataset Version。
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<DatasetVersion> findDatasetVersion(ProjectId projectId, UUID versionId) {
        return mapper.findDatasetVersion(projectId.value(), versionId).map(this::datasetVersion);
    }

    /**
     * 列出固定 Dataset Version 的 Test Cases。
     */
    @Override
    @Transactional(readOnly = true)
    public List<EvaluationTestCase> listTestCases(ProjectId projectId, UUID versionId) {
        return mapper.listTestCases(projectId.value(), versionId).stream()
            .map(this::testCase).toList();
    }

    /**
     * 原子插入 Evaluator 和不可变 Version。
     */
    @Override
    @Transactional
    public void insertEvaluator(Evaluator evaluator, EvaluatorVersion version, OrganizationId organizationId,
                                ProjectId projectId, String actor) {

        Instant now = Instant.now();
        mapper.insertEvaluator(new EvaluatorRow(
            evaluator.id(),
            organizationId.value(),
            projectId.value(),
            evaluator.key(),
            evaluator.name(),
            evaluator.status(),
            evaluator.version(),
            now), actor);
        mapper.insertEvaluatorVersion(new EvaluatorVersionRow(
            version.id(),
            organizationId.value(),
            projectId.value(),
            evaluator.id(),
            version.versionNumber(),
            version.type().name(),
            json(version.config()),
            bytes(version.contentHash()),
            now), actor);
    }

    /**
     * 列出 Evaluator。
     */
    @Override
    @Transactional(readOnly = true)
    public List<Evaluator> listEvaluators(ProjectId projectId, int limit) {
        return mapper.listEvaluators(projectId.value(), limit).stream()
            .map(row -> new Evaluator(row.id(), row.evaluatorKey(), row.name(), row.status(), row.version()))
            .toList();
    }

    /**
     * 读取固定 Evaluator Version。
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<EvaluatorVersion> findEvaluatorVersion(ProjectId projectId, UUID versionId) {
        return mapper.findEvaluatorVersion(projectId.value(), versionId)
            .map(this::evaluatorVersion);
    }

    /**
     * 原子插入终态 Evaluation Run 与只追加 Scores。
     */
    @Override
    @Transactional
    public void insertEvaluationRun(EvaluationRun run, List<EvaluationScore> scores, OrganizationId organizationId,
                                    ProjectId projectId, String actor) {

        mapper.insertEvaluationRun(new EvaluationRunRow(
                run.id(),
                organizationId.value(),
                projectId.value(),
                run.candidateRevisionId().value(),
                run.candidateSnapshotId().value(),
                run.datasetVersionId(),
                run.evaluatorVersionId(),
                run.provider(),
                run.model().orElse(null),
                run.threshold(),
                run.baselineRunId().orElse(null),
                run.status().name(),
                run.totalScore().orElse(null),
                run.regressionDelta().orElse(null),
                run.createdAt(), run.completedAt().orElse(null)),
            actor);

        for (EvaluationScore score : scores) {
            mapper.insertEvaluationScore(new EvaluationScoreRow(
                score.id(),
                organizationId.value(),
                projectId.value(),
                score.runId(),
                score.testCaseId(),
                score.metricKey(),
                score.score(),
                score.passed(),
                json(score.details()),
                run.completedAt().orElse(run.createdAt())));
        }
    }

    /**
     * 列出 Evaluation Run。
     */
    @Override
    @Transactional(readOnly = true)
    public List<EvaluationRun> listEvaluationRuns(ProjectId projectId, int limit) {
        return mapper.listEvaluationRuns(projectId.value(), limit).stream()
            .map(this::evaluationRun).toList();
    }

    /**
     * 读取 Evaluation Run。
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<EvaluationRun> findEvaluationRun(ProjectId projectId, UUID runId) {
        return mapper.findEvaluationRun(projectId.value(), runId).map(this::evaluationRun);
    }

    /**
     * 新建或乐观锁更新 Release Gate。
     */
    @Override
    @Transactional
    public ReleaseGate saveReleaseGate(ReleaseGate gate, Optional<Long> expectedVersion, String actor, Instant now) {
        ReleaseGateRow row = releaseGateRow(gate);
        if (expectedVersion.isEmpty()) {
            mapper.insertReleaseGate(row, actor, now);
            return gate;
        }
        if (mapper.updateReleaseGate(row, expectedVersion.orElseThrow(), actor, now) != 1) {
            throw new IamConflictException("release gate optimistic lock conflicted");
        }
        return mapper.findReleaseGate(gate.projectId().value(), gate.id())
            .map(this::releaseGate).orElseThrow();
    }

    /**
     * 列出 Release Gate。
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReleaseGate> listReleaseGates(ProjectId projectId, int limit) {
        return mapper.listReleaseGates(projectId.value(), limit).stream()
            .map(this::releaseGate).toList();
    }

    /**
     * 读取活动 Gate 并检查目标 Revision 的固定版本通过证明。
     */
    @Override
    @Transactional(readOnly = true)
    public ReleaseGateDecision evaluateReleaseGate(OrganizationId organizationId, ProjectId projectId, AgentId agentId,
                                                   EnvironmentId environmentId, RevisionId revisionId) {

        Optional<ReleaseGateRow> match = mapper.findActiveReleaseGate(organizationId.value(), projectId.value(),
            agentId.value(), environmentId.value());
        if (match.isEmpty()) {
            return new ReleaseGateDecision(true, false, Optional.empty(),
                Optional.empty(), "no.active.gate");
        }
        ReleaseGateRow gate = match.orElseThrow();
        Optional<UUID> passing = mapper.findPassingEvaluation(projectId.value(), revisionId.value(), gate.datasetVersionId(),
            gate.evaluatorVersionId(), gate.threshold());
        if (passing.isPresent()) {
            return new ReleaseGateDecision(true, false, Optional.of(gate.id()), passing, "evaluation.passed");
        }
        boolean soft = gate.enforcement().equals("SOFT");
        return new ReleaseGateDecision(soft, soft, Optional.of(gate.id()), Optional.empty(), "evaluation.required");
    }

    /**
     * 返回过去时间窗口的治理概览。
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> overview(ProjectId projectId, Instant from) {
        OverviewRow row = mapper.overview(projectId.value(), from);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("auditCount", row.auditCount());
        result.put("activeQuotaCount", row.activeQuotaCount());
        result.put("evaluationRunCount", row.evaluationRunCount());
        result.put("costAmount", row.costAmount());
        result.put("tokenCount", row.tokenCount());
        return Map.copyOf(result);
    }

    /**
     * 将 Audit 领域模型转换为数据库行。
     */
    private AuditRow auditRow(AuditEvent event) {
        return new AuditRow(
            event.id().value(),
            event.sourceEventId(),
            event.sourcePlane().name(),
            event.organizationId().map(OrganizationId::value).orElse(null),
            event.projectId().map(ProjectId::value).orElse(null),
            event.principalType().name(),
            event.principalRef(),
            event.scopeType().name(),
            event.scopeRef(),
            event.action(),
            event.result().name(),
            event.resourceType(),
            event.resourceRef(),
            event.diffSummary().isEmpty() ? null : json(event.diffSummary()),
            event.policyVersion().orElse(null),
            event.roleVersion().orElse(null),
            event.traceId().orElse(null),
            event.requestId().orElse(null),
            event.occurredAt(),
            event.ingestedAt());
    }

    /**
     * 将数据库 Audit 行转换为领域模型。
     */
    private AuditEvent audit(AuditRow row) {
        return new AuditEvent(
            new EventId(row.id()),
            row.sourceEventId(),
            AuditPlane.valueOf(row.sourcePlane()),
            Optional.ofNullable(row.organizationId()).map(OrganizationId::new),
            Optional.ofNullable(row.projectId()).map(ProjectId::new),
            AuditPrincipalType.valueOf(row.principalType()),
            row.principalRef(),
            AuditScopeType.valueOf(row.scopeType()),
            row.scopeRef(),
            row.action(),
            AuditResult.valueOf(row.result()),
            row.resourceType(),
            row.resourceRef(),
            row.diffSummaryJson() == null ? Map.of() : map(row.diffSummaryJson()),
            Optional.ofNullable(row.policyVersion()),
            Optional.ofNullable(row.roleVersion()),
            Optional.ofNullable(row.traceId()),
            Optional.ofNullable(row.requestId()),
            row.occurredAt(),
            row.ingestedAt());
    }

    /**
     * 将数据库 Price Table 行转换为领域模型。
     */
    private PriceTable priceTable(PriceTableRow row) {
        return new PriceTable(
            row.id(),
            new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()),
            row.priceKey(),
            row.name(),
            row.status(),
            row.version(),
            row.createdAt(),
            row.updatedAt());
    }

    /**
     * 将 Price Version 领域模型转换为数据库行。
     */
    private PriceVersionRow priceVersionRow(PriceTableVersion value) {
        return new PriceVersionRow(
            value.id(),
            value.organizationId().value(),
            value.projectId().value(),
            value.priceTableId(),
            value.versionNumber(),
            value.currency(),
            value.effectiveFrom(),
            json(value.entries()),
            bytes(value.contentHash()),
            value.createdAt());
    }

    /**
     * 将数据库 Price Version 行转换为领域模型。
     */
    private PriceTableVersion priceVersion(PriceVersionRow row) {
        return new PriceTableVersion(
            row.id(),
            new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()),
            row.priceTableId(),
            row.versionNumber(),
            row.currency(),
            row.effectiveFrom(),
            decimalMap(row.entriesJson()),
            checksum(row.contentHash()),
            row.createdAt());
    }

    /**
     * 将 Usage 领域模型转换为数据库行。
     */
    private UsageRow usageRow(UsageLedgerEntry value) {
        return new UsageRow(
            value.id(),
            value.sourcePlane(),
            value.sourceRecordId(),
            value.organizationId().value(),
            value.projectId().value(),
            value.agentId().map(AgentId::value).orElse(null),
            value.revisionId().map(RevisionId::value).orElse(null),
            value.deploymentId().map(DeploymentId::value).orElse(null),
            value.sessionId().map(SessionId::value).orElse(null),
            value.turnId().map(TurnId::value).orElse(null),
            value.runId().map(RunId::value).orElse(null),
            value.usageType().name(),
            value.provider(),
            value.model().orElse(null),
            value.tool().orElse(null),
            value.inputTokens(),
            value.outputTokens(),
            value.cachedTokens(),
            value.embeddingTokens(),
            value.toolCalls(),
            value.sandboxDurationMs(),
            value.estimated(),
            value.priceTableVersionId().orElse(null),
            value.currency().orElse(null),
            value.costAmount(),
            value.occurredAt(),
            value.ingestedAt());
    }

    /**
     * 将数据库 Usage 行转换为领域模型。
     */
    private UsageLedgerEntry usage(UsageRow row) {
        return new UsageLedgerEntry(
            row.id(),
            row.sourcePlane(),
            row.sourceRecordId(),
            new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()),
            Optional.ofNullable(row.agentId()).map(AgentId::new),
            Optional.ofNullable(row.revisionId()).map(RevisionId::new),
            Optional.ofNullable(row.deploymentId()).map(DeploymentId::new),
            Optional.ofNullable(row.sessionId()).map(SessionId::new),
            Optional.ofNullable(row.turnId()).map(TurnId::new),
            Optional.ofNullable(row.runId()).map(RunId::new),
            UsageType.valueOf(row.usageType()),
            row.provider(),
            Optional.ofNullable(row.model()),
            Optional.ofNullable(row.tool()),
            row.inputTokens(),
            row.outputTokens(),
            row.cachedTokens(),
            row.embeddingTokens(),
            row.toolCalls(),
            row.sandboxDurationMs(),
            row.estimated(),
            Optional.ofNullable(row.priceTableVersionId()),
            Optional.ofNullable(row.currency()),
            row.costAmount(),
            row.occurredAt(),
            row.ingestedAt());
    }

    /**
     * 将 Quota Policy 转换为数据库行。
     */
    private QuotaPolicyRow quotaPolicyRow(QuotaPolicy value, Instant createdAt) {
        return new QuotaPolicyRow(
            value.id(),
            value.organizationId().value(),
            value.projectId().value(),
            value.scopeType().name(),
            value.scopeRef(),
            value.metric().name(),
            value.enforcement().name(),
            value.limitValue(),
            value.windowSeconds().orElse(null),
            value.budgetAction().name(),
            value.effectiveFrom(),
            value.effectiveUntil().orElse(null),
            value.status(),
            value.version(),
            createdAt);
    }

    /**
     * 将数据库 Quota Policy 行转换为领域模型。
     */
    private QuotaPolicy quotaPolicy(QuotaPolicyRow row) {
        return new QuotaPolicy(
            row.id(),
            new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()),
            QuotaScopeType.valueOf(row.scopeType()),
            row.scopeRef(),
            QuotaMetric.valueOf(row.metric()),
            QuotaEnforcement.valueOf(row.enforcement()),
            row.limitValue(),
            Optional.ofNullable(row.windowSeconds()),
            BudgetAction.valueOf(row.budgetAction()),
            row.effectiveFrom(),
            Optional.ofNullable(row.effectiveUntil()),
            row.status(),
            row.version());
    }

    /**
     * 将数据库 Dataset Version 行转换为领域模型。
     */
    private DatasetVersion datasetVersion(DatasetVersionRow row) {
        return new DatasetVersion(
            row.id(),
            row.datasetId(),
            row.versionNumber(),
            map(row.schemaJson()),
            checksum(row.contentHash()));
    }

    /**
     * 将数据库 Test Case 行转换为领域模型。
     */
    private EvaluationTestCase testCase(TestCaseRow row) {
        return new EvaluationTestCase(
            row.id(),
            row.datasetVersionId(),
            row.caseKey(),
            row.inputObjectUri(),
            checksum(row.inputContentHash()),
            map(row.expectedJson()),
            checksum(row.expectedContentHash()),
            row.weight());
    }

    /**
     * 将数据库 Evaluator Version 行转换为领域模型。
     */
    private EvaluatorVersion evaluatorVersion(EvaluatorVersionRow row) {
        return new EvaluatorVersion(
            row.id(),
            row.evaluatorId(),
            row.versionNumber(),
            EvaluatorType.valueOf(row.evaluatorType()),
            map(row.configJson()),
            checksum(row.contentHash()));
    }

    /**
     * 将数据库 Evaluation Run 行转换为领域模型。
     */
    private EvaluationRun evaluationRun(EvaluationRunRow row) {
        return new EvaluationRun(
            row.id(),
            new RevisionId(row.candidateRevisionId()),
            new SnapshotId(row.candidateSnapshotId()),
            row.datasetVersionId(),
            row.evaluatorVersionId(),
            row.provider(),
            Optional.ofNullable(row.model()),
            row.threshold(),
            Optional.ofNullable(row.baselineRunId()),
            EvaluationStatus.valueOf(row.status()),
            Optional.ofNullable(row.totalScore()),
            Optional.ofNullable(row.regressionDelta()),
            row.createdAt(),
            Optional.ofNullable(row.completedAt()));
    }

    /**
     * 将 Release Gate 领域模型转换为数据库行。
     */
    private ReleaseGateRow releaseGateRow(ReleaseGate value) {
        return new ReleaseGateRow(
            value.id(),
            value.organizationId().value(),
            value.projectId().value(),
            value.agentId().value(),
            value.environmentId().map(EnvironmentId::value).orElse(null),
            value.datasetVersionId(),
            value.evaluatorVersionId(),
            value.threshold(),
            value.enforcement().name(),
            value.status(),
            value.version());
    }

    /**
     * 将数据库 Release Gate 行转换为领域模型。
     */
    private ReleaseGate releaseGate(ReleaseGateRow row) {
        return new ReleaseGate(
            row.id(),
            new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()),
            new AgentId(row.agentId()),
            Optional.ofNullable(row.environmentId()).map(EnvironmentId::new),
            row.datasetVersionId(),
            row.evaluatorVersionId(),
            row.threshold(),
            QuotaEnforcement.valueOf(row.enforcement()),
            row.status(),
            row.version());
    }

    /**
     * 将对象序列化为合法 JSON。
     */
    private String json(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    /**
     * 将 JSON 解析为普通 Map。
     */
    private Map<String, Object> map(String value) {
        return jsonMapper.readValue(value, new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * 将价格 JSON 解析为 BigDecimal Map。
     */
    private Map<String, BigDecimal> decimalMap(String value) {
        return jsonMapper.readValue(value, new TypeReference<Map<String, BigDecimal>>() {
        });
    }

    /**
     * 将 Checksum 转换为数据库 32 字节摘要。
     */
    private byte[] bytes(Checksum value) {
        return HexFormat.of().parseHex(value.hex());
    }

    /**
     * 将数据库 32 字节摘要转换为 Checksum。
     */
    private Checksum checksum(byte[] value) {
        return new Checksum("sha256:" + HexFormat.of().formatHex(value));
    }
}
