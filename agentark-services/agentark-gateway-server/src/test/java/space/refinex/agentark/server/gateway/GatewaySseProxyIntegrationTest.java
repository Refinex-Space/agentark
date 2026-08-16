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

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 验证 Gateway SSE 路由真实代理、重放标识透传和禁用代理缓冲响应头。
 *
 * @author refinex
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "agentark.foundation.security.enabled=true")
@Import(GatewayJwtSecurityIntegrationTest.TestJwtConfiguration.class)
class GatewaySseProxyIntegrationTest {

    /**
     * 记录下游实际收到的 Last-Event-ID。
     */
    private static final AtomicReference<String> LAST_EVENT_ID = new AtomicReference<>();

    /**
     * 提供有限 SSE 响应的本地 Runtime 替身服务器。
     */
    private static final DisposableServer BACKEND = HttpServer.create()
        .host("127.0.0.1")
        .port(0)
        .route(routes -> routes.get(
            "/api/v1/runtime/runs/run-1/events:stream",
            (request, response) -> {
                LAST_EVENT_ID.set(request.requestHeaders().get("Last-Event-ID"));
                return response
                    .status(HttpResponseStatus.OK)
                    .header(HttpHeaderNames.CONTENT_TYPE, "text/event-stream")
                    .sendString(Mono.just("id: 7\nevent: runtime.event\ndata: {}\n\n"));
            }))
        .bindNow();

    /**
     * 测试环境与随机 Gateway 端口来源。
     */
    @Autowired
    private Environment environment;

    /**
     * 真实 HTTP 测试客户端。
     */
    private WebTestClient webTestClient;

    /**
     * 将 Gateway Runtime 路由指向本地替身服务器。
     *
     * @param registry 测试动态属性注册器
     */
    @DynamicPropertySource
    static void runtimeBackend(DynamicPropertyRegistry registry) {
        registry.add(
            "agentark.gateway.runtime-base-url",
            () -> "http://127.0.0.1:" + BACKEND.port());
    }

    /**
     * 使用随机端口创建 Gateway 客户端并清理前次观察值。
     */
    @BeforeEach
    void createWebTestClient() {
        LAST_EVENT_ID.set(null);
        Integer port = environment.getProperty("local.server.port", Integer.class);
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
    }

    /**
     * 验证 Last-Event-ID 原样透传，SSE 响应禁用缓存和反向代理缓冲。
     */
    @Test
    void proxiesSseWithReplayAndNoBufferHeaders() {
        var result = webTestClient.get()
            .uri("/api/v1/runtime/runs/run-1/events:stream")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header("Last-Event-ID", "6")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
            .expectHeader().valueEquals("X-Accel-Buffering", "no")
            .expectBody()
            .returnResult();

        assertThat(LAST_EVENT_ID).hasValue("6");
        assertThat(new String(result.getResponseBody(), StandardCharsets.UTF_8))
            .contains("id: 7", "event: runtime.event", "data: {}");
    }

    /**
     * 关闭测试替身服务器，释放随机端口。
     */
    @AfterAll
    static void stopBackend() {
        BACKEND.disposeNow();
    }
}
