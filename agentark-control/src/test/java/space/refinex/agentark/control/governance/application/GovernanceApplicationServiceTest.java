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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import space.refinex.agentark.control.governance.application.GovernanceCommands.EvaluationRunCommand;
import space.refinex.agentark.control.governance.application.port.GovernanceRepository;
import space.refinex.agentark.control.governance.domain.GovernanceModels.*;
import space.refinex.agentark.control.iam.application.IamAuthorizationService;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.iam.domain.IamStatus;
import space.refinex.agentark.control.iam.domain.Project;
import space.refinex.agentark.control.release.application.port.ReleaseRepository;
import space.refinex.agentark.control.release.domain.ReleaseModels.AgentRevision;
import space.refinex.agentark.control.release.domain.ReleaseModels.StoredSnapshot;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.foundation.security.TenantSelection;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证 Governance 应用服务固定版本 Evaluation 的确定性评分与持久化边界。
 *
 * @author refinex
 */
class GovernanceApplicationServiceTest {

    /**
     * 创建治理应用服务测试实例。
     */
    GovernanceApplicationServiceTest() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /**
     * 证明 Evaluation 同时固定 Revision、Snapshot、Dataset、Evaluator，并按权重确定评分。
     */
    @Test
    void evaluatesFixedVersionsDeterministically() {
        Instant now = Instant.parse("2026-08-17T10:00:00Z");
        OrganizationId organizationId = OrganizationId.generate();
        ProjectId projectId = ProjectId.generate();
        Project project = new Project(
            projectId, organizationId, "governance-test", "治理测试", IamStatus.ACTIVE,
            0, now, now);
        RevisionId revisionId = RevisionId.generate();
        SnapshotId snapshotId = SnapshotId.generate();
        AgentRevision revision = new AgentRevision(
            revisionId, organizationId, projectId, AgentId.generate(), snapshotId, 1, 1,
            "agentscope-java-2", Checksum.sha256("snapshot"), List.of("streaming"), now);
        UUID datasetId = EventId.generate().value();
        UUID datasetVersionId = EventId.generate().value();
        UUID evaluatorId = EventId.generate().value();
        UUID evaluatorVersionId = EventId.generate().value();
        Checksum expectedAlpha = Checksum.sha256("alpha");
        Checksum expectedBeta = Checksum.sha256("beta");
        EvaluationTestCase alpha = new EvaluationTestCase(
            EventId.generate().value(), datasetVersionId, "alpha", "object://evaluation/alpha",
            Checksum.sha256("input-alpha"), Map.of("kind", "exact"), expectedAlpha,
            BigDecimal.ONE);
        EvaluationTestCase beta = new EvaluationTestCase(
            EventId.generate().value(), datasetVersionId, "beta", "object://evaluation/beta",
            Checksum.sha256("input-beta"), Map.of("kind", "exact"), expectedBeta,
            BigDecimal.valueOf(3));

        GovernanceRepository repository = mock(GovernanceRepository.class);
        TenantCatalogRepository tenantRepository = mock(TenantCatalogRepository.class);
        IamAuthorizationService authorizationService = mock(IamAuthorizationService.class);
        ReleaseRepository releaseRepository = mock(ReleaseRepository.class);
        when(tenantRepository.findProject(projectId)).thenReturn(Optional.of(project));
        when(releaseRepository.findSnapshot(projectId, revisionId))
            .thenReturn(Optional.of(new StoredSnapshot(revision, "{\"schemaVersion\":1}")));
        when(repository.findDatasetVersion(projectId, datasetVersionId)).thenReturn(Optional.of(
            new DatasetVersion(
                datasetVersionId, datasetId, 1, Map.of("type", "object"),
                Checksum.sha256("dataset"))));
        when(repository.findEvaluatorVersion(projectId, evaluatorVersionId)).thenReturn(Optional.of(
            new EvaluatorVersion(
                evaluatorVersionId, evaluatorId, 1, EvaluatorType.DETERMINISTIC,
                Map.of("metric", "exact_match"), Checksum.sha256("evaluator"))));
        when(repository.listTestCases(projectId, datasetVersionId))
            .thenReturn(List.of(alpha, beta));
        GovernanceApplicationService service = new GovernanceApplicationService(
            repository, tenantRepository, authorizationService, releaseRepository,
            Clock.fixed(now, ZoneOffset.UTC));
        AgentArkPrincipal principal = new AgentArkPrincipal(
            "https://issuer.agentark.test", "governance-user", PrincipalType.USER,
            Set.of("evaluation:manage"), Optional.of(new TenantSelection(
                organizationId, Optional.of(projectId), Optional.empty())), Optional.empty());

        EvaluationRun run = service.evaluateDeterministically(
            principal,
            projectId,
            new EvaluationRunCommand(
                revisionId.asString(), datasetVersionId.toString(),
                evaluatorVersionId.toString(), BigDecimal.valueOf(0.5), Optional.empty(),
                Map.of(
                    "alpha", expectedAlpha,
                    "beta", Checksum.sha256("not-beta"))));

        assertThat(run.candidateRevisionId()).isEqualTo(revisionId);
        assertThat(run.candidateSnapshotId()).isEqualTo(snapshotId);
        assertThat(run.datasetVersionId()).isEqualTo(datasetVersionId);
        assertThat(run.evaluatorVersionId()).isEqualTo(evaluatorVersionId);
        assertThat(run.totalScore()).contains(BigDecimal.valueOf(0.25));
        assertThat(run.status()).isEqualTo(EvaluationStatus.FAILED);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvaluationScore>> scores = ArgumentCaptor.forClass(List.class);
        verify(repository).insertEvaluationRun(
            eq(run), scores.capture(), eq(organizationId), eq(projectId),
            eq("https://issuer.agentark.test:governance-user"));
        assertThat(scores.getValue())
            .extracting(EvaluationScore::passed)
            .containsExactly(true, false);
        verify(repository).appendAudit(argThat(event ->
            event.action().equals("evaluation.run.complete")
                && event.diffSummary().get("status").equals("FAILED")));
    }
}
