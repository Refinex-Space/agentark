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

import org.springframework.http.HttpMethod;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Set;

/**
 * 只要求携带 BFF Session Cookie 的非安全方法通过 CSRF 校验，不阻断无状态 API Client。
 *
 * @author refinex
 */
public final class GatewayBffCsrfMatcher implements ServerWebExchangeMatcher {

    /**
     * 不产生服务器副作用的 HTTP 方法。
     */
    private static final Set<HttpMethod> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.TRACE);

    /**
     * BFF 服务端会话 Cookie 名称。
     */
    private final String sessionCookieName;

    /**
     * 创建 BFF CSRF 匹配器。
     *
     * @param sessionCookieName 服务端会话 Cookie 名称
     */
    public GatewayBffCsrfMatcher(String sessionCookieName) {
        this.sessionCookieName = Objects.requireNonNull(
            sessionCookieName, "sessionCookieName must not be null");
    }

    /**
     * Session Cookie 非安全请求需要 CSRF；Bearer/API Key 无状态请求不需要浏览器 CSRF Token。
     *
     * @param exchange 当前请求交换
     * @return 是否要求 CSRF
     */
    @Override
    public Mono<MatchResult> matches(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        boolean sessionRequest = exchange.getRequest().getCookies().containsKey(sessionCookieName);
        return method != null && !SAFE_METHODS.contains(method) && sessionRequest
            ? MatchResult.match()
            : MatchResult.notMatch();
    }
}
