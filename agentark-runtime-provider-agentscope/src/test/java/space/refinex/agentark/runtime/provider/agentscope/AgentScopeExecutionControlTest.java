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

package space.refinex.agentark.runtime.provider.agentscope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import space.refinex.agentark.kernel.id.ApprovalId;
import space.refinex.agentark.runtime.application.RuntimeCommands.ResumeCommand;
import space.refinex.agentark.runtime.application.RuntimeCommands.CancellationCommand;
import space.refinex.agentark.runtime.domain.RuntimeModels.ExecutionOutcome;
import space.refinex.agentark.runtime.domain.RuntimeModels.FencingToken;
import space.refinex.agentark.runtime.provider.agentscope.compiler.AgentScopeCompilationPlan;
import space.refinex.agentark.runtime.provider.agentscope.compiler.AgentScopeRuntimeMaterializer;
import space.refinex.agentark.runtime.provider.agentscope.error.AgentScopeProviderException;
import space.refinex.agentark.runtime.provider.agentscope.event.AgentScopeEventMapper;
import space.refinex.agentark.runtime.provider.agentscope.model.RuntimeHandle;
import space.refinex.agentark.runtime.provider.agentscope.model.RuntimeInputMapper;

/**
 * 验证 Approval Resume 只提交与参数 Hash 绑定的 AgentScope ConfirmResult。
 *
 * @author refinex
 */
class AgentScopeExecutionControlTest {

