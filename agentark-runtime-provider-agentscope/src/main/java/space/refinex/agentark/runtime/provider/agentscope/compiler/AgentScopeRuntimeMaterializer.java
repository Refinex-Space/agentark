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

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import space.refinex.agentark.runtime.domain.RuntimeModels.Run;
import space.refinex.agentark.runtime.domain.RuntimeModels.Session;
import space.refinex.agentark.runtime.domain.RuntimeModels.SnapshotDescriptor;
import space.refinex.agentark.runtime.port.CheckpointStore;
import space.refinex.agentark.runtime.provider.agentscope.error.AgentScopeProviderException;
import space.refinex.agentark.runtime.provider.agentscope.error.ProviderErrorCode;
import space.refinex.agentark.runtime.provider.agentscope.model.AgentScopeModelFactory;
import space.refinex.agentark.runtime.provider.agentscope.model.RuntimeHandle;
import space.refinex.agentark.runtime.provider.agentscope.mcp.McpEndpointGuard;
import space.refinex.agentark.runtime.provider.agentscope.prompt.PromptMapper;
import space.refinex.agentark.runtime.provider.agentscope.secret.ResolvedSecret;
import space.refinex.agentark.runtime.provider.agentscope.secret.SecretResolver;
import space.refinex.agentark.runtime.provider.agentscope.state.AgentScopeStateStoreAdapter;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 将可缓存编译计划实例化为单 Run 专属 HarnessAgent 和可关闭 RuntimeHandle。
 *
 * @author refinex
 */
public final class AgentScopeRuntimeMaterializer {

    /**
     * Snapshot 编译器。
     */
    private final AgentScopeSnapshotCompiler compiler;

    /**
     * 单 Run AgentScope Model 工厂。
     */
    private final AgentScopeModelFactory modelFactory;

    /**
     * MCP、Skill、Knowledge 和 Profile 组件工厂。
     */
    private final AgentScopeRuntimeComponentFactory componentFactory;

    /**
     * 按需 SecretRef 解析端口。
     */
    private final SecretResolver secretResolver;

    /**
     * AgentArk Agent State 权威端口。
     */
    private final space.refinex.agentark.runtime.port.AgentStateStore stateStore;

    /**
     * AgentArk Checkpoint 端口。
     */
    private final CheckpointStore checkpointStore;

    /**
     * Prompt 映射器。
     */
    private final PromptMapper promptMapper;

    /**
     * MCP SSRF、DNS Rebinding 与命令白名单守卫。
     */
    private final McpEndpointGuard mcpEndpointGuard;

    /**
     * State 和 Checkpoint 写入时钟。
     */
    private final Clock clock;

