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

package space.refinex.agentark.control.secret.adapter.out.vault;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 定义生产 Vault KV v2 地址、挂载点、令牌文件和有界超时。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.control.secret.vault")
public class VaultSecretProperties {

    /** 是否启用生产 Vault 解析器。 */
    private boolean enabled;

    /** Vault HTTPS 根地址。 */
    private URI address;

    /** KV v2 挂载点。 */
    private String mount = "secret";

    /** 可选 Vault Enterprise Namespace。 */
    private String namespace = "";

    /** 由工作负载身份挂载的短期令牌文件。 */
    private Path tokenFile = Path.of("/var/run/secrets/agentark/vault-token");

    /** TCP 连接超时。 */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** 单次读取总超时。 */
    private Duration readTimeout = Duration.ofSeconds(5);

    /** @return 是否启用 Vault 解析器 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否启用 Vault 解析器 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return Vault 根地址 */
    public URI getAddress() {
        return address;
    }

    /**
     * 设置绝对 HTTPS Vault 地址，禁止凭据、查询和片段。
     *
     * @param address Vault 根地址
     */
    public void setAddress(URI address) {
        if (address == null || !address.isAbsolute() || !"https".equals(address.getScheme())
            || address.getHost() == null || address.getRawUserInfo() != null
            || address.getRawQuery() != null || address.getRawFragment() != null) {
            throw new IllegalArgumentException("vault address must be an absolute HTTPS URI");
        }
        this.address = address;
    }

    /** @return KV v2 挂载点 */
    public String getMount() {
        return mount;
    }

    /** @param mount 小写安全挂载点 */
    public void setMount(String mount) {
        if (mount == null || !mount.matches("[a-z][a-z0-9_-]{0,62}")) {
            throw new IllegalArgumentException("vault mount is invalid");
        }
        this.mount = mount;
    }

    /** @return 可选 Vault Namespace */
    public String getNamespace() {
        return namespace;
    }

    /** @param namespace 不含控制字符的 Namespace */
    public void setNamespace(String namespace) {
        if (namespace == null || namespace.length() > 255
            || namespace.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("vault namespace is invalid");
        }
        this.namespace = namespace;
    }

    /** @return 短期令牌文件 */
    public Path getTokenFile() {
        return tokenFile;
    }

    /** @param tokenFile 专用绝对普通文件路径 */
    public void setTokenFile(Path tokenFile) {
        if (tokenFile == null || !tokenFile.isAbsolute()) {
            throw new IllegalArgumentException("vault token file must be absolute");
        }
        this.tokenFile = tokenFile.normalize();
    }

    /** @return 连接超时 */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /** @param connectTimeout 正数且不超过三十秒 */
    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = timeout(connectTimeout, "connectTimeout");
    }

    /** @return 读取超时 */
    public Duration getReadTimeout() {
        return readTimeout;
    }

    /** @param readTimeout 正数且不超过三十秒 */
    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = timeout(readTimeout, "readTimeout");
    }

    /**
     * 校验超时范围。
     *
     * @param value 候选超时
     * @param name 属性名称
     * @return 已校验超时
     */
    private Duration timeout(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()
            || value.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException(name + " must be positive and at most 30 seconds");
        }
        return value;
    }
}
