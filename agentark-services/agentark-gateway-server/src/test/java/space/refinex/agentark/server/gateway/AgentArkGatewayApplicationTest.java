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

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 验证 Gateway 路由表、失败默认认证、内部路径拒绝和健康探针。
 *
 * @author refinex
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentArkGatewayApplicationTest {

    /**
     * 读取测试运行期端口、应用标识和 Gateway 配置。
     */
    @Autowired
    private Environment environment;

    /**
     * 固定 Gateway 路由定位器。
     */
    @Autowired
    @Qualifier("gatewayRouteLocator")
    private RouteLocator routeLocator;

    /**
     * 随机端口 WebFlux 测试客户端。
     */
    private WebTestClient webTestClient;

    /**
     * 使用随机端口构建真实 HTTP 测试客户端。
     */
    @BeforeEach
    void createWebTestClient() {
        Integer port = environment.getProperty("local.server.port", Integer.class);
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
    }

    /**
     * 证明 Gateway 能启动且路由优先级、平面归属和 SSE 超时语义固定。
     */
    @Test
    void startsWithFixedPublicRouteTable() {
        assertThat(environment.getProperty("local.server.port", Integer.class)).isPositive();
        assertThat(environment.getProperty("spring.application.name"))
            .isEqualTo("agentark-gateway-server");
        Map<String, Route> routes = routeLocator.getRoutes()
            .collectMap(Route::getId)
            .block(Duration.ofSeconds(5));
        assertThat(routes).isNotNull();
        assertThat(routes).containsOnlyKeys(
            "internal-path-reject",
            "runtime-event-stream",
            "runtime-public",
            "scheduler-webhook",
            "scheduler-public",
            "control-public");
        assertThat(routes.get("internal-path-reject").getOrder()).isEqualTo(-100);
        assertThat(routes.get("runtime-event-stream").getOrder()).isEqualTo(-20);
        assertThat(routes.get("runtime-event-stream").getMetadata())
            .containsEntry(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, -1L);
        assertThat(routes.get("control-public").getOrder()).isEqualTo(20);
    }

    /**
     * 证明安全未配置时公共 API 失败关闭，内部路径不被代理，健康探针仍可用。
     */
    @Test
    void failsClosedWithoutSecurityConfiguration() {
        webTestClient.get()
            .uri("/api/v1/organizations")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectHeader().valueEquals("X-Frame-Options", "DENY");

        webTestClient.get()
            .uri("/internal/v1/auth/api-keys:verify")
            .exchange()
            .expectStatus().isNotFound();

        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk();
    }
}
