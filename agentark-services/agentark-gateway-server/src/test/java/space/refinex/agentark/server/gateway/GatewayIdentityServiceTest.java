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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository;
import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.redis.RateLimitDecision;
import space.refinex.agentark.foundation.redis.RateLimiter;
import space.refinex.agentark.server.gateway.GatewayIdentityModels.Account;
import space.refinex.agentark.server.gateway.GatewayIdentityModels.AccountStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 验证本人修改密码的当前密码校验、历史策略、原子写入和 Session 失效。
 *
 * @author refinex
 */
class GatewayIdentityServiceTest {

    /** 固定测试账号 UUIDv7。 */
    private static final UUID ACCOUNT_ID =
        UUID.fromString("019d0000-0000-7000-8000-000000000301");

    /** 固定 UTC 测试时间。 */
    private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");

    /** 证明正确当前密码会写入新摘要并删除该账号全部 Redis Session。 */
    @Test
    void changesOwnPasswordAndInvalidatesSessions() {
        Fixture fixture = fixture();
        Account current = account("hash-current", 2L);
        Account changed = account("hash-new", 3L);
        when(fixture.repository.findByIdForPasswordChange(ACCOUNT_ID)).thenReturn(Optional.of(current));
        when(fixture.passwords.matches("current password phrase", "hash-current")).thenReturn(true);
        when(fixture.passwords.matches("new password phrase", "hash-current")).thenReturn(false);
        when(fixture.repository.recentPasswordHashes(ACCOUNT_ID)).thenReturn(java.util.List.of("hash-old"));
        when(fixture.passwords.matches("new password phrase", "hash-old")).thenReturn(false);
        when(fixture.passwords.encode("new password phrase")).thenReturn("hash-new");
        when(fixture.repository.changePassword(
            ACCOUNT_ID, "hash-new", ACCOUNT_ID.toString(), NOW)).thenReturn(changed);
        ReactiveRedisIndexedSessionRepository.RedisSession session =
            mock(ReactiveRedisIndexedSessionRepository.RedisSession.class);
        when(fixture.sessions.findByPrincipalName("operator"))
            .thenReturn(Mono.just(Map.of("session-1", session)));
        when(fixture.sessions.deleteById("session-1")).thenReturn(Mono.empty());

        assertThatCode(() -> fixture.service.changeOwnPassword(
            ACCOUNT_ID, "current password phrase", "new password phrase").block())
            .doesNotThrowAnyException();

        verify(fixture.passwords).validateNewPassword(
            "new password phrase", "operator", "operator@example.test");
        verify(fixture.repository).changePassword(
            ACCOUNT_ID, "hash-new", ACCOUNT_ID.toString(), NOW);
        verify(fixture.sessions).deleteById("session-1");
    }

    /** 证明错误当前密码只记录拒绝事件，不写新摘要或删除会话。 */
    @Test
    void rejectsWrongCurrentPasswordWithoutMutation() {
        Fixture fixture = fixture();
        when(fixture.repository.findByIdForPasswordChange(ACCOUNT_ID))
            .thenReturn(Optional.of(account("hash-current", 2L)));
        when(fixture.passwords.matches("wrong password phrase", "hash-current")).thenReturn(false);

        assertThatThrownBy(() -> fixture.service.changeOwnPassword(
            ACCOUNT_ID, "wrong password phrase", "new password phrase").block())
            .isInstanceOf(BadCredentialsException.class);

        verify(fixture.repository).recordPasswordChangeDenied(ACCOUNT_ID, NOW);
        verify(fixture.repository, never()).changePassword(any(), any(), any(), any());
        verifyNoInteractions(fixture.sessions);
    }

    /** 创建允许限流且本地事务同步执行的测试夹具。 */
    private static Fixture fixture() {
        GatewayIdentityProperties properties = new GatewayIdentityProperties();
        GatewayIdentityPasswordService passwords = mock(GatewayIdentityPasswordService.class);
        GatewayIdentityRepository repository = mock(GatewayIdentityRepository.class);
        ReactiveRedisIndexedSessionRepository sessions =
            mock(ReactiveRedisIndexedSessionRepository.class);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        when(rateLimiter.acquire(eq("identity-password-change"), eq(ACCOUNT_ID.toString()),
            eq(5L), eq(Duration.ofMinutes(1))))
            .thenReturn(new RateLimitDecision(true, 4, Duration.ZERO));
        when(repository.transaction(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<Object> work = invocation.getArgument(0, Supplier.class);
            return work.get();
        });
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new Fixture(
            new GatewayIdentityService(
                properties, passwords, repository, sessions, rateLimiter, clock),
            passwords,
            repository,
            sessions);
    }

    /** 创建指定摘要和认证版本的活动账号。 */
    private static Account account(String hash, long authVersion) {
        return new Account(
            ACCOUNT_ID,
            "operator",
            "operator@example.test",
            "Operator",
            AccountStatus.ACTIVE,
            false,
            authVersion,
            authVersion,
            NOW,
            hash,
            false,
            null,
            Set.of());
    }

    /**
     * 本人改密测试夹具。
     *
     * @param service    被测应用服务
     * @param passwords  密码策略 Mock
     * @param repository Identity 身份仓储模拟对象
     * @param sessions   Redis 会话仓储模拟对象
     * @author refinex
     */
    private record Fixture(
        GatewayIdentityService service,
        GatewayIdentityPasswordService passwords,
        /** Identity 身份仓储模拟对象。 */
        GatewayIdentityRepository repository,
        /** Redis 会话仓储模拟对象。 */
        ReactiveRedisIndexedSessionRepository sessions) {
    }
}
