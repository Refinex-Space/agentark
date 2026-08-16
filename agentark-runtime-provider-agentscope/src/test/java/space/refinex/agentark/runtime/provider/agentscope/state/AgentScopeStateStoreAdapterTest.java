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

package space.refinex.agentark.runtime.provider.agentscope.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.state.AgentState;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import space.refinex.agentark.runtime.domain.RuntimeModels.AgentStateVersion;
import space.refinex.agentark.runtime.domain.RuntimeModels.Checkpoint;
import space.refinex.agentark.runtime.port.CheckpointStore;
import space.refinex.agentark.runtime.provider.agentscope.ProviderTestFixtures;
import space.refinex.agentark.runtime.provider.agentscope.error.AgentScopeProviderException;

/**
 * 验证 AgentScope State 仅通过 AgentArk 版本端口追加、提交和建立 Checkpoint。
 *
 * @author refinex
 */
class AgentScopeStateStoreAdapterTest {

    /** 验证主 Agent State 可往返恢复且 Checkpoint 只引用已提交版本。 */
    @Test
    void persistsAndRestoresCommittedAgentState() {
        var descriptor = ProviderTestFixtures.snapshot(new ObjectMapper());
        var session = ProviderTestFixtures.session(descriptor);
        var run = ProviderTestFixtures.run(session);
        var stateStore = mock(space.refinex.agentark.runtime.port.AgentStateStore.class);
        var checkpointStore = mock(CheckpointStore.class);
        AtomicReference<AgentStateVersion> pending = new AtomicReference<>();
        AtomicReference<AgentStateVersion> committed = new AtomicReference<>();
        when(checkpointStore.findLatestRecoverable(run.id())).thenReturn(Optional.empty());
        when(stateStore.findLatestCommitted(
            any(), anyString(), anyString(), anyInt())).thenAnswer(invocation ->
                Optional.ofNullable(committed.get()));
        doAnswer(invocation -> {
            pending.set(invocation.getArgument(0));
            return null;
        }).when(stateStore).append(any(AgentStateVersion.class));
        doAnswer(invocation -> {
            AgentStateVersion value = pending.get();
            committed.set(new AgentStateVersion(
                value.id(), value.organizationId(), value.projectId(), value.sessionId(),
                value.runId(), value.agentKey(), value.stateKey(), value.itemIndex(),
                value.stateVersion(), value.payload(), value.contentHash(), true,
                value.fencingToken(), value.createdAt()));
            return null;
        }).when(stateStore).commit(any(AgentStateVersion.class), any());
        AgentScopeStateStoreAdapter adapter = new AgentScopeStateStoreAdapter(
            session, run, "review-agent", stateStore, checkpointStore,
            Clock.fixed(ProviderTestFixtures.NOW, ZoneOffset.UTC));
        AgentState original = AgentState.builder()
            .sessionId(session.id().asString())
            .userId(session.projectId().asString())
            .summary("recoverable")
            .build();

        adapter.save(
            session.projectId().asString(), session.id().asString(), "agent_state", original);
        AgentState restored = adapter.get(
            session.projectId().asString(), session.id().asString(),
            "agent_state", AgentState.class).orElseThrow();

        assertThat(restored.getSummary()).isEqualTo("recoverable");
        assertThat(committed.get().committed()).isTrue();
        verify(checkpointStore).append(any(Checkpoint.class));
    }

    /** 验证适配器不允许 AgentScope 跨越构造时绑定的 Session。 */
    @Test
    void rejectsCrossSessionStateAccess() {
        var descriptor = ProviderTestFixtures.snapshot(new ObjectMapper());
        var session = ProviderTestFixtures.session(descriptor);
        var run = ProviderTestFixtures.run(session);
        var stateStore = mock(space.refinex.agentark.runtime.port.AgentStateStore.class);
        var checkpointStore = mock(CheckpointStore.class);
        when(checkpointStore.findLatestRecoverable(run.id())).thenReturn(Optional.empty());
        AgentScopeStateStoreAdapter adapter = new AgentScopeStateStoreAdapter(
            session, run, "review-agent", stateStore, checkpointStore,
            Clock.fixed(ProviderTestFixtures.NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> adapter.exists(
            session.projectId().asString(), "another-session"))
            .isInstanceOf(AgentScopeProviderException.class)
            .hasMessageContaining("crossed the bound session");
        assertThatThrownBy(() -> adapter.exists("another-project", session.id().asString()))
            .isInstanceOf(AgentScopeProviderException.class)
            .hasMessageContaining("crossed the bound project");
        assertThat(adapter.exists(null, session.id().asString())).isFalse();
    }
}