    /** 验证输入映射在 Event Stream 前失败时会移除并关闭已注册 Handle。 */
    @Test
    void closesHandleWhenExecutionCannotStart() {
        ObjectMapper objectMapper = new ObjectMapper();
        var snapshot = ProviderTestFixtures.snapshot(objectMapper);
        var session = ProviderTestFixtures.session(snapshot);
        var run = ProviderTestFixtures.run(session);
        AgentScopeRuntimeMaterializer materializer = mock(AgentScopeRuntimeMaterializer.class);
        RuntimeHandle handle = mock(RuntimeHandle.class);
        when(handle.seedMessages()).thenReturn(List.of());
        when(materializer.materialize(session, run, snapshot)).thenReturn(handle);
        AgentScopeExecutionEngine engine = new AgentScopeExecutionEngine(
            materializer,
            new AgentScopeEventMapper(objectMapper),
            new RuntimeInputMapper(objectMapper),
            (currentSession, currentRun, signal) -> { });

        var result = engine.execute(
            session, run, snapshot,
            space.refinex.agentark.runtime.domain.RuntimeModels.RuntimePayload.inline("not-json"));

        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.FAILED);
        verify(handle).close();
    }

    /** 验证取消只中断目标 Run 的 RuntimeContext 并形成稳定取消结果。 */
    @Test
    void cancelsOnlyTheTargetActiveRun() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        var snapshot = ProviderTestFixtures.snapshot(objectMapper);
        var session = ProviderTestFixtures.session(snapshot);
        var run = ProviderTestFixtures.run(session);
        AgentScopeRuntimeMaterializer materializer = mock(AgentScopeRuntimeMaterializer.class);
        RuntimeHandle handle = mock(RuntimeHandle.class);
        HarnessAgent agent = mock(HarnessAgent.class);
        ReActAgent delegate = mock(ReActAgent.class);
        RuntimeContext context = RuntimeContext.builder()
            .userId(session.projectId().asString())
            .sessionId(session.id().asString())
            .build();
        AgentScopeCompilationPlan plan = mock(AgentScopeCompilationPlan.class);
        when(plan.turnTimeout()).thenReturn(Duration.ofSeconds(5));
        when(handle.agent()).thenReturn(agent);
        when(handle.context()).thenReturn(context);
        when(handle.plan()).thenReturn(plan);
        when(handle.fencingToken()).thenReturn(run.fencingToken());
        when(handle.seedMessages()).thenReturn(List.of());
        when(agent.getDelegate()).thenReturn(delegate);
        when(materializer.materialize(session, run, snapshot)).thenReturn(handle);
        AtomicBoolean cancelled = new AtomicBoolean();
        doAnswer(invocation -> {
            cancelled.set(true);
            return null;
        }).when(handle).requestCancellation();
        when(handle.isCancellationRequested()).thenAnswer(invocation -> cancelled.get());
        CountDownLatch subscribed = new CountDownLatch(1);
        Sinks.Many<AgentEvent> events = Sinks.many().unicast().onBackpressureBuffer();
        when(agent.streamEvents(anyList(), any(RuntimeContext.class)))
            .thenReturn(events.asFlux().doOnSubscribe(ignored -> subscribed.countDown()));
        AgentScopeExecutionEngine engine = new AgentScopeExecutionEngine(
            materializer,
            new AgentScopeEventMapper(objectMapper),
            new RuntimeInputMapper(objectMapper),
            (currentSession, currentRun, signal) -> { });

        var result = CompletableFuture.supplyAsync(() -> engine.execute(
            session, run, snapshot,
            space.refinex.agentark.runtime.domain.RuntimeModels.RuntimePayload.inline(
                "{\"message\":\"cancel me\"}")));
        assertThat(subscribed.await(2, TimeUnit.SECONDS)).isTrue();
        engine.cancel(new CancellationCommand(
            run.turnId(), run.id(), new FencingToken(run.fencingToken().value() + 1),
            "stale_owner"));
        verify(delegate, never()).interrupt(context);
        engine.cancel(new CancellationCommand(
            run.turnId(), run.id(), run.fencingToken(), "user_requested"));
        events.tryEmitNext(new AgentResultEvent(new UserMessage("late result")));
        events.tryEmitComplete();

        assertThat(result.join().outcome()).isEqualTo(ExecutionOutcome.CANCELLED);
        verify(delegate).interrupt(context);
    }

    /** 验证旧 Fencing Token 的恢复命令在物化 AgentScope 资源前即被拒绝。 */
    @Test
    void rejectsResumeFromStaleFencingOwner() {
        ObjectMapper objectMapper = new ObjectMapper();
        var snapshot = ProviderTestFixtures.snapshot(objectMapper);
        var session = ProviderTestFixtures.session(snapshot);
        var run = ProviderTestFixtures.run(session);
        AgentScopeRuntimeMaterializer materializer = mock(AgentScopeRuntimeMaterializer.class);
        AgentScopeExecutionEngine engine = new AgentScopeExecutionEngine(
            materializer,
            new AgentScopeEventMapper(objectMapper),
            new RuntimeInputMapper(objectMapper),
            (currentSession, currentRun, signal) -> { });
        ResumeCommand command = new ResumeCommand(
            run.id(), ApprovalId.generate(), snapshot.contentHash(),
            new FencingToken(run.fencingToken().value() + 1));

        assertThatThrownBy(() -> engine.resume(session, run, snapshot, command))
            .isInstanceOf(AgentScopeProviderException.class)
            .hasMessageContaining("fenced run");
        verifyNoInteractions(materializer);
    }

    /** 验证恢复命令匹配待审批 Tool 后产生确认消息并完成同一 Run。 */
    @Test
    void resumesOnlyMatchingApprovedToolCall() {
        ObjectMapper objectMapper = new ObjectMapper();
        var snapshot = ProviderTestFixtures.snapshot(objectMapper);
        var session = ProviderTestFixtures.session(snapshot);
        var run = ProviderTestFixtures.run(session);
        ToolUseBlock tool = new ToolUseBlock(
            "tool-1", "repository.read", Map.of("path", "/README.md"));
        AgentScopeRuntimeMaterializer materializer = mock(AgentScopeRuntimeMaterializer.class);
        RuntimeHandle handle = mock(RuntimeHandle.class);
        HarnessAgent agent = mock(HarnessAgent.class);
        RuntimeContext context = RuntimeContext.builder()
            .userId(session.projectId().asString())
            .sessionId(session.id().asString())
            .build();
        AgentScopeCompilationPlan plan = mock(AgentScopeCompilationPlan.class);
        when(plan.turnTimeout()).thenReturn(Duration.ofSeconds(5));
        when(handle.agent()).thenReturn(agent);
        when(handle.context()).thenReturn(context);
        when(handle.plan()).thenReturn(plan);
        when(handle.pendingToolCalls()).thenReturn(List.of(tool));
        when(materializer.materialize(session, run, snapshot)).thenReturn(handle);
        when(agent.streamEvents(anyList(), any(RuntimeContext.class)))
            .thenReturn(Flux.just(new AgentResultEvent(new UserMessage("done"))));
        AgentScopeEventMapper eventMapper = new AgentScopeEventMapper(objectMapper);
        AgentScopeExecutionEngine engine = new AgentScopeExecutionEngine(
            materializer, eventMapper, new RuntimeInputMapper(objectMapper),
            (currentSession, currentRun, signal) -> { });
        ResumeCommand command = new ResumeCommand(
            run.id(), ApprovalId.generate(), eventMapper.argumentHash(tool), run.fencingToken());

        var result = engine.resume(session, run, snapshot, command);

        assertThat(result.outcome()).isEqualTo(ExecutionOutcome.SUCCEEDED);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Msg>> messages = ArgumentCaptor.forClass(List.class);
        verify(agent).streamEvents(messages.capture(), any(RuntimeContext.class));
        Object metadata = messages.getValue().getFirst().getMetadata()
            .get(Msg.METADATA_CONFIRM_RESULTS);
        assertThat(metadata).isInstanceOf(List.class);
        ConfirmResult confirmation = (ConfirmResult) ((List<?>) metadata).getFirst();
        assertThat(confirmation.isConfirmed()).isTrue();
        assertThat(confirmation.getToolCall().getId()).isEqualTo("tool-1");
    }
}
