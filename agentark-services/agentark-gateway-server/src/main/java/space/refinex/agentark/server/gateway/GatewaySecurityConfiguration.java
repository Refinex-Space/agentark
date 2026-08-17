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

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import space.refinex.agentark.foundation.redis.RateLimiter;
import space.refinex.agentark.foundation.security.AgentArkJwtPrincipalConverter;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;

/**
 * 配置 Gateway JWT、API Key、CORS、安全响应头、Header 清洗和可选 Redis 限流。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewaySecurityConfiguration {

    /**
     * 创建 Gateway 安全错误写出器。
     *
     * @param objectMapper 应用统一 JSON 映射器
     * @return RFC 9457 安全错误写出器
     */
    @Bean
    public GatewaySecurityProblemWriter gatewaySecurityProblemWriter(ObjectMapper objectMapper) {
        return new GatewaySecurityProblemWriter(objectMapper);
    }

    /**
     * 创建清除客户端派生身份 Header 的全局过滤器。
     *
     * @return Header 清理过滤器
     */
    @Bean
    public GatewayHeaderSanitizationFilter gatewayHeaderSanitizationFilter() {
        return new GatewayHeaderSanitizationFilter();
    }

    /**
     * 创建 API Key 缓存使用的 UTC 时钟。
     *
     * @return UTC 系统时钟
     */
    @Bean("gatewayClock")
    public Clock gatewayClock() {
        return Clock.systemUTC();
    }

    /**
     * 创建绑定 Control 基础地址的非阻塞 API Key 内部客户端。
     *
     * @param properties Gateway 地址配置
     * @param builders   Spring Boot 可选观测客户端构建器
     * @param observationRegistry 当前 Micrometer Observation Registry
     * @return 无缓存 Control 客户端
     */
    @Bean
    public ControlApiKeyClient controlApiKeyClient(
        GatewayProperties properties,
        ObjectProvider<WebClient.Builder> builders,
        ObservationRegistry observationRegistry) {
        WebClient.Builder builder = builders.getIfAvailable(
            () -> WebClient.builder().observationRegistry(observationRegistry));
        WebClient webClient = builder
            .baseUrl(properties.getControlBaseUrl().toString())
            .build();
        return new HttpControlApiKeyClient(webClient);
    }

    /**
     * 创建只缓存成功主体且以凭据摘要为键的短缓存验证器。
     *
     * @param client     Control 无缓存客户端
     * @param properties Gateway 缓存配置
     * @param clock      UTC 时钟
     * @return API Key 验证端口
     */
    @Bean
    public ControlApiKeyVerifier controlApiKeyVerifier(
        ControlApiKeyClient client,
        GatewayProperties properties,
        @Qualifier("gatewayClock") Clock clock) {
        return new CachedControlApiKeyVerifier(
            client,
            properties.getApiKeyCacheTtl(),
            properties.getApiKeyCacheMaxEntries(),
            clock);
    }

    /**
     * 安全启用时创建 API Key 认证过滤器；安全关闭时公共 API 保持失败默认。
     *
     * @param verifier      API Key 验证端口
     * @param problemWriter 安全错误写出器
     * @return API Key WebFlux 过滤器
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "agentark.foundation.security", name = "enabled", havingValue = "true")
    public GatewayApiKeyAuthenticationFilter gatewayApiKeyAuthenticationFilter(
        ControlApiKeyVerifier verifier, GatewaySecurityProblemWriter problemWriter) {
        return new GatewayApiKeyAuthenticationFilter(verifier, problemWriter);
    }

    /**
     * 限流显式启用时要求 Redis RateLimiter 存在，避免保护能力缺失时静默放行。
     *
     * @param rateLimiter  Redis 原子限流端口
     * @param properties   Gateway 限流配置
     * @param problemWriter 安全错误写出器
     * @return Gateway 限流过滤器
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "agentark.gateway", name = "rate-limit-enabled", havingValue = "true")
    public GatewayRateLimitFilter gatewayRateLimitFilter(
        RateLimiter rateLimiter,
        GatewayProperties properties,
        GatewaySecurityProblemWriter problemWriter) {
        return new GatewayRateLimitFilter(rateLimiter, properties, problemWriter);
    }

    /**
     * 创建精确 Origin CORS 白名单；空列表代表禁止所有跨域浏览器请求。
     *
     * @param properties Gateway CORS 配置
     * @return WebFlux CORS 配置源
     */
    @Bean
    public CorsConfigurationSource gatewayCorsConfigurationSource(
        GatewayProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.getAllowedOrigins());
        cors.setAllowedMethods(List.of(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()));
        cors.setAllowedHeaders(List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            "Idempotency-Key",
            "Last-Event-ID",
            "X-Request-Id",
            "X-AgentArk-Organization-Id",
            "X-AgentArk-Project-Id",
            "X-AgentArk-Environment-Id",
            "X-AgentArk-Webhook-Signature",
            "X-AgentArk-Webhook-Timestamp",
            "X-AgentArk-Webhook-Nonce"));
        cors.setExposedHeaders(List.of(
            HttpHeaders.LOCATION,
            "X-Request-Id",
            HttpHeaders.ETAG,
            HttpHeaders.RETRY_AFTER,
            "X-RateLimit-Remaining"));
        cors.setAllowCredentials(!properties.getAllowedOrigins().isEmpty());
        cors.setMaxAge(3_600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    /**
     * 安全未启用时仅开放健康探针、Webhook 回调和边缘内部拒绝路由，其余请求全部拒绝。
     *
     * @param http          WebFlux Security Builder
     * @param corsSource    精确 CORS 配置源
     * @param problemWriter 安全错误写出器
     * @return 失败默认安全链
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "agentark.foundation.security", name = "enabled",
        havingValue = "false", matchIfMissing = true)
    public SecurityWebFilterChain disabledGatewaySecurity(
        ServerHttpSecurity http,
        CorsConfigurationSource corsSource,
        GatewaySecurityProblemWriter problemWriter) {
        return baseSecurity(http, corsSource, problemWriter)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/health/**").permitAll()
                .pathMatchers("/internal", "/internal/**").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/v1/scheduler/webhooks/**").permitAll()
                .anyExchange().denyAll())
            .build();
    }

    /**
     * 将 Foundation 阻塞 JwtDecoder 包装到 boundedElastic，避免阻塞 Gateway 事件循环。
     *
     * @param jwtDecoder 已配置算法、Issuer、Audience 和时间校验的 Decoder
     * @return 响应式 JWT Decoder
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "agentark.foundation.security", name = "enabled", havingValue = "true")
    public ReactiveJwtDecoder gatewayReactiveJwtDecoder(JwtDecoder jwtDecoder) {
        return token -> Mono.fromCallable(() -> jwtDecoder.decode(token))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 安全启用时要求所有公共 API 经 Bearer JWT 或已验证 API Key 认证；Webhook 由下游签名校验。
     *
     * @param http               WebFlux Security Builder
     * @param corsSource         精确 CORS 配置源
     * @param decoder            响应式 JWT Decoder
     * @param principalConverter Foundation Principal 转换器
     * @param problemWriter      安全错误写出器
     * @return 生产安全链
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "agentark.foundation.security", name = "enabled", havingValue = "true")
    public SecurityWebFilterChain gatewaySecurity(
        ServerHttpSecurity http,
        CorsConfigurationSource corsSource,
        ReactiveJwtDecoder decoder,
        AgentArkJwtPrincipalConverter principalConverter,
        GatewaySecurityProblemWriter problemWriter) {
        GatewayJwtAuthenticationConverter converter =
            new GatewayJwtAuthenticationConverter(principalConverter);
        return baseSecurity(http, corsSource, problemWriter)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/health/**").permitAll()
                .pathMatchers("/internal", "/internal/**").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/v1/scheduler/webhooks/**").permitAll()
                .pathMatchers("/actuator/info", "/api/v1/**").authenticated()
                .anyExchange().denyAll())
            .oauth2ResourceServer(resourceServer -> resourceServer
                .authenticationEntryPoint(problemWriter)
                .accessDeniedHandler(problemWriter)
                .jwt(jwt -> jwt
                    .jwtDecoder(decoder)
                    .jwtAuthenticationConverter(converter)))
            .build();
    }

    /**
     * 应用两条安全链共享的无状态协议、CORS、异常和安全响应头规则。
     *
     * @param http          WebFlux Security Builder
     * @param corsSource    精确 CORS 配置源
     * @param problemWriter 安全错误写出器
     * @return 可继续追加授权规则的 Builder
     */
    private ServerHttpSecurity baseSecurity(
        ServerHttpSecurity http,
        CorsConfigurationSource corsSource,
        GatewaySecurityProblemWriter problemWriter) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .cors(cors -> cors.configurationSource(corsSource))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(problemWriter)
                .accessDeniedHandler(problemWriter))
            .headers(headers -> headers
                .contentSecurityPolicy(policy -> policy.policyDirectives("default-src 'none'"))
                .frameOptions(frame -> frame.mode(
                    XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                .referrerPolicy(referrer -> referrer.policy(
                    ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.NO_REFERRER))
                .permissionsPolicy(policy -> policy.policy(
                    "camera=(), microphone=(), geolocation=(), payment=()")));
    }
}
