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

package space.refinex.agentark.runtime.provider.agentscope.prompt;

import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 Snapshot Prompt 映射为 Harness 系统提示词和非系统种子消息。
 *
 * @author refinex
 */
public final class PromptMapper {

    /**
     * 按 Snapshot 顺序合并所有 system Prompt。
     *
     * @param prompts 已验证 Prompt 列表
     * @return 以两个换行分隔的系统提示词
     */
    public String systemPrompt(List<PromptBinding> prompts) {
        return prompts.stream().filter(prompt -> "system".equals(prompt.role()))
            .map(PromptBinding::content).reduce((left, right) -> left + "\n\n" + right)
            .orElse("");
    }

    /**
     * 将 user 和 assistant Prompt 转换为执行时种子消息。
     *
     * @param prompts 已验证 Prompt 列表
     * @return 不含 system Prompt 的 AgentScope 消息列表
     */
    public List<Msg> seedMessages(List<PromptBinding> prompts) {
        List<Msg> messages = new ArrayList<>();
        prompts.forEach(prompt -> {
            if ("user".equals(prompt.role())) {
                messages.add(new UserMessage(prompt.content()));
            } else if ("assistant".equals(prompt.role())) {
                messages.add(new AssistantMessage(prompt.content()));
            }
        });
        return List.copyOf(messages);
    }
}
