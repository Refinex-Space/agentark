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

package space.refinex.agentark.runtime.provider.agentscope.mcp;

import org.junit.jupiter.api.Test;
import space.refinex.agentark.runtime.provider.agentscope.error.AgentScopeProviderException;

import java.net.InetAddress;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 证明 MCP Endpoint 守卫拒绝私网、元数据服务、DNS Rebinding 和越权 STDIO。
 *
 * @author refinex
 */
class McpEndpointGuardTest {

    /**
     * 证明白名单 HTTPS 主机只能获得当前公网 IP 的连接许可。
     *
     * @throws Exception 测试地址解析失败时抛出
     */
    @Test
    void shouldAuthorizePinnedPublicAddress() throws Exception {
        McpEndpointGuard guard = new McpEndpointGuard(
            Set.of("mcp.example.com"), Set.of(),
            host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
            Clock.systemUTC());

        McpEndpointGuard.ConnectionPermit permit = guard.authorize(remote());

        assertThat(permit.addresses()).containsExactly("93.184.216.34");
        guard.revalidate(permit);
    }

    /**
     * 证明同一主机二次解析到不同公网地址时按 DNS Rebinding 拒绝连接。
     *
     * @throws Exception 测试地址解析失败时抛出
     */
    @Test
    void shouldRejectDnsRebinding() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        McpEndpointGuard guard = new McpEndpointGuard(
            Set.of("mcp.example.com"), Set.of(), host -> new InetAddress[]{
                InetAddress.getByName(attempts.getAndIncrement() == 0
                    ? "93.184.216.34" : "93.184.216.35")}, Clock.systemUTC());
        McpEndpointGuard.ConnectionPermit permit = guard.authorize(remote());

        assertThatThrownBy(() -> guard.revalidate(permit))
            .isInstanceOf(AgentScopeProviderException.class)
            .hasMessageContaining("DNS resolution changed");
    }

    /**
     * 证明白名单域名只要解析结果包含私网或 IPv6 ULA 就整体拒绝。
     *
     * @throws Exception 测试地址解析失败时抛出
     */
    @Test
    void shouldRejectMixedPrivateAndUniqueLocalAddresses() throws Exception {
        McpEndpointGuard privateGuard = new McpEndpointGuard(
            Set.of("mcp.example.com"), Set.of(), host -> new InetAddress[]{
                InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.0.0.1")},
            Clock.systemUTC());
        McpEndpointGuard ipv6Guard = new McpEndpointGuard(
            Set.of("mcp.example.com"), Set.of(),
            host -> new InetAddress[]{InetAddress.getByName("fd00::1")}, Clock.systemUTC());

        assertThatThrownBy(() -> privateGuard.authorize(remote()))
            .isInstanceOf(AgentScopeProviderException.class)
            .hasMessageContaining("forbidden network");
        assertThatThrownBy(() -> ipv6Guard.authorize(remote()))
            .isInstanceOf(AgentScopeProviderException.class)
            .hasMessageContaining("forbidden network");
    }

    /**
     * 证明云元数据主机即使命中部署白名单也不能获得连接许可。
     */
    @Test
    void shouldRejectCloudMetadataHostname() {
        McpEndpointGuard guard = new McpEndpointGuard(
            Set.of("metadata.google.internal"), Set.of(),
            host -> new InetAddress[0], Clock.systemUTC());
        McpBinding binding = new McpBinding(
            "version-1", "streamable-http", URI.create("https://metadata.google.internal/mcp"),
            Optional.empty(), List.of("read"));

        assertThatThrownBy(() -> guard.authorize(binding))
            .isInstanceOf(AgentScopeProviderException.class)
            .hasMessageContaining("allowlisted HTTPS endpoint");
    }

    /**
     * 证明 STDIO MCP 只能执行部署白名单中的固定命令。
     */
    @Test
    void shouldRequireStdioCommandAllowlist() {
        McpBinding binding = new McpBinding(
            "version-1", "stdio", URI.create("stdio://trusted-mcp"),
            Optional.empty(), List.of("read"));

        assertThat(new McpEndpointGuard(Set.of(), Set.of("trusted-mcp"))
            .authorize(binding).stdioCommand()).isEqualTo("trusted-mcp");
        assertThatThrownBy(() -> new McpEndpointGuard(Set.of(), Set.of()).authorize(binding))
            .isInstanceOf(AgentScopeProviderException.class)
            .hasMessageContaining("not allowlisted");
    }

    /**
     * 创建用于安全测试的固定远程 MCP 绑定。
     *
     * @return 不含凭据的 HTTPS MCP 绑定
     */
    private McpBinding remote() {
        return new McpBinding(
            "version-1", "streamable-http", URI.create("https://mcp.example.com/mcp"),
            Optional.empty(), List.of("read"));
    }
}
