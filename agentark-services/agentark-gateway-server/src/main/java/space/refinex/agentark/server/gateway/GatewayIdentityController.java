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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;
import space.refinex.agentark.server.gateway.GatewayIdentityModels.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 暴露不返回 Token 的本地登录、首次改密、账号治理和公开 JWK 端点。
 *
 * @author refinex
 */
@RestController
@ConditionalOnProperty(prefix = "agentark.gateway.identity", name = "enabled", havingValue = "true")
public final class GatewayIdentityController {

    /**
     * WebSession 中保存预认证账号的属性键。
     */
    private static final String PASSWORD_CHANGE_ACCOUNT =
        "agentark.identity.password-change-account";

    /**
     * Identity 应用服务。
     */
    private final GatewayIdentityService identityService;

    /**
     * 内部 JWT/JWK 服务。
     */
    private final GatewayIdentityTokenService tokens;

    /**
     * Redis 会话安全上下文持久化仓储。
     */
    private final ServerSecurityContextRepository securityContexts;

    /**
     * 创建 Identity Controller。
     */
    public GatewayIdentityController(
        GatewayIdentityService identityService,
        GatewayIdentityTokenService tokens,
        ServerSecurityContextRepository securityContexts) {
        this.identityService = java.util.Objects.requireNonNull(identityService, "identityService must not be null");
        this.tokens = java.util.Objects.requireNonNull(tokens, "tokens must not be null");
        this.securityContexts = java.util.Objects.requireNonNull(securityContexts, "securityContexts must not be null");
    }

    /**
     * 使用用户名或邮箱和密码建立 Redis Session；临时密码只得到受限改密 Challenge。
     */
    @PostMapping("/api/v1/auth/login")
    public Mono<ResponseEntity<LoginResponse>> login(
        @Valid @RequestBody LoginRequest request, ServerWebExchange exchange) {
        String remoteKey = exchange.getRequest().getRemoteAddress() == null
            ? "unknown"
            : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        return identityService.authenticate(request.usernameOrEmail(), request.password(), remoteKey)
            .flatMap(result -> exchange.getSession().flatMap(session -> {
                if (result.status() == LoginStatus.PASSWORD_CHANGE_REQUIRED) {
                    session.getAttributes().put(
                        PASSWORD_CHANGE_ACCOUNT, result.principal().id().toString());
                    return session.changeSessionId().thenReturn(ResponseEntity
                        .status(HttpStatus.PRECONDITION_REQUIRED)
                        .body(new LoginResponse(
                            "PASSWORD_CHANGE_REQUIRED", true, null)));
                }
                session.getAttributes().remove(PASSWORD_CHANGE_ACCOUNT);
                return establish(exchange, session, result.principal())
                    .thenReturn(ResponseEntity.ok(new LoginResponse(
                        "AUTHENTICATED", false, principalView(result.principal()))));
            }));
    }

