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

package space.refinex.agentark.runtime.provider.agentscope.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.event.*;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatUsage;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.domain.RuntimeModels.ExecutionSignal;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimePayload;
import space.refinex.agentark.runtime.provider.agentscope.error.AgentScopeProviderException;
import space.refinex.agentark.runtime.provider.agentscope.error.ProviderErrorCode;

import java.util.*;

/**
 * 将 AgentScope Typed Event 转换为稳定 AgentArk 执行信号，不序列化上游 Event 对象。
 *
 * <p>Thinking Block 被明确丢弃，避免将隐藏推理链进入 API 或事件库。
 *
 * @author refinex
 */
public final class AgentScopeEventMapper {

    /**
     * 只用于序列化新建 AgentArk Payload 的 Jackson 2 映射器。
     */
    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper Jackson 2 映射器
     */
    public AgentScopeEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 转换一个 AgentScope Event；Thinking Event 返回空。
     *
     * @param event AgentScope Typed Event
     * @return 稳定 AgentArk 执行信号
     */
    public Optional<ExecutionSignal> map(AgentEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (event instanceof ThinkingBlockStartEvent
            || event instanceof ThinkingBlockDeltaEvent
            || event instanceof ThinkingBlockEndEvent) {
            return Optional.empty();
        }
        if (event instanceof AgentStartEvent start) {
            return signal("agent.lifecycle.started", Map.of(
                "name", safe(start.getName()), "replyId", safe(start.getReplyId()),
                "source", safe(event.getSource())));
        }
        if (event instanceof AgentEndEvent end) {
            return signal("agent.lifecycle.ended", Map.of(
                "replyId", safe(end.getReplyId()), "source", safe(event.getSource())));
        }
        if (event instanceof ModelCallStartEvent start) {
            return signal("model.call.started", Map.of("replyId", safe(start.getReplyId())));
        }
        if (event instanceof ModelCallEndEvent end) {
            return signal("model.call.completed", modelUsage(end.getReplyId(), end.getUsage()));
        }
        if (event instanceof TextBlockDeltaEvent delta) {
            return signal("agent.text.delta", Map.of(
                "replyId", safe(delta.getReplyId()), "blockId", safe(delta.getBlockId()),
                "delta", safe(delta.getDelta()), "source", safe(event.getSource()),
                "trustLevel", "UNTRUSTED_MODEL_OUTPUT"));
        }
        if (event instanceof ToolCallStartEvent start) {
            return signal("tool.call.started", toolCall(
                start.getReplyId(), start.getToolCallId(), start.getToolCallName(), null));
        }
        if (event instanceof ToolCallDeltaEvent delta) {
            return signal("tool.call.delta", toolCall(
                delta.getReplyId(), delta.getToolCallId(), delta.getToolCallName(), delta.getDelta()));
        }
        if (event instanceof ToolCallEndEvent end) {
            return signal("tool.call.completed", toolCall(
                end.getReplyId(), end.getToolCallId(), end.getToolCallName(), null));
        }
        if (event instanceof ToolResultTextDeltaEvent delta) {
            return signal("tool.result.delta", Map.of(
                "replyId", safe(delta.getReplyId()), "toolCallId", safe(delta.getToolCallId()),
                "toolName", safe(delta.getToolCallName()), "delta", safe(delta.getDelta()),
                "trustLevel", "UNTRUSTED_TOOL_OUTPUT"));
        }
        if (event instanceof ToolResultEndEvent end) {
            return signal("tool.result.completed", Map.of(
                "replyId", safe(end.getReplyId()), "toolCallId", safe(end.getToolCallId()),
                "toolName", safe(end.getToolCallName()),
                "state", end.getState() == null ? "unknown" : end.getState().name(),
                "trustLevel", "UNTRUSTED_TOOL_OUTPUT"));
        }
        if (event instanceof RequireUserConfirmEvent approval) {
            return signal("approval.requested", Map.of(
                "replyId", safe(approval.getReplyId()),
                "toolCalls", approval.getToolCalls().stream().map(this::approvalTool).toList()));
        }
        if (event instanceof UserConfirmResultEvent confirmation) {
            return signal("approval.resolved", Map.of(
                "replyId", safe(confirmation.getReplyId()),
                "results", confirmation.getConfirmResults().stream().map(result -> Map.of(
                    "confirmed", result.isConfirmed(),
                    "toolCallId", safe(result.getToolCall().getId()),
                    "toolName", safe(result.getToolCall().getName()))).toList()));
        }
        if (event instanceof RequireExternalExecutionEvent external) {
            return signal("tool.external.requested", Map.of(
                "replyId", safe(external.getReplyId()),
                "toolCalls", external.getToolCalls().stream().map(this::approvalTool).toList()));
        }
        if (event instanceof ExternalExecutionResultEvent external) {
            return signal("tool.external.completed", Map.of(
                "replyId", safe(external.getReplyId()),
                "resultCount", external.getToolResults().size(),
                "trustLevel", "UNTRUSTED_TOOL_OUTPUT"));
        }
        if (event instanceof AgentResultEvent result) {
            String text = result.getResult() == null ? "" : safe(result.getResult().getTextContent());
            return signal("agent.result.completed", Map.of(
                "text", text, "trustLevel", "UNTRUSTED_MODEL_OUTPUT"));
        }
        if (event instanceof ExceedMaxItersEvent exceeded) {
            return signal("agent.run.failed", Map.of(
                "reason", "max_iterations_exceeded", "maxIters", exceeded.getMaxIters(),
                "currentIter", exceeded.getCurrentIter()));
        }
        if (event instanceof AllToolsDeniedEvent denied) {
            return signal("agent.run.failed", Map.of(
                "reason", "all_tools_denied", "toolCount", denied.getDeniedToolCalls().size()));
        }
        if (event instanceof RequestStopEvent stop) {
            return signal("agent.run.stopped", Map.of("reason", safe(stop.getReason())));
        }
        if (event instanceof CustomEvent custom) {
            String type = custom.getName() != null
                && (custom.getName().toLowerCase(java.util.Locale.ROOT).contains("rag")
                || custom.getName().toLowerCase(java.util.Locale.ROOT).contains("retriev"))
                ? "rag.activity.observed" : "provider.event.custom";
            return signal(type, Map.of(
                "name", safe(custom.getName()),
                "trustLevel", "rag.activity.observed".equals(type)
                    ? "UNTRUSTED_RETRIEVAL_CONTENT" : "UNTRUSTED_PROVIDER_EVENT"));
        }
        return signal("provider.event.unknown", Map.of(
            "upstreamType", event.getType().name(), "source", safe(event.getSource())));
    }

