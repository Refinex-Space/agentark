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

package space.refinex.agentark.scheduling.port;

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 定义供应商中立的 Channel 投递 Port，Scheduler Domain 不依赖任何 Harness 类型。
 *
 * @author refinex
 */
@FunctionalInterface
public interface ChannelGateway {

    /**
     * 使用稳定 Provider 幂等键投递中立消息。
     *
     * @param message 待投递消息
     * @return Provider 回执
     */
    CompletionStage<ChannelReceipt> send(ChannelMessage message);

    /**
     * @param organizationId         组织标识
     * @param projectId              项目标识
     * @param channelId              Control 固定的 Channel 版本标识
     * @param conversationKey        稳定会话外部键
     * @param recipient              不含 Credential 的接收者身份
     * @param text                   用户可见文本
     * @param attributes             不含 Secret 的低风险属性
     * @param providerIdempotencyKey Provider 幂等键
     * @param createdAt              消息创建时间
     * @author refinex
     */
    record ChannelMessage(
        OrganizationId organizationId,
        ProjectId projectId,
        String channelId,
        String conversationKey,
        String recipient,
        String text,
        Map<String, String> attributes,
        String providerIdempotencyKey,
        Instant createdAt) {

        /**
         * 校验消息租户、路由、正文和幂等键。
         */
        public ChannelMessage {
            Objects.requireNonNull(organizationId, "organizationId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            attributes = Map.copyOf(Objects.requireNonNull(attributes,
                "attributes must not be null"));
            if (channelId == null || channelId.isBlank()
                || conversationKey == null || conversationKey.isBlank()
                || recipient == null || recipient.isBlank()
                || text == null || text.isBlank() || text.length() > 100_000
                || providerIdempotencyKey == null || providerIdempotencyKey.isBlank()) {
                throw new IllegalArgumentException("channel message fields are invalid");
            }
        }
    }

    /**
     * @param providerMessageId Provider 消息标识
     * @param responseSummary   不含正文与凭据的响应摘要
     * @author refinex
     */
    record ChannelReceipt(String providerMessageId, Optional<String> responseSummary) {

        /**
         * 校验 Provider 回执不含空白标识或空白 Optional。
         */
        public ChannelReceipt {
            responseSummary = Objects.requireNonNull(
                responseSummary, "responseSummary must not be null");
            if (providerMessageId == null || providerMessageId.isBlank()
                || responseSummary.filter(String::isBlank).isPresent()) {
                throw new IllegalArgumentException("channel receipt is invalid");
            }
        }
    }
}
