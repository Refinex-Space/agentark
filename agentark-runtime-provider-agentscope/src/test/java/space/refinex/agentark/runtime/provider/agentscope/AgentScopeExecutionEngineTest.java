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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import space.refinex.agentark.runtime.domain.RuntimeModels.AgentStateVersion;
import space.refinex.agentark.runtime.domain.RuntimeModels.ExecutionOutcome;
import space.refinex.agentark.runtime.domain.RuntimeModels.ExecutionSignal;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimePayload;
import space.refinex.agentark.kernel.id.SessionId;
import space.refinex.agentark.runtime.port.CheckpointStore;
import space.refinex.agentark.runtime.provider.agentscope.compiler.*;
import space.refinex.agentark.runtime.provider.agentscope.event.AgentScopeEventMapper;
import space.refinex.agentark.runtime.provider.agentscope.knowledge.KnowledgeBinding;
import space.refinex.agentark.runtime.provider.agentscope.mcp.McpBinding;
import space.refinex.agentark.runtime.provider.agentscope.memory.MemoryBinding;
import space.refinex.agentark.runtime.provider.agentscope.permission.PermissionBinding;
import space.refinex.agentark.runtime.provider.agentscope.sandbox.SandboxBinding;
import space.refinex.agentark.runtime.provider.agentscope.secret.ResolvedSecret;
import space.refinex.agentark.runtime.provider.agentscope.skill.SkillBinding;
import space.refinex.agentark.runtime.provider.agentscope.model.RuntimeInputMapper;
import space.refinex.agentark.runtime.provider.agentscope.prompt.PromptMapper;
import space.refinex.agentark.runtime.provider.agentscope.workspace.WorkspaceBinding;

/**
 * 验证 Fake Model Streaming 经 HarnessAgent 转换为 AgentArk 事件且不共享 Session 可变状态。
 *
 * @author refinex
 */
class AgentScopeExecutionEngineTest {

    /** 每个测试使用的隔离 Workspace 根目录。 */
    @TempDir
    Path workspace;

