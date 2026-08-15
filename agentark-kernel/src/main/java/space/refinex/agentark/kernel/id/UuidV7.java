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

package space.refinex.agentark.kernel.id;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * RFC 9562 UUIDv7 的生成、解析与校验实现，仅供强类型领域标识复用。
 *
 * @author refinex
 */
final class UuidV7 {

    /**
     * UUIDv7 可表达的 48 位 Unix 毫秒最大值。
     */
    private static final long MAX_UNIX_MILLIS = 0x0000FFFFFFFFFFFFL;

    /**
     * 生产环境生成随机位时使用的进程级密码学安全随机源。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 禁止实例化无状态 UUIDv7 支撑类。
     */
    private UuidV7() {
    }

    /**
     * 使用当前时间和安全随机源生成 UUIDv7。
     *
     * @return 符合 RFC 9562 的 UUIDv7
     */
    static UUID generate() {
        return generate(Instant.now(), RANDOM);
    }

    /**
     * 使用指定时间与随机源生成 UUIDv7，供可重复测试和受控调用使用。
     *
     * @param instant 写入 UUID 前 48 位的 UTC 时刻
     * @param random  填充随机位的随机源
     * @return 符合 RFC 9562 的 UUIDv7
     * @throws NullPointerException     当时间或随机源为 {@code null} 时抛出
     * @throws IllegalArgumentException 当时间超出 UUIDv7 可表示范围时抛出
     */
    static UUID generate(Instant instant, RandomGenerator random) {
        Objects.requireNonNull(instant, "instant must not be null");
        Objects.requireNonNull(random, "random must not be null");
        long unixMillis = instant.toEpochMilli();
        if (unixMillis < 0 || unixMillis > MAX_UNIX_MILLIS) {
            throw new IllegalArgumentException("instant is outside the UUIDv7 timestamp range");
        }

        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        bytes[0] = (byte) (unixMillis >>> 40);
        bytes[1] = (byte) (unixMillis >>> 32);
        bytes[2] = (byte) (unixMillis >>> 24);
        bytes[3] = (byte) (unixMillis >>> 16);
        bytes[4] = (byte) (unixMillis >>> 8);
        bytes[5] = (byte) unixMillis;
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

        long mostSignificantBits = 0;
        long leastSignificantBits = 0;
        for (int index = 0; index < 8; index++) {
            mostSignificantBits = (mostSignificantBits << 8) | (bytes[index] & 0xFFL);
            leastSignificantBits = (leastSignificantBits << 8) | (bytes[index + 8] & 0xFFL);
        }
        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    /**
     * 解析并校验小写规范 UUIDv7 字符串。
     *
     * @param rawValue 待解析字符串
     * @param typeName 写入异常上下文的领域类型名
     * @return 已验证的 UUIDv7
     * @throws IllegalArgumentException 当字符串为空、格式不规范或版本不合法时抛出
     */
    static UUID parse(String rawValue, String typeName) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(typeName + " must not be blank");
        }
        String normalized = rawValue.toLowerCase(Locale.ROOT);
        if (!rawValue.equals(normalized)) {
            throw new IllegalArgumentException(typeName + " must be a lowercase canonical UUIDv7");
        }
        UUID value;
        try {
            value = UUID.fromString(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(typeName + " must be a canonical UUIDv7", exception);
        }
        if (!value.toString().equals(normalized)) {
            throw new IllegalArgumentException(typeName + " must be a canonical UUIDv7");
        }
        return require(value, typeName);
    }

    /**
     * 校验 UUID 的版本号与 Variant，不改变原值。
     *
     * @param value    待校验 UUID
     * @param typeName 写入异常上下文的领域类型名
     * @return 校验通过的同一 UUID
     * @throws NullPointerException     当 UUID 为 {@code null} 时抛出
     * @throws IllegalArgumentException 当版本不是 7 或 Variant 不是 RFC 4122 形式时抛出
     */
    static UUID require(UUID value, String typeName) {
        Objects.requireNonNull(value, typeName + " must not be null");
        if (value.version() != 7 || value.variant() != 2) {
            throw new IllegalArgumentException(typeName + " must be an RFC 9562 UUIDv7");
        }
        return value;
    }

    /**
     * 提取 UUIDv7 内嵌的 48 位 Unix 毫秒时间。
     *
     * @param value 待解析的 UUIDv7
     * @return UTC 时间线上的生成时刻
     * @throws NullPointerException     当 UUID 为 {@code null} 时抛出
     * @throws IllegalArgumentException 当 UUID 不是合法 UUIDv7 时抛出
     */
    static Instant timestamp(UUID value) {
        require(value, "value");
        long unixMillis = (value.getMostSignificantBits() >>> 16) & MAX_UNIX_MILLIS;
        return Instant.ofEpochMilli(unixMillis);
    }
}
