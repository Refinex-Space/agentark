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

package space.refinex.agentark.runtime.provider.agentscope.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.runtime.domain.RuntimeModels.Session;
import space.refinex.agentark.runtime.port.AgentStateStore;
import space.refinex.agentark.runtime.port.CheckpointStore;
import space.refinex.agentark.runtime.provider.agentscope.ProviderTestFixtures;
import space.refinex.agentark.runtime.provider.agentscope.RuntimeProviderDescriptor;
import space.refinex.agentark.runtime.provider.agentscope.error.AgentScopeProviderException;
import space.refinex.agentark.runtime.provider.agentscope.error.ProviderErrorCode;
import space.refinex.agentark.runtime.provider.agentscope.prompt.PromptMapper;

/**
 * 验证 RuntimeHandle 物化阶段按需解析 Secret 且缺失时不会创建 AgentScope 组件。
 *
 * @author refinex
 */
class AgentScopeRuntimeMaterializerTest {

    /** 验证缺失 SecretRef 解析结果被稳定分类。 */
    @Test
    void rejectsMissingSecretBeforeModelCreation() {
        ObjectMapper objectMapper = new ObjectMapper();
        var snapshot = ProviderTestFixtures.snapshot(objectMapper);
        var session = ProviderTestFixtures.session(snapshot);
        var run = ProviderTestFixtures.run(session);
        AgentScopeRuntimeMaterializer materializer = materializer(objectMapper);

        assertThatThrownBy(() -> materializer.materialize(session, run, snapshot))
            .isInstanceOfSatisfying(AgentScopeProviderException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ProviderErrorCode.SECRET_UNAVAILABLE));
    }

    /** 验证 Session 租户与 Snapshot 不一致时在 Secret 解析前失败。 */
    @Test
    void rejectsCrossTenantSnapshotMaterialization() {
        ObjectMapper objectMapper = new ObjectMapper();
        var snapshot = ProviderTestFixtures.snapshot(objectMapper);
        var original = ProviderTestFixtures.session(snapshot);
        Session mismatched = new Session(
            original.id(), original.organizationId(), ProjectId.generate(), original.deploymentId(),
            original.revisionId(), original.snapshotId(), original.snapshotHash(),
            original.participantMetadata(), original.channelMetadata(), original.status(),
            original.eventSequence(), original.version(), original.createdAt(), original.updatedAt());
        var run = ProviderTestFixtures.run(mismatched);

        assertThatThrownBy(() -> materializer(objectMapper).materialize(mismatched, run, snapshot))
            .isInstanceOfSatisfying(AgentScopeProviderException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ProviderErrorCode.SNAPSHOT_INVALID));
    }

    /**
     * 创建在 Secret 解析后立即返回缺失结果的物化器。
     *
     * @param objectMapper Jackson 2 映射器
     * @return 不会创建 Model 的物化器
     */
    private AgentScopeRuntimeMaterializer materializer(ObjectMapper objectMapper) {
        return new AgentScopeRuntimeMaterializer(
            new AgentScopeSnapshotCompiler(
                RuntimeProviderDescriptor.current(), objectMapper, new SnapshotCompilationCache()),
            (binding, secret) -> {
                throw new AssertionError("model factory must not run without a Secret");
            },
            mock(AgentScopeRuntimeComponentFactory.class),
            (reference, policy) -> null,
            mock(AgentStateStore.class),
            mock(CheckpointStore.class),
            new PromptMapper(),
            Clock.fixed(ProviderTestFixtures.NOW, ZoneOffset.UTC));
    }
}
