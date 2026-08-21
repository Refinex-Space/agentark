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

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import space.refinex.agentark.foundation.redis.RateLimiter;
import space.refinex.agentark.server.gateway.GatewayIdentityModels.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * 编排内置身份登录、首次改密、账号治理和 Redis Session 失效。
 *
 * <p>全部 JDBC 与 Argon2 工作都转移到 boundedElastic，禁止阻塞 Netty Event Loop。
 *
 * @author refinex
 */
public final class GatewayIdentityService {

    /**
     * Identity 配置。
     */
    private final GatewayIdentityProperties properties;

    /**
     * 密码策略与摘要服务。
     */
    private final GatewayIdentityPasswordService passwords;

    /**
     * MySQL 身份持久化仓储。
     */
    private final GatewayIdentityRepository repository;

    /**
     * 可按 Principal 索引删除 Session 的 Redis Repository。
     */
    private final ReactiveRedisIndexedSessionRepository sessions;

    /**
     * Redis 原子登录限流器。
     */
    private final RateLimiter rateLimiter;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 创建内置身份应用服务。
     *
     * @param properties Identity 配置
     * @param passwords  密码服务
     * @param repository MySQL Repository
     * @param sessions   Redis Session Repository
     * @param rateLimiter Redis 原子登录与改密限流器
     * @param clock      UTC 时钟
     */
    public GatewayIdentityService(
        GatewayIdentityProperties properties,
        GatewayIdentityPasswordService passwords,
        GatewayIdentityRepository repository,
        ReactiveRedisIndexedSessionRepository sessions,
        RateLimiter rateLimiter,
        Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.passwords = Objects.requireNonNull(passwords, "passwords must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 幂等初始化随机临时密码管理员。
     *
     * @return 初始化完成信号
     */
    public Mono<Void> bootstrap() {
        return blocking(() -> {
            properties.validate();
            String username = GatewayIdentityRepository.normalizeUsername(
                properties.getBootstrapUsername());
            String email = GatewayIdentityRepository.normalizeEmail(properties.getBootstrapEmail());
            String hash = passwords.encode(properties.getBootstrapPassword());
            repository.bootstrap(username, email, properties.getBootstrapDisplayName(), hash, clock.instant());
            return null;
        }).then();
    }

    /**
     * 校验用户名或邮箱和密码，不透露账号存在性、状态或锁定原因。
     *
     * @param login       用户名或邮箱
     * @param rawPassword 原始密码
     * @param remoteKey   不包含原始地址的调用来源键
     * @return 完整登录或强制改密结果
     */
    public Mono<LoginResult> authenticate(String login, String rawPassword, String remoteKey) {
        return blocking(() -> {
            String rateKey = sha256((login == null ? "" : login.strip().toLowerCase())
                + "\n" + (remoteKey == null ? "unknown" : remoteKey));
            if (!rateLimiter.acquire("identity-login", rateKey, 10, java.time.Duration.ofMinutes(1)).allowed()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "login rate limit exceeded");
            }
            Optional<Account> candidate = repository.findByLogin(login);
            if (candidate.isEmpty()) {
                repository.recordUnknownFailure(clock.instant());
                throw new BadCredentialsException("invalid credentials");
            }
            Account account = candidate.orElseThrow();
            Instant now = clock.instant();
            boolean locked = account.lockedUntil() != null && account.lockedUntil().isAfter(now);
            if (account.status() != AccountStatus.ACTIVE || locked
                || !passwords.matches(rawPassword, account.passwordHash())) {
                if (!locked && account.status() == AccountStatus.ACTIVE) {
                    repository.recordFailure(
                        account, properties.getFailureThreshold(), properties.getLockDuration(), now);
                }
                throw new BadCredentialsException("invalid credentials");
            }
            LocalPrincipal principal = principal(account);
            if (account.passwordChangeRequired() || account.temporaryPassword()) {
                return new LoginResult(LoginStatus.PASSWORD_CHANGE_REQUIRED, principal);
            }
            repository.recordSuccess(account.id(), now);
            return new LoginResult(LoginStatus.AUTHENTICATED, principal);
        });
    }

    /**
     * 使用预认证账号设置正式密码并取得更新后的主体。
     *
     * @param accountId   预认证账号
     * @param newPassword 新密码
     * @return 更新后的主体
     */
    public Mono<LocalPrincipal> completeRequiredPasswordChange(UUID accountId, String newPassword) {
        return blocking(() -> repository.transaction(() -> {
            Account account = repository.findByIdForPasswordChange(accountId)
                .orElseThrow(() -> new BadCredentialsException("password change challenge is invalid"));
            if (!account.passwordChangeRequired()) {
                throw new IllegalStateException("password change is not required");
            }
            passwords.validateNewPassword(newPassword, account.username(), account.email());
            if (passwords.matches(newPassword, account.passwordHash())) {
                throw new IllegalArgumentException("new password must differ from the temporary password");
            }
            if (repository.recentPasswordHashes(accountId).stream()
                .anyMatch(hash -> passwords.matches(newPassword, hash))) {
                throw new IllegalArgumentException("new password must not reuse a recent password");
            }
            Account updated = repository.changePassword(
                accountId, passwords.encode(newPassword), accountId.toString(), clock.instant());
            return principal(updated);
        })).flatMap(principal -> blocking(() -> {
            repository.recordSuccess(accountId, clock.instant());
            return principal;
        }));
    }

    /**
     * 验证当前密码后由用户本人设置新密码，并清除该账号全部 Redis Session。
     *
     * @param accountId       当前登录账号 UUIDv7
     * @param currentPassword 当前密码，只在调用栈内短暂存在
     * @param newPassword     新正式密码
     * @return 修改及会话失效完成信号
     */
    public Mono<Void> changeOwnPassword(
        UUID accountId, String currentPassword, String newPassword) {
        return blocking(() -> {
            if (!rateLimiter.acquire("identity-password-change", accountId.toString(), 5,
                java.time.Duration.ofMinutes(1)).allowed()) {
                throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "password change rate limit exceeded");
            }
            PasswordChangeDecision decision = repository.transaction(() -> {
                Account account = repository.findByIdForPasswordChange(accountId)
                    .orElseThrow(() -> new BadCredentialsException("identity account is unavailable"));
                if (account.status() != AccountStatus.ACTIVE || account.passwordChangeRequired()
                    || !passwords.matches(currentPassword, account.passwordHash())) {
                    return new PasswordChangeDecision(account.username(), false);
                }
                passwords.validateNewPassword(newPassword, account.username(), account.email());
                if (passwords.matches(newPassword, account.passwordHash())) {
                    throw new IllegalArgumentException("new password must differ from current password");
                }
                if (repository.recentPasswordHashes(accountId).stream()
                    .anyMatch(hash -> passwords.matches(newPassword, hash))) {
                    throw new IllegalArgumentException("new password must not reuse a recent password");
                }
                Account changed = repository.changePassword(
                    accountId, passwords.encode(newPassword), accountId.toString(), clock.instant());
                return new PasswordChangeDecision(changed.username(), true);
            });
            if (!decision.changed()) {
                repository.recordPasswordChangeDenied(accountId, clock.instant());
                throw new BadCredentialsException("current password is invalid");
            }
            return decision.username();
        }).flatMap(this::invalidateSessions).then();
    }

