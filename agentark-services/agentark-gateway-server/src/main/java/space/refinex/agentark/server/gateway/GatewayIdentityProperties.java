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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * 定义 Gateway 内置 Identity 的 MySQL 账号、密码策略、会话和内部 JWT 配置。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.gateway.identity")
public class GatewayIdentityProperties {

    /**
     * 是否启用 MySQL 内置身份；关闭时仍可使用外部 OIDC 或机器凭据。
     */
    private boolean enabled;

    /**
     * 内置 JWT 稳定 Issuer，不随部署 Host 漂移。
     */
    private URI issuer = URI.create("https://identity.agentark.local");

    /**
     * Redis WebSession Cookie 名称。
     */
    private String sessionCookieName = "AGENTARK_SESSION";

    /**
     * 生产 Cookie 是否要求 HTTPS。
     */
    private boolean sessionCookieSecure = true;

    /**
     * 初始管理员用户名。
     */
    private String bootstrapUsername = "agentark-admin";

    /**
     * 初始管理员邮箱。
     */
    private String bootstrapEmail = "agentark-admin@localhost.invalid";

    /**
     * 初始管理员展示名称。
     */
    private String bootstrapDisplayName = "AgentArk Administrator";

    /**
     * 只由 Docker Secret 或 Secret Manager 注入的一次性初始密码。
     */
    private String bootstrapPassword;

    /**
     * 密码 Pepper，不进入数据库或日志。
     */
    private String passwordPepper;

    /**
     * PKCS#8 PEM RSA 私钥，只由 Secret 注入。
     */
    private String signingPrivateKey;

    /**
     * 内部 JWT Key ID。
     */
    private String signingKeyId = "agentark-local-rs256-v1";

    /**
     * 向下游转发的内部 JWT 有效期。
     */
    private Duration accessTokenTtl = Duration.ofSeconds(90);

    /**
     * 用户自定义正式密码最小 Unicode Code Point 数。
     */
    private int passwordMinLength = 15;

    /**
     * 密码最大 Unicode Code Point 数，兼容密码管理器与长口令。
     */
    private int passwordMaxLength = 128;

    /**
     * 连续失败达到该次数后锁定账号。
     */
    private int failureThreshold = 5;

    /**
     * 自动锁定时长。
     */
    private Duration lockDuration = Duration.ofMinutes(15);

    /**
     * 返回是否启用内置身份。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置内置身份启用状态。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回稳定 Issuer。
     */
    public URI getIssuer() {
        return issuer;
    }

    /**
     * 设置稳定 Issuer。
     */
    public void setIssuer(URI issuer) {
        this.issuer = issuer;
    }

    /**
     * 返回 Session Cookie 名称。
     */
    public String getSessionCookieName() {
        return sessionCookieName;
    }

    /**
     * 设置 Session Cookie 名称。
     */
    public void setSessionCookieName(String sessionCookieName) {
        this.sessionCookieName = sessionCookieName;
    }

    /**
     * 返回 Cookie Secure 开关。
     */
    public boolean isSessionCookieSecure() {
        return sessionCookieSecure;
    }

    /**
     * 设置 Cookie Secure 开关。
     */
    public void setSessionCookieSecure(boolean sessionCookieSecure) {
        this.sessionCookieSecure = sessionCookieSecure;
    }

    /**
     * 返回初始管理员用户名。
     */
    public String getBootstrapUsername() {
        return bootstrapUsername;
    }

    /**
     * 设置初始管理员用户名。
     */
    public void setBootstrapUsername(String bootstrapUsername) {
        this.bootstrapUsername = bootstrapUsername;
    }

    /**
     * 返回初始管理员邮箱。
     */
    public String getBootstrapEmail() {
        return bootstrapEmail;
    }

    /**
     * 设置初始管理员邮箱。
     */
    public void setBootstrapEmail(String bootstrapEmail) {
        this.bootstrapEmail = bootstrapEmail;
    }

    /**
     * 返回初始管理员展示名称。
     */
    public String getBootstrapDisplayName() {
        return bootstrapDisplayName;
    }

