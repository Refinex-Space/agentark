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

package space.refinex.agentark.server.runtime;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 将 WebFlux 认证与访问拒绝写成稳定 RFC 9457 JSON，且不回显 Token 或异常消息。
 *
 * @author refinex
 */
public final class RuntimeSecurityProblemWriter
    implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    /**
     * 安全 JSON 映射器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建 Runtime 安全错误写出器。
     *
     * @param objectMapper Jackson 3 映射器
     */
    public RuntimeSecurityProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(
            objectMapper, "objectMapper must not be null");
    }

    /**
     * 写出未认证的 401 Problem Detail。
     *
     * @param exchange  WebFlux 请求交换
     * @param exception 认证异常；内容不会回显
     * @return 写出完成信号
     */
    @Override
    public Mono<Void> commence(
        ServerWebExchange exchange, AuthenticationException exception) {
        return write(
            exchange, HttpStatus.UNAUTHORIZED, "ARK-RUNTIME-UNAUTHORIZED-00001",
            "Runtime authentication is required");
    }

    /**
     * 写出已认证但无权限的 403 Problem Detail。
     *
     * @param exchange WebFlux 请求交换
     * @param denied   访问拒绝异常；内容不会回显
     * @return 写出完成信号
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return write(
            exchange, HttpStatus.FORBIDDEN, "ARK-RUNTIME-FORBIDDEN-00001",
            "Runtime operation is forbidden");
    }

    /**
     * 写出不含内部异常、Token 和租户细节的标准错误体。
     *
     * @param exchange WebFlux 请求交换
     * @param status   HTTP 状态
     * @param code     稳定错误码
     * @param title    稳定标题
     * @return 写出完成信号
     */
    private Mono<Void> write(
        ServerWebExchange exchange, HttpStatus status, String code, String title) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://agentark.dev/problems/" + code.toLowerCase());
        problem.put("title", title);
        problem.put("status", status.value());
        problem.put("code", code);
        problem.put("detail", title);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(problem);
        } catch (Exception exception) {
            return Mono.error(new IllegalStateException(
                "runtime security problem cannot be serialized", exception));
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
