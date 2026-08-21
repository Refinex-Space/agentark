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
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Gateway 会话响应只返回主体与 CSRF 参数，不暴露任何 OIDC Token。
 *
 * @author refinex
 */
class GatewaySessionControllerTest {

    /** 测试 BFF 配置。 */
    private final GatewayBffProperties properties = properties();

    /** 测试内置 Identity 配置，默认关闭以验证 OIDC 兼容。 */
    private final GatewayIdentityProperties identityProperties = new GatewayIdentityProperties();

    /** 待测会话 Controller。 */
    private final GatewaySessionController controller =
        new GatewaySessionController(properties, identityProperties);

    /**
     * 验证匿名响应仍提供登录入口和 CSRF Token。
     */
    @Test
    void returnsAnonymousSessionWithoutCredential() {
        var response = controller.session(exchange()).block();

        assertThat(response.authenticated()).isFalse();
        assertThat(response.loginUri()).isEqualTo("/oauth2/authorization/agentark");
        assertThat(response.csrfToken()).isEqualTo("csrf-value");
        assertThat(response.principal()).isNull();
    }

    /**
     * 验证 OIDC 会话只投影 Subject、展示名称和 Issuer。
     */
    @Test
    void returnsSanitizedOidcPrincipal() {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
            "sensitive-id-token",
            now.minusSeconds(1),
            now.plusSeconds(60),
            Map.of(
                "iss", "https://identity.example.test",
                "sub", "user-1",
                "preferred_username", "refinex"));
        DefaultOidcUser user = new DefaultOidcUser(
            Set.of(new SimpleGrantedAuthority("ROLE_USER")),
            idToken,
            "preferred_username");
        OAuth2AuthenticationToken authentication =
            new OAuth2AuthenticationToken(user, user.getAuthorities(), "agentark");
        var exchange = exchange().mutate().principal(Mono.just(authentication)).build();

        var response = controller.session(exchange).block();

        assertThat(response.authenticated()).isTrue();
        assertThat(response.principal().subject()).isEqualTo("user-1");
        assertThat(response.principal().displayName()).isEqualTo("refinex");
        assertThat(response.toString()).doesNotContain("sensitive-id-token");
    }

    /**
     * 创建带延迟 CSRF Token 的测试交换。
     *
     * @return 测试交换
     */
    private MockServerWebExchange exchange() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/auth/session").build());
        CsrfToken csrf = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "csrf-value");
        exchange.getAttributes().put(CsrfToken.class.getName(), Mono.just(csrf));
        return exchange;
    }

    /**
     * 创建最小会话 Controller 配置。
     *
     * @return 测试配置
     */
    private GatewayBffProperties properties() {
        GatewayBffProperties value = new GatewayBffProperties();
        value.setRegistrationId("agentark");
        value.setLoginLabel("使用本地账号登录");
        return value;
    }
}
