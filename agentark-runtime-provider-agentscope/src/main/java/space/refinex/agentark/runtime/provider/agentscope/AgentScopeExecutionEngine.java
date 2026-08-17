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

import io.agentscope.core.event.*;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ModelHttpException;
import io.agentscope.core.model.transport.HttpTransportException;
import io.agentscope.core.state.AgentState;
import space.refinex.agentark.kernel.id.RunId;
import space.refinex.agentark.runtime.application.RuntimeCommands.CancellationCommand;
import space.refinex.agentark.runtime.application.RuntimeCommands.ResumeCommand;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import space.refinex.agentark.runtime.port.AgentExecutionEngine;
import space.refinex.agentark.runtime.port.ExecutionSignalSink;
import space.refinex.agentark.runtime.provider.agentscope.compiler.AgentScopeRuntimeMaterializer;
import space.refinex.agentark.runtime.provider.agentscope.error.AgentScopeProviderException;
import space.refinex.agentark.runtime.provider.agentscope.error.ProviderErrorCode;
import space.refinex.agentark.runtime.provider.agentscope.event.AgentScopeEventMapper;
import space.refinex.agentark.runtime.provider.agentscope.model.RuntimeHandle;
import space.refinex.agentark.runtime.provider.agentscope.model.RuntimeInputMapper;
import space.refinex.agentark.foundation.observability.AgentArkTelemetry;
import space.refinex.agentark.foundation.observability.SpanConvention;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用单 Run RuntimeHandle 执行 AgentScope Harness Event 流，并转换为 AgentArk 稳定语义。
 *
 * @author refinex
 */
public final class AgentScopeExecutionEngine implements AgentExecutionEngine {

    /**
     * 单次从上游请求的 Event 上限，避免不受控预取。
     */
    private static final int EVENT_PREFETCH = 32;

    /**
     * Snapshot 编译与单 Run Harness 实例化器。
     */
    private final AgentScopeRuntimeMaterializer materializer;

    /**
     * AgentScope Event 到 AgentArk Signal 映射器。
     */
    private final AgentScopeEventMapper eventMapper;

    /**
     * RuntimePayload 与 HITL 消息映射器。
     */
    private final RuntimeInputMapper inputMapper;

    /**
     * 由 Runtime 应用层实现的事件接收端口。
     */
    private final ExecutionSignalSink signalSink;

    /** Agent 执行 Telemetry。 */
    private final AgentArkTelemetry telemetry;

    /**
     * 仅保留执行中或已暂停 Run 的 RuntimeHandle。
     */
    private final ConcurrentMap<RunId, RuntimeHandle> activeHandles = new ConcurrentHashMap<>();

    /**
     * @param materializer Snapshot 编译与 RuntimeHandle 实例化器
     * @param eventMapper  AgentScope Event 映射器
     * @param inputMapper  Runtime 输入映射器
     * @param signalSink   Runtime 应用层信号端口
     */
    public AgentScopeExecutionEngine(
        AgentScopeRuntimeMaterializer materializer,
        AgentScopeEventMapper eventMapper,
        RuntimeInputMapper inputMapper,
        ExecutionSignalSink signalSink) {
        this(materializer, eventMapper, inputMapper, signalSink, AgentArkTelemetry.noop());
    }

