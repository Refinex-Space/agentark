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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import space.refinex.agentark.server.gateway.GatewayIdentityModels.AccountStatus;
import space.refinex.agentark.server.gateway.GatewayIdentityModels.LocalPrincipal;

import java.util.Objects;

/**
 * 将本地 Redis Session 转换为下游可独立校验的短期 RS256 JWT。
 *
 * @author refinex
 */
public final class GatewayLocalIdentityTokenRelayFilter implements GlobalFilter, Ordered {

    /**
     * Identity 应用服务。
     */
    private final GatewayIdentityService identityService;

    /**
     * 内部 JWT 签发器。
     */
    private final GatewayIdentityTokenService tokens;

    /**
     * 创建本地 Session Token Relay。
     */
    public GatewayLocalIdentityTokenRelayFilter(
        GatewayIdentityService identityService, GatewayIdentityTokenService tokens) {
        this.identityService = Objects.requireNonNull(identityService, "identityService must not be null");
        this.tokens = Objects.requireNonNull(tokens, "tokens must not be null");
    }

    /**
     * 只处理业务 API 的本地主体；Bearer、API Key、认证端点保持原请求。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/v1/")
            || path.startsWith("/api/v1/auth/")
            || path.startsWith("/api/v1/identity/")) {
            return chain.filter(exchange);
        }
        return exchange.getPrincipal()
            .ofType(Authentication.class)
            .filter(authentication -> authentication.getPrincipal() instanceof LocalPrincipal)
            .flatMap(authentication -> {
                LocalPrincipal principal = (LocalPrincipal) authentication.getPrincipal();
                return identityService.currentAccount(principal.id())
                    .flatMap(account -> {
                        if (account.status() != AccountStatus.ACTIVE
                            || account.authVersion() != principal.authVersion()) {
                            return exchange.getSession()
                                .flatMap(session -> session.invalidate())
                                .then(Mono.defer(() -> {
                                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                    return exchange.getResponse().setComplete();
                                }));
                        }
                        String token = tokens.issueUserToken(principal);
                        var request = exchange.getRequest().mutate()
                            .headers(headers -> headers.set(
                                HttpHeaders.AUTHORIZATION, "Bearer " + token))
                            .build();
                        return chain.filter(exchange.mutate().request(request).build());
                    });
            })
            .switchIfEmpty(chain.filter(exchange));
    }

    /**
     * 在身份 Header 清洗后、路由前执行。
     */
    @Override
    public int getOrder() {
        return -79;
    }
}
