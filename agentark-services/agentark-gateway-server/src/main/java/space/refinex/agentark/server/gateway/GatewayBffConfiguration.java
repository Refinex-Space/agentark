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

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import space.refinex.agentark.foundation.security.AgentArkSecurityProperties;

/**
 * 装配 OIDC Client、Redis WebSession Cookie 和 BFF Token Relay。
 *
 * @author refinex
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayBffProperties.class)
@ConditionalOnProperty(prefix = "agentark.gateway.bff", name = "enabled", havingValue = "true")
public class GatewayBffConfiguration {

    /**
     * 创建单一受信 OIDC Client Registration；生产可使用 Discovery，本地使用显式端点。
     *
     * @param properties         BFF 配置
     * @param securityProperties Resource Server 安全配置
     * @return 响应式 Client Registration 仓库
     */
    @Bean
    public ReactiveClientRegistrationRepository gatewayClientRegistrationRepository(
        GatewayBffProperties properties,
        AgentArkSecurityProperties securityProperties) {
        properties.validate();
        if (!securityProperties.isEnabled()) {
            throw new IllegalStateException("Gateway BFF requires JWT resource server security");
        }
        ClientRegistration.Builder builder;
        if (properties.hasExplicitProviderEndpoints()) {
            builder = ClientRegistration.withRegistrationId(properties.getRegistrationId())
                .authorizationUri(properties.getAuthorizationUri().toString())
                .tokenUri(properties.getTokenUri().toString())
                .jwkSetUri(properties.getJwkSetUri().toString())
                .userInfoUri(properties.getUserInfoUri().toString())
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .issuerUri(properties.getIssuerUri().toString())
                .providerConfigurationMetadata(java.util.Map.of(
                    "end_session_endpoint", properties.getEndSessionUri().toString()));
        } else {
            builder = ClientRegistrations.fromIssuerLocation(properties.getIssuerUri().toString())
                .registrationId(properties.getRegistrationId());
        }
        ClientRegistration registration = builder
            .clientId(properties.getClientId())
            .clientSecret(properties.getClientSecret())
            .clientName(properties.getClientName())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(properties.getRedirectUri().toString())
            .scope("openid", "profile", "email")
            .build();
        return new InMemoryReactiveClientRegistrationRepository(registration);
    }

    /**
     * 创建会在 Access Token 过期时使用 Refresh Token 轮换的 Authorized Client 管理器。
     *
     * @param registrations     OIDC Client Registration 仓库
     * @param authorizedClients 保存于 Redis WebSession 的 Authorized Client 仓库
     * @return 可刷新的 Authorized Client 管理器
     */
    @Bean
    @ConditionalOnMissingBean(ReactiveOAuth2AuthorizedClientManager.class)
    public ReactiveOAuth2AuthorizedClientManager gatewayAuthorizedClientManager(
        ReactiveClientRegistrationRepository registrations,
        ServerOAuth2AuthorizedClientRepository authorizedClients) {
        ReactiveOAuth2AuthorizedClientProvider provider =
            ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build();
        DefaultReactiveOAuth2AuthorizedClientManager manager =
            new DefaultReactiveOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    /**
     * 创建仅为 OIDC 浏览器会话向下游注入未过期或已刷新 Access Token 的全局过滤器。
     *
     * @param authorizedClientManager 可刷新的 Authorized Client 管理器
     * @return Token Relay 过滤器
     */
    @Bean
    public GatewayBffTokenRelayFilter gatewayBffTokenRelayFilter(
        ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        return new GatewayBffTokenRelayFilter(authorizedClientManager);
    }

}
