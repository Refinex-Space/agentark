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

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Gateway BFF 配置完整性、HTTPS 默认和本地显式放宽边界。
 *
 * @author refinex
 */
class GatewayBffPropertiesTest {

    /**
     * 验证生产默认拒绝 HTTP OIDC 与非 Secure Cookie。
     */
    @Test
    void rejectsInsecureProductionConfiguration() {
        GatewayBffProperties properties = completeLocalProperties();
        properties.setInsecureHttpEnabled(false);

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HTTPS");
    }

    /**
     * 验证显式本地 Profile 可使用完整 HTTP 端点组合。
     */
    @Test
    void acceptsExplicitLocalIdentityConfiguration() {
        GatewayBffProperties properties = completeLocalProperties();

        properties.validate();
    }

    /**
     * 验证显式 Provider Endpoint 必须四项同时配置，避免混用 Discovery 地址。
     */
    @Test
    void rejectsPartialProviderEndpoints() {
        GatewayBffProperties properties = completeLocalProperties();
        properties.setUserInfoUri(null);

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("configured together");
    }

    /**
     * 创建完整本地 BFF 配置。
     *
     * @return 可通过本地校验的配置
     */
    private GatewayBffProperties completeLocalProperties() {
        GatewayBffProperties properties = new GatewayBffProperties();
        properties.setEnabled(true);
        properties.setClientId("agentark-console");
        properties.setClientSecret("0123456789abcdef0123456789abcdef");
        properties.setIssuerUri(URI.create("http://localhost:8180/realms/agentark"));
        properties.setAuthorizationUri(URI.create(
            "http://localhost:8180/realms/agentark/protocol/openid-connect/auth"));
        properties.setTokenUri(URI.create(
            "http://identity:8080/realms/agentark/protocol/openid-connect/token"));
        properties.setJwkSetUri(URI.create(
            "http://identity:8080/realms/agentark/protocol/openid-connect/certs"));
        properties.setUserInfoUri(URI.create(
            "http://identity:8080/realms/agentark/protocol/openid-connect/userinfo"));
        properties.setEndSessionUri(URI.create(
            "http://localhost:8180/realms/agentark/protocol/openid-connect/logout"));
        properties.setRedirectUri(URI.create(
            "http://localhost:8080/login/oauth2/code/agentark"));
        properties.setPostLoginRedirectUri(URI.create("http://localhost:5173/"));
        properties.setPostLogoutRedirectUri(URI.create("http://localhost:5173/sign-in"));
        properties.setSessionCookieSecure(false);
        properties.setInsecureHttpEnabled(true);
        return properties;
    }
}
