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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import space.refinex.agentark.runtime.domain.RuntimeModels.RuntimePayload;
import space.refinex.agentark.runtime.provider.agentscope.error.AgentScopeProviderException;
import space.refinex.agentark.runtime.provider.agentscope.error.ProviderErrorCode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将 AgentArk RuntimePayload 和 HITL 恢复命令映射为 AgentScope UserMessage。
 *
 * @author refinex
 */
public final class RuntimeInputMapper {

    /**
     * 用于识别常见文本输入字段的 Jackson 2 映射器。
     */
    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper Jackson 2 映射器
     */
    public RuntimeInputMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 将内联 JSON 转换为 UserMessage；带 ObjectRef 输入需由后续 Object Payload Port 解析。
     *
     * @param payload Turn 输入
     * @return AgentScope UserMessage
     */
    public Msg input(RuntimePayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        String json = payload.inlineJson().orElseThrow(() -> new AgentScopeProviderException(
            ProviderErrorCode.INPUT_PAYLOAD_UNAVAILABLE,
            "external RuntimePayload requires an Object payload resolver"));
        String text = text(json);
        return new UserMessage(text);
    }

    /**
     * 构造只确认一个已绑定审批参数的 HITL 恢复消息。
     *
     * @param toolCall 参数 Hash 与 Approval 匹配的 Tool Call
     * @return AgentScope HITL 恢复消息
     */
    public Msg approval(ToolUseBlock toolCall) {
        Objects.requireNonNull(toolCall, "toolCall must not be null");
        return UserMessage.builder()
            .textContent("Approved tool execution.")
            .metadata(Map.of(
                Msg.METADATA_CONFIRM_RESULTS, List.of(new ConfirmResult(true, toolCall))))
            .build();
    }

    /**
     * 优先提取 text 或 message 字段，其他 JSON 作为完整用户内容传入。
     *
     * @param json 内联 JSON
     * @return 用户文本
     */
    private String text(String json) {
        try {
            Map<String, Object> value = objectMapper.readValue(
                json, new TypeReference<Map<String, Object>>() {
                });
            Object text = value.get("text");
            if (!(text instanceof String)) {
                text = value.get("message");
            }
            return text instanceof String string && !string.isBlank() ? string : json;
        } catch (JsonProcessingException exception) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.INPUT_PAYLOAD_UNAVAILABLE,
                "inline RuntimePayload is not a JSON object", exception);
        }
    }
}
