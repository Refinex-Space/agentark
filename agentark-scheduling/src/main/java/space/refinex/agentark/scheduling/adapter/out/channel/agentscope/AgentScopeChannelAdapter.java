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

package space.refinex.agentark.scheduling.adapter.out.channel.agentscope;

import space.refinex.agentark.scheduling.port.ChannelGateway;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 将 AgentArk 中立 Channel Message 映射到受组合层提供的 AgentScope Channel Bridge。
 *
 * <p>本适配器故意不导入 Harness 类型；具体版本绑定只能在独立 Provider 组合层实现 Bridge，
 * 从而满足 Scheduler 不拥有推理循环且验收闭包不携带 Harness 的硬边界。
 *
 * @author refinex
 */
public final class AgentScopeChannelAdapter implements ChannelGateway {

    /**
     * 由独立 Provider 组合层实现的版本绑定 Bridge。
     */
    private final AgentScopeChannelBridge bridge;

    /**
     * 创建 AgentScope Channel 防腐层。
     *
     * @param bridge 版本绑定 Bridge
     */
    public AgentScopeChannelAdapter(AgentScopeChannelBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge must not be null");
    }

    /**
     * 映射中立消息并委托 Provider Bridge 投递。
     *
     * @param message 中立 Channel 消息
     * @return 中立回执
     */
    @Override
    public CompletionStage<ChannelReceipt> send(ChannelMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        return bridge.send(new AgentScopeChannelRequest(
                message.channelId(), message.conversationKey(), message.recipient(), message.text(),
                message.attributes(), message.providerIdempotencyKey()))
            .thenApply(receipt -> new ChannelReceipt(
                receipt.providerMessageId(), receipt.responseSummary()));
    }

    /**
     * 定义独立 Provider 组合层必须实现的最小 Bridge，不传播任何 Provider 类型。
     *
     * @author refinex
     */
    @FunctionalInterface
    public interface AgentScopeChannelBridge {

        /**
         * 投递已映射请求。
         *
         * @param request Provider 中立请求
         * @return Provider 中立回执
         */
        CompletionStage<AgentScopeChannelReceipt> send(AgentScopeChannelRequest request);
    }

    /**
     * @param channelId              Channel 版本标识
     * @param conversationKey        稳定会话键
     * @param recipient              接收者身份
     * @param text                   用户可见文本
     * @param attributes             不含 Secret 的属性
     * @param providerIdempotencyKey Provider 幂等键
     * @author refinex
     */
    public record AgentScopeChannelRequest(
        String channelId,
        String conversationKey,
        String recipient,
        String text,
        Map<String, String> attributes,
        String providerIdempotencyKey) {

        /**
         * 防御性复制 Provider 请求属性。
         */
        public AgentScopeChannelRequest {
            attributes = Map.copyOf(Objects.requireNonNull(attributes,
                "attributes must not be null"));
        }
    }

    /**
     * @param providerMessageId Provider 消息标识
     * @param responseSummary   可选脱敏摘要
     * @author refinex
     */
    public record AgentScopeChannelReceipt(
        String providerMessageId, Optional<String> responseSummary) {

        /**
         * 校验 Bridge 回执容器。
         */
        public AgentScopeChannelReceipt {
            Objects.requireNonNull(responseSummary, "responseSummary must not be null");
        }
    }
}
