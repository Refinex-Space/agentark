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

package space.refinex.agentark.foundation.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 使用版本固定的 Redis Lua 脚本实现 Lease、Fencing、幂等和固定窗口限流原子语义。
 *
 * @author refinex
 */
public final class RedisCoordinationStore
    implements DistributedLeaseManager, FencingTokenSource, IdempotencyStore, RateLimiter {

    /**
     * 租约获取脚本：先生成单调令牌，再以 NX 和毫秒 TTL 原子竞争。
     */
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT =
        new DefaultRedisScript<>(
            "local token=redis.call('INCR',KEYS[2]); "
                + "if redis.call('SET',KEYS[1],ARGV[1]..':'..token,'NX','PX',ARGV[2]) "
                + "then return token else return 0 end",
            Long.class);

    /**
     * 租约续约脚本：Owner 和 Fencing 必须同时匹配。
     */
    private static final DefaultRedisScript<Long> RENEW_SCRIPT =
        new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1])==ARGV[1] then "
                + "return redis.call('PEXPIRE',KEYS[1],ARGV[2]) else return 0 end",
            Long.class);

    /**
     * 租约释放脚本：旧 Owner 或旧 Fencing 不能删除新租约。
     */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
        new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1])==ARGV[1] then "
                + "return redis.call('DEL',KEYS[1]) else return 0 end",
            Long.class);

    /**
     * 幂等声明脚本：原子区分首次请求、相同 Hash 重放和不同 Hash 冲突。
     */
    private static final DefaultRedisScript<String> IDEMPOTENCY_SCRIPT =
        new DefaultRedisScript<>(
            "local current=redis.call('GET',KEYS[1]); "
                + "if not current then redis.call('PSETEX',KEYS[1],ARGV[3],ARGV[1]..'|'..ARGV[2]); return 'N|' end; "
                + "local split=string.find(current,'|',1,true); local hash=string.sub(current,1,split-1); "
                + "if hash==ARGV[1] then return 'R|'..string.sub(current,split+1) else return 'C|' end",
            String.class);

    /**
     * 固定窗口限流脚本：计数与首个请求设置过期时间保持原子。
     */
    private static final DefaultRedisScript<List> RATE_LIMIT_SCRIPT =
        new DefaultRedisScript<>(
            "local count=redis.call('INCR',KEYS[1]); "
                + "if count==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]) end; "
                + "return {count,redis.call('PTTL',KEYS[1])}",
            List.class);

    /**
     * Redis 字符串操作模板。
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 统一 Key 和 TTL 约束。
     */
    private final RedisKeyNamespace keyNamespace;

    /**
     * 创建 Redis 协调实现。
     *
     * @param redisTemplate Redis 字符串模板
     * @param keyNamespace  Key 与 TTL 约束
     */
    public RedisCoordinationStore(StringRedisTemplate redisTemplate, RedisKeyNamespace keyNamespace) {
        this.redisTemplate =
            java.util.Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.keyNamespace =
            java.util.Objects.requireNonNull(keyNamespace, "keyNamespace must not be null");
    }

    /**
     * 原子获取带新栅栏令牌的租约。
     *
     * @param namespace   业务命名空间
     * @param resourceKey 业务资源键
     * @param ownerId     当前实例标识
     * @param ttl         有限租期
     * @return 成功租约或空
     */
    @Override
    public Optional<Lease> tryAcquire(
        String namespace, String resourceKey, String ownerId, Duration ttl) {
        Duration checkedTtl = keyNamespace.requireTtl(ttl);
        String leaseKey = keyNamespace.key("lease-" + requireNamespace(namespace), resourceKey);
        String fenceKey = keyNamespace.key("fence-" + namespace, resourceKey);
        Long token =
            redisTemplate.execute(
                ACQUIRE_SCRIPT,
                List.of(leaseKey, fenceKey),
                requireOwner(ownerId),
                Long.toString(checkedTtl.toMillis()));
        if (token == null || token == 0) {
            return Optional.empty();
        }
        return Optional.of(
            new Lease(namespace, resourceKey, ownerId, token, Instant.now().plus(checkedTtl)));
    }

    /**
     * 仅为当前 Owner 与 Fencing 匹配的租约续期。
     *
     * @param lease 当前租约
     * @param ttl   新租期
     * @return 续约成功时为 {@code true}
     */
    @Override
    public boolean renew(Lease lease, Duration ttl) {
        Duration checkedTtl = keyNamespace.requireTtl(ttl);
        Long result =
            redisTemplate.execute(
                RENEW_SCRIPT,
                List.of(keyNamespace.key("lease-" + lease.namespace(), lease.resourceKey())),
                ownerToken(lease),
                Long.toString(checkedTtl.toMillis()));
        return Long.valueOf(1).equals(result);
    }

    /**
     * 仅释放当前 Owner 与 Fencing 匹配的租约。
     *
     * @param lease 当前租约
     * @return 释放成功时为 {@code true}
     */
    @Override
    public boolean release(Lease lease) {
        Long result =
            redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(keyNamespace.key("lease-" + lease.namespace(), lease.resourceKey())),
                ownerToken(lease));
        return Long.valueOf(1).equals(result);
    }

    /**
     * 为资源生成单调递增栅栏令牌。
     *
     * @param namespace   业务命名空间
     * @param resourceKey 业务资源键
     * @return 正数栅栏令牌
     * @throws IllegalStateException 当 Redis 未返回令牌时抛出
     */
    @Override
    public long next(String namespace, String resourceKey) {
        Long value =
            redisTemplate
                .opsForValue()
                .increment(keyNamespace.key("fence-" + requireNamespace(namespace), resourceKey));
        if (value == null || value < 1) {
            throw new IllegalStateException("Redis did not return a fencing token");
        }
        return value;
    }

    /**
     * 原子声明幂等键并区分重放与冲突。
     *
     * @param namespace       业务命名空间
     * @param idempotencyKey  幂等键
     * @param requestHash     规范请求摘要
     * @param resultReference 已持久化结果引用
     * @param ttl             Redis 加速记录 TTL
     * @return 幂等判断结果
     */
    @Override
    public IdempotencyDecision claim(
        String namespace,
        String idempotencyKey,
        String requestHash,
        String resultReference,
        Duration ttl) {
        requireOpaque(requestHash, "requestHash");
        requireOpaque(resultReference, "resultReference");
        String value =
            redisTemplate.execute(
                IDEMPOTENCY_SCRIPT,
                List.of(keyNamespace.key("idempotency-" + requireNamespace(namespace), idempotencyKey)),
                requestHash,
                resultReference,
                Long.toString(keyNamespace.requireTtl(ttl).toMillis()));
        if (value == null || value.length() < 2) {
            throw new IllegalStateException("Redis did not return an idempotency decision");
        }
        return switch (value.charAt(0)) {
            case 'N' -> new IdempotencyDecision(IdempotencyDecision.Status.NEW, Optional.empty());
            case 'R' -> new IdempotencyDecision(
                IdempotencyDecision.Status.REPLAY, Optional.of(value.substring(2)));
            case 'C' -> new IdempotencyDecision(IdempotencyDecision.Status.CONFLICT, Optional.empty());
            default -> throw new IllegalStateException("Redis returned an unknown idempotency decision");
        };
    }

    /**
     * 在固定窗口内原子消耗一个配额。
     *
     * @param namespace  业务命名空间
     * @param subjectKey 主体或资源键
     * @param limit      正数窗口上限
     * @param window     有限窗口
     * @return 限流结果
     */
    @Override
    public RateLimitDecision acquire(
        String namespace, String subjectKey, long limit, Duration window) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<?> values =
            redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(keyNamespace.key("rate-" + requireNamespace(namespace), subjectKey)),
                Long.toString(keyNamespace.requireTtl(window).toMillis()));
        if (values == null || values.size() != 2) {
            throw new IllegalStateException("Redis did not return a rate limit decision");
        }
        long count = ((Number) values.get(0)).longValue();
        long ttlMillis = Math.max(0, ((Number) values.get(1)).longValue());
        boolean allowed = count <= limit;
        return new RateLimitDecision(
            allowed,
            Math.max(0, limit - count),
            allowed ? Duration.ZERO : Duration.ofMillis(ttlMillis));
    }

    /**
     * 生成租约脚本使用的 Owner 与 Fencing 组合值。
     *
     * @param lease 当前租约
     * @return 不含 Secret 的比较值
     */
    private String ownerToken(Lease lease) {
        java.util.Objects.requireNonNull(lease, "lease must not be null");
        return lease.ownerId() + ":" + lease.fencingToken();
    }

    /**
     * 校验并返回业务命名空间。
     *
     * @param namespace 待校验命名空间
     * @return 原命名空间
     */
    private String requireNamespace(String namespace) {
        if (namespace == null || !namespace.matches("[a-z][a-z0-9-]{0,48}")) {
            throw new IllegalArgumentException("namespace must be a stable lowercase segment");
        }
        return namespace;
    }

    /**
     * 校验租约 Owner 标识不会破坏内部比较格式。
     *
     * @param ownerId 待校验 Owner
     * @return 原 Owner
     */
    private String requireOwner(String ownerId) {
        if (ownerId == null || !ownerId.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("ownerId contains invalid characters");
        }
        return ownerId;
    }

    /**
     * 校验幂等摘要和结果引用不会破坏脚本分隔格式。
     *
     * @param value 待校验值
     * @param name  字段名称
     */
    private void requireOpaque(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 512 || value.contains("|")) {
            throw new IllegalArgumentException(name + " contains invalid characters");
        }
    }
}
