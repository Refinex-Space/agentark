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

import space.refinex.agentark.control.secret.application.ResolvedSecret;
import space.refinex.agentark.control.secret.application.port.SecretResolver;
import space.refinex.agentark.control.secret.domain.SecretMetadata;
import space.refinex.agentark.control.secret.domain.SecretMetadataStatus;
import space.refinex.agentark.control.secret.domain.SecretProviderType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

/**
 * 通过 HTTPS 和短期工作负载令牌读取 Vault KV v2 的固定版本 `value` 字段。
 *
 * @author refinex
 */
public final class VaultKvV2SecretResolver implements SecretResolver {

    /** 单次 Vault JSON 响应最大字节数。 */
    private static final int MAX_RESPONSE_BYTES = 65536;

    /** 禁止自动重定向的 HTTP Client。 */
    private final HttpClient httpClient;

    /** Vault 根地址。 */
    private final URI address;

    /** KV v2 挂载点。 */
    private final String mount;

    /** 可选 Vault Namespace。 */
    private final String namespace;

    /** 按请求读取的短期令牌来源。 */
    private final VaultTokenSource tokenSource;

    /** JSON 解析器。 */
    private final JsonMapper jsonMapper;

    /** 单次读取总超时。 */
    private final Duration readTimeout;

    /**
     * 创建生产 Vault KV v2 解析器。
     *
     * @param httpClient 禁止重定向且具有受控连接超时的客户端
     * @param address Vault HTTPS 根地址
     * @param mount KV v2 挂载点
     * @param namespace 可选 Vault Namespace
     * @param tokenSource 短期令牌来源
     * @param jsonMapper JSON 解析器
     * @param readTimeout 单次读取总超时
     */
    public VaultKvV2SecretResolver(
        HttpClient httpClient,
        URI address,
        String mount,
        String namespace,
        VaultTokenSource tokenSource,
        JsonMapper jsonMapper,
        Duration readTimeout) {
        this(httpClient, address, mount, namespace, tokenSource, jsonMapper, readTimeout, false);
    }

    /**
     * 创建可显式允许回环 HTTP 的测试实例。
     *
     * @param httpClient 禁止重定向的客户端
     * @param address Vault 地址
     * @param mount KV v2 挂载点
     * @param namespace 可选 Namespace
     * @param tokenSource 短期令牌来源
     * @param jsonMapper JSON 解析器
     * @param readTimeout 读取超时
     * @param allowLoopbackHttp 是否仅允许回环 HTTP
     */
    public VaultKvV2SecretResolver(
        HttpClient httpClient,
        URI address,
        String mount,
        String namespace,
        VaultTokenSource tokenSource,
        JsonMapper jsonMapper,
        Duration readTimeout,
        boolean allowLoopbackHttp) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("vault redirects must be disabled");
        }
        this.address = requireAddress(address, allowLoopbackHttp);
        if (mount == null || !mount.matches("[a-z][a-z0-9_-]{0,62}")) {
            throw new IllegalArgumentException("vault mount is invalid");
        }
        this.mount = mount;
        this.namespace = Objects.requireNonNull(namespace, "namespace must not be null");
        this.tokenSource = Objects.requireNonNull(tokenSource, "tokenSource must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
        if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()
            || readTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("readTimeout must be positive and at most 30 seconds");
        }
        this.readTimeout = readTimeout;
    }

    /**
     * 读取启用且类型匹配的固定 Secret 版本，响应正文和令牌绝不进入异常消息。
     *
     * @param metadata 已授权非敏感元数据
     * @return 可清零的短期 Secret
     * @throws IOException Vault 拒绝、超时、响应超限或格式错误时抛出
     */
    @Override
    public ResolvedSecret resolve(SecretMetadata metadata) throws IOException {
        Objects.requireNonNull(metadata, "metadata must not be null");
        if (metadata.provider() != SecretProviderType.VAULT
            || metadata.status() != SecretMetadataStatus.ENABLED) {
            throw new IOException("secret metadata is not enabled for Vault resolution");
        }
        String path = safePath(metadata.externalPath());
        String version = metadata.externalVersion();
        if (!version.isBlank() && !version.matches("[1-9][0-9]{0,18}")) {
            throw new IOException("vault secret version is invalid");
        }
        URI requestUri = address.resolve(
            "/v1/" + mount + "/data/" + path + (version.isBlank() ? "" : "?version=" + version));
        char[] token = tokenSource.load();
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(requestUri)
                .timeout(readTimeout)
                .header("Accept", "application/json")
                .header("X-Vault-Token", new String(token))
                .GET();
            if (!namespace.isBlank()) {
                request.header("X-Vault-Namespace", namespace);
            }
            HttpResponse<InputStream> response = httpClient.send(
                request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (response.statusCode() != 200 || bytes.length > MAX_RESPONSE_BYTES) {
                    throw new IOException("vault response was rejected");
                }
                return resolved(bytes, version);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("vault request was interrupted", exception);
        } finally {
            Arrays.fill(token, '\0');
        }
    }

    /**
     * 解析固定 KV v2 响应并只读取 `data.data.value`。
     *
     * @param bytes 有界 JSON 响应
     * @param expectedVersion 可选期望版本
     * @return 可清零 Secret
     * @throws IOException 响应结构或版本不匹配时抛出
     */
    private ResolvedSecret resolved(byte[] bytes, String expectedVersion) throws IOException {
        try {
            JsonNode data = jsonMapper.readTree(bytes).path("data");
            JsonNode value = data.path("data").path("value");
            JsonNode version = data.path("metadata").path("version");
            if (!value.isString() || value.stringValue().isEmpty()
                || !version.canConvertToLong()
                || !expectedVersion.isBlank()
                && version.longValue() != Long.parseLong(expectedVersion)) {
                throw new IOException("vault response structure is invalid");
            }
            return new ResolvedSecret(value.stringValue().toCharArray());
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("vault response structure is invalid", exception);
        }
    }

    /**
     * 校验 Vault 外部路径并保留斜杠层级。
     *
     * @param value 元数据外部路径
     * @return 安全相对路径
     * @throws IOException 路径包含空段、目录穿越或非法字符时抛出
     */
    private String safePath(String value) throws IOException {
        if (value == null || value.startsWith("/") || value.endsWith("/")
            || !value.matches("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*")
            || Arrays.stream(value.split("/")).anyMatch(segment -> segment.equals(".."))) {
            throw new IOException("vault external path is invalid");
        }
        return value;
    }

    /**
     * 校验生产 HTTPS 地址；测试仅允许回环 HTTP。
     *
     * @param value Vault 地址
     * @param allowLoopbackHttp 是否允许回环 HTTP
     * @return 已校验地址
     */
    private static URI requireAddress(URI value, boolean allowLoopbackHttp) {
        if (value == null || !value.isAbsolute() || value.getHost() == null
            || value.getRawUserInfo() != null || value.getRawQuery() != null
            || value.getRawFragment() != null) {
            throw new IllegalArgumentException("vault address is invalid");
        }
        if ("https".equals(value.getScheme())) {
            return value;
        }
        try {
            if (allowLoopbackHttp && "http".equals(value.getScheme())
                && InetAddress.getByName(value.getHost()).isLoopbackAddress()) {
                return value;
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("vault test address cannot be resolved", exception);
        }
        throw new IllegalArgumentException("vault address must use HTTPS");
    }
}
