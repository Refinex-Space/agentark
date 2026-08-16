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

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 在 Gateway 边缘解析 API Key，并通过 Control 独立摘要认证结果建立请求级主体。
 *
 * @author refinex
 */
public final class GatewayApiKeyAuthenticationFilter implements WebFilter, Ordered {

    /**
     * API Key 的 Authorization 认证方案前缀。
     */
    private static final String SCHEME = "ApiKey ";

    /**
     * API Key 严格格式，长度与 Control 单次生成格式一致。
     */
    private static final String CREDENTIAL_PATTERN =
        "ark_[A-Za-z0-9_-]{12}_[A-Za-z0-9_-]{43}";

    /**
     * API Key 验证端口。
     */
    private final ControlApiKeyVerifier verifier;

    /**
     * 安全错误写出器。
     */
    private final GatewaySecurityProblemWriter problemWriter;

    /**
     * 创建 API Key 认证过滤器。
     *
     * @param verifier      Control 验证端口
     * @param problemWriter 安全错误写出器
     */
    public GatewayApiKeyAuthenticationFilter(
        ControlApiKeyVerifier verifier, GatewaySecurityProblemWriter problemWriter) {
        this.verifier = java.util.Objects.requireNonNull(verifier, "verifier must not be null");
        this.problemWriter = java.util.Objects.requireNonNull(
            problemWriter, "problemWriter must not be null");
    }

    /**
     * 对明确使用 ApiKey Scheme 的 Control 公共请求执行认证，其余请求交给 JWT 链。
     *
     * @param exchange 当前请求交换
     * @param chain    后续 WebFlux 过滤链
     * @return 过滤完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authorization = exchange.getRequest().getHeaders()
            .getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(SCHEME)) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        String credential = authorization.substring(SCHEME.length());
        if (!path.startsWith("/api/v1/")
            || path.startsWith("/api/v1/runtime/")
            || path.startsWith("/api/v1/scheduler/")
            || !credential.matches(CREDENTIAL_PATTERN)) {
            return problemWriter.commence(
                exchange, new BadCredentialsException("invalid API key request"));
        }
        return verifier.verify(credential)
            .onErrorResume(error -> problemWriter.serviceUnavailable(exchange)
                .then(Mono.empty()))
            .flatMap(result -> result.<Mono<Void>>map(principal -> {
                var authorities = principal.authorities().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, authorities);
                return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
            }).orElseGet(() -> problemWriter.commence(
                exchange, new BadCredentialsException("invalid API key"))));
    }

    /**
     * 在 Spring Security 授权判断前建立 API Key SecurityContext。
     *
     * @return 高于默认安全代理的过滤顺序
     */
    @Override
    public int getOrder() {
        return -110;
    }
}
