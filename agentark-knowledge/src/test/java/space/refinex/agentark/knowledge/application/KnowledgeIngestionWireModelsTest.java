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
import space.refinex.agentark.knowledge.adapter.contract.KnowledgeIngestionWireModels.IngestionPlanView;
import space.refinex.agentark.knowledge.adapter.contract.KnowledgeIngestionWireModels.IngestionResultView;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionCommand;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionPlan;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionResult;
import space.refinex.agentark.knowledge.domain.KnowledgeRevisionStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证摄取计划与结果通过显式 Wire DTO 往返后不丢失固定范围或幂等字段。
 *
 * @author refinex
 */
class KnowledgeIngestionWireModelsTest {

    /** 验证计划和成功结果的语言中立 DTO 可无损还原领域值。 */
    @Test
    void roundTripsPlanAndResultWithoutLeakingKernelRecordShapes() {
        Phase14Fixtures.Fixture fixture = Phase14Fixtures.create(
            KnowledgeRevisionStatus.INGESTING);
        IngestionPlan plan = Phase14Fixtures.plan(fixture);
        IngestionCommand command = Phase14Fixtures.command(fixture);
        IngestionResult result = Phase14Fixtures.success(fixture, command);

        assertThat(IngestionPlanView.from(plan).toDomain()).isEqualTo(plan);
        assertThat(IngestionResultView.from(result).toDomain()).isEqualTo(result);
        assertThat(IngestionPlanView.from(plan).embeddingProfile().credentialSecretRef()).isNull();
    }
}
