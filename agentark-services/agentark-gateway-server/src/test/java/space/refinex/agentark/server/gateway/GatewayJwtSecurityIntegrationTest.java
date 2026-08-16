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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 验证 Gateway 启用 Resource Server 后的 Bearer JWT 入口与 Actuator 隔离。
 *
 * @author refinex
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "agentark.foundation.security.enabled=true",
        "agentark.gateway.allowed-origins[0]=https://console.example.test"
    })
@Import(GatewayJwtSecurityIntegrationTest.TestJwtConfiguration.class)
class GatewayJwtSecurityIntegrationTest {

    /**
     * 测试环境与随机端口来源。
     */
    @Autowired
    private Environment environment;

    /**
     * 真实 HTTP 测试客户端。
     */
    private WebTestClient webTestClient;

    /**
     * 使用随机端口创建测试客户端。
     */
    @BeforeEach
    void createWebTestClient() {
        Integer port = environment.getProperty("local.server.port", Integer.class);
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
    }

    /**
     * 验证有效 Bearer JWT 可读取受保护 Info，而无效 Token 得到安全 401。
     */
    @Test
    void authenticatesBearerJwtAndRejectsInvalidToken() {
        webTestClient.get()
            .uri("/actuator/info")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .exchange()
            .expectStatus().isOk();

        webTestClient.get()
            .uri("/actuator/info")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk();
    }

    /**
     * 验证浏览器预检只允许精确配置的 Origin，不能由近似域名绕过。
     */
    @Test
    void allowsOnlyConfiguredCorsOrigin() {
        webTestClient.options()
            .uri("/api/v1/organizations")
            .header(HttpHeaders.ORIGIN, "https://console.example.test")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals(
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                "https://console.example.test");

        webTestClient.options()
            .uri("/api/v1/organizations")
            .header(HttpHeaders.ORIGIN, "https://console.example.test.attacker.invalid")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            .exchange()
            .expectStatus().isForbidden()
            .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    /**
     * 提供不访问网络的测试 JWT Decoder；生产仍由 Foundation JWK Decoder 装配。
     *
     * @author refinex
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestJwtConfiguration {

        /**
         * 创建只接受固定测试 Token 的 Decoder。
         *
         * @return 测试 JWT Decoder
         */
        @Bean
        JwtDecoder testJwtDecoder() {
            return token -> {
                if (!"valid-token".equals(token)) {
                    throw new BadJwtException("invalid test token");
                }
                Instant now = Instant.now();
                return new Jwt(
                    token,
                    now.minusSeconds(1),
                    now.plusSeconds(60),
                    Map.of("alg", "RS256", "kid", "test-key"),
                    Map.of(
                        "iss", "https://issuer.example.test",
                        "sub", "user-1",
                        "aud", List.of("agentark-gateway"),
                        "scope", "gateway:read"));
            };
        }
    }
}
