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

import org.junit.jupiter.api.Test;
import space.refinex.agentark.scheduling.domain.SchedulerException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Webhook HMAC、时间窗口、Nonce 格式与临时 Secret 清零边界。
 *
 * @author refinex
 */
class WebhookSignatureVerifierTest {

    /** 固定测试时间。 */
    private static final Instant NOW = Instant.parse("2026-08-16T10:00:00Z");

    /** 创建 Webhook 验签测试实例。 */
    WebhookSignatureVerifierTest() {
        // JUnit Jupiter 为每个测试方法创建实例。
    }

    /** 证明合法签名被接受且 Resolver 返回的密钥数组在调用结束时清零。 */
    @Test
    void acceptsValidSignatureAndClearsResolvedSecret() throws Exception {
        char[] resolved = "0123456789abcdef0123456789abcdef".toCharArray();
        String timestamp = Long.toString(NOW.getEpochSecond());
        String nonce = "nonce-0123456789abcdef";
        byte[] body = "{\"event\":\"ready\"}".getBytes(StandardCharsets.UTF_8);
        WebhookSignatureVerifier verifier = verifier(resolved);

        Instant verified = verifier.verify(
            "secret://webhook/test", timestamp, nonce,
            signature(timestamp, nonce, body), body);

        assertThat(verified).isEqualTo(NOW);
        assertThat(resolved).containsOnly('\0');
    }

    /** 证明超出允许时钟偏差的请求在解析 Secret 前即被拒绝。 */
    @Test
    void rejectsExpiredTimestampBeforeResolvingSecret() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(
            secretRef -> {
                throw new AssertionError("expired request must not resolve secret");
            }, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));

        assertThatThrownBy(() -> verifier.verify(
            "secret://webhook/test", Long.toString(NOW.minusSeconds(301).getEpochSecond()),
            "nonce-0123456789abcdef", "v1=" + "0".repeat(64), new byte[] {1}))
            .isInstanceOf(SchedulerException.class)
            .extracting(exception -> ((SchedulerException) exception).code())
            .isEqualTo("WEBHOOK_TIMESTAMP_EXPIRED");
    }

    /** 证明超出 Instant 范围的 Unix 秒得到稳定时间戳错误而不是未分类异常。 */
    @Test
    void rejectsTimestampOutsideInstantRange() {
        WebhookSignatureVerifier verifier = verifier(
            "0123456789abcdef0123456789abcdef".toCharArray());

        assertThatThrownBy(() -> verifier.verify(
            "secret://webhook/test", Long.toString(Long.MAX_VALUE),
            "nonce-0123456789abcdef", "v1=" + "0".repeat(64), new byte[0]))
            .isInstanceOf(SchedulerException.class)
            .extracting(exception -> ((SchedulerException) exception).code())
            .isEqualTo("WEBHOOK_TIMESTAMP_INVALID");
    }

    /** 证明错误签名返回稳定错误且不泄漏密钥或正文。 */
    @Test
    void rejectsInvalidSignature() {
        char[] resolved = "0123456789abcdef0123456789abcdef".toCharArray();
        WebhookSignatureVerifier verifier = verifier(resolved);

        assertThatThrownBy(() -> verifier.verify(
            "secret://webhook/test", Long.toString(NOW.getEpochSecond()),
            "nonce-0123456789abcdef", "v1=" + "0".repeat(64), new byte[] {1, 2, 3}))
            .isInstanceOf(SchedulerException.class)
            .hasMessage("webhook signature is invalid");
        assertThat(resolved).containsOnly('\0');
    }

    /**
     * 创建使用固定时间和指定密钥数组的验签器。
     *
     * @param resolved Resolver 返回的可清零数组
     * @return Webhook 验签器
     */
    private WebhookSignatureVerifier verifier(char[] resolved) {
        return new WebhookSignatureVerifier(
            secretRef -> resolved, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));
    }

    /**
     * 生成与生产契约相同的 v1 HMAC-SHA256 签名。
     *
     * @param timestamp Unix 秒
     * @param nonce     Nonce
     * @param body      原始正文
     * @return v1 签名
     * @throws Exception 测试 JDK 不支持 HmacSHA256 时抛出
     */
    private String signature(String timestamp, String nonce, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"));
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        mac.update(nonce.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        return "v1=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
