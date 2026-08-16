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

package space.refinex.agentark.scheduling.application;

import space.refinex.agentark.scheduling.domain.SchedulerException;
import space.refinex.agentark.scheduling.port.WebhookSecretResolver;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 验证 Webhook HMAC-SHA256、时间窗口和安全 Nonce，不记录请求正文或密钥。
 *
 * @author refinex
 */
public final class WebhookSignatureVerifier {

    /**
     * 单个 Webhook 最大正文为 1 MiB。
     */
    private static final int MAX_BODY_BYTES = 1_048_576;

    /**
     * 外部 Secret 解析端口。
     */
    private final WebhookSecretResolver secretResolver;

    /**
     * UTC 时间来源。
     */
    private final Clock clock;

    /**
     * 允许的签名时钟偏差。
     */
    private final Duration allowedSkew;

    /**
     * 创建 Webhook 验签器。
     *
     * @param secretResolver SecretRef 解析器
     * @param clock          UTC 时钟
     * @param allowedSkew    允许的双向时间偏差
     */
    public WebhookSignatureVerifier(
        WebhookSecretResolver secretResolver, Clock clock, Duration allowedSkew) {
        this.secretResolver = Objects.requireNonNull(
            secretResolver, "secretResolver must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.allowedSkew = Objects.requireNonNull(allowedSkew, "allowedSkew must not be null");
        if (allowedSkew.isNegative() || allowedSkew.isZero()
            || allowedSkew.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("allowedSkew must be between 1ns and 15 minutes");
        }
    }

    /**
     * 验证 `v1=hex` 签名；签名内容为 `timestamp.nonce.body` 的 UTF-8 字节。
     *
     * @param secretRef Secret 引用
     * @param timestamp Unix 秒字符串
     * @param nonce     16 至 128 个安全字符
     * @param signature `v1=` 前缀的小写 SHA-256 十六进制摘要
     * @param body      原始请求体字节
     * @return 已验证的请求时间
     */
    public Instant verify(
        String secretRef, String timestamp, String nonce, String signature, byte[] body) {
        Objects.requireNonNull(body, "body must not be null");
        if (body.length > MAX_BODY_BYTES
            || nonce == null || !nonce.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{15,127}")
            || signature == null || !signature.matches("v1=[0-9a-f]{64}")) {
            throw new SchedulerException("WEBHOOK_SIGNATURE_INVALID", "webhook signature is invalid");
        }
        long epochSecond;
        try {
            epochSecond = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw new SchedulerException("WEBHOOK_TIMESTAMP_INVALID", "webhook timestamp is invalid");
        }
        Instant requestTime;
        try {
            requestTime = Instant.ofEpochSecond(epochSecond);
        } catch (DateTimeException exception) {
            throw new SchedulerException("WEBHOOK_TIMESTAMP_INVALID", "webhook timestamp is invalid");
        }
        if (Duration.between(requestTime, clock.instant()).abs().compareTo(allowedSkew) > 0) {
            throw new SchedulerException("WEBHOOK_TIMESTAMP_EXPIRED", "webhook timestamp is outside allowed skew");
        }
        char[] secret = secretResolver.resolve(secretRef);
        if (secret == null || secret.length < 32) {
            throw new SchedulerException("WEBHOOK_SECRET_UNAVAILABLE", "webhook secret is unavailable");
        }
        byte[] key = encode(secret);
        byte[] expected = null;
        byte[] supplied = null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(nonce.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            expected = mac.doFinal(body);
            supplied = HexFormat.of().parseHex(signature.substring(3));
            if (!MessageDigest.isEqual(expected, supplied)) {
                throw new SchedulerException("WEBHOOK_SIGNATURE_INVALID", "webhook signature is invalid");
            }
            return requestTime;
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new SchedulerException("WEBHOOK_SIGNATURE_INVALID", "webhook signature is invalid");
        } finally {
            Arrays.fill(secret, '\0');
            Arrays.fill(key, (byte) 0);
            if (expected != null) {
                Arrays.fill(expected, (byte) 0);
            }
            if (supplied != null) {
                Arrays.fill(supplied, (byte) 0);
            }
        }
    }

    /**
     * 将可清零字符密钥编码为 UTF-8 字节，并清理中间缓冲区。
     *
     * @param secret 字符密钥
     * @return UTF-8 字节
     */
    private byte[] encode(char[] secret) {
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(secret));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            if (buffer.hasArray()) {
                Arrays.fill(buffer.array(), (byte) 0);
            }
            return bytes;
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new SchedulerException("WEBHOOK_SECRET_INVALID", "webhook secret encoding is invalid");
        }
    }
}
