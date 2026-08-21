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

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * 使用部署 Pepper 与 Argon2id 完成密码摘要、校验、策略检查和临时密码生成。
 *
 * @author refinex
 */
public final class GatewayIdentityPasswordService {

    /**
     * Argon2id 实现，参数采用 Spring Security 5.8 安全基线。
     */
    private final Argon2PasswordEncoder encoder =
        Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    /**
     * 临时密码随机源。
     */
    private final SecureRandom random = new SecureRandom();

    /**
     * 不允许设置的常见弱密码规范值。
     */
    private static final Set<String> BLOCKED = Set.of(
        "password", "password123", "123456789012345", "adminadminadmin", "agentark-agentark");

    /**
     * 密码策略与 Pepper 配置。
     */
    private final GatewayIdentityProperties properties;

    /**
     * 创建密码服务。
     *
     * @param properties 已验证的 Identity 配置
     */
    public GatewayIdentityPasswordService(GatewayIdentityProperties properties) {
        this.properties = java.util.Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * 生成 192 bit Base64URL 临时密码，不包含 Shell 或 URL 元字符。
     *
     * @return 只应交付一次的随机临时密码
     */
    public String temporaryPassword() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 对 Pepper 后的密码生成 Argon2id PHC 摘要。
     *
     * @param rawPassword 原始密码，仅在调用栈内短暂存在
     * @return PHC 格式摘要
     */
    public String encode(String rawPassword) {
        return encoder.encode(peppered(rawPassword));
    }

    /**
     * 常量时间校验原始密码与 PHC 摘要。
     *
     * @param rawPassword 原始密码
     * @param encoded     数据库摘要
     * @return 是否匹配
     */
    public boolean matches(String rawPassword, String encoded) {
        if (rawPassword == null || encoded == null || rawPassword.codePointCount(0, rawPassword.length())
            > properties.getPasswordMaxLength()) {
            return false;
        }
        return encoder.matches(peppered(rawPassword), encoded);
    }

    /**
     * 校验用户自定义正式密码长度、弱密码、用户名和邮箱复用边界。
     *
     * @param password 新密码
     * @param username 当前用户名
     * @param email    可空邮箱
     */
    public void validateNewPassword(String password, String username, String email) {
        if (password == null) {
            throw new IllegalArgumentException("password is required");
        }
        int length = password.codePointCount(0, password.length());
        if (length < properties.getPasswordMinLength() || length > properties.getPasswordMaxLength()) {
            throw new IllegalArgumentException("password length is outside the configured range");
        }
        String normalized = password.toLowerCase(Locale.ROOT);
        if (BLOCKED.contains(normalized)
            || normalized.equals(username.toLowerCase(Locale.ROOT))
            || (email != null && normalized.equals(email.toLowerCase(Locale.ROOT)))) {
            throw new IllegalArgumentException("password is blocked by policy");
        }
    }

    /**
     * 使用 HMAC-SHA-256 Pepper 预处理密码，数据库泄漏时仍需要部署 Secret。
     */
    private String peppered(String password) {
        if (password == null) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                properties.getPasswordPepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(
                mac.doFinal(password.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("password pepper algorithm is unavailable", exception);
        }
    }
}
