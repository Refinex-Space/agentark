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

package space.refinex.agentark.knowledge.application;

import org.junit.jupiter.api.Test;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionCommand;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionResult;
import space.refinex.agentark.knowledge.domain.KnowledgeRevisionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Control 结果事务、READY 门禁、幂等重放和冲突语义。
 *
 * @author refinex
 */
class KnowledgeIngestionControlServiceTest {

    /**
     * 验证成功结果推进 READY 并写入一次 Outbox，完全相同重放不重复写入。
     */
    @Test
    void acceptsVerifiedResultAtomicallyAndReplaysIdempotently() {
        Phase14Fixtures.Fixture fixture = Phase14Fixtures.create(
            KnowledgeRevisionStatus.INGESTING);
        KnowledgeIngestionControlService service = service(fixture);
        IngestionCommand command = Phase14Fixtures.command(fixture);
        IngestionResult result = Phase14Fixtures.success(fixture, command);

        assertThat(service.loadPlan(
            Phase14Fixtures.servicePrincipal(), fixture.request().id()).revision().id())
            .isEqualTo(fixture.revision().id());
        assertThat(service.acceptResult(Phase14Fixtures.servicePrincipal(), result))
            .isEqualTo(result);
        assertThat(fixture.repository().findKnowledgeRevision(
            fixture.projectId(), fixture.revision().id()).orElseThrow().status())
            .isEqualTo(KnowledgeRevisionStatus.READY);
        assertThat(fixture.resultRepository().outboxCount()).isEqualTo(1);

        assertThat(service.acceptResult(Phase14Fixtures.servicePrincipal(), result))
            .isEqualTo(result);
        assertThat(fixture.resultRepository().outboxCount()).isEqualTo(1);
    }

    /**
     * 验证同一幂等键绑定不同 Attempt 内容时返回冲突。
     */
    @Test
    void rejectsDifferentResultForSameIdempotencyKey() {
        Phase14Fixtures.Fixture fixture = Phase14Fixtures.create(
            KnowledgeRevisionStatus.INGESTING);
        KnowledgeIngestionControlService service = service(fixture);
        IngestionCommand command = Phase14Fixtures.command(fixture);
        IngestionResult first = Phase14Fixtures.success(fixture, command);
        service.acceptResult(Phase14Fixtures.servicePrincipal(), first);
        IngestionResult different = new IngestionResult(
            JobId.generate().value(), first.requestId(), first.organizationId(), first.projectId(),
            first.revisionId(), first.schedulerJobId(), first.attemptId(), first.idempotencyKey(),
            first.documentCount(), first.chunkCount(), first.checksum(), first.artifacts(),
            first.status(), first.failureCode(), first.completedAt());

        assertThatThrownBy(() -> service.acceptResult(
            Phase14Fixtures.servicePrincipal(), different))
            .isInstanceOf(KnowledgeConflictException.class);
    }

    /**
     * 创建固定时钟 Control 服务。
     *
     * @param fixture 测试夹具
     * @return Control 服务
     */
    private KnowledgeIngestionControlService service(Phase14Fixtures.Fixture fixture) {
        return new KnowledgeIngestionControlService(
            fixture.repository(), fixture.resultRepository(), JsonMapper.builder().build(),
            Clock.fixed(fixture.now(), ZoneOffset.UTC));
    }
}
