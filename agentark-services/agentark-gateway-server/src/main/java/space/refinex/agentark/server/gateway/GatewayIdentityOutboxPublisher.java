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

package space.refinex.agentark.server.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.reactive.function.client.WebClient;
import space.refinex.agentark.server.gateway.GatewayIdentityModels.OutboxItem;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 有界领取 Identity Outbox，并通过签名服务 JWT 幂等投影到 Control。
 *
 * @author refinex
 */
public final class GatewayIdentityOutboxPublisher {

    /**
     * 脱敏运行日志。
     */
    private static final Logger LOGGER =
        LoggerFactory.getLogger(GatewayIdentityOutboxPublisher.class);

    /**
     * MySQL 持久 Outbox 仓储。
     */
    private final GatewayIdentityRepository repository;

    /**
     * Control Internal API 客户端。
     */
    private final WebClient control;

    /**
     * 服务 JWT 签发器。
     */
    private final GatewayIdentityTokenService tokens;

    /**
     * JSON 映射器。
     */
    private final ObjectMapper objectMapper;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 当前领取者稳定实例标识。
     */
    private final String owner = "gateway-" + GatewayIdentityIds.generate();

    /**
     * 创建 Identity Outbox Publisher。
     */
    public GatewayIdentityOutboxPublisher(
        GatewayIdentityRepository repository,
        WebClient control,
        GatewayIdentityTokenService tokens,
        ObjectMapper objectMapper,
        Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.control = Objects.requireNonNull(control, "control must not be null");
        this.tokens = Objects.requireNonNull(tokens, "tokens must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 每秒最多投递十条，单条失败不阻塞后续定时轮次。
     */
    @Scheduled(fixedDelayString = "${agentark.gateway.identity.outbox-delay:1s}")
    public void publishAvailable() {
        for (int index = 0; index < 10; index++) {
            Optional<OutboxItem> claimed = repository.claimOutbox(owner, clock.instant());
            if (claimed.isEmpty()) {
                return;
            }
            OutboxItem item = claimed.orElseThrow();
            try {
                Map<String, Object> payload = objectMapper.readValue(
                    item.payloadJson(), new TypeReference<>() {
                    });
                control.post()
                    .uri("/internal/v1/identity/accounts:project")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issueGatewayServiceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));
                repository.markOutboxPublished(item.id(), clock.instant());
            } catch (Exception exception) {
                repository.markOutboxFailed(item.id(), item.attempts(), clock.instant());
                LOGGER.warn(
                    "Identity projection delivery failed eventType={} attempts={}",
                    item.eventType(), item.attempts());
            }
        }
    }
}
