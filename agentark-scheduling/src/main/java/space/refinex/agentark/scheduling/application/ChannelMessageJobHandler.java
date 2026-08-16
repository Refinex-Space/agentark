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

package space.refinex.agentark.scheduling.application;

import space.refinex.agentark.scheduling.domain.SchedulerModels.*;
import space.refinex.agentark.scheduling.domain.SchedulerUuidV7;
import space.refinex.agentark.scheduling.port.ChannelGateway;
import space.refinex.agentark.scheduling.port.ChannelGateway.ChannelMessage;
import space.refinex.agentark.scheduling.port.DeliveryRepository;
import space.refinex.agentark.scheduling.port.JobHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 将中立 Channel Job 持久化为 Delivery，再通过防腐层投递并保存 Provider 回执。
 *
 * @author refinex
 */
public final class ChannelMessageJobHandler implements JobHandler {

    /**
     * 中立 Channel Gateway。
     */
    private final ChannelGateway gateway;

    /**
     * Delivery 权威仓储。
     */
    private final DeliveryRepository deliveryRepository;

    /**
     * JSON 解析器。
     */
    private final ObjectMapper objectMapper;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 创建 Channel Message Handler。
     *
     * @param gateway            Channel Gateway
     * @param deliveryRepository Delivery 仓储
     * @param objectMapper       JSON 解析器
     * @param clock              UTC 时钟
     */
    public ChannelMessageJobHandler(
        ChannelGateway gateway,
        DeliveryRepository deliveryRepository,
        ObjectMapper objectMapper,
        Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.deliveryRepository = Objects.requireNonNull(
            deliveryRepository, "deliveryRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 返回 Channel Message Job 类型。
     *
     * @return CHANNEL_MESSAGE
     */
    @Override
    public JobType type() {
        return JobType.CHANNEL_MESSAGE;
    }

    /**
     * Channel Provider 必须支持稳定幂等键。
     *
     * @return PROVIDER_KEY
     */
    @Override
    public IdempotencyCapability idempotencyCapability() {
        return IdempotencyCapability.PROVIDER_KEY;
    }

    /**
     * 创建或复用 Delivery 并执行异步投递。
     *
     * @param claim 当前 Claim
     * @return Handler 结果
     */
    @Override
    public CompletionStage<HandlerResult> handle(ClaimedJob claim) {
        JsonNode root = objectMapper.readTree(claim.job().payload());
        String providerKey = "channel:" + claim.job().id().asString();
        Delivery delivery = deliveryRepository.findByProviderKey(providerKey)
            .orElseGet(() -> createDelivery(claim, root, providerKey));
        if (delivery.status() == DeliveryStatus.SUCCEEDED) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                HandlerResult.success(Optional.of("delivery:" + delivery.id())));
        }
        deliveryRepository.transition(
            delivery.id().toString(), claim.job().id(), claim.fencingToken(),
            delivery.status(), DeliveryStatus.SENDING, Optional.empty(), Optional.empty(),
            clock.instant());
        ChannelMessage message = new ChannelMessage(
            claim.job().organizationId(), claim.job().projectId(), text(root, "channelId"),
            text(root, "conversationKey"), text(root, "recipient"), text(root, "text"),
            Map.of(), providerKey, clock.instant());
        try {
            return gateway.send(message).thenApply(receipt -> {
                deliveryRepository.transition(
                    delivery.id().toString(), claim.job().id(), claim.fencingToken(),
                    DeliveryStatus.SENDING, DeliveryStatus.SUCCEEDED,
                    Optional.of(receipt.providerMessageId()), receipt.responseSummary(),
                    clock.instant());
                return HandlerResult.success(Optional.of("delivery:" + delivery.id()));
            }).exceptionally(exception -> {
                deliveryRepository.transition(
                    delivery.id().toString(), claim.job().id(), claim.fencingToken(),
                    DeliveryStatus.SENDING, DeliveryStatus.RETRY_WAIT,
                    Optional.empty(), Optional.of("provider-error"), clock.instant());
                return HandlerResult.failure("CHANNEL_PROVIDER_FAILED", true);
            });
        } catch (RuntimeException exception) {
            deliveryRepository.transition(
                delivery.id().toString(), claim.job().id(), claim.fencingToken(),
                DeliveryStatus.SENDING, DeliveryStatus.RETRY_WAIT,
                Optional.empty(), Optional.of("provider-error"), clock.instant());
            return java.util.concurrent.CompletableFuture.completedFuture(
                HandlerResult.failure("CHANNEL_PROVIDER_FAILED", true));
        }
    }

    /**
     * 创建并持久化 PENDING Delivery。
     *
     * @param claim       当前 Claim
     * @param root        Payload 根节点
     * @param providerKey Provider 幂等键
     * @return Delivery
     */
    private Delivery createDelivery(ClaimedJob claim, JsonNode root, String providerKey) {
        var now = clock.instant();
        Delivery delivery = new Delivery(
            SchedulerUuidV7.generate(now), claim.job().id(), "AGENTSCOPE",
            text(root, "channelId") + ":" + text(root, "recipient"),
            Optional.of(providerKey), DeliveryStatus.PENDING,
            Optional.empty(), Optional.empty(), now, now);
        deliveryRepository.insert(delivery);
        return delivery;
    }

    /**
     * 读取必需文本字段。
     *
     * @param root  根节点
     * @param field 字段名
     * @return 文本值
     */
    private String text(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("channel payload field is missing");
        }
        return value.asText();
    }
}