    /**
     * 列出账号安全视图。
     */
    public Mono<List<AccountView>> listAccounts() {
        return blocking(repository::listAccounts);
    }

    /**
     * 列出最近身份安全事件。
     */
    public Mono<List<SecurityEventView>> listSecurityEvents() {
        return blocking(repository::listSecurityEvents);
    }

    /**
     * 创建必须首次改密的普通账号，并仅本次返回随机临时密码。
     */
    public Mono<CreatedAccount> createAccount(
        String username, String email, String displayName, String actor, String idempotencyKey) {
        return blocking(() -> repository.transaction(() -> {
            String normalizedUsername = GatewayIdentityRepository.normalizeUsername(username);
            String normalizedEmail = GatewayIdentityRepository.normalizeEmail(email);
            String normalizedDisplayName = requireDisplayName(displayName);
            String requestHash = sha256(String.join("\n",
                normalizedUsername, normalizedEmail == null ? "" : normalizedEmail, normalizedDisplayName));
            Optional<UUID> replay = repository.reserveCreateIdempotency(
                actor, idempotencyKey, requestHash, clock.instant());
            if (replay.isPresent()) {
                Account existing = repository.findById(replay.orElseThrow()).orElseThrow();
                return new CreatedAccount(repository.view(existing), null);
            }
            String temporary = passwords.temporaryPassword();
            Account account = repository.createAccount(
                normalizedUsername,
                normalizedEmail,
                normalizedDisplayName,
                passwords.encode(temporary),
                actor,
                clock.instant());
            repository.completeCreateIdempotency(
                actor, idempotencyKey, account.id(), clock.instant());
            return new CreatedAccount(repository.view(account), temporary);
        }));
    }

