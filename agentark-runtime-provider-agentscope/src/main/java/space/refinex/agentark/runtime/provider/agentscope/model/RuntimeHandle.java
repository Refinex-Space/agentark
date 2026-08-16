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

package space.refinex.agentark.runtime.provider.agentscope.model;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.harness.agent.HarnessAgent;
import space.refinex.agentark.runtime.domain.RuntimeModels.FencingToken;
import space.refinex.agentark.runtime.provider.agentscope.compiler.AgentScopeCompilationPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 按单个 Run 隔离 HarnessAgent、RuntimeContext、Secret 和外部资源的执行句柄。
 *
 * @author refinex
 */
public final class RuntimeHandle implements AutoCloseable {

    /**
     * 当前 Run 专属 HarnessAgent。
     */
    private final HarnessAgent agent;

    /**
     * 包含明确 userId/sessionId 的调用上下文。
     */
    private final RuntimeContext context;

    /**
     * 无敏感编译计划。
     */
    private final AgentScopeCompilationPlan plan;

    /**
     * 创建该 Handle 时绑定的 Runtime Fencing Token。
     */
    private final FencingToken fencingToken;

    /**
     * 非 system Prompt 映射得到的种子消息。
     */
    private final List<Msg> seedMessages;

    /**
     * 按与创建相反顺序关闭的 Secret、Client 和 Model 资源。
     */
    private final List<AutoCloseable> resources;

    /**
     * 当前 HITL 暂停对应的 Tool Call。
     */
    private final AtomicReference<List<ToolUseBlock>> pendingToolCalls =
        new AtomicReference<>(List.of());

    /**
     * 防止资源重复关闭。
     */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 标记 Runtime 应用层已持久化取消命令。
     */
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();

    /**
     * @param agent        HarnessAgent
     * @param context      Run 上下文
     * @param plan         编译计划
     * @param fencingToken 创建 Handle 时的有效栅栏令牌
     * @param seedMessages Prompt 种子消息
     * @param resources    待关闭资源
     */
    public RuntimeHandle(
        HarnessAgent agent,
        RuntimeContext context,
        AgentScopeCompilationPlan plan,
        FencingToken fencingToken,
        List<Msg> seedMessages,
        List<AutoCloseable> resources) {
        this.agent = Objects.requireNonNull(agent, "agent must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.plan = Objects.requireNonNull(plan, "plan must not be null");
        this.fencingToken = Objects.requireNonNull(
            fencingToken, "fencingToken must not be null");
        this.seedMessages = List.copyOf(Objects.requireNonNull(
            seedMessages, "seedMessages must not be null"));
        this.resources = List.copyOf(Objects.requireNonNull(
            resources, "resources must not be null"));
    }

    /**
     * @return 当前 Run 专属 HarnessAgent
     */
    public HarnessAgent agent() {
        return agent;
    }

    /**
     * @return 按 Project/Session 隔离的 RuntimeContext
     */
    public RuntimeContext context() {
        return context;
    }

    /**
     * @return 无敏感编译计划
     */
    public AgentScopeCompilationPlan plan() {
        return plan;
    }

    /**
     * @return Handle 创建时绑定的 Runtime Fencing Token
     */
    public FencingToken fencingToken() {
        return fencingToken;
    }

    /**
     * @return 非 system Prompt 种子消息
     */
    public List<Msg> seedMessages() {
        return seedMessages;
    }

    /**
     * 记录当前事件流中请求 HITL 的 Tool Call。
     *
     * @param toolCalls 待审批 Tool Call
     */
    public void rememberPendingToolCalls(List<ToolUseBlock> toolCalls) {
        pendingToolCalls.set(List.copyOf(Objects.requireNonNull(
            toolCalls, "toolCalls must not be null")));
    }

    /**
     * 返回当前暂停 Tool Call；若 JVM 重启，则从 AgentArk StateStore 恢复的 AgentState 扫描 ASKING 状态。
     *
     * @return 待审批 Tool Call
     */
    public List<ToolUseBlock> pendingToolCalls() {
        List<ToolUseBlock> current = pendingToolCalls.get();
        if (!current.isEmpty()) {
            return current;
        }
        List<Msg> contextMessages = agent.getDelegate().getAgentState(context).getContext();
        for (int index = contextMessages.size() - 1; index >= 0; index--) {
            List<ToolUseBlock> asking = contextMessages.get(index)
                .getContentBlocks(ToolUseBlock.class).stream()
                .filter(tool -> tool.getState() == ToolCallState.ASKING).toList();
            if (!asking.isEmpty()) {
                pendingToolCalls.compareAndSet(current, asking);
                return asking;
            }
        }
        return List.of();
    }

    /**
     * 标记当前 Run 已收到取消命令。
     */
    public void requestCancellation() {
        cancellationRequested.set(true);
    }

    /**
     * @return 当前 Run 是否已收到取消命令
     */
    public boolean isCancellationRequested() {
        return cancellationRequested.get();
    }

    /**
     * 先关闭 HarnessAgent，再按创建的相反顺序释放外部资源并清零 Secret。
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        RuntimeException failure = null;
        try {
            agent.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        List<AutoCloseable> reverse = new ArrayList<>(resources);
        for (int index = reverse.size() - 1; index >= 0; index--) {
            try {
                reverse.get(index).close();
            } catch (Exception exception) {
                if (failure == null) {
                    failure = new IllegalStateException("runtime resource close failed", exception);
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
