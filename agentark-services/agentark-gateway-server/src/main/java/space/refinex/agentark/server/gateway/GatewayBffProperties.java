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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Objects;

/**
 * 定义 Gateway OIDC BFF、浏览器跳转和服务端会话 Cookie 配置。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.gateway.bff")
public class GatewayBffProperties {

    /**
     * 是否启用 OIDC Authorization Code BFF。
     */
    private boolean enabled;

    /**
     * Spring Security Client Registration 标识。
     */
    private String registrationId = "agentark";

    /**
     * 身份提供方登记的 OIDC 客户端标识。
     */
    private String clientId;

    /**
     * 身份提供方为机密 OIDC 客户端签发的秘密值。
     */
    private String clientSecret;

    /**
     * OIDC Provider 展示名称。
     */
    private String clientName = "AgentArk Identity";

    /**
     * 受信 OIDC Issuer。
     */
    private URI issuerUri;

    /**
     * 可选显式 Authorization Endpoint；为空时使用 Issuer Discovery。
     */
    private URI authorizationUri;

    /**
     * 可选显式 Token Endpoint；为空时使用 Issuer Discovery。
     */
    private URI tokenUri;

    /**
     * 可选显式 JWK Set Endpoint；为空时使用 Issuer Discovery。
     */
    private URI jwkSetUri;

    /**
     * 可选显式 UserInfo Endpoint；为空时使用 Issuer Discovery。
     */
    private URI userInfoUri;

    /**
     * 可选显式 OIDC RP-Initiated Logout Endpoint；为空时使用 Issuer Discovery。
     */
    private URI endSessionUri;

    /**
     * OIDC 回调地址，必须已在 Provider Client 中精确登记。
     */
    private URI redirectUri;

    /**
     * 登录完成后的 Web 控制台地址。
     */
    private URI postLoginRedirectUri;

    /**
     * 完成 OIDC 退出后的 Web 控制台地址。
     */
    private URI postLogoutRedirectUri;

    /**
     * 登录按钮面向用户的名称。
     */
    private String loginLabel = "使用组织身份登录";

    /**
     * 浏览器只保存的不可读服务端会话 Cookie 名称。
     */
    private String sessionCookieName = "AGENTARK_SESSION";

    /**
     * 是否为会话 Cookie 强制 Secure 属性。
     */
    private boolean sessionCookieSecure = true;

    /**
     * 是否仅为本地身份 Profile 允许明文 HTTP OIDC 端点。
     */
    private boolean insecureHttpEnabled;

    /**
     * 返回 BFF 是否启用。
     *
     * @return 启用时为 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 BFF 是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回 Client Registration 标识。
     *
     * @return 小写稳定标识
     */
    public String getRegistrationId() {
        return registrationId;
    }

    /**
     * 设置 Client Registration 标识。
     *
     * @param registrationId 小写稳定标识
     */
    public void setRegistrationId(String registrationId) {
        this.registrationId = requireIdentifier(registrationId, "registrationId");
    }

    /**
     * 返回 OIDC Client ID。
     *
     * @return Client ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * 设置 OIDC Client ID。
     *
     * @param clientId Provider 注册的 Client ID
     */
    public void setClientId(String clientId) {
        this.clientId = requireText(clientId, "clientId", 255);
    }

    /**
     * 返回 OIDC Client Secret。
     *
     * @return 只来自 Secret 注入的值
     */
    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * 设置 OIDC Client Secret。
     *
     * @param clientSecret 只来自 Secret 注入的值
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = requireText(clientSecret, "clientSecret", 1024);
    }

    /**
     * 返回 Provider 展示名称。
     *
     * @return 非空名称
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * 设置 Provider 展示名称。
     *
     * @param clientName 非空名称
     */
    public void setClientName(String clientName) {
        this.clientName = requireText(clientName, "clientName", 128);
    }

    /**
     * 返回受信 Issuer。
     *
     * @return OIDC Issuer
     */
    public URI getIssuerUri() {
        return issuerUri;
    }

    /**
     * 设置受信 Issuer。
     *
     * @param issuerUri OIDC Issuer
     */
    public void setIssuerUri(URI issuerUri) {
        this.issuerUri = requireUri(issuerUri, "issuerUri");
    }

    /**
     * 返回显式 Authorization Endpoint。
     *
     * @return 可为空的端点
     */
    public URI getAuthorizationUri() {
        return authorizationUri;
    }

    /**
     * 设置显式 Authorization Endpoint。
     *
     * @param authorizationUri 可为空的端点
     */
    public void setAuthorizationUri(URI authorizationUri) {
        this.authorizationUri = requireUri(authorizationUri, "authorizationUri");
    }

    /**
     * 返回显式 Token Endpoint。
     *
     * @return 可为空的端点
     */
    public URI getTokenUri() {
        return tokenUri;
    }

    /**
     * 设置显式 Token Endpoint。
     *
     * @param tokenUri 可为空的端点
     */
    public void setTokenUri(URI tokenUri) {
        this.tokenUri = requireUri(tokenUri, "tokenUri");
    }

    /**
     * 返回显式 JWK Set Endpoint。
     *
     * @return 可为空的端点
     */
    public URI getJwkSetUri() {
        return jwkSetUri;
    }

    /**
     * 设置显式 JWK Set Endpoint。
     *
     * @param jwkSetUri 可为空的端点
     */
    public void setJwkSetUri(URI jwkSetUri) {
        this.jwkSetUri = requireUri(jwkSetUri, "jwkSetUri");
    }

    /**
     * 返回显式 UserInfo Endpoint。
     *
     * @return 可为空的端点
     */
    public URI getUserInfoUri() {
        return userInfoUri;
    }

    /**
     * 设置显式 UserInfo Endpoint。
     *
     * @param userInfoUri 可为空的端点
     */
    public void setUserInfoUri(URI userInfoUri) {
        this.userInfoUri = requireUri(userInfoUri, "userInfoUri");
    }

    /**
     * 返回显式 OIDC Logout Endpoint。
     *
     * @return 可为空的端点
     */
    public URI getEndSessionUri() {
        return endSessionUri;
    }

    /**
     * 设置显式 OIDC Logout Endpoint。
     *
     * @param endSessionUri 可为空的端点
     */
    public void setEndSessionUri(URI endSessionUri) {
        this.endSessionUri = requireUri(endSessionUri, "endSessionUri");
    }

    /**
     * 返回 OIDC 回调地址。
     *
     * @return 精确回调地址
     */
    public URI getRedirectUri() {
        return redirectUri;
    }

    /**
     * 设置 OIDC 回调地址。
     *
     * @param redirectUri 精确回调地址
     */
    public void setRedirectUri(URI redirectUri) {
        this.redirectUri = requireUri(redirectUri, "redirectUri");
    }

    /**
     * 返回登录完成跳转地址。
     *
     * @return Web 控制台地址
     */
    public URI getPostLoginRedirectUri() {
        return postLoginRedirectUri;
    }

    /**
     * 设置登录完成跳转地址。
     *
     * @param postLoginRedirectUri Web 控制台地址
     */
    public void setPostLoginRedirectUri(URI postLoginRedirectUri) {
        this.postLoginRedirectUri = requireUri(postLoginRedirectUri, "postLoginRedirectUri");
    }

    /**
     * 返回退出完成跳转地址。
     *
     * @return Web 控制台地址
     */
    public URI getPostLogoutRedirectUri() {
        return postLogoutRedirectUri;
    }

    /**
     * 设置退出完成跳转地址。
     *
     * @param postLogoutRedirectUri Web 控制台地址
     */
    public void setPostLogoutRedirectUri(URI postLogoutRedirectUri) {
        this.postLogoutRedirectUri = requireUri(postLogoutRedirectUri, "postLogoutRedirectUri");
    }

    /**
     * 返回登录按钮文案。
     *
     * @return 用户可见名称
     */
    public String getLoginLabel() {
        return loginLabel;
    }

    /**
     * 设置登录按钮文案。
     *
     * @param loginLabel 用户可见名称
     */
    public void setLoginLabel(String loginLabel) {
        this.loginLabel = requireText(loginLabel, "loginLabel", 64);
    }

    /**
     * 返回服务端会话 Cookie 名称。
     *
     * @return 安全 Cookie 名称
     */
    public String getSessionCookieName() {
        return sessionCookieName;
    }

    /**
     * 设置服务端会话 Cookie 名称。
     *
     * @param sessionCookieName 安全 Cookie 名称
     */
    public void setSessionCookieName(String sessionCookieName) {
        if (sessionCookieName == null || !sessionCookieName.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("sessionCookieName is invalid");
        }
        this.sessionCookieName = sessionCookieName;
    }

    /**
     * 返回会话 Cookie 是否强制 Secure。
     *
     * @return 强制时为 {@code true}
     */
    public boolean isSessionCookieSecure() {
        return sessionCookieSecure;
    }

    /**
     * 设置会话 Cookie 是否强制 Secure。
     *
     * @param sessionCookieSecure 是否强制
     */
    public void setSessionCookieSecure(boolean sessionCookieSecure) {
        this.sessionCookieSecure = sessionCookieSecure;
    }

    /**
     * 返回是否允许本地 HTTP OIDC。
     *
     * @return 允许时为 {@code true}
     */
    public boolean isInsecureHttpEnabled() {
        return insecureHttpEnabled;
    }

    /**
     * 设置是否允许本地 HTTP OIDC。
     *
     * @param insecureHttpEnabled 是否仅为本地 Profile 放宽
     */
    public void setInsecureHttpEnabled(boolean insecureHttpEnabled) {
        this.insecureHttpEnabled = insecureHttpEnabled;
    }

    /**
     * 校验 BFF 启用所需的完整安全配置。
     *
     * @throws IllegalStateException 当配置缺失、端点组合不完整或传输不安全时抛出
     */
    void validate() {
        if (!enabled) {
            return;
        }
        requireText(clientId, "clientId", 255);
        requireText(clientSecret, "clientSecret", 1024);
        Objects.requireNonNull(issuerUri, "issuerUri must not be null");
        Objects.requireNonNull(redirectUri, "redirectUri must not be null");
        Objects.requireNonNull(postLoginRedirectUri, "postLoginRedirectUri must not be null");
        Objects.requireNonNull(postLogoutRedirectUri, "postLogoutRedirectUri must not be null");
        long explicitEndpoints = java.util.stream.Stream.of(
                authorizationUri, tokenUri, jwkSetUri, userInfoUri, endSessionUri)
            .filter(Objects::nonNull)
            .count();
        if (explicitEndpoints != 0 && explicitEndpoints != 5) {
            throw new IllegalStateException(
                "authorizationUri, tokenUri, jwkSetUri, userInfoUri and endSessionUri must be configured together");
        }
        if (!insecureHttpEnabled
            && java.util.stream.Stream.of(
                issuerUri,
                authorizationUri,
                tokenUri,
                jwkSetUri,
                userInfoUri,
                endSessionUri,
                redirectUri,
                postLoginRedirectUri,
                postLogoutRedirectUri)
            .filter(Objects::nonNull)
            .anyMatch(uri -> !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException("BFF identity and redirect URIs must use HTTPS");
        }
        if (!insecureHttpEnabled && !sessionCookieSecure) {
            throw new IllegalStateException("production BFF session cookie must be Secure");
        }
    }

    /**
     * 判断是否使用显式 Provider Endpoint，避免本地容器 Discovery 地址与浏览器地址冲突。
     *
     * @return 四个显式端点均存在时为 {@code true}
     */
    boolean hasExplicitProviderEndpoints() {
        return authorizationUri != null
            && tokenUri != null
            && jwSetAndSessionEndpointsConfigured();
    }

    /**
     * 判断 JWK、UserInfo 与 Logout 三个显式端点均存在。
     *
     * @return 三个端点均存在时为 {@code true}
     */
    private boolean jwSetAndSessionEndpointsConfigured() {
        return jwkSetUri != null && userInfoUri != null && endSessionUri != null;
    }

    /**
     * 校验配置标识。
     *
     * @param value 配置值
     * @param name  字段名
     * @return 原值
     */
    private String requireIdentifier(String value, String name) {
        if (value == null || !value.matches("[a-z][a-z0-9-]{0,31}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    /**
     * 校验非空受限文本。
     *
     * @param value     配置值
     * @param name      字段名
     * @param maxLength 最大字符数
     * @return 原值
     */
    private String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1 to " + maxLength + " characters");
        }
        return value;
    }

    /**
     * 校验无凭据、无 Query/Fragment 的绝对 HTTP(S) URI。
     *
     * @param value 配置值
     * @param name  字段名
     * @return 原值或 {@code null}
     */
    private URI requireUri(URI value, String name) {
        if (value != null
            && (!value.isAbsolute()
            || value.getHost() == null
            || value.getUserInfo() != null
            || value.getQuery() != null
            || value.getFragment() != null
            || !("https".equalsIgnoreCase(value.getScheme())
            || "http".equalsIgnoreCase(value.getScheme())))) {
            throw new IllegalArgumentException(name + " must be an absolute HTTP(S) URI without credentials");
        }
        return value;
    }
}
