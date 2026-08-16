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
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.knowledge.application.IngestionModels.*;
import space.refinex.agentark.knowledge.application.KnowledgeIngestionWorker;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;
import space.refinex.agentark.scheduling.domain.SchedulerUuidV7;
import space.refinex.agentark.scheduling.port.JobHandler.HandlerResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证 Scheduler Knowledge Handler 使用 Job/Attempt 派生稳定结果命令且不写 Control DB。
 *
 * @author refinex
 */
class KnowledgeIngestionJobHandlerTest {

    /** 固定测试时间。 */
    private static final Instant NOW = Instant.parse("2026-08-16T10:00:00Z");

    /** 创建 Knowledge Handler 测试实例。 */
    KnowledgeIngestionJobHandlerTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明成功摄取结果映射为稳定结果引用，并保留当前 Job/Attempt 身份。 */
    @Test
    void mapsSuccessfulIngestionResult() {
        KnowledgeIngestionWorker worker = mock(KnowledgeIngestionWorker.class);
        ClaimedJob claim = claim();
        when(worker.execute(any())).thenAnswer(invocation -> {
            IngestionCommand command = invocation.getArgument(0);
            Checksum checksum = Checksum.sha256("manifest");
            IngestionResult result = new IngestionResult(
                SchedulerUuidV7.generate(NOW), command.requestId(), command.organizationId(),
                command.projectId(), command.revisionId(), command.schedulerJobId(),
                command.attemptId(), command.idempotencyKey(), 1, 1, checksum,
                List.of(new ArtifactReference(
                    ArtifactKind.CHUNKS,
                    ObjectRef.of("object://knowledge/chunks", checksum, 8, "application/json"))),
                ResultStatus.SUCCEEDED, "", NOW);
            return CompletableFuture.completedFuture(result);
        });
        KnowledgeIngestionJobHandler handler = new KnowledgeIngestionJobHandler(
            worker, JsonMapper.builder().build());

        HandlerResult result = handler.handle(claim).toCompletableFuture().join();

        assertThat(result.successful()).isTrue();
        assertThat(result.resultRef()).hasValueSatisfying(value ->
            assertThat(value).startsWith("knowledge-result:"));
        verify(worker).execute(argThat(command ->
            command.schedulerJobId().equals(claim.job().id())
                && command.attemptId().equals(claim.attemptId())
                && command.idempotencyKey().endsWith(":1")));
    }

    /**
     * 创建包含固定摄取请求和 Revision 的 Claim。
     *
     * @return Knowledge 摄取 Claim
     */
    private ClaimedJob claim() {
        IngestionRequestId requestId = IngestionRequestId.generate();
        KnowledgeRevisionId revisionId = KnowledgeRevisionId.generate();
        String payload = "{\"requestId\":\"" + requestId.asString()
            + "\",\"knowledgeRevisionId\":\"" + revisionId.asString() + "\"}";
        Job job = new Job(
            JobId.generate(), OrganizationId.generate(), ProjectId.generate(),
            JobType.KNOWLEDGE_INGESTION, "knowledge-fixture", payload,
            Checksum.sha256(payload), JobStatus.CLAIMED, 0, NOW,
            new RetryPolicy(
                3, Duration.ofSeconds(1), Duration.ofSeconds(10), 2.0, 0.0,
                Duration.ofMinutes(5)),
            IdempotencyCapability.PROVIDER_KEY, 1, 1, NOW, NOW);
        return new ClaimedJob(
            job, SchedulerUuidV7.generate(NOW), 1, "scheduler-test", 1,
            NOW.plusSeconds(30));
    }
}
