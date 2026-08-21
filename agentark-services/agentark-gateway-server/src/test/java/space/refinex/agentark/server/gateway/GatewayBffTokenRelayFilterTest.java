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

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * 验证 BFF 只从服务端 Authorized Client 向业务路由转发短期 Access Token。
 *
 * @author refinex
 */
class GatewayBffTokenRelayFilterTest {

    /**
     * 验证 OIDC Session 请求获得服务端 Bearer，浏览器无需提供 Authorization Header。
     */
    @Test
    void relaysAuthorizedClientAccessToken() {
        ClientRegistration registration = registration();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "server-side-access-token",
            Instant.now().minusSeconds(1),
            Instant.now().plusSeconds(60));
        OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(
            registration, "user-1", accessToken);
        DefaultOAuth2User principal = new DefaultOAuth2User(
            Set.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("sub", "user-1"),
            "sub");
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
            principal, principal.getAuthorities(), "agentark");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/organizations").build())
            .mutate()
            .principal(Mono.just(authentication))
            .build();
        ReactiveOAuth2AuthorizedClientManager manager =
            mock(ReactiveOAuth2AuthorizedClientManager.class);
        doReturn(Mono.just(client)).when(manager).authorize(any(OAuth2AuthorizeRequest.class));
        GatewayBffTokenRelayFilter filter = new GatewayBffTokenRelayFilter(manager);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = current -> {
            captured.set(current);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(captured.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
            .isEqualTo("Bearer server-side-access-token");
    }

    /**
     * 验证 Access Token 过期后仍走 Authorized Client Manager，以便 Refresh Token 轮换后再转发。
     */
    @Test
    void authorizesExpiredAccessTokenBeforeRelay() {
        ClientRegistration registration = registration();
        OAuth2AccessToken refreshedToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "refreshed-access-token",
            Instant.now().minusSeconds(1),
            Instant.now().plusSeconds(60));
        OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(
            registration, "user-1", refreshedToken);
        DefaultOAuth2User principal = new DefaultOAuth2User(
            Set.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("sub", "user-1"),
            "sub");
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
            principal, principal.getAuthorities(), "agentark");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/organizations").build())
            .mutate()
            .principal(Mono.just(authentication))
            .build();
        ReactiveOAuth2AuthorizedClientManager manager =
            mock(ReactiveOAuth2AuthorizedClientManager.class);
        doReturn(Mono.just(client)).when(manager).authorize(any(OAuth2AuthorizeRequest.class));
        GatewayBffTokenRelayFilter filter = new GatewayBffTokenRelayFilter(manager);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = current -> {
            captured.set(current);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(captured.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
            .isEqualTo("Bearer refreshed-access-token");
    }

    /**
     * 创建完整但不联网的测试 Client Registration。
     *
     * @return 测试 Registration
     */
    private ClientRegistration registration() {
        return ClientRegistration.withRegistrationId("agentark")
            .clientId("agentark-console")
            .clientSecret("test-client-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://console.example.test/login/oauth2/code/agentark")
            .authorizationUri("https://identity.example.test/authorize")
            .tokenUri("https://identity.example.test/token")
            .jwkSetUri("https://identity.example.test/jwks")
            .userInfoUri("https://identity.example.test/userinfo")
            .userNameAttributeName("sub")
            .clientName("AgentArk")
            .build();
    }
}
