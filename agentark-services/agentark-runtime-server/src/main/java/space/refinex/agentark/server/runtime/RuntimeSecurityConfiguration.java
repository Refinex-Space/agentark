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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import space.refinex.agentark.foundation.security.AgentArkJwtPrincipalConverter;
import space.refinex.agentark.foundation.security.AgentArkSecurityProperties;
import tools.jackson.databind.ObjectMapper;

/**
 * 为 Runtime WebFlux API 建立安全失败默认与 JWT Resource Server 过滤链。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentArkSecurityProperties.class)
public class RuntimeSecurityConfiguration {

    /**
     * 创建不泄漏认证异常与凭证内容的 Runtime 安全错误写出器。
     *
     * @param objectMapper Jackson 3 映射器
     * @return 安全错误写出器
     */
    @Bean
    public RuntimeSecurityProblemWriter runtimeSecurityProblemWriter(ObjectMapper objectMapper) {
        return new RuntimeSecurityProblemWriter(objectMapper);
    }

    /**
     * 安全未显式启用时只开放健康与信息端点，并拒绝全部 Runtime API。
     *
     * @param http          WebFlux Security Builder
     * @param problemWriter 安全错误写出器
     * @return 安全失败默认过滤链
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "agentark.foundation.security", name = "enabled",
        havingValue = "false", matchIfMissing = true)
    public SecurityWebFilterChain disabledRuntimeSecurity(
        ServerHttpSecurity http, RuntimeSecurityProblemWriter problemWriter) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(problemWriter)
                .accessDeniedHandler(problemWriter))
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .anyExchange().denyAll())
            .build();
    }

    /**
     * 将 Foundation 阻塞 JwtDecoder 包装为 boundedElastic ReactiveJwtDecoder。
     *
     * @param jwtDecoder 已配置 Issuer/JWK/Audience 校验的 Decoder
     * @return 响应式 Decoder
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "agentark.foundation.security", name = "enabled", havingValue = "true")
    public ReactiveJwtDecoder reactiveJwtDecoder(JwtDecoder jwtDecoder) {
        return token -> Mono.fromCallable(() -> jwtDecoder.decode(token))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 安全启用时要求所有 Runtime Public API 使用 Bearer JWT。
     *
     * @param http               WebFlux Security Builder
     * @param decoder            Reactive JWT Decoder
     * @param principalConverter Foundation Principal Converter
     * @param problemWriter      安全错误写出器
     * @return JWT Resource Server 过滤链
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "agentark.foundation.security", name = "enabled", havingValue = "true")
    public SecurityWebFilterChain runtimeSecurity(
        ServerHttpSecurity http,
        ReactiveJwtDecoder decoder,
        AgentArkJwtPrincipalConverter principalConverter,
        RuntimeSecurityProblemWriter problemWriter) {
        RuntimeJwtAuthenticationConverter converter =
            new RuntimeJwtAuthenticationConverter(principalConverter);
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(problemWriter)
                .accessDeniedHandler(problemWriter))
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .pathMatchers("/api/v1/runtime/**").authenticated()
                .anyExchange().denyAll())
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.jwtDecoder(decoder)
                    .jwtAuthenticationConverter(converter)))
            .build();
    }
}