    /**
     * 构建模型调用完成载荷。
     *
     * @param replyId AgentScope Reply 标识
     * @param usage   可选用量
     * @return 模型调用载荷
     */
    private Map<String, Object> modelUsage(String replyId, ChatUsage usage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("replyId", safe(replyId));
        if (usage != null) {
            payload.put("inputTokens", usage.getInputTokens());
            payload.put("outputTokens", usage.getOutputTokens());
            payload.put("cachedTokens", usage.getCachedTokens());
            payload.put("durationMillis", Math.max(0L, Math.round(usage.getTime() * 1000)));
        }
        return payload;
    }

    /**
     * 构建 Tool Call 载荷。
     *
     * @param replyId    AgentScope Reply 标识
     * @param toolCallId Tool Call 标识
     * @param toolName   Tool 名称
     * @param delta      可选参数增量，仅记录长度，不记录可能包含敏感信息的原文
     * @return Tool Call 载荷
     */
    private Map<String, Object> toolCall(
        String replyId, String toolCallId, String toolName, String delta) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("replyId", safe(replyId));
        payload.put("toolCallId", safe(toolCallId));
        payload.put("toolName", safe(toolName));
        if (delta != null) {
            payload.put("argumentDeltaLength", delta.length());
        }
        return payload;
    }

    /**
     * 将待审批 Tool 转换为不暴露原始参数的摘要。
     *
     * @param toolCall AgentScope Tool Use
     * @return Tool 审批摘要
     */
    private Map<String, Object> approvalTool(ToolUseBlock toolCall) {
        return Map.of(
            "toolCallId", safe(toolCall.getId()),
            "toolName", safe(toolCall.getName()),
            "argumentHash", argumentHash(toolCall).toString());
    }

    /**
     * 按字段名稳定排序 Tool 参数后计算审批绑定 Hash。
     *
     * @param toolCall AgentScope Tool Use
     * @return 与事件载荷一致的参数 Hash
     */
    public Checksum argumentHash(ToolUseBlock toolCall) {
        Objects.requireNonNull(toolCall, "toolCall must not be null");
        return Checksum.sha256(write(canonical(toolCall.getInput())));
    }

    /**
     * 递归对 JSON Object 键排序，保证 JVM 重启后审批 Hash 不变。
     *
     * @param value JSON 值
     * @return 稳定顺序 JSON 值
     */
    private Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nested) -> sorted.put(String.valueOf(key), canonical(nested)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonical).toList();
        }
        return value;
    }

    /**
     * 将稳定事件类型和新建载荷序列化为 AgentArk 执行信号。
     *
     * @param type    稳定事件类型
     * @param payload 语言中立载荷
     * @return 执行信号
     */
    private Optional<ExecutionSignal> signal(String type, Map<String, Object> payload) {
        return Optional.of(new ExecutionSignal(type, RuntimePayload.inline(write(payload))));
    }

    /**
     * 序列化新建的 AgentArk 语言中立载荷。
     *
     * @param payload 载荷对象
     * @return 紧凑 JSON
     */
    private String write(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.EXECUTION_FAILED,
                "mapped runtime event cannot be serialized", exception);
        }
    }

    /**
     * 将可空上游文本转换为稳定非空值。
     *
     * @param value 上游文本
     * @return 非空文本
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}
