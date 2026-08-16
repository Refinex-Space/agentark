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

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import space.refinex.agentark.foundation.redis.RateLimitDecision;
import space.refinex.agentark.foundation.redis.RateLimiter;

import java.net.InetSocketAddress;
import java.security.Principal;

/**
 * 使用 Foundation Redis 固定窗口算法对公共请求执行主体级或来源级限流。
 *
 * @author refinex
 */
public final class GatewayRateLimitFilter implements GlobalFilter, Ordered {

    /**
     * Redis 原子限流端口。
     */
    private final RateLimiter rateLimiter;

    /**
     * Gateway 限流配置。
     */
    private final GatewayProperties properties;

    /**
     * 安全错误写出器。
     */
    private final GatewaySecurityProblemWriter problemWriter;

    /**
     * 创建 Redis 限流过滤器。
     *
     * @param rateLimiter  Redis 原子限流端口
     * @param properties   Gateway 限流配置
     * @param problemWriter 安全错误写出器
     */
    public GatewayRateLimitFilter(
        RateLimiter rateLimiter,
        GatewayProperties properties,
        GatewaySecurityProblemWriter problemWriter) {
        this.rateLimiter = java.util.Objects.requireNonNull(
            rateLimiter, "rateLimiter must not be null");
        this.properties = java.util.Objects.requireNonNull(
            properties, "properties must not be null");
        this.problemWriter = java.util.Objects.requireNonNull(
            problemWriter, "problemWriter must not be null");
    }

    /**
     * 健康和内部路径不消耗公共额度；Webhook 按远端来源，其余请求按已认证主体限流。
     *
     * @param exchange 当前请求交换
     * @param chain    后续 Gateway 过滤链
     * @return 限流或继续代理信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator/") || path.startsWith("/internal/")) {
            return chain.filter(exchange);
        }
        boolean webhook = path.startsWith("/api/v1/scheduler/webhooks/");
        return subjectKey(exchange, webhook)
            .flatMap(subject -> Mono.fromCallable(() -> rateLimiter.acquire(
                    webhook ? "webhook" : "public",
                    subject,
                    webhook ? properties.getWebhookRateLimit() : properties.getDefaultRateLimit(),
                    properties.getRateLimitWindow()))
                .subscribeOn(Schedulers.boundedElastic()))
            .onErrorResume(error -> problemWriter.rateLimitUnavailable(exchange)
                .then(Mono.empty()))
            .flatMap(decision -> handleDecision(exchange, chain, decision));
    }

    /**
     * 解析不可由普通客户端 Header 直接指定的限流主体键。
     *
     * @param exchange 当前请求交换
     * @param webhook  是否为匿名 Webhook
     * @return 主体或远端来源键
     */
    private Mono<String> subjectKey(ServerWebExchange exchange, boolean webhook) {
        if (webhook) {
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            String host = remoteAddress == null ? "unknown" : remoteAddress.getHostString();
            return Mono.just("source:" + host);
        }
        return exchange.<Principal>getPrincipal()
            .map(principal -> "principal:" + principal.getName())
            .switchIfEmpty(Mono.just("anonymous"));
    }

    /**
     * 写出剩余额度；被拒绝时同时提供向上取整的 Retry-After 秒数。
     *
     * @param exchange 当前请求交换
     * @param chain    后续 Gateway 过滤链
     * @param decision Redis 原子限流结果
     * @return 拒绝或继续代理信号
     */
    private Mono<Void> handleDecision(
        ServerWebExchange exchange,
        GatewayFilterChain chain,
        RateLimitDecision decision) {
        exchange.getResponse().getHeaders().set(
            "X-RateLimit-Remaining", Long.toString(decision.remaining()));
        if (decision.allowed()) {
            return chain.filter(exchange);
        }
        long retryAfterSeconds = Math.max(
            1L, Math.ceilDiv(decision.retryAfter().toMillis(), 1_000L));
        exchange.getResponse().getHeaders().set(
            HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return problemWriter.tooManyRequests(exchange);
    }

    /**
     * 在身份认证完成后、下游代理前执行限流。
     *
     * @return Gateway 全局过滤顺序
     */
    @Override
    public int getOrder() {
        return -20;
    }
}
