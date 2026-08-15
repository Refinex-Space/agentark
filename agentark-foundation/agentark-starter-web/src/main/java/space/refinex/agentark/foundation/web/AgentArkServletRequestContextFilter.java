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

package space.refinex.agentark.foundation.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为 Servlet 请求建立受控 Request ID、Trace ID 和认证租户上下文作用域。
 *
 * @author refinex
 */
public final class AgentArkServletRequestContextFilter extends OncePerRequestFilter {

    /**
     * Web 配置属性。
     */
    private final AgentArkWebProperties properties;

    /**
     * 请求上下文工厂。
     */
    private final RequestContextFactory requestContextFactory;

    /**
     * 同步请求上下文访问器。
     */
    private final RequestContextAccessor requestContextAccessor;

    /**
     * 创建 Servlet 请求上下文过滤器。
     *
     * @param properties             Web 配置属性
     * @param requestContextFactory  请求上下文工厂
     * @param requestContextAccessor 同步上下文访问器
     */
    public AgentArkServletRequestContextFilter(
        AgentArkWebProperties properties,
        RequestContextFactory requestContextFactory,
        RequestContextAccessor requestContextAccessor) {
        this.properties = java.util.Objects.requireNonNull(properties, "properties must not be null");
        this.requestContextFactory =
            java.util.Objects.requireNonNull(
                requestContextFactory, "requestContextFactory must not be null");
        this.requestContextAccessor =
            java.util.Objects.requireNonNull(
                requestContextAccessor, "requestContextAccessor must not be null");
    }

    /**
     * 建立上下文、回写 Request ID 并确保请求结束后清理 ThreadLocal。
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 后续过滤链
     * @throws ServletException 后续 Servlet 处理失败时抛出
     * @throws IOException      HTTP 输入输出失败时抛出
     */
    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        RequestContext context =
            requestContextFactory.create(
                request.getHeader(properties.getRequestIdHeader()), request.getHeader("traceparent"));
        response.setHeader(properties.getRequestIdHeader(), context.requestId());
        try (RequestContextAccessor.Scope ignored = requestContextAccessor.open(context)) {
            filterChain.doFilter(request, response);
        }
    }
}
