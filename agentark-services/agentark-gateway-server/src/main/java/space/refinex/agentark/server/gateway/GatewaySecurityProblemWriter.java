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

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.web.AgentArkReactiveWebAutoConfiguration;
import space.refinex.agentark.foundation.web.RequestContext;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 将 Gateway 认证、授权、限流和依赖失败写为不泄漏凭据的 RFC 9457 响应。
 *
 * @author refinex
 */
public final class GatewaySecurityProblemWriter
    implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    /**
     * 应用统一 JSON 映射器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建安全错误写出器。
     *
     * @param objectMapper Jackson 3 映射器
     */
    public GatewaySecurityProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 写出未认证响应，不回显 Token、API Key 或底层异常。
     *
     * @param exchange  当前请求交换
     * @param exception 认证异常
     * @return 写出完成信号
     */
    @Override
    public Mono<Void> commence(
        ServerWebExchange exchange, AuthenticationException exception) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return write(
            exchange,
            HttpStatus.UNAUTHORIZED,
            "ARK-GATEWAY-UNAUTHORIZED-00001",
            "身份认证失败",
            "请求需要有效的 Bearer Token 或 API Key");
    }

    /**
     * 写出已认证但无权访问的响应。
     *
     * @param exchange 当前请求交换
     * @param denied   访问拒绝异常
     * @return 写出完成信号
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return write(
            exchange,
            HttpStatus.FORBIDDEN,
            "ARK-GATEWAY-FORBIDDEN-00001",
            "没有访问权限",
            "当前主体无权访问该公共入口");
    }

    /**
     * 写出 Control 暂时无法完成 API Key 验证的响应。
     *
     * @param exchange 当前请求交换
     * @return 写出完成信号
     */
    public Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        return write(
            exchange,
            HttpStatus.SERVICE_UNAVAILABLE,
            "ARK-GATEWAY-AUTH-UPSTREAM-00001",
            "认证服务暂不可用",
            "API Key 暂时无法验证，请稍后重试");
    }

    /**
     * 写出 Redis 暂时无法完成限流判定的响应，避免在保护能力失效时静默放行。
     *
     * @param exchange 当前请求交换
     * @return 写出完成信号
     */
    public Mono<Void> rateLimitUnavailable(ServerWebExchange exchange) {
        return write(
            exchange,
            HttpStatus.SERVICE_UNAVAILABLE,
            "ARK-GATEWAY-RATE-LIMIT-UPSTREAM-00001",
            "限流服务暂不可用",
            "当前无法安全完成限流判定，请稍后重试");
    }

    /**
     * 写出超过 Redis 限流额度的响应。
     *
     * @param exchange 当前请求交换
     * @return 写出完成信号
     */
    public Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        return write(
            exchange,
            HttpStatus.TOO_MANY_REQUESTS,
            "ARK-GATEWAY-RATE-LIMIT-00001",
            "请求过于频繁",
            "当前请求已超过固定窗口额度");
    }

    /**
     * 写出稳定 ProblemDetail 字段并附加请求关联标识。
     *
     * @param exchange 当前请求交换
     * @param status   HTTP 状态
     * @param code     稳定错误码
     * @param title    中文标题
     * @param detail   安全详情
     * @return 写出完成信号
     */
    private Mono<Void> write(
        ServerWebExchange exchange,
        HttpStatus status,
        String code,
        String title,
        String detail) {
        if (exchange.getResponse().isCommitted()) {
            return exchange.getResponse().setComplete();
        }
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "urn:agentark:error:" + code);
        problem.put("title", title);
        problem.put("status", status.value());
        problem.put("code", code);
        problem.put("detail", detail);
        problem.put("requestId", requestId(exchange));
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(problem);
        } catch (Exception error) {
            return Mono.error(new IllegalStateException(
                "gateway problem detail cannot be serialized", error));
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * 读取 Foundation 已清洗请求标识；过滤顺序异常时生成临时关联值。
     *
     * @param exchange 当前请求交换
     * @return 非敏感请求标识
     */
    private String requestId(ServerWebExchange exchange) {
        RequestContext context = exchange.getAttribute(
            AgentArkReactiveWebAutoConfiguration.REQUEST_CONTEXT_ATTRIBUTE);
        return context == null ? UUID.randomUUID().toString() : context.requestId();
    }
}
