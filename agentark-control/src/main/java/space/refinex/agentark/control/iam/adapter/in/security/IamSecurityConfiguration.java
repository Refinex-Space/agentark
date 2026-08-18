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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import space.refinex.agentark.foundation.security.AgentArkJwtPrincipalConverter;

/**
 * 配置 Control 无状态安全链：健康端点匿名、Public API 必须经 OIDC/JWT 或 API Key 认证。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableWebSecurity
public class IamSecurityConfiguration {

    /**
     * 创建无状态 IAM 安全配置。
     */
    public IamSecurityConfiguration() {
    }

    /**
     * 创建 Control 安全过滤链；未配置 OIDC 时 Bearer 不启用，但 API 仍默认拒绝匿名请求。
     *
     * @param http                Servlet 安全构建器
     * @param apiKeyFilter        API Key 摘要认证过滤器
     * @param problemWriter       稳定安全错误写出器
     * @param jwtDecoders         可选 Foundation JWT Decoder
     * @param principalConverters 可选 Foundation Principal 转换器
     * @return 无状态安全过滤链
     */
    @Bean
    public SecurityFilterChain iamSecurityFilterChain(
        HttpSecurity http,
        IamApiKeyAuthenticationFilter apiKeyFilter,
        IamSecurityProblemWriter problemWriter,
        ObjectProvider<JwtDecoder> jwtDecoders,
        ObjectProvider<AgentArkJwtPrincipalConverter> principalConverters) {

        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .requestMatchers("/internal/v1/**").authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    problemWriter.unauthorized(request, response))
                .accessDeniedHandler((request, response, exception) ->
                    problemWriter.forbidden(request, response)))
            .addFilterBefore(apiKeyFilter, BearerTokenAuthenticationFilter.class)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable);

        JwtDecoder jwtDecoder = jwtDecoders.getIfAvailable();
        if (jwtDecoder != null) {
            AgentArkJwtPrincipalConverter converter = principalConverters.getIfAvailable();
            if (converter == null) {
                throw new IllegalStateException("AgentArkJwtPrincipalConverter is required when JwtDecoder is configured");
            }
            http.oauth2ResourceServer(oauth -> oauth
                .authenticationEntryPoint((request, response, exception) ->
                    problemWriter.unauthorized(request, response))
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(new IamJwtAuthenticationConverter(converter))));
        }
        return http.build();
    }

    /**
     * 禁止 Servlet 容器在 Spring Security Chain 之外重复注册 API Key Filter。
     *
     * <p>同一 OncePerRequestFilter 若先由容器执行，会留下已过滤标记，导致安全链跳过认证并
     * 清除先前线程上下文。关闭容器注册后，Filter 只在明确的 Bearer Filter 前执行一次。
     *
     * @param apiKeyFilter API Key 摘要认证过滤器
     * @return 已禁用的容器 Filter 注册描述
     */
    @Bean
    public FilterRegistrationBean<IamApiKeyAuthenticationFilter> apiKeyFilterRegistration(
        IamApiKeyAuthenticationFilter apiKeyFilter) {
        FilterRegistrationBean<IamApiKeyAuthenticationFilter> registration = new FilterRegistrationBean<>(apiKeyFilter);
        registration.setEnabled(false);
        return registration;
    }
}
