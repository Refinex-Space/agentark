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
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * 将服务端 OIDC 会话的短期 Access Token 转发给独立验证 JWT 的下游平面。
 *
 * @author refinex
 */
public final class GatewayBffTokenRelayFilter implements GlobalFilter, Ordered {

    /**
     * 可刷新 Access Token 的服务端 Authorized Client 管理器。
     */
    private final ReactiveOAuth2AuthorizedClientManager authorizedClientManager;

    /**
     * 创建 BFF Token Relay。
     *
     * @param authorizedClientManager 包含 Refresh Token 轮换的 Authorized Client 管理器
     */
    public GatewayBffTokenRelayFilter(
        ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = Objects.requireNonNull(
            authorizedClientManager, "authorizedClientManager must not be null");
    }

    /**
     * 只为 OIDC Session Principal 转发未过期或已刷新的 Access Token；Bearer/API Key 请求保持原凭据。
     *
     * @param exchange 当前 Gateway 请求
     * @param chain    后续代理链
     * @return 转发完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/v1/") || path.startsWith("/api/v1/auth/")) {
            return chain.filter(exchange);
        }
        return exchange.getPrincipal()
            .ofType(OAuth2AuthenticationToken.class)
            .flatMap(authentication -> authorizedClientManager.authorize(
                OAuth2AuthorizeRequest
                    .withClientRegistrationId(authentication.getAuthorizedClientRegistrationId())
                    .principal(authentication)
                    .attribute(ServerWebExchange.class.getName(), exchange)
                    .build()))
            .flatMap(client -> {
                var request = exchange.getRequest().mutate()
                    .headers(headers -> headers.set(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + client.getAccessToken().getTokenValue()))
                    .build();
                return chain.filter(exchange.mutate().request(request).build());
            })
            .switchIfEmpty(chain.filter(exchange));
    }

    /**
     * 在身份 Header 清洗后、实际路由前执行。
     *
     * @return 固定过滤顺序
     */
    @Override
    public int getOrder() {
        return -80;
    }
}
