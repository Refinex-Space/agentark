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

package space.refinex.agentark.scheduling.application;

import org.junit.jupiter.api.Test;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;
import space.refinex.agentark.scheduling.port.SchedulerAuditPort;
import space.refinex.agentark.scheduling.port.SchedulerRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Scheduler 管理命令的租户边界、Redrive 事务调用和审计事实。
 *
 * @author refinex
 */
class SchedulerApplicationServiceTest {

    /** 固定测试时间。 */
    private static final Instant NOW = Instant.parse("2026-08-16T10:00:00Z");

    /** 创建应用服务测试实例。 */
    SchedulerApplicationServiceTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明 Redrive 只操作匹配租户的 Job，并且审计调用不可省略。 */
    @Test
    void redrivesTenantJobAndRecordsAudit() {
        SchedulerRepository repository = mock(SchedulerRepository.class);
        SchedulerAuditPort auditPort = mock(SchedulerAuditPort.class);
        OrganizationId organizationId = OrganizationId.generate();
        ProjectId projectId = ProjectId.generate();
        Job job = deadLetteredJob(organizationId, projectId);
        when(repository.find(job.id())).thenReturn(Optional.of(job));
        SchedulerApplicationService service = new SchedulerApplicationService(
            repository, auditPort, Clock.fixed(NOW, ZoneOffset.UTC));

        service.redrive(
            organizationId, projectId, job.id(), "issuer:subject", "人工核验后重放");

        verify(repository).redrive(
            eq(job.id()), eq(organizationId), eq(projectId), eq("issuer:subject"),
            eq("人工核验后重放"), any(SchedulerOutbox.class), eq(NOW));
        verify(auditPort).record(
            "scheduler.dead-letter.redrive", "issuer:subject", organizationId,
            projectId, job.id(), "人工核验后重放", NOW);
    }

    /**
     * 创建已进入 Dead Letter 的固定 Job。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @return Dead Letter Job
     */
    private static Job deadLetteredJob(
        OrganizationId organizationId, ProjectId projectId) {
        RetryPolicy retryPolicy = new RetryPolicy(
            3, Duration.ofSeconds(1), Duration.ofSeconds(30), 2.0, 0.1,
            Duration.ofMinutes(1));
        return new Job(
            JobId.generate(), organizationId, projectId, JobType.OUTBOUND_WEBHOOK,
            "redrive-fixture", "{}", Checksum.sha256("{}"),
            JobStatus.DEAD_LETTERED, 0, NOW, retryPolicy,
            IdempotencyCapability.PROVIDER_KEY, 3, 3, NOW, NOW);
    }
}
