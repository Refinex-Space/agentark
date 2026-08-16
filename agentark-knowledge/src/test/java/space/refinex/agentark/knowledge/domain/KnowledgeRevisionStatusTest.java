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

package space.refinex.agentark.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import space.refinex.agentark.kernel.id.ChunkProfileId;
import space.refinex.agentark.kernel.id.DocumentRevisionId;
import space.refinex.agentark.kernel.id.EmbeddingProfileId;
import space.refinex.agentark.kernel.id.KnowledgeBaseId;
import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ParserProfileId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.RetrievalProfileId;
import space.refinex.agentark.kernel.ref.Checksum;

/**
 * 验证 Knowledge Revision 状态机、失败代码约束和 READY 引用条件。
 *
 * @author refinex
 */
class KnowledgeRevisionStatusTest {

    /** 创建状态机测试实例。 */
    KnowledgeRevisionStatusTest() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /** 证明合法摄取路径只能按 CREATED、INGESTING、VERIFYING、READY 顺序推进。 */
    @Test
    void followsTheOnlyReadyPath() {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        KnowledgeRevision created = revision(now);

        KnowledgeRevision ingesting = created.transitionTo(
            KnowledgeRevisionStatus.INGESTING, "", now.plusSeconds(1));
        KnowledgeRevision verifying = ingesting.transitionTo(
            KnowledgeRevisionStatus.VERIFYING, "", now.plusSeconds(2));
        KnowledgeRevision ready = verifying.transitionTo(
            KnowledgeRevisionStatus.READY, "", now.plusSeconds(3));

        assertThat(created.isReferenceable()).isFalse();
        assertThat(ready.isReferenceable()).isTrue();
        assertThat(ready.version()).isEqualTo(3);
        assertThat(ready.documentRevisionIds()).isEqualTo(created.documentRevisionIds());
    }

    /** 证明跳过验证、FAILED 无失败码和 DELETED 复活均被拒绝。 */
    @Test
    void rejectsInvalidTransitionsAndFailureMetadata() {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        KnowledgeRevision created = revision(now);

        assertThatThrownBy(() -> created.transitionTo(
            KnowledgeRevisionStatus.READY, "", now.plusSeconds(1)))
            .isInstanceOf(IllegalStateException.class);
        KnowledgeRevision ingesting = created.transitionTo(
            KnowledgeRevisionStatus.INGESTING, "", now.plusSeconds(1));
        assertThatThrownBy(() -> ingesting.transitionTo(
            KnowledgeRevisionStatus.FAILED, "", now.plusSeconds(2)))
            .isInstanceOf(IllegalArgumentException.class);
        KnowledgeRevision deleted = created
            .transitionTo(KnowledgeRevisionStatus.DELETING, "", now.plusSeconds(1))
            .transitionTo(KnowledgeRevisionStatus.DELETED, "", now.plusSeconds(2));
        assertThatThrownBy(() -> deleted.transitionTo(
            KnowledgeRevisionStatus.CREATED, "", now.plusSeconds(3)))
            .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 创建最小 CREATED Revision Fixture。
     *
     * @param now 创建时间
     * @return CREATED Revision
     */
    private static KnowledgeRevision revision(Instant now) {
        return new KnowledgeRevision(
            KnowledgeRevisionId.generate(), OrganizationId.generate(), ProjectId.generate(),
            KnowledgeBaseId.generate(), 1, List.of(DocumentRevisionId.generate()),
            ParserProfileId.generate(), ChunkProfileId.generate(), EmbeddingProfileId.generate(),
            RetrievalProfileId.generate(), Checksum.sha256("revision"),
            KnowledgeRevisionStatus.CREATED, "", 0, now, now);
    }
}