    /**
     * @param compiler         Snapshot Compiler
     * @param modelFactory     AgentScope Model 工厂
     * @param componentFactory Runtime 组件工厂
     * @param secretResolver   SecretRef 解析端口
     * @param stateStore       AgentArk State Store
     * @param checkpointStore  AgentArk Checkpoint Store
     * @param promptMapper     Prompt 映射器
     * @param mcpEndpointGuard MCP 连接目标守卫
     * @param clock            UTC 时钟
     */
    public AgentScopeRuntimeMaterializer(
        AgentScopeSnapshotCompiler compiler,
        AgentScopeModelFactory modelFactory,
        AgentScopeRuntimeComponentFactory componentFactory,
        SecretResolver secretResolver,
        space.refinex.agentark.runtime.port.AgentStateStore stateStore,
        CheckpointStore checkpointStore,
        PromptMapper promptMapper,
        McpEndpointGuard mcpEndpointGuard,
        Clock clock) {
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        this.modelFactory = Objects.requireNonNull(modelFactory, "modelFactory must not be null");
        this.componentFactory = Objects.requireNonNull(
            componentFactory, "componentFactory must not be null");
        this.secretResolver = Objects.requireNonNull(
            secretResolver, "secretResolver must not be null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
        this.checkpointStore = Objects.requireNonNull(
            checkpointStore, "checkpointStore must not be null");
        this.promptMapper = Objects.requireNonNull(promptMapper, "promptMapper must not be null");
        this.mcpEndpointGuard = Objects.requireNonNull(
            mcpEndpointGuard, "mcpEndpointGuard must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 为当前 Run 解析 Secret、创建 Model、强制注入 AgentArk StateStore 并构造 HarnessAgent。
     *
     * @param session  当前 Session
     * @param run      当前 Run
     * @param snapshot 不可变 Snapshot
     * @return 单 Run RuntimeHandle
     */
    public RuntimeHandle materialize(Session session, Run run, SnapshotDescriptor snapshot) {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(run, "run must not be null");
        AgentScopeCompilationPlan plan = compiler.compile(snapshot);
        validateOwnership(session, run, snapshot, plan);
        List<AutoCloseable> resources = new ArrayList<>();
        try {
            ResolvedSecret modelSecret = secretResolver.resolve(
                plan.model().secretRef(), plan.model().resolutionPolicy());
            if (modelSecret == null) {
                throw new AgentScopeProviderException(
                    ProviderErrorCode.SECRET_UNAVAILABLE, "model SecretRef cannot be resolved");
            }
            resources.add(modelSecret);
            Model model = Objects.requireNonNull(
                modelFactory.create(plan.model(), modelSecret), "model factory returned null");
            if (model instanceof AutoCloseable closeable) {
                resources.add(closeable);
            }

            AgentScopeStateStoreAdapter stateAdapter = new AgentScopeStateStoreAdapter(
                session, run, plan.agentId().asString(), stateStore, checkpointStore, clock);
            HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(plan.agentName())
                .agentId(plan.agentId().asString())
                .description("AgentArk immutable Snapshot runtime")
                .sysPrompt(promptMapper.systemPrompt(plan.prompts()))
                .model(model)
                .stateStore(stateAdapter)
                .defaultSessionId(session.id().asString())
                .maxIters(Math.max(1, Math.min(1000, plan.maxToolCalls() + 1)))
                .enableAgentTracingLog(false)
                .disableSessionPersistence()
                .disableToolsConfig();

            if (plan.maxSubAgents() == 0) {
                builder.disableSubagents().disableDynamicSubagents();
            }
            if (plan.skills().isEmpty()) {
                builder.disableDynamicSkills().disableDefaultWorkspaceSkills();
            }
            List<McpEndpointGuard.ConnectionPermit> mcpPermits =
                mcpEndpointGuard.authorize(plan.mcpServers());
            componentFactory.configureMcp(
                builder, plan.mcpServers(), mcpPermits, secretResolver, resources);
            componentFactory.configureSkills(builder, plan.skills(), resources);
            componentFactory.configureKnowledge(builder, plan.knowledge(), resources);
            componentFactory.configureMemory(builder, plan.memory());
            componentFactory.configureWorkspace(builder, plan.workspace(), resources);
            componentFactory.configureSandbox(builder, plan.sandbox(), resources);
            componentFactory.configurePermission(builder, plan.permission());

            HarnessAgent agent = builder.build();
            RuntimeContext context = RuntimeContext.builder()
                .userId(session.projectId().asString())
                .sessionId(session.id().asString())
                .build();
            return new RuntimeHandle(
                agent, context, plan, run.fencingToken(),
                promptMapper.seedMessages(plan.prompts()), resources);
        } catch (AgentScopeProviderException exception) {
            close(resources, exception);
            throw exception;
        } catch (RuntimeException exception) {
            AgentScopeProviderException wrapped = new AgentScopeProviderException(
                ProviderErrorCode.EXECUTION_FAILED,
                "AgentScope runtime handle cannot be materialized", exception);
            close(resources, wrapped);
            throw wrapped;
        }
    }

    /**
     * 校验编译计划、Session、Run 与 Snapshot 均属于同一租户和固定发布版本。
     *
     * @param session  当前 Session
     * @param run      当前 Run
     * @param snapshot 已加载 Snapshot
     * @param plan     已验证编译计划
     */
    private void validateOwnership(
        Session session,
        Run run,
        SnapshotDescriptor snapshot,
        AgentScopeCompilationPlan plan) {
        boolean tenantMismatch = !session.organizationId().equals(plan.organizationId())
            || !session.projectId().equals(plan.projectId())
            || !run.organizationId().equals(session.organizationId())
            || !run.projectId().equals(session.projectId())
            || !run.sessionId().equals(session.id());
        boolean snapshotMismatch = !session.revisionId().equals(snapshot.revisionId())
            || !session.snapshotId().equals(snapshot.snapshotId())
            || !session.snapshotHash().equals(snapshot.contentHash());
        boolean providerMismatch = !run.runtimeProvider().equals(plan.cacheKey().providerId())
            || !run.compilerVersion().equals(plan.cacheKey().compilerVersion());
        if (tenantMismatch || snapshotMismatch || providerMismatch) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.SNAPSHOT_INVALID,
                "runtime session, run and snapshot ownership do not match");
        }
    }

    /**
     * 在构造失败时按相反顺序释放已创建资源。
     *
     * @param resources 已创建资源
     * @param failure   原始失败
     */
    private void close(List<AutoCloseable> resources, RuntimeException failure) {
        for (int index = resources.size() - 1; index >= 0; index--) {
            try {
                resources.get(index).close();
            } catch (Exception exception) {
                failure.addSuppressed(exception);
            }
        }
    }
}
