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

package space.refinex.agentark.server.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import space.refinex.agentark.foundation.security.AgentArkJwtPrincipalConverter;
import space.refinex.agentark.foundation.security.AgentArkSecurityProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * 为 Scheduler Webhook、管理 API 与 Internal API 建立失败默认的无状态安全链。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(AgentArkSecurityProperties.class)
public class SchedulerSecurityConfiguration {

    /**
     * 创建 Scheduler 安全错误写出器。
     *
     * @param jsonMapper 统一 JSON 映射器
     * @return 安全错误写出器
     */
    @Bean
    public SchedulerSecurityProblemWriter schedulerSecurityProblemWriter(JsonMapper jsonMapper) {
        return new SchedulerSecurityProblemWriter(jsonMapper);
    }

    /**
     * 安全未显式启用时仅开放健康端点和自身 HMAC 验签的 Webhook，拒绝管理与内部 API。
     *
     * @param http          Servlet 安全构建器
     * @param problemWriter 安全错误写出器
     * @return 失败默认安全链
     * @throws Exception Spring Security 配置失败时抛出
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "agentark.foundation.security", name = "enabled",
        havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain disabledSchedulerSecurity(
        HttpSecurity http, SchedulerSecurityProblemWriter problemWriter) throws Exception {
        return common(http, problemWriter)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/scheduler/webhooks/**").permitAll()
                .anyRequest().denyAll())
            .build();
    }

    /**
     * 安全启用时开放 HMAC Webhook，并要求管理与 Internal API 使用 Bearer JWT。
     *
     * @param http               Servlet 安全构建器
     * @param decoder            严格 JWT Decoder
     * @param principalConverter Foundation Principal 转换器
     * @param problemWriter      安全错误写出器
     * @return JWT Resource Server 安全链
     * @throws Exception Spring Security 配置失败时抛出
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "agentark.foundation.security", name = "enabled", havingValue = "true")
    public SecurityFilterChain schedulerSecurity(
        HttpSecurity http,
        JwtDecoder decoder,
        AgentArkJwtPrincipalConverter principalConverter,
        SchedulerSecurityProblemWriter problemWriter) throws Exception {
        SchedulerJwtAuthenticationConverter converter =
            new SchedulerJwtAuthenticationConverter(principalConverter);
        return common(http, problemWriter)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/scheduler/webhooks/**").permitAll()
                .requestMatchers("/api/v1/scheduler/**", "/internal/v1/scheduler/**")
                .authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth
                .authenticationEntryPoint((request, response, exception) ->
                    problemWriter.unauthorized(request, response))
                .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
            .build();
    }

    /**
     * 应用 Scheduler 无状态、无表单、无 Basic Auth 与稳定安全错误的共同配置。
     *
     * @param http          Servlet 安全构建器
     * @param problemWriter 安全错误写出器
     * @return 已应用共同约束的安全构建器
     * @throws Exception Spring Security 配置失败时抛出
     */
    private HttpSecurity common(
        HttpSecurity http, SchedulerSecurityProblemWriter problemWriter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    problemWriter.unauthorized(request, response))
                .accessDeniedHandler((request, response, exception) ->
                    problemWriter.forbidden(request, response)))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable());
    }
}