    /**
     * 创建带真实 Agent Run Telemetry 的执行引擎。
     *
     * @param materializer Snapshot 编译与 RuntimeHandle 实例化器
     * @param eventMapper  AgentScope Event 映射器
     * @param inputMapper  Runtime 输入映射器
     * @param signalSink   Runtime 应用层信号端口
     * @param telemetry    Agent Run Telemetry
     */
    public AgentScopeExecutionEngine(
        AgentScopeRuntimeMaterializer materializer,
        AgentScopeEventMapper eventMapper,
        RuntimeInputMapper inputMapper,
        ExecutionSignalSink signalSink,
        AgentArkTelemetry telemetry) {
        this.materializer = Objects.requireNonNull(materializer, "materializer must not be null");
        this.eventMapper = Objects.requireNonNull(eventMapper, "eventMapper must not be null");
        this.inputMapper = Objects.requireNonNull(inputMapper, "inputMapper must not be null");
        this.signalSink = Objects.requireNonNull(signalSink, "signalSink must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
    }

    /**
     * 编译固定 Snapshot，构造单 Run HarnessAgent 并执行首次输入。
     *
     * @param session  固定 Revision/Snapshot 的 Session
     * @param run      当前 Run Attempt
     * @param snapshot 已校验 Snapshot
     * @param input    Turn 输入
     * @return 供应商中立结果
     */
    @Override
    public ExecutionResult execute(
        Session session, Run run, SnapshotDescriptor snapshot, RuntimePayload input) {
        return telemetry.inSpan(
            SpanConvention.AGENT, "run",
            java.util.Map.of("operation", "execute", "runtime.provider", run.runtimeProvider()),
            () -> executeTracked(session, run, snapshot, input));
    }

    /**
     * 在 {@code agent.run} Span 内物化并执行 AgentScope Event 流。
     *
     * @param session  固定 Session
     * @param run      Run Attempt
     * @param snapshot 固定 Snapshot
     * @param input    Turn 输入
     * @return 中立执行结果
     */
    private ExecutionResult executeTracked(
        Session session, Run run, SnapshotDescriptor snapshot, RuntimePayload input) {
        RuntimeHandle handle = null;
        try {
            handle = materializer.materialize(session, run, snapshot);
            RuntimeHandle existing = activeHandles.putIfAbsent(run.id(), handle);
            if (existing != null) {
                handle.close();
                throw new AgentScopeProviderException(
                    ProviderErrorCode.EXECUTION_FAILED, "run is already active in this instance");
            }
            List<Msg> messages = new ArrayList<>(handle.seedMessages());
            messages.add(inputMapper.input(input));
            return executeStream(session, run, handle, messages);
        } catch (AgentScopeProviderException exception) {
            closeFailedStart(run.id(), handle, exception);
            return failed(exception);
        } catch (RuntimeException exception) {
            AgentScopeProviderException wrapped = new AgentScopeProviderException(
                ProviderErrorCode.EXECUTION_FAILED, "AgentScope execution could not start", exception);
            closeFailedStart(run.id(), handle, wrapped);
            return failed(wrapped);
        }
    }

    /**
     * 向已暂停的同一 Session 发送绑定 Approval Hash 的 ConfirmResult。
     *
     * @param session  固定 Revision/Snapshot 的 Session
     * @param run      PAUSED Run
     * @param snapshot 已校验 Snapshot
     * @param command  绑定 Approval 与参数 Hash 的恢复命令
     * @return 供应商中立结果
     */
    @Override
    public ExecutionResult resume(
        Session session, Run run, SnapshotDescriptor snapshot, ResumeCommand command) {
        RuntimeHandle handle = null;
        try {
            requireResumeOwnership(run, command);
            RuntimeHandle stale = activeHandles.remove(run.id());
            if (stale != null) {
                stale.close();
            }
            handle = materializer.materialize(session, run, snapshot);
            RuntimeHandle existing = activeHandles.putIfAbsent(run.id(), handle);
            if (existing != null) {
                handle.close();
                throw new AgentScopeProviderException(
                    ProviderErrorCode.RESUME_STATE_UNAVAILABLE,
                    "run is already active in this instance");
            }
            List<ToolUseBlock> toolCalls = recoverToolCalls(handle, command);
            return executeStream(
                session, run, handle, List.of(inputMapper.approval(toolCalls, command.decisions())));
        } catch (AgentScopeProviderException exception) {
            closeFailedStart(run.id(), handle, exception);
            throw exception;
        } catch (RuntimeException exception) {
            AgentScopeProviderException wrapped = new AgentScopeProviderException(
                ProviderErrorCode.RESUME_STATE_UNAVAILABLE,
                "AgentScope paused state cannot be recovered", exception);
            closeFailedStart(run.id(), handle, wrapped);
            throw wrapped;
        }
    }

    /**
     * 重新物化固定 Snapshot，并以空输入从持久 AgentState/Checkpoint 继续中断 Run。
     *
     * @param session    固定 Session
     * @param run        新 Fencing Token 接管的 Run
     * @param snapshot   固定 Snapshot
     * @param checkpoint 最新可恢复 Checkpoint
     * @return 供应商中立结果
     */
    @Override
    public ExecutionResult recover(
        Session session, Run run, SnapshotDescriptor snapshot, Checkpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint must not be null");
        RuntimeHandle handle = null;
        try {
            RuntimeHandle stale = activeHandles.remove(run.id());
            if (stale != null) {
                stale.close();
            }
            handle = materializer.materialize(session, run, snapshot);
            RuntimeHandle existing = activeHandles.putIfAbsent(run.id(), handle);
            if (existing != null) {
                handle.close();
                throw new AgentScopeProviderException(
                    ProviderErrorCode.RESUME_STATE_UNAVAILABLE,
                    "run is already active in this instance");
            }
            return executeStream(session, run, handle, List.of());
        } catch (AgentScopeProviderException exception) {
            closeFailedStart(run.id(), handle, exception);
            return failed(exception);
        } catch (RuntimeException exception) {
            AgentScopeProviderException wrapped = new AgentScopeProviderException(
                ProviderErrorCode.RESUME_STATE_UNAVAILABLE,
                "AgentScope checkpoint recovery could not start", exception);
            closeFailedStart(run.id(), handle, wrapped);
            return failed(wrapped);
        }
    }

    /**
     * 定向中断当前 Run 的 userId/sessionId 槽位，不影响其他 Session。
     *
     * @param command 已持久化取消事实的命令
     */
    @Override
    public void cancel(CancellationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        RuntimeHandle handle = activeHandles.get(command.expectedRunId());
        if (handle != null && handle.fencingToken().equals(command.fencingToken())) {
            handle.requestCancellation();
            handle.agent().getDelegate().interrupt(handle.context());
        }
    }

    /**
     * 校验恢复命令仍绑定当前 Run 与当前 Fencing Token。
     *
     * @param run     当前 Run
     * @param command 恢复命令
     */
    private void requireResumeOwnership(Run run, ResumeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!command.runId().equals(run.id())
            || !command.fencingToken().equals(run.fencingToken())) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.RESUME_STATE_UNAVAILABLE,
                "resume command does not own the current fenced run");
        }
    }

    /**
     * 清理尚未进入 Event Stream 就失败的 Handle，并把关闭失败保留为原异常的附加信息。
     *
     * @param runId   Run 标识
     * @param handle  可能尚未创建的 Handle
     * @param failure 原始失败
     */
    private void closeFailedStart(
        RunId runId, RuntimeHandle handle, AgentScopeProviderException failure) {
        if (handle == null) {
            return;
        }
        activeHandles.remove(runId, handle);
        try {
            handle.close();
        } catch (RuntimeException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    /**
     * 订阅 AgentScope Event 流，先同步交付语言中立 Signal，再认定终态。
     *
     * @param session  Session
     * @param run      Run
     * @param handle   RuntimeHandle
     * @param messages 本次输入消息
     * @return 执行结果
     */
    private ExecutionResult executeStream(
        Session session, Run run, RuntimeHandle handle, List<Msg> messages) {
        AtomicBoolean paused = new AtomicBoolean();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean failed = new AtomicBoolean();
        try {
            handle.agent().streamEvents(messages, handle.context())
                .limitRate(EVENT_PREFETCH)
                .timeout(handle.plan().turnTimeout())
                .doOnNext(event -> consume(session, run, handle, event, paused, completed, failed))
                .blockLast();
            if (handle.isCancellationRequested()) {
                return finish(run.id(), handle, new ExecutionResult(
                    ExecutionOutcome.CANCELLED, Optional.empty(), Optional.of("run cancelled")));
            }
            if (paused.get()) {
                return new ExecutionResult(
                    ExecutionOutcome.PAUSED, Optional.empty(), Optional.of("approval required"));
            }
            if (failed.get() || !completed.get()) {
                return finish(run.id(), handle, new ExecutionResult(
                    ExecutionOutcome.FAILED,
                    Optional.of(ProviderErrorCode.EXECUTION_FAILED.name()),
                    Optional.of("AgentScope stream ended without a successful result")));
            }
            return finish(run.id(), handle, new ExecutionResult(
                ExecutionOutcome.SUCCEEDED, Optional.empty(), Optional.empty()));
        } catch (RuntimeException exception) {
            if (handle.isCancellationRequested()) {
                return finish(run.id(), handle, new ExecutionResult(
                    ExecutionOutcome.CANCELLED, Optional.empty(), Optional.of("run cancelled")));
            }
            if (containsTimeout(exception)) {
                handle.agent().getDelegate().interrupt(handle.context());
                return finish(run.id(), handle, new ExecutionResult(
                    ExecutionOutcome.TIMED_OUT,
                    Optional.of(ProviderErrorCode.PROVIDER_TIMEOUT.name()),
                    Optional.of("AgentScope execution exceeded the snapshot timeout")));
            }
            if (containsHttpStatus(exception, 429)) {
                return finish(run.id(), handle, new ExecutionResult(
                    ExecutionOutcome.FAILED,
                    Optional.of(ProviderErrorCode.PROVIDER_RATE_LIMITED.name()),
                    Optional.of("AgentScope provider rate limit was reached")));
            }
            AgentScopeProviderException providerException = exception instanceof AgentScopeProviderException value
                ? value : new AgentScopeProviderException(
                ProviderErrorCode.EXECUTION_FAILED, "AgentScope event stream failed", exception);
            return finish(run.id(), handle, failed(providerException));
        }
    }

    /**
     * 记录 HITL/结果边界，并将非推理 Event 同步交给 Runtime 端口。
     *
     * @param session   Session
     * @param run       Run
     * @param handle    RuntimeHandle
     * @param event     AgentScope Event
     * @param paused    暂停标记
     * @param completed 成功结果标记
     * @param failed    失败结果标记
     */
    private void consume(
        Session session,
        Run run,
        RuntimeHandle handle,
        AgentEvent event,
        AtomicBoolean paused,
        AtomicBoolean completed,
        AtomicBoolean failed) {
        if (event instanceof RequireUserConfirmEvent approval) {
            handle.rememberPendingToolCalls(approval.getToolCalls());
            paused.set(true);
        } else if (event instanceof RequireExternalExecutionEvent) {
            paused.set(true);
        } else if (event instanceof AgentResultEvent) {
            completed.set(true);
        } else if (event instanceof ExceedMaxItersEvent || event instanceof AllToolsDeniedEvent) {
            failed.set(true);
        }
        eventMapper.map(event).ifPresent(signal -> signalSink.emit(session, run, signal));
    }

    /**
     * 从异常链判断 Reactor 超时。
     *
     * @param throwable 异常
     * @return 存在 TimeoutException 时为 true
     */
    private boolean containsTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 按 AgentScope 稳定异常接口和 Core Transport 异常检查 HTTP 状态，不解析异常消息或响应正文。
     *
     * @param throwable  异常链
     * @param statusCode 目标 HTTP 状态
     * @return 异常链中存在目标状态时为 true
     */
    private boolean containsHttpStatus(Throwable throwable, int statusCode) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ModelHttpException modelException
                && Objects.equals(modelException.getStatusCode(), statusCode)) {
                return true;
            }
            if (current instanceof HttpTransportException transportException
                && Objects.equals(transportException.getStatusCode(), statusCode)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 从已持久 AgentState 的消息上下文恢复待决 Tool，并再次校验 Tool Call ID 与参数 Hash。
     *
     * @param handle  新 Fencing Token 对应的 RuntimeHandle
     * @param command 已持久审批决策
     * @return 与决策顺序一致的 Tool Call
     */
    private List<ToolUseBlock> recoverToolCalls(RuntimeHandle handle, ResumeCommand command) {
        AgentState state = handle.agent().getDelegate().getAgentState(
            handle.context().getUserId(), handle.context().getSessionId());
        java.util.Map<String, ToolUseBlock> candidates = new java.util.HashMap<>();
        state.getContext().forEach(message -> message.getContentBlocks(ToolUseBlock.class)
            .forEach(toolCall -> candidates.put(toolCall.getId(), toolCall)));
        return command.decisions().stream().map(decision -> {
            ToolUseBlock toolCall = candidates.get(decision.toolCallId());
            if (toolCall == null || !eventMapper.argumentHash(toolCall).equals(decision.argumentHash())) {
                throw new AgentScopeProviderException(
                    ProviderErrorCode.RESUME_STATE_UNAVAILABLE,
                    "approval does not match recoverable AgentScope tool state");
            }
            return toolCall;
        }).toList();
    }

    /**
     * 移除终态 Handle 并释放 Secret、Model、MCP 和 Harness 资源。
     *
     * @param runId  Run 标识
     * @param handle RuntimeHandle
     * @param result 原执行结果
     * @return 释放成功时返回原结果，释放失败时返回稳定失败
     */
    private ExecutionResult finish(RunId runId, RuntimeHandle handle, ExecutionResult result) {
        activeHandles.remove(runId, handle);
        try {
            handle.close();
            return result;
        } catch (RuntimeException exception) {
            return new ExecutionResult(
                ExecutionOutcome.FAILED,
                Optional.of(ProviderErrorCode.EXECUTION_FAILED.name()),
                Optional.of("AgentScope runtime resource cleanup failed"));
        }
    }

    /**
     * 将 Provider 异常转换为不包含原始 Prompt/Secret 的稳定失败。
     *
     * @param exception Provider 异常
     * @return FAILED 执行结果
     */
    private ExecutionResult failed(AgentScopeProviderException exception) {
        return new ExecutionResult(
            ExecutionOutcome.FAILED,
            Optional.of(exception.errorCode().name()),
            Optional.of(exception.getMessage()));
    }
}
