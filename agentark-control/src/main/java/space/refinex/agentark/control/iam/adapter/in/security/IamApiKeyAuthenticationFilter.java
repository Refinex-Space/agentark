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

package space.refinex.agentark.control.iam.adapter.in.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import space.refinex.agentark.control.iam.application.IamApiKeyService;

import java.io.IOException;
import java.util.Arrays;

/**
 * 解析 Authorization ApiKey 凭据并调用摘要认证服务，明文字符数组在请求内清零。
 *
 * @author refinex
 */
public final class IamApiKeyAuthenticationFilter extends OncePerRequestFilter {

    /**
     * API Key 的认证方案前缀。
     */
    private static final String SCHEME = "ApiKey ";

    /**
     * API Key 认证服务。
     */
    private final IamApiKeyService apiKeyService;

    /**
     * 安全错误写出器。
     */
    private final IamSecurityProblemWriter problemWriter;

    /**
     * 创建 API Key 过滤器。
     *
     * @param apiKeyService API Key 认证服务
     * @param problemWriter 安全错误写出器
     */
    public IamApiKeyAuthenticationFilter(
        IamApiKeyService apiKeyService, IamSecurityProblemWriter problemWriter) {
        this.apiKeyService = java.util.Objects.requireNonNull(
            apiKeyService, "apiKeyService must not be null");
        this.problemWriter = java.util.Objects.requireNonNull(
            problemWriter, "problemWriter must not be null");
    }

    /**
     * 对明确使用 ApiKey Scheme 的请求执行一次认证；无该 Scheme 时交给 Bearer 链。
     *
     * @param request     当前请求
     * @param response    当前响应
     * @param filterChain 后续过滤链
     * @throws ServletException 后续过滤器失败时抛出
     * @throws IOException      响应或后续过滤器写入失败时抛出
     */
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(SCHEME)) {
            filterChain.doFilter(request, response);
            return;
        }
        String credential = authorization.substring(SCHEME.length());
        if (!credential.matches("ark_[A-Za-z0-9_-]{12}_[A-Za-z0-9_-]{43}")) {
            problemWriter.unauthorized(request, response);
            return;
        }
        String prefix = credential.substring(4, 16);
        char[] secret = credential.substring(17).toCharArray();
        try {
            var principal = apiKeyService.authenticate(prefix, secret, "agentark-control");
            if (principal.isEmpty()) {
                problemWriter.unauthorized(request, response);
                return;
            }
            var authenticated = principal.orElseThrow();
            var authorities = authenticated.authorities().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
            SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                    authenticated, null, authorities));
            filterChain.doFilter(request, response);
        } finally {
            Arrays.fill(secret, '\0');
        }
    }
}