    /**
     * 完成首次强制改密并创建完整 Session。
     */
    @PostMapping("/api/v1/auth/required-password-change")
    public Mono<LoginResponse> requiredPasswordChange(
        @Valid @RequestBody PasswordChangeRequest request, ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            String rawAccount = session.getAttribute(PASSWORD_CHANGE_ACCOUNT);
            if (rawAccount == null) {
                return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "password change challenge is missing"));
            }
            UUID accountId = UUID.fromString(rawAccount);
            return identityService.completeRequiredPasswordChange(accountId, request.newPassword())
                .flatMap(principal -> {
                    session.getAttributes().remove(PASSWORD_CHANGE_ACCOUNT);
                    return establish(exchange, session, principal)
                        .thenReturn(new LoginResponse(
                            "AUTHENTICATED", false, principalView(principal)));
                });
        });
    }

    /**
     * 注销当前本地 Redis Session。
     */
    @PostMapping("/api/v1/auth/logout")
    public Mono<ResponseEntity<Void>> logout(ServerWebExchange exchange) {
        return exchange.getSession()
            .flatMap(WebSession::invalidate)
            .thenReturn(ResponseEntity.noContent().build());
    }

    /**
     * 公开下游验证内部 JWT 所需的 JWK Set。
     */
    @GetMapping("/api/v1/auth/jwks")
    public Map<String, Object> jwks() {
        return tokens.publicJwkSet();
    }

    /**
     * 列出本地账号安全视图。
     */
    @GetMapping("/api/v1/identity/accounts")
    public Mono<List<AccountView>> listAccounts(Authentication authentication) {
        LocalPrincipal actor = require(authentication, "identity.account.read");
        return identityService.listAccounts().doOnSubscribe(subscription -> actor.getName());
    }

    /**
     * 列出最近一百条身份安全事件。
     */
    @GetMapping("/api/v1/identity/security-events")
    public Mono<List<SecurityEventView>> listSecurityEvents(Authentication authentication) {
        require(authentication, "identity.security_event.read");
        return identityService.listSecurityEvents();
    }

    /**
     * 创建必须首次改密的账号并一次性交付随机临时密码。
     */
    @PostMapping("/api/v1/identity/accounts")
    public Mono<ResponseEntity<CreatedAccount>> createAccount(
        Authentication authentication,
        @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
        @Valid @RequestBody CreateAccountRequest request) {
        LocalPrincipal actor = require(authentication, "identity.account.manage");
        requireIdempotencyKey(idempotencyKey);
        return identityService.createAccount(
                request.username(), request.email(), request.displayName(), actor.id().toString(),
                idempotencyKey)
            .map(created -> ResponseEntity.created(
                URI.create("/api/v1/identity/accounts/" + created.account().id())).body(created));
    }

    /**
     * 按乐观锁启用、暂停或禁用账号。
     */
    @PatchMapping("/api/v1/identity/accounts/{accountId}/status")
    public Mono<AccountView> updateStatus(
        Authentication authentication,
        @PathVariable String accountId,
        @Valid @RequestBody UpdateStatusRequest request) {
        LocalPrincipal actor = require(authentication, "identity.account.manage");
        return identityService.updateStatus(
            UUID.fromString(accountId),
            AccountStatus.valueOf(request.status()),
            request.expectedVersion(),
            actor.id().toString());
    }

    /**
     * 重置其他账号随机临时密码并一次性交付。
     */
    @PostMapping("/api/v1/identity/accounts/{accountId}/password-resets")
    public Mono<CreatedAccount> resetPassword(
        Authentication authentication,
        @PathVariable String accountId,
        @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
        LocalPrincipal actor = require(authentication, "identity.credential.reset");
        requireIdempotencyKey(idempotencyKey);
        return identityService.resetPassword(
            UUID.fromString(accountId), actor.id().toString(), idempotencyKey);
    }

    /**
     * 验证当前密码后由已登录用户修改自己的密码，并注销全部现有会话。
     */
    @PostMapping("/api/v1/identity/me/password-changes")
    public Mono<ResponseEntity<Void>> changeOwnPassword(
        Authentication authentication,
        @Valid @RequestBody OwnPasswordChangeRequest request,
        ServerWebExchange exchange) {
        LocalPrincipal actor = requireLocal(authentication);
        return identityService.changeOwnPassword(
                actor.id(), request.currentPassword(), request.newPassword())
            .then(exchange.getSession().flatMap(WebSession::invalidate))
            .thenReturn(ResponseEntity.noContent().build());
    }

    /**
     * 人工解除账号登录失败锁定。
     */
    @PostMapping("/api/v1/identity/accounts/{accountId}/unlock")
    public Mono<AccountView> unlock(
        Authentication authentication, @PathVariable String accountId) {
        LocalPrincipal actor = require(authentication, "identity.account.unlock");
        return identityService.unlock(UUID.fromString(accountId), actor.id().toString());
    }

    /**
     * 将所有匿名登录失败统一映射为不暴露账号存在性的 401。
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> badCredentials(BadCredentialsException error) {
        if ("current password is invalid".equals(error.getMessage())) {
            return problem(
                HttpStatus.UNAUTHORIZED,
                "ARK-IDENTITY-CURRENT-PASSWORD-INVALID",
                "当前密码错误");
        }
        return problem(
            HttpStatus.UNAUTHORIZED,
            "ARK-IDENTITY-INVALID-CREDENTIALS",
            "用户名、电子邮箱或密码错误");
    }

    /**
     * 将请求字段和密码策略错误映射为稳定 400，不返回原始密码。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> invalidRequest(IllegalArgumentException error) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "ARK-IDENTITY-INVALID-REQUEST",
            error.getMessage() == null ? "身份请求不合法" : error.getMessage());
    }

    /**
     * 将幂等执行中或乐观锁冲突映射为 409。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> conflict(IllegalStateException error) {
        return problem(
            HttpStatus.CONFLICT,
            "ARK-IDENTITY-CONFLICT",
            error.getMessage() == null ? "身份状态冲突" : error.getMessage());
    }

    /**
     * 将本地主体保存为完整 Redis SecurityContext 并轮换 Session ID。
     */
    private Mono<Void> establish(
        ServerWebExchange exchange, WebSession session, LocalPrincipal principal) {
        Set<SimpleGrantedAuthority> authorities = principal.authorities().stream()
            .map(SimpleGrantedAuthority::new)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        UsernamePasswordAuthenticationToken authentication =
            UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        return session.changeSessionId()
            .then(securityContexts.save(exchange, new SecurityContextImpl(authentication)));
    }

    /**
     * 验证请求来自具有指定平台权限的本地 Session。
     */
    private static LocalPrincipal require(Authentication authentication, String authority) {
        LocalPrincipal principal = requireLocal(authentication);
        if (!principal.authorities().contains(authority)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "identity permission is required");
        }
        return principal;
    }

    /**
     * 验证请求来自已认证的 Built-in Identity 本地会话，不要求管理员权限。
     */
    private static LocalPrincipal requireLocal(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof LocalPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "local identity session is required");
        }
        return principal;
    }

    /**
     * 验证幂等键长度，不记录键值。
     */
    private static void requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("idempotency key must contain 1 to 128 characters");
        }
    }

    /**
     * 构造不包含原始凭据的 RFC 9457 错误响应。
     */
    private static ResponseEntity<ProblemDetail> problem(
        HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle("Identity request rejected");
        problem.setProperty("code", code);
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }

    /**
     * 将 Session Principal 转为安全响应。
     */
    private static PrincipalResponse principalView(LocalPrincipal principal) {
        return new PrincipalResponse(
            principal.id().toString(), principal.displayName(), principal.username(), principal.email());
    }

    /**
     * 密码登录请求。
     *
     * @param usernameOrEmail 用户名或邮箱
     * @param password        原始密码，不记录或持久化
     * @author refinex
     */
    public record LoginRequest(
        @NotBlank @Size(max = 320) String usernameOrEmail,
        @NotBlank @Size(max = 128) String password) {
    }

    /**
     * 首次改密请求。
     *
     * @param newPassword 新正式密码
     * @author refinex
     */
    public record PasswordChangeRequest(@NotBlank @Size(max = 128) String newPassword) {
    }

    /**
     * 已登录用户本人修改密码请求。
     *
     * @param currentPassword 当前密码，不记录或持久化
     * @param newPassword     新正式密码
     * @author refinex
     */
    public record OwnPasswordChangeRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(max = 128) String newPassword) {
    }

    /**
     * 登录结果。
     *
     * @param status                 稳定状态码
     * @param passwordChangeRequired 是否必须改密
     * @param principal              可空主体
     * @author refinex
     */
    public record LoginResponse(
        String status, boolean passwordChangeRequired, PrincipalResponse principal) {
    }

    /**
     * 登录主体安全响应。
     *
     * @param subject     账号 UUIDv7 Subject
     * @param displayName 展示名称
     * @param username    用户名
     * @param email       可空邮箱
     * @author refinex
     */
    public record PrincipalResponse(
        String subject, String displayName, String username, String email) {
    }

    /**
     * 创建账号请求。
     *
     * @param username    用户名
     * @param email       可空邮箱
     * @param displayName 展示名称
     * @author refinex
     */
    public record CreateAccountRequest(
        @NotBlank @Size(max = 64) String username,
        @Size(max = 320) String email,
        @NotBlank @Size(max = 128) String displayName) {
    }

    /**
     * 更新账号状态请求。
     *
     * @param status          ACTIVE、SUSPENDED 或 DISABLED
     * @param expectedVersion 调用方读取的乐观锁版本
     * @author refinex
     */
    public record UpdateStatusRequest(@NotBlank String status, @Min(0) long expectedVersion) {
    }
}
