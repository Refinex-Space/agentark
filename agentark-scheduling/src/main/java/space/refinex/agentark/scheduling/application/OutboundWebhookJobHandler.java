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
import space.refinex.agentark.scheduling.port.DeliveryRepository;
import space.refinex.agentark.scheduling.port.JobHandler;
import space.refinex.agentark.scheduling.port.OutboundEndpointResolver;
import space.refinex.agentark.scheduling.port.OutboundWebhookClient;
import space.refinex.agentark.scheduling.port.OutboundWebhookClient.WebhookRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 通过固定 Endpoint Identity 投递 Outbound Webhook，并持久化幂等 Delivery 与响应分类。
 *
 * @author refinex
 */
public final class OutboundWebhookJobHandler implements JobHandler {

    /**
     * HTTPS Webhook 投递客户端。
     */
    private final OutboundWebhookClient client;

    /**
     * 受 Control 配置约束的 Endpoint Resolver。
     */
    private final OutboundEndpointResolver endpointResolver;

    /**
     * Delivery 仓储。
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
     * 创建 Outbound Webhook Handler。
     *
     * @param client             HTTPS Client
     * @param endpointResolver   固定端点解析器
     * @param deliveryRepository Delivery 仓储
     * @param objectMapper       JSON 解析器
     * @param clock              UTC 时钟
     */
    public OutboundWebhookJobHandler(
        OutboundWebhookClient client,
        OutboundEndpointResolver endpointResolver,
        DeliveryRepository deliveryRepository,
        ObjectMapper objectMapper,
        Clock clock) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.endpointResolver = Objects.requireNonNull(
            endpointResolver, "endpointResolver must not be null");
        this.deliveryRepository = Objects.requireNonNull(
            deliveryRepository, "deliveryRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 返回 Outbound Webhook Job 类型。
     *
     * @return OUTBOUND_WEBHOOK
     */
    @Override
    public JobType type() {
        return JobType.OUTBOUND_WEBHOOK;
    }

    /**
     * 自动重试要求下游接受 Provider Idempotency Key。
     *
     * @return PROVIDER_KEY
     */
    @Override
    public IdempotencyCapability idempotencyCapability() {
        return IdempotencyCapability.PROVIDER_KEY;
    }

    /**
     * 创建 Delivery 并执行受限 HTTPS 投递。
     *
     * @param claim 当前 Claim
     * @return Handler 结果
     */
    @Override
    public CompletionStage<HandlerResult> handle(ClaimedJob claim) {
        JsonNode root = objectMapper.readTree(claim.job().payload());
        String endpointId = text(root, "endpointId");
        String providerKey = "webhook:" + claim.job().id().asString();
        Delivery delivery = deliveryRepository.findByProviderKey(providerKey)
            .orElseGet(() -> createDelivery(claim, endpointId, providerKey));
        if (delivery.status() == DeliveryStatus.SUCCEEDED) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                HandlerResult.success(Optional.of("delivery:" + delivery.id())));
        }
        deliveryRepository.transition(
            delivery.id().toString(), claim.job().id(), claim.fencingToken(),
            delivery.status(), DeliveryStatus.SENDING, Optional.empty(), Optional.empty(),
            clock.instant());
        try {
            WebhookRequest request = new WebhookRequest(
                endpointResolver.resolve(
                    claim.job().organizationId(), claim.job().projectId(), endpointId),
                text(root, "body"), Map.of(), providerKey, Duration.ofSeconds(30));
            return client.send(request).thenApply(receipt -> {
                DeliveryStatus target = receipt.successful()
                    ? DeliveryStatus.SUCCEEDED
                    : receipt.retryable() ? DeliveryStatus.RETRY_WAIT : DeliveryStatus.FAILED;
                deliveryRepository.transition(
                    delivery.id().toString(), claim.job().id(), claim.fencingToken(),
                    DeliveryStatus.SENDING, target, Optional.empty(), receipt.responseSummary(),
                    clock.instant());
                return receipt.successful()
                    ? HandlerResult.success(Optional.of("delivery:" + delivery.id()))
                    : HandlerResult.failure(
                    "WEBHOOK_HTTP_" + receipt.statusCode(), receipt.retryable());
            }).exceptionally(exception -> {
                deliveryRepository.transition(
                    delivery.id().toString(), claim.job().id(), claim.fencingToken(),
                    DeliveryStatus.SENDING, DeliveryStatus.RETRY_WAIT,
                    Optional.empty(), Optional.of("provider-error"), clock.instant());
                return HandlerResult.failure("WEBHOOK_PROVIDER_FAILED", true);
            });
        } catch (RuntimeException exception) {
            deliveryRepository.transition(
                delivery.id().toString(), claim.job().id(), claim.fencingToken(),
                DeliveryStatus.SENDING, DeliveryStatus.FAILED,
                Optional.empty(), Optional.of("endpoint-rejected"), clock.instant());
            return java.util.concurrent.CompletableFuture.completedFuture(
                HandlerResult.failure("WEBHOOK_ENDPOINT_REJECTED", false));
        }
    }

    /**
     * 创建 PENDING Delivery。
     *
     * @param claim       当前 Claim
     * @param endpointId  固定端点身份
     * @param providerKey Provider 幂等键
     * @return Delivery
     */
    private Delivery createDelivery(ClaimedJob claim, String endpointId, String providerKey) {
        var now = clock.instant();
        Delivery delivery = new Delivery(
            SchedulerUuidV7.generate(now), claim.job().id(), "WEBHOOK", endpointId,
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
            throw new IllegalArgumentException("outbound webhook payload field is missing");
        }
        return value.asText();
    }
}
