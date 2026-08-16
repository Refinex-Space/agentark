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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用真实 Redis 验证 Gateway 固定窗口限流的装配、原子计数和拒绝语义。
 *
 * @author refinex
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "agentark.gateway.rate-limit-enabled=true",
        "agentark.gateway.webhook-rate-limit=2",
        "agentark.gateway.rate-limit-window=1m",
        "agentark.foundation.redis.enabled=true",
        "agentark.foundation.redis.application-name=gateway"
    })
class GatewayRedisRateLimitTest {

    /** Redis 8.10 GA 测试容器。 */
    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
        DockerImageName.parse("redis:8.10.0"))
        .withExposedPorts(6379);

    /** 真实 Redis RateLimiter 驱动的 Gateway 全局过滤器。 */
    @Autowired
    private GatewayRateLimitFilter filter;

    /**
     * 将 Spring Data Redis 连接绑定到随机映射的容器端口。
     *
     * @param registry 动态属性注册器
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    /**
     * 验证同一可信远端来源在窗口内只放行两次，第三次由 Gateway 返回 429。
     */
    @Test
    void enforcesWebhookLimitWithRealRedis() {
        AtomicInteger forwarded = new AtomicInteger();
        GatewayFilterChain chain = exchange -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        };

        MockServerWebExchange first = exchange();
        MockServerWebExchange second = exchange();
        MockServerWebExchange third = exchange();

        filter.filter(first, chain).block();
        filter.filter(second, chain).block();
        filter.filter(third, chain).block();

        assertThat(forwarded).hasValue(2);
        assertThat(first.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
            .isEqualTo("1");
        assertThat(second.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
            .isEqualTo("0");
        assertThat(third.getResponse().getStatusCode())
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(third.getResponse().getHeaders().getFirst("Retry-After")).isNotBlank();
    }

    /**
     * 创建带固定真实远端 Socket 的 Webhook 请求，避免信任客户端转发 Header。
     *
     * @return Webhook 请求交换
     */
    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.post(
                "/api/v1/scheduler/webhooks/test")
            .remoteAddress(new InetSocketAddress("192.0.2.10", 31415))
            .build());
    }
}