    /**
     * 按乐观锁更新账号状态并使旧 Session 失效。
     */
    public Mono<AccountView> updateStatus(
        UUID accountId, AccountStatus status, long expectedVersion, String actor) {
        return blocking(() -> repository.updateStatus(
            accountId, status, expectedVersion, actor, clock.instant()))
            .flatMap(account -> invalidateSessions(account.username()).thenReturn(repository.view(account)));
    }

    /**
     * 重置随机临时密码并使旧 Session 失效；幂等重放不再次返回临时密码。
     */
    public Mono<CreatedAccount> resetPassword(
        UUID accountId, String actor, String idempotencyKey) {
        return blocking(() -> repository.transaction(() -> {
            String requestHash = sha256(accountId.toString());
            Optional<UUID> replay = repository.reserveResetPasswordIdempotency(
                actor, idempotencyKey, requestHash, clock.instant());
            if (replay.isPresent()) {
                Account existing = repository.findById(replay.orElseThrow()).orElseThrow();
                return new CreatedAccount(repository.view(existing), null);
            }
            String temporary = passwords.temporaryPassword();
            Account account = repository.resetPassword(
                accountId, passwords.encode(temporary), actor, clock.instant());
            repository.completeResetPasswordIdempotency(
                actor, idempotencyKey, accountId, clock.instant());
            return new CreatedAccount(repository.view(account), temporary);
        })).flatMap(created -> invalidateSessions(created.account().username()).thenReturn(created));
    }

    /**
     * 解除账号锁定。
     */
    public Mono<AccountView> unlock(UUID accountId, String actor) {
        return blocking(() -> repository.view(repository.unlock(accountId, actor, clock.instant())));
    }

    /**
     * 按账号 ID 读取当前认证版本，供 Session Token Relay 拒绝旧会话。
     */
    public Mono<Account> currentAccount(UUID accountId) {
        return blocking(() -> repository.findById(accountId)
            .orElseThrow(() -> new BadCredentialsException("identity account is unavailable")));
    }

    /**
     * 删除该用户名的全部 Redis Session；MySQL 认证版本仍是最终失效边界。
     */
    private Mono<Void> invalidateSessions(String username) {
        return sessions.findByPrincipalName(username)
            .flatMapMany(found -> Flux.fromIterable(found.keySet()))
            .flatMap(sessions::deleteById)
            .then();
    }

    /**
     * 将内部账号转换为 Redis Session Principal。
     */
    private static LocalPrincipal principal(Account account) {
        return new LocalPrincipal(
            account.id(), account.username(), account.displayName(), account.email(),
            account.authVersion(), account.authorities());
    }

    /**
     * 校验展示名称。
     */
    private static String requireDisplayName(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("display name must contain 1 to 128 characters");
        }
        return value.strip();
    }

    /**
     * 计算规范请求 SHA-256，幂等记录不保存原输入。
     */
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 将阻塞任务移出事件循环。
     */
    private static <T> Mono<T> blocking(java.util.concurrent.Callable<T> task) {
        return Mono.fromCallable(task).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 本人改密事务结果。
     *
     * @param username Redis Session Principal 索引用户名
     * @param changed  是否已经提交新密码
     * @author refinex
     */
    private record PasswordChangeDecision(String username, boolean changed) {
    }
}