    /**
     * 设置初始管理员展示名称。
     */
    public void setBootstrapDisplayName(String bootstrapDisplayName) {
        this.bootstrapDisplayName = bootstrapDisplayName;
    }

    /**
     * 返回一次性初始密码。
     */
    public String getBootstrapPassword() {
        return bootstrapPassword;
    }

    /**
     * 设置一次性初始密码。
     */
    public void setBootstrapPassword(String bootstrapPassword) {
        this.bootstrapPassword = bootstrapPassword;
    }

    /**
     * 返回密码 Pepper。
     */
    public String getPasswordPepper() {
        return passwordPepper;
    }

    /**
     * 设置密码 Pepper。
     */
    public void setPasswordPepper(String passwordPepper) {
        this.passwordPepper = passwordPepper;
    }

    /**
     * 返回 RSA 私钥 PEM。
     */
    public String getSigningPrivateKey() {
        return signingPrivateKey;
    }

    /**
     * 设置 RSA 私钥 PEM。
     */
    public void setSigningPrivateKey(String signingPrivateKey) {
        this.signingPrivateKey = signingPrivateKey;
    }

    /**
     * 返回 JWT Key ID。
     */
    public String getSigningKeyId() {
        return signingKeyId;
    }

    /**
     * 设置 JWT Key ID。
     */
    public void setSigningKeyId(String signingKeyId) {
        this.signingKeyId = signingKeyId;
    }

    /**
     * 返回内部 JWT TTL。
     */
    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    /**
     * 设置内部 JWT TTL。
     */
    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    /**
     * 返回正式密码最小长度。
     */
    public int getPasswordMinLength() {
        return passwordMinLength;
    }

    /**
     * 设置正式密码最小长度。
     */
    public void setPasswordMinLength(int passwordMinLength) {
        this.passwordMinLength = passwordMinLength;
    }

    /**
     * 返回密码最大长度。
     */
    public int getPasswordMaxLength() {
        return passwordMaxLength;
    }

    /**
     * 设置密码最大长度。
     */
    public void setPasswordMaxLength(int passwordMaxLength) {
        this.passwordMaxLength = passwordMaxLength;
    }

    /**
     * 返回失败锁定阈值。
     */
    public int getFailureThreshold() {
        return failureThreshold;
    }

    /**
     * 设置失败锁定阈值。
     */
    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    /**
     * 返回自动锁定时长。
     */
    public Duration getLockDuration() {
        return lockDuration;
    }

    /**
     * 设置自动锁定时长。
     */
    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }

    /**
     * 校验内置身份配置完整、安全且适合当前运行模式。
     */
    public void validate() {
        if (!enabled) {
            return;
        }
        if (issuer == null || !issuer.isAbsolute()
            || !("https".equalsIgnoreCase(issuer.getScheme())
            || ("http".equalsIgnoreCase(issuer.getScheme())))) {
            throw new IllegalStateException("built-in identity issuer must be an absolute HTTP(S) URI");
        }
        requireSecret(bootstrapPassword, "bootstrap password");
        requireSecret(passwordPepper, "password pepper");
        requireSecret(signingPrivateKey, "signing private key");
        if (sessionCookieName == null || sessionCookieName.isBlank()) {
            throw new IllegalStateException("identity session cookie name is required");
        }
        if (passwordMinLength < 15 || passwordMaxLength < 64
            || passwordMaxLength < passwordMinLength) {
            throw new IllegalStateException("identity password length policy is invalid");
        }
        if (failureThreshold < 3 || lockDuration == null || lockDuration.isNegative()
            || lockDuration.isZero()) {
            throw new IllegalStateException("identity login lock policy is invalid");
        }
        if (accessTokenTtl == null || accessTokenTtl.compareTo(Duration.ofMinutes(5)) > 0
            || accessTokenTtl.compareTo(Duration.ofSeconds(30)) < 0) {
            throw new IllegalStateException("identity access token ttl must be 30 to 300 seconds");
        }
    }

    /**
     * 校验敏感配置存在但不回显。
     */
    private static void requireSecret(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
    }
}
