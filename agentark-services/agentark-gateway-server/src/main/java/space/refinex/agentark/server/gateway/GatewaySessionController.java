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

import org.springframework.http.CacheControl;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;

/**
 * 暴露不含 Token 的浏览器 BFF 会话投影和 CSRF 提交参数。
 *
 * @author refinex
 */
@RestController
public class GatewaySessionController {

    /**
     * BFF 配置。
     */
    private final GatewayBffProperties properties;

    /**
     * 内置 Identity 配置。
     */
    private final GatewayIdentityProperties identityProperties;

    /**
     * 创建 Gateway 会话 Controller。
     *
     * @param properties         BFF 配置
     * @param identityProperties 内置 Identity 配置
     */
    public GatewaySessionController(
        GatewayBffProperties properties, GatewayIdentityProperties identityProperties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.identityProperties = Objects.requireNonNull(
            identityProperties, "identityProperties must not be null");
    }

    /**
     * 返回当前浏览器会话，不返回 ID Token、Access Token、Refresh Token 或 Client Secret。
     *
     * @param exchange 当前请求交换
     * @return 不缓存的会话投影
     */
    @GetMapping("/api/v1/auth/session")
    public Mono<GatewaySessionResponse> session(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().setCacheControl(CacheControl.noStore());
        return csrfToken(exchange).flatMap(csrf -> exchange.getPrincipal()
            .ofType(Authentication.class)
            .filter(Authentication::isAuthenticated)
            .map(authentication -> authenticated(authentication, csrf))
            .defaultIfEmpty(anonymous(csrf)));
    }

    /**
     * 创建已认证会话投影。
     *
     * @param authentication 已认证 OIDC Principal
     * @param csrf           当前服务端会话 CSRF Token
     * @return 已认证响应
     */
    private GatewaySessionResponse authenticated(Authentication authentication, CsrfToken csrf) {
        if (authentication.getPrincipal()
            instanceof GatewayIdentityModels.LocalPrincipal localPrincipal) {
            GatewaySessionPrincipal principal = new GatewaySessionPrincipal(
                localPrincipal.id().toString(),
                localPrincipal.displayName(),
                identityProperties.getIssuer().toString());
            return response(true, principal, csrf);
        }
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2)) {
            return anonymous(csrf);
        }
        if (!(oauth2.getPrincipal() instanceof OidcUser oidcUser)
            || oidcUser.getIssuer() == null) {
            throw new IllegalStateException("OIDC principal with issuer is required");
        }
        String displayName = Optional.ofNullable(oidcUser.getFullName())
            .filter(value -> !value.isBlank())
            .orElseGet(() -> Optional.ofNullable(oidcUser.getPreferredUsername())
                .filter(value -> !value.isBlank())
                .orElse(oidcUser.getSubject()));
        GatewaySessionPrincipal principal = new GatewaySessionPrincipal(
            oidcUser.getSubject(),
            displayName,
            oidcUser.getIssuer().toString());
        return response(true, principal, csrf);
    }

    /**
     * 创建匿名会话投影。
     *
     * @param csrf 当前服务端会话 CSRF Token
     * @return 匿名响应
     */
    private GatewaySessionResponse anonymous(CsrfToken csrf) {
        return response(false, null, csrf);
    }

    /**
     * 组合稳定登录/退出地址和当前 CSRF 参数。
     *
     * @param authenticated 是否已认证
     * @param principal     可空主体
     * @param csrf          当前服务端会话 CSRF Token
     * @return 会话响应
     */
    private GatewaySessionResponse response(
        boolean authenticated,
        GatewaySessionPrincipal principal,
        CsrfToken csrf) {
        return new GatewaySessionResponse(
            authenticated,
            properties.isEnabled() || identityProperties.isEnabled(),
            identityProperties.isEnabled() ? "使用用户名或电子邮箱登录" : properties.getLoginLabel(),
            identityProperties.isEnabled() ? "/api/v1/auth/login"
                : "/oauth2/authorization/" + properties.getRegistrationId(),
            "/api/v1/auth/logout",
            identityProperties.isEnabled() ? "PASSWORD" : "OIDC",
            identityProperties.isEnabled() ? "/api/v1/auth/required-password-change" : null,
            csrf.getHeaderName(),
            csrf.getParameterName(),
            csrf.getToken(),
            principal);
    }

    /**
     * 从 Spring Security WebFlux 交换属性读取延迟生成的 CSRF Token。
     *
     * @param exchange 当前请求交换
     * @return CSRF Token
     */
    @SuppressWarnings("unchecked")
    private Mono<CsrfToken> csrfToken(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(CsrfToken.class.getName());
        if (value instanceof Mono<?> token) {
            return ((Mono<CsrfToken>) token)
                .switchIfEmpty(Mono.error(new IllegalStateException("CSRF token is unavailable")));
        }
        if (value instanceof CsrfToken token) {
            return Mono.just(token);
        }
        return Mono.error(new IllegalStateException("CSRF token is unavailable"));
    }

    /**
     * 浏览器 BFF 会话响应。
     *
     * @param authenticated     是否已经建立 OIDC 会话
     * @param loginEnabled      是否允许发起登录
     * @param loginLabel        登录入口文案
     * @param loginUri          OIDC 登录入口
     * @param logoutUri         OIDC 退出入口
     * @param loginMode         PASSWORD 或 OIDC
     * @param passwordChangeUri 可空强制改密入口
     * @param csrfHeaderName    CSRF Header 名称
     * @param csrfParameterName CSRF 表单字段名称
     * @param csrfToken         当前会话 CSRF Token
     * @param principal         可空主体投影
     * @author refinex
     */
    public record GatewaySessionResponse(
        boolean authenticated,
        boolean loginEnabled,
        String loginLabel,
        String loginUri,
        String logoutUri,
        String loginMode,
        String passwordChangeUri,
        String csrfHeaderName,
        String csrfParameterName,
        String csrfToken,
        GatewaySessionPrincipal principal) {
    }

    /**
     * 不含权限和凭据的浏览器主体投影。
     *
     * @param subject     Issuer 内稳定 Subject
     * @param displayName 用户展示名称
     * @param issuer      受信 Issuer
     * @author refinex
     */
    public record GatewaySessionPrincipal(String subject, String displayName, String issuer) {
    }
}
