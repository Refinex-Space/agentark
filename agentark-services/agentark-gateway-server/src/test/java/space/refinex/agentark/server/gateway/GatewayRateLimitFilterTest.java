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
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.redis.RateLimitDecision;
import space.refinex.agentark.foundation.redis.RateLimiter;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 验证 Gateway Redis 限流拒绝响应、健康绕过和保护服务失败关闭行为。
 *
 * @author refinex
 */
class GatewayRateLimitFilterTest {

    /**
     * 验证超过额度时返回 RFC 9457、剩余额度和 Retry-After，且不调用下游。
     */
    @Test
    void rejectsRequestsOverLimit() {
        RateLimiter limiter = (namespace, subject, limit, window) ->
            new RateLimitDecision(false, 0, Duration.ofMillis(1_500));
        GatewayRateLimitFilter filter = filter(limiter);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/organizations").build());
        AtomicBoolean called = new AtomicBoolean();
        GatewayFilterChain chain = current -> {
            called.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(called).isFalse();
        assertThat(exchange.getResponse().getStatusCode())
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
            .isEqualTo("2");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
            .isEqualTo("0");
    }

    /**
     * 验证健康探针不会消耗 Redis 公共额度。
     */
    @Test
    void bypassesHealthProbe() {
        RateLimiter limiter = (namespace, subject, limit, window) -> {
            throw new AssertionError("health probe must not reach rate limiter");
        };
        GatewayRateLimitFilter filter = filter(limiter);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/actuator/health").build());
        AtomicBoolean called = new AtomicBoolean();

        filter.filter(exchange, current -> {
            called.set(true);
            return Mono.empty();
        }).block();

        assertThat(called).isTrue();
    }

    /**
     * 创建使用指定限流端口的过滤器。
     *
     * @param limiter 测试限流端口
     * @return Gateway 限流过滤器
     */
    private GatewayRateLimitFilter filter(RateLimiter limiter) {
        return new GatewayRateLimitFilter(
            limiter,
            new GatewayProperties(),
            new GatewaySecurityProblemWriter(JsonMapper.builder().build()));
    }
}
