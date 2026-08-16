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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.foundation.security.TenantSelection;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验证 API Key 只缓存成功结果、TTL 到期后重新验证且吊销窗口受控。
 *
 * @author refinex
 */
class CachedControlApiKeyVerifierTest {

    /**
     * 验证未过期成功结果命中缓存，到期后必须再次访问 Control。
     */
    @Test
    void cachesOnlyWithinConfiguredTtl() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-16T00:00:00Z"));
        AtomicInteger calls = new AtomicInteger();
        AgentArkPrincipal principal = apiKeyPrincipal();
        ControlApiKeyClient client = credential -> {
            calls.incrementAndGet();
            return Mono.just(Optional.of(principal));
        };
        CachedControlApiKeyVerifier verifier = new CachedControlApiKeyVerifier(
            client, Duration.ofSeconds(10), 16, clock);

        assertThat(verifier.verify("credential-a").block()).contains(principal);
        assertThat(verifier.verify("credential-a").block()).contains(principal);
        assertThat(calls).hasValue(1);

        clock.advance(Duration.ofSeconds(10));
        assertThat(verifier.verify("credential-a").block()).contains(principal);
        assertThat(calls).hasValue(2);
    }

    /**
     * 验证无效 API Key 不进入正缓存，下一请求仍会访问 Control。
     */
    @Test
    void doesNotCacheInvalidCredentials() {
        AtomicInteger calls = new AtomicInteger();
        ControlApiKeyClient client = credential -> {
            calls.incrementAndGet();
            return Mono.just(Optional.empty());
        };
        CachedControlApiKeyVerifier verifier = new CachedControlApiKeyVerifier(
            client,
            Duration.ofSeconds(10),
            16,
            Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC));

        assertThat(verifier.verify("invalid-a").block()).isEmpty();
        assertThat(verifier.verify("invalid-a").block()).isEmpty();
        assertThat(calls).hasValue(2);
    }

    /**
     * 创建具有项目租户选择的 API Key 主体。
     *
     * @return 测试主体
     */
    private AgentArkPrincipal apiKeyPrincipal() {
        TenantSelection selection = new TenantSelection(
            OrganizationId.generate(),
            Optional.of(ProjectId.generate()),
            Optional.empty());
        return new AgentArkPrincipal(
            "agentark-iam",
            "service-account-1",
            PrincipalType.API_KEY,
            Set.of("agent:read"),
            Optional.of(selection),
            Optional.empty());
    }

    /**
     * 提供可前移的测试时钟。
     *
     * @author refinex
     */
    private static final class MutableClock extends Clock {

        /**
         * 当前测试时刻。
         */
        private Instant instant;

        /**
         * 创建 UTC 测试时钟。
         *
         * @param instant 初始时刻
         */
        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        /**
         * 返回 UTC 时区。
         *
         * @return UTC
         */
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /**
         * 忽略新时区并保留 UTC 语义。
         *
         * @param zone 请求时区
         * @return 当前测试时钟
         */
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        /**
         * 返回当前测试时刻。
         *
         * @return 当前时刻
         */
        @Override
        public Instant instant() {
            return instant;
        }

        /**
         * 前移测试时钟。
         *
         * @param duration 前移时长
         */
        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