    /** 验证两个 Session 可使用独立 HarnessAgent/RuntimeContext 并发完成 Fake Streaming。 */
    @Test
    void streamsFakeModelAcrossIsolatedSessions() {
        ObjectMapper objectMapper = new ObjectMapper();
        var snapshot = ProviderTestFixtures.snapshot(objectMapper);
        List<ExecutionSignal> signals = new CopyOnWriteArrayList<>();
        AgentScopeExecutionEngine engine = engine(objectMapper, signals);
        var firstSession = ProviderTestFixtures.session(snapshot);
        var secondSession = ProviderTestFixtures.session(snapshot);
        var firstRun = ProviderTestFixtures.run(firstSession);
        var secondRun = ProviderTestFixtures.run(secondSession);

        var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> engine.execute(
            firstSession, firstRun, snapshot, RuntimePayload.inline("{\"message\":\"one\"}")));
        var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> engine.execute(
            secondSession, secondRun, snapshot, RuntimePayload.inline("{\"message\":\"two\"}")));

        assertThat(List.of(first.join().outcome(), second.join().outcome()))
            .containsOnly(ExecutionOutcome.SUCCEEDED);
        assertThat(signals).extracting(ExecutionSignal::type)
            .contains("agent.text.delta", "agent.result.completed");
    }

    /**
     * 构造使用 Fake Model、可恢复内存 State Port 和受控 Workspace 的执行引擎。
     *
     * @param objectMapper Jackson 2 映射器
     * @param signals      接收已映射信号的列表
     * @return AgentScope Execution Engine
     */
    private AgentScopeExecutionEngine engine(
        ObjectMapper objectMapper, List<ExecutionSignal> signals) {
        SnapshotCompilationCache cache = new SnapshotCompilationCache();
        AgentScopeSnapshotCompiler compiler = new AgentScopeSnapshotCompiler(
            RuntimeProviderDescriptor.current(), objectMapper, cache);
        CheckpointStore checkpointStore = mock(CheckpointStore.class);
        when(checkpointStore.findLatestRecoverable(any())).thenReturn(Optional.empty());
        AgentScopeRuntimeMaterializer materializer = new AgentScopeRuntimeMaterializer(
            compiler,
            (binding, secret) -> new FakeStreamingModel(),
            new SafeTestComponentFactory(workspace),
            (reference, policy) -> new ResolvedSecret("test-secret".toCharArray()),
            stateStore(),
            checkpointStore,
            new PromptMapper(),
            Clock.fixed(ProviderTestFixtures.NOW, ZoneOffset.UTC));
        return new AgentScopeExecutionEngine(
            materializer,
            new AgentScopeEventMapper(objectMapper),
            new RuntimeInputMapper(objectMapper),
            (session, run, signal) -> signals.add(signal));
    }

    /**
     * 创建一个按 Session/Agent/State/Index 隔离且支持追加提交的测试 State Port。
     *
     * @return AgentArk State Store Mock
     */
    private space.refinex.agentark.runtime.port.AgentStateStore stateStore() {
        var store = mock(space.refinex.agentark.runtime.port.AgentStateStore.class);
        ConcurrentMap<String, AgentStateVersion> pending = new ConcurrentHashMap<>();
        ConcurrentMap<String, AgentStateVersion> committed = new ConcurrentHashMap<>();
        when(store.findLatestCommitted(any(), anyString(), anyString(), anyInt()))
            .thenAnswer(invocation -> Optional.ofNullable(committed.get(key(
                ((SessionId) invocation.getArgument(0)).asString(), invocation.getArgument(1),
                invocation.getArgument(2), invocation.getArgument(3)))));
        doAnswer(invocation -> {
            AgentStateVersion value = invocation.getArgument(0);
            pending.put(value.id().asString(), value);
            return null;
        }).when(store).append(any(AgentStateVersion.class));
        doAnswer(invocation -> {
            AgentStateVersion value = invocation.getArgument(0);
            AgentStateVersion stored = pending.get(value.id().asString());
            AgentStateVersion visible = new AgentStateVersion(
                stored.id(), stored.organizationId(), stored.projectId(), stored.sessionId(),
                stored.runId(), stored.agentKey(), stored.stateKey(), stored.itemIndex(),
                stored.stateVersion(), stored.payload(), stored.contentHash(), true,
                stored.fencingToken(), stored.createdAt());
            committed.put(key(
                stored.sessionId().asString(), stored.agentKey(), stored.stateKey(),
                stored.itemIndex()), visible);
            return null;
        }).when(store).commit(any(AgentStateVersion.class), any());
        return store;
    }

    /**
     * 构造测试 State 复合键。
     *
     * @param sessionId Session 标识
     * @param agentKey  Agent 键
     * @param stateKey  State 键
     * @param index     列表下标
     * @return 复合键
     */
    private String key(String sessionId, String agentKey, String stateKey, int index) {
        return sessionId + "|" + agentKey + "|" + stateKey + "|" + index;
    }

    /**
     * 返回一次文本 Streaming 响应的无状态 Fake Model。
     *
     * @author refinex
     */
    private static final class FakeStreamingModel implements Model {

        /**
         * 返回包含一个 TextBlock 的有限流。
         *
         * @param messages 当前消息
         * @param tools    当前 Tool Schema
         * @param options  生成参数
         * @return Fake ChatResponse 流
         */
        @Override
        public Flux<ChatResponse> stream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            return Flux.just(ChatResponse.builder()
                .content(List.of(TextBlock.builder().text("done").build()))
                .finishReason("stop")
                .build());
        }

        /**
         * @return Fake 模型名称
         */
        @Override
        public String getModelName() {
            return "fake-stream-model";
        }
    }

    /**
     * 为测试禁用未使用的文件、Shell、Memory 和子 Agent 能力，并将 Workspace 限定到临时目录。
     *
     * @author refinex
     */
    private static final class SafeTestComponentFactory
        implements AgentScopeRuntimeComponentFactory {

        /** 当前测试的临时 Workspace。 */
        private final Path workspace;

        /**
         * @param workspace 临时 Workspace
         */
        private SafeTestComponentFactory(Path workspace) {
            this.workspace = workspace;
        }

        /** 验证 Golden Snapshot 未配置 MCP。 */
        @Override
        public void configureMcp(
            HarnessAgent.Builder builder,
            List<McpBinding> bindings,
            space.refinex.agentark.runtime.provider.agentscope.secret.SecretResolver resolver,
            List<AutoCloseable> resources) {
            assertThat(bindings).isEmpty();
        }

        /** 验证 Golden Snapshot 未配置 Skill。 */
        @Override
        public void configureSkills(
            HarnessAgent.Builder builder,
            List<SkillBinding> bindings,
            List<AutoCloseable> resources) {
            assertThat(bindings).isEmpty();
        }

        /** 验证 Golden Snapshot 未配置 Knowledge。 */
        @Override
        public void configureKnowledge(
            HarnessAgent.Builder builder,
            List<KnowledgeBinding> bindings,
            List<AutoCloseable> resources) {
            assertThat(bindings).isEmpty();
        }

        /** 将 session Memory 映射为只依赖 Agent State 上下文的测试配置。 */
        @Override
        public void configureMemory(HarnessAgent.Builder builder, MemoryBinding binding) {
            builder.disableMemoryTools().disableMemoryHooks();
        }

        /** 将 isolated Workspace 映射到临时目录并禁用文件工具。 */
        @Override
        public void configureWorkspace(
            HarnessAgent.Builder builder,
            WorkspaceBinding binding,
            List<AutoCloseable> resources) {
            builder.workspace(workspace)
                .disableFilesystemTools()
                .disableShellTool()
                .disableWorkspaceContext()
                .disableAtPathExpansion();
        }

        /** 测试不启动真实 Sandbox，但保留配置输入验证边界。 */
        @Override
        public void configureSandbox(
            HarnessAgent.Builder builder,
            SandboxBinding binding,
            List<AutoCloseable> resources) {
            assertThat(binding.configuration()).containsEntry("network", "deny");
        }

        /** 测试不注册 Tool，因此 DENY 默认策略无可执行对象。 */
        @Override
        public void configurePermission(
            HarnessAgent.Builder builder, PermissionBinding binding) {
            assertThat(binding.defaultDecision()).isEqualTo("DENY");
        }
    }
}
