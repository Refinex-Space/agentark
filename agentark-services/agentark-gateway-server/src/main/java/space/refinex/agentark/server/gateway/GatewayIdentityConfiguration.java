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

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import space.refinex.agentark.foundation.redis.RateLimiter;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/**
 * 装配 Gateway 内置 Identity 的 MySQL、Argon2、Redis Session、JWT 和 HTTP API。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayIdentityProperties.class)
@ConditionalOnProperty(prefix = "agentark.gateway.identity", name = "enabled", havingValue = "true")
@EnableScheduling
public class GatewayIdentityConfiguration {

    /**
     * 创建无状态配置实例。
     */
    public GatewayIdentityConfiguration() {
    }

    /**
     * 创建密码摘要与策略服务。
     */
    @Bean
    public GatewayIdentityPasswordService gatewayIdentityPasswordService(
        GatewayIdentityProperties properties) {
        properties.validate();
        return new GatewayIdentityPasswordService(properties);
    }

    /**
     * 创建 Identity MySQL Repository。
     */
    @Bean
    public GatewayIdentityRepository gatewayIdentityRepository(
        JdbcTemplate jdbc,
        TransactionTemplate transactions,
        ObjectMapper objectMapper,
        GatewayIdentityProperties properties) {
        return new GatewayIdentityRepository(jdbc, transactions, objectMapper, properties);
    }

    /**
     * 创建内置身份应用服务。
     */
    @Bean
    public GatewayIdentityService gatewayIdentityService(
        GatewayIdentityProperties properties,
        GatewayIdentityPasswordService passwords,
        GatewayIdentityRepository repository,
        ReactiveRedisIndexedSessionRepository sessions,
        RateLimiter rateLimiter,
        Clock gatewayClock) {
        return new GatewayIdentityService(
            properties, passwords, repository, sessions, rateLimiter, gatewayClock);
    }

    /**
     * 创建 Redis WebSession SecurityContext Repository。
     */
    @Bean
    public ServerSecurityContextRepository gatewaySecurityContextRepository() {
        return new WebSessionServerSecurityContextRepository();
    }

    /**
     * 创建内部 JWT 与公开 JWK 服务。
     */
    @Bean
    public GatewayIdentityTokenService gatewayIdentityTokenService(
        GatewayIdentityProperties properties, Clock gatewayClock) {
        return new GatewayIdentityTokenService(properties, gatewayClock);
    }

    /**
     * 创建本地 Session Token Relay。
     */
    @Bean
    public GatewayLocalIdentityTokenRelayFilter gatewayLocalIdentityTokenRelayFilter(
        GatewayIdentityService identityService, GatewayIdentityTokenService tokens) {
        return new GatewayLocalIdentityTokenRelayFilter(identityService, tokens);
    }

    /**
     * 创建 Identity Outbox 到 Control 的有界投递器。
     */
    @Bean
    public GatewayIdentityOutboxPublisher gatewayIdentityOutboxPublisher(
        GatewayIdentityRepository repository,
        GatewayIdentityTokenService tokens,
        ObjectMapper objectMapper,
        Clock gatewayClock,
        GatewayProperties gatewayProperties) {
        WebClient client = WebClient.builder()
            .baseUrl(gatewayProperties.getControlBaseUrl().toString())
            .build();
        return new GatewayIdentityOutboxPublisher(
            repository, client, tokens, objectMapper, gatewayClock);
    }

    /**
     * 应用启动后在 Flyway 完成的 Identity Schema 中幂等创建初始管理员。
     */
    @Bean
    public ApplicationRunner gatewayIdentityBootstrapRunner(GatewayIdentityService identityService) {
        return arguments -> identityService.bootstrap().block();
    }
}
