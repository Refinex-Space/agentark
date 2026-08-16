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

import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * 对 Control 的 API Key 成功结果建立短 TTL、容量受限且只以摘要为键的本地缓存。
 *
 * @author refinex
 */
public final class CachedControlApiKeyVerifier implements ControlApiKeyVerifier {

    /**
     * 不带缓存的远程客户端。
     */
    private final ControlApiKeyClient client;

    /**
     * 正缓存有效期。
     */
    private final Duration ttl;

    /**
     * 正缓存最大条目数。
     */
    private final int maxEntries;

    /**
     * 可测试 UTC 时钟。
     */
    private final Clock clock;

    /**
     * 访问顺序缓存；键为凭据 SHA-256，不保存原始 API Key。
     */
    private final LinkedHashMap<String, CacheEntry> cache =
        new LinkedHashMap<>(16, 0.75f, true);

    /**
     * 创建 API Key 短缓存。
     *
     * @param client     Control 远程客户端
     * @param ttl        正缓存有效期
     * @param maxEntries 最大条目数
     * @param clock      UTC 时钟
     */
    public CachedControlApiKeyVerifier(
        ControlApiKeyClient client, Duration ttl, int maxEntries, Clock clock) {
        this.client = java.util.Objects.requireNonNull(client, "client must not be null");
        this.ttl = java.util.Objects.requireNonNull(ttl, "ttl must not be null");
        this.clock = java.util.Objects.requireNonNull(clock, "clock must not be null");
        if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("ttl must be positive and at most 30 seconds");
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    /**
     * 优先读取未过期正缓存；无效凭据和远程错误均不缓存。
     *
     * @param credential 当前完整 API Key
     * @return 成功主体或无效凭据空结果
     */
    @Override
    public Mono<Optional<AgentArkPrincipal>> verify(String credential) {
        String digest = digest(credential);
        Optional<AgentArkPrincipal> cached = cached(digest, clock.instant());
        if (cached.isPresent()) {
            return Mono.just(cached);
        }
        return client.verifyRemotely(credential)
            .doOnNext(result -> result.ifPresent(principal -> put(
                digest, principal, clock.instant().plus(ttl))));
    }

    /**
     * 读取未过期缓存，并主动移除已过期条目。
     *
     * @param digest 凭据摘要
     * @param now    当前时刻
     * @return 命中的主体或空
     */
    private synchronized Optional<AgentArkPrincipal> cached(String digest, Instant now) {
        CacheEntry entry = cache.get(digest);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(now)) {
            cache.remove(digest);
            return Optional.empty();
        }
        return Optional.of(entry.principal());
    }

    /**
     * 写入成功结果并按最近最少使用顺序淘汰超出容量的条目。
     *
     * @param digest    凭据摘要
     * @param principal 已验证主体
     * @param expiresAt 过期时刻
     */
    private synchronized void put(
        String digest, AgentArkPrincipal principal, Instant expiresAt) {
        cache.put(digest, new CacheEntry(principal, expiresAt));
        while (cache.size() > maxEntries) {
            String eldest = cache.keySet().iterator().next();
            cache.remove(eldest);
        }
    }

    /**
     * 计算缓存键，避免原始 API Key 长期驻留在缓存结构中。
     *
     * @param credential 完整 API Key
     * @return 小写十六进制 SHA-256
     */
    private String digest(String credential) {
        java.util.Objects.requireNonNull(credential, "credential must not be null");
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(credential.getBytes(StandardCharsets.US_ASCII));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    /**
     * 表示单条非秘密正缓存记录。
     *
     * @param principal 已验证主体
     * @param expiresAt 过期时刻
     * @author refinex
     */
    private record CacheEntry(AgentArkPrincipal principal, Instant expiresAt) {

        /**
         * 校验缓存记录。
         *
         * @param principal 已验证主体
         * @param expiresAt 过期时刻
         */
        private CacheEntry {
            java.util.Objects.requireNonNull(principal, "principal must not be null");
            java.util.Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }
    }
}
