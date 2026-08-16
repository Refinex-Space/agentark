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

import org.junit.jupiter.api.Test;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.scheduling.domain.SchedulerModels.*;
import space.refinex.agentark.scheduling.domain.SchedulerUuidV7;
import space.refinex.agentark.scheduling.port.*;
import space.refinex.agentark.scheduling.port.ChannelGateway.ChannelReceipt;
import space.refinex.agentark.scheduling.port.JobHandler.HandlerResult;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证 Channel 与 Outbound Webhook Handler 的幂等 Delivery 和失败状态转换。
 *
 * @author refinex
 */
class DeliveryJobHandlerTest {

    /** 固定测试时间。 */
    private static final Instant NOW = Instant.parse("2026-08-16T10:00:00Z");

    /** 创建 Delivery Handler 测试实例。 */
    DeliveryJobHandlerTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明 Channel 成功投递会持久化 Delivery 并以当前 Token 转为 SUCCEEDED。 */
    @Test
    void persistsSuccessfulChannelDelivery() {
        ChannelGateway gateway = message -> CompletableFuture.completedFuture(
            new ChannelReceipt("provider-message-1", Optional.of("accepted")));
        DeliveryRepository repository = mock(DeliveryRepository.class);
        when(repository.findByProviderKey(anyString())).thenReturn(Optional.empty());
        ChannelMessageJobHandler handler = new ChannelMessageJobHandler(
            gateway, repository, JsonMapper.builder().build(), clock());

        HandlerResult result = handler.handle(claim(
            JobType.CHANNEL_MESSAGE,
            "{\"channelId\":\"channel-1\",\"conversationKey\":\"conversation-1\","
                + "\"recipient\":\"user-1\",\"text\":\"hello\"}"))
            .toCompletableFuture().join();

        assertThat(result.successful()).isTrue();
        verify(repository).insert(any(Delivery.class));
        verify(repository).transition(
            anyString(), any(), eq(1L), eq(DeliveryStatus.SENDING),
            eq(DeliveryStatus.SUCCEEDED), eq(Optional.of("provider-message-1")),
            eq(Optional.of("accepted")), eq(NOW));
    }

    /** 证明 Webhook Provider 异步异常会把 Delivery 转为 RETRY_WAIT 并返回可重试失败。 */
    @Test
    void marksWebhookDeliveryRetryableOnProviderFailure() {
        OutboundWebhookClient client = request -> CompletableFuture.failedFuture(
            new IllegalStateException("provider unavailable"));
        OutboundEndpointResolver resolver = (organizationId, projectId, endpointId) ->
            URI.create("https://hooks.example.test/agentark");
        DeliveryRepository repository = mock(DeliveryRepository.class);
        when(repository.findByProviderKey(anyString())).thenReturn(Optional.empty());
        OutboundWebhookJobHandler handler = new OutboundWebhookJobHandler(
            client, resolver, repository, JsonMapper.builder().build(), clock());

        HandlerResult result = handler.handle(claim(
            JobType.OUTBOUND_WEBHOOK,
            "{\"endpointId\":\"endpoint-1\",\"body\":\"{}\"}"))
            .toCompletableFuture().join();

        assertThat(result.successful()).isFalse();
        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCode()).contains("WEBHOOK_PROVIDER_FAILED");
        verify(repository).transition(
            anyString(), any(), eq(1L), eq(DeliveryStatus.SENDING),
            eq(DeliveryStatus.RETRY_WAIT), isNullOrOptionalEmpty(),
            eq(Optional.of("provider-error")), eq(NOW));
    }

    /**
     * 返回固定 UTC 时钟。
     *
     * @return 固定时钟
     */
    private Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    /**
     * 创建指定 Job Type 与 Payload 的首个 Claim。
     *
     * @param type    Job 类型
     * @param payload Job Payload
     * @return Claim
     */
    private ClaimedJob claim(JobType type, String payload) {
        Job job = new Job(
            JobId.generate(), OrganizationId.generate(), ProjectId.generate(), type,
            "delivery-fixture-" + type.name().toLowerCase(), payload,
            Checksum.sha256(payload), JobStatus.CLAIMED, 0, NOW,
            new RetryPolicy(
                3, Duration.ofSeconds(1), Duration.ofSeconds(10), 2.0, 0.0,
                Duration.ofSeconds(30)),
            IdempotencyCapability.PROVIDER_KEY, 1, 1, NOW, NOW);
        return new ClaimedJob(
            job, SchedulerUuidV7.generate(NOW), 1, "scheduler-test", 1,
            NOW.plusSeconds(30));
    }

    /**
     * 匹配空 Optional，避免把数据库空值误写成 null 容器。
     *
     * @return Mockito 参数匹配器
     */
    private Optional<String> isNullOrOptionalEmpty() {
        return argThat(Optional::isEmpty);
    }
}
