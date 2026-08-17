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

package space.refinex.agentark.control.secret;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import space.refinex.agentark.control.secret.adapter.out.vault.VaultKvV2SecretResolver;
import space.refinex.agentark.control.secret.application.ResolvedSecret;
import space.refinex.agentark.control.secret.domain.SecretMetadata;
import space.refinex.agentark.control.secret.domain.SecretMetadataStatus;
import space.refinex.agentark.control.secret.domain.SecretProviderType;
import space.refinex.agentark.control.secret.domain.SecretScope;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.SecretMetadataId;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 证明 Vault KV v2 适配器固定版本、禁用重定向、限制地址并保持 Secret 脱敏。
 *
 * @author refinex
 */
class VaultKvV2SecretResolverTest {

    /**
     * 证明解析器只读取固定 KV v2 `value`，按请求发送令牌且返回值可清零。
     *
     * @throws Exception 本地 HTTP Server 或解析失败时抛出
     */
    @Test
    void shouldResolveVersionedVaultValueWithoutExposingIt() throws Exception {
        String dynamicValue = UUID.randomUUID().toString();
        String dynamicToken = UUID.randomUUID().toString();
        AtomicReference<String> observedPath = new AtomicReference<>();
        AtomicReference<String> observedToken = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/secret/data/projects/demo", exchange -> {
            observedPath.set(exchange.getRequestURI().toString());
            observedToken.set(exchange.getRequestHeaders().getFirst("X-Vault-Token"));
            byte[] response = ("{\"data\":{\"data\":{\"value\":\"" + dynamicValue
                + "\"},\"metadata\":{\"version\":7}}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            VaultKvV2SecretResolver resolver = resolver(server, dynamicToken);
            try (ResolvedSecret resolved = resolver.resolve(metadata(
                SecretMetadataStatus.ENABLED, "7"))) {
                assertThat(resolved.copy()).containsExactly(dynamicValue.toCharArray());
                assertThat(resolved.toString()).isEqualTo("[REDACTED]");
            }
            assertThat(observedPath.get()).isEqualTo(
                "/v1/secret/data/projects/demo?version=7");
            assertThat(observedToken.get()).isEqualTo(dynamicToken);
        } finally {
            server.stop(0);
        }
    }

    /**
     * 证明禁用 Metadata 在发出任何网络请求前失败关闭。
     *
     * @throws Exception 本地 HTTP Server 构造失败时抛出
     */
    @Test
    void shouldRejectDisabledMetadata() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            VaultKvV2SecretResolver resolver = resolver(server, UUID.randomUUID().toString());
            assertThatThrownBy(() -> resolver.resolve(metadata(
                SecretMetadataStatus.DISABLED, "7")))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("not enabled");
        } finally {
            server.stop(0);
        }
    }

    /**
     * 证明生产构造器拒绝 HTTP Vault 地址，避免令牌明文传输。
     */
    @Test
    void shouldRequireHttpsOutsideLoopbackTestMode() {
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build();

        assertThatThrownBy(() -> new VaultKvV2SecretResolver(
            client, URI.create("http://127.0.0.1:8200"), "secret", "",
            () -> UUID.randomUUID().toString().toCharArray(), JsonMapper.builder().build(),
            Duration.ofSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("HTTPS");
    }

    /**
     * @param server 回环测试 Server
     * @param token 动态生成且不会进入源码的测试令牌
     * @return 仅允许回环 HTTP 的测试解析器
     */
    private VaultKvV2SecretResolver resolver(HttpServer server, String token) {
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(1)).build();
        return new VaultKvV2SecretResolver(
            client, URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            "secret", "", () -> token.toCharArray(), JsonMapper.builder().build(),
            Duration.ofSeconds(2), true);
    }

    /**
     * @param status Metadata 生命周期状态
     * @param externalVersion 固定 Vault 版本
     * @return 不含 Secret 值的 Vault Metadata
     */
    private SecretMetadata metadata(SecretMetadataStatus status, String externalVersion) {
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        return new SecretMetadata(
            SecretMetadataId.generate(), OrganizationId.generate(), ProjectId.generate(),
            "runtime-key", "Runtime Key", SecretProviderType.VAULT, "projects/demo",
            externalVersion, SecretScope.PROJECT, status, 0, now, now);
    }
}
