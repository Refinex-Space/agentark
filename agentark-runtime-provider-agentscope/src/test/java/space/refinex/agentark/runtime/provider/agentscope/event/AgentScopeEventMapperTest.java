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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockStartEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.message.ToolUseBlock;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import space.refinex.agentark.kernel.ref.Checksum;

/**
 * 验证 Text、Approval、未知事件的稳定映射及隐藏推理过滤。
 *
 * @author refinex
 */
class AgentScopeEventMapperTest {

    /** Jackson 2 映射器。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 待测试 Event Mapper。 */
    private final AgentScopeEventMapper mapper = new AgentScopeEventMapper(objectMapper);

    /** 验证文本增量映射为稳定类型且保留关联字段。 */
    @Test
    void mapsTextDelta() throws Exception {
        var signal = mapper.map(new TextBlockDeltaEvent("reply-1", "block-1", "hello"))
            .orElseThrow();

        assertThat(signal.type()).isEqualTo("agent.text.delta");
        assertThat(payload(signal.payload().inlineJson().orElseThrow()))
            .containsEntry("replyId", "reply-1")
            .containsEntry("delta", "hello");
    }

    /** 验证 Text Delta 的稳定类型和载荷字段与 Golden Contract 一致。 */
    @Test
    void matchesTextDeltaGoldenContract() throws Exception {
        var signal = mapper.map(new TextBlockDeltaEvent("reply-1", "block-1", "hello"))
            .orElseThrow();
        try (InputStream input = getClass().getResourceAsStream(
            "/golden/event-text-delta-v1.json")) {
            assertThat(input).isNotNull();
            var expected = objectMapper.readTree(input);
            assertThat(signal.type()).isEqualTo(expected.get("type").asText());
            assertThat(objectMapper.readTree(signal.payload().inlineJson().orElseThrow()))
                .isEqualTo(expected.get("payload"));
        }
    }

    /** 验证审批事件只暴露参数 Hash，不暴露原始 Tool 参数。 */
    @Test
    void mapsApprovalWithoutRawArguments() {
        ToolUseBlock tool = new ToolUseBlock(
            "tool-1", "filesystem.write", Map.of("path", "/safe", "content", "private"));
        String json = mapper.map(new RequireUserConfirmEvent("reply-1", List.of(tool)))
            .orElseThrow().payload().inlineJson().orElseThrow();

        assertThat(json)
            .contains("\"argumentHash\":\"sha256:")
            .doesNotContain("/safe")
            .doesNotContain("private");
        assertThat(mapper.argumentHash(tool)).isInstanceOf(Checksum.class);
    }

    /** 验证 Tool 参数流只记录长度，避免将凭据或用户数据写入中立事件。 */
    @Test
    void mapsToolArgumentDeltaWithoutRawContent() {
        String json = mapper.map(new ToolCallDeltaEvent(
            "reply-1", "tool-1", "remote.call", "{\"token\":\"private\"}"))
            .orElseThrow().payload().inlineJson().orElseThrow();

        assertThat(json)
            .contains("\"argumentDeltaLength\":19")
            .doesNotContain("token")
            .doesNotContain("private");
    }

    /** 验证 Thinking Delta 不进入 AgentArk Event 流。 */
    @Test
    void filtersHiddenThinking() {
        assertThat(mapper.map(new ThinkingBlockDeltaEvent("reply", "block", "hidden")))
            .isEmpty();
    }

    /** 验证新增但未专门映射的事件不会使整个流崩溃。 */
    @Test
    void mapsUnknownTypedEventToForwardCompatibleEnvelope() {
        var signal = mapper.map(new TextBlockStartEvent("reply", "block")).orElseThrow();

        assertThat(signal.type()).isEqualTo("provider.event.unknown");
        assertThat(signal.payload().inlineJson().orElseThrow())
            .contains("TEXT_BLOCK_START")
            .doesNotContain("TextBlockStartEvent{");
    }

    /**
     * 解析内联 AgentArk Event Payload。
     *
     * @param json Payload JSON
     * @return Payload 对象
     */
    private Map<String, Object> payload(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
    }
}
