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

import space.refinex.agentark.runtime.provider.agentscope.error.AgentScopeProviderException;
import space.refinex.agentark.runtime.provider.agentscope.error.ProviderErrorCode;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 在 MCP Transport 创建前执行主机白名单、DNS/IP 和云元数据 SSRF 防御。
 *
 * <p>远程连接只允许 HTTPS 443。解析得到的全部地址必须是公网地址，生产 Transport 必须使用
 * {@link ConnectionPermit#addresses()} 建立连接，并在重连前重新调用本守卫，不能再次信任主机名解析结果。
 *
 * @author refinex
 */
public final class McpEndpointGuard {

    /**
     * 已知云元数据服务主机名。
     */
    private static final Set<String> METADATA_HOSTS = Set.of(
        "metadata.google.internal", "metadata.aws.internal", "instance-data",
        "metadata.azure.internal");

    /**
     * 允许的远程主机匹配规则，支持精确主机或 `*.example.com` 子域规则。
     */
    private final Set<String> allowedRemoteHosts;

    /**
     * 允许的本地 STDIO 命令名。
     */
    private final Set<String> allowedStdioCommands;

    /**
     * 可替换 DNS 解析器，测试用例可稳定复现 Rebinding。
     */
    private final DnsResolver dnsResolver;

    /**
     * Permit 生成时钟。
     */
    private final Clock clock;

    /**
     * MCP TCP/TLS 连接超时。
     */
    private final Duration connectTimeout;

    /**
     * MCP 单次请求总超时。
     */
    private final Duration requestTimeout;

    /**
     * MCP 单次响应最大字节数。
     */
    private final long maxResponseBytes;

    /**
     * @param allowedRemoteHosts 远程主机白名单
     * @param allowedStdioCommands STDIO 命令白名单
     */
    public McpEndpointGuard(Set<String> allowedRemoteHosts, Set<String> allowedStdioCommands) {
        this(allowedRemoteHosts, allowedStdioCommands, InetAddress::getAllByName,
            Clock.systemUTC(), Duration.ofSeconds(3), Duration.ofSeconds(30), 1024 * 1024);
    }

    /**
     * @param allowedRemoteHosts 远程主机白名单
     * @param allowedStdioCommands STDIO 命令白名单
     * @param dnsResolver DNS 解析器
     * @param clock UTC 时钟
     */
    public McpEndpointGuard(
        Set<String> allowedRemoteHosts,
        Set<String> allowedStdioCommands,
        DnsResolver dnsResolver,
        Clock clock) {
        this(allowedRemoteHosts, allowedStdioCommands, dnsResolver, clock,
            Duration.ofSeconds(3), Duration.ofSeconds(30), 1024 * 1024);
    }

    /**
     * @param allowedRemoteHosts 远程主机白名单
     * @param allowedStdioCommands STDIO 命令白名单
     * @param dnsResolver DNS 解析器
     * @param clock UTC 时钟
     * @param connectTimeout TCP/TLS 连接超时
     * @param requestTimeout 单次请求总超时
     * @param maxResponseBytes 单次响应最大字节数
     */
    public McpEndpointGuard(
        Set<String> allowedRemoteHosts,
        Set<String> allowedStdioCommands,
        DnsResolver dnsResolver,
        Clock clock,
        Duration connectTimeout,
        Duration requestTimeout,
        long maxResponseBytes) {
        this.allowedRemoteHosts = normalize(allowedRemoteHosts, "remote host");
        this.allowedStdioCommands = normalize(allowedStdioCommands, "stdio command");
        this.dnsResolver = Objects.requireNonNull(dnsResolver, "dnsResolver must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.connectTimeout = timeout(connectTimeout, "connectTimeout", Duration.ofSeconds(10));
        this.requestTimeout = timeout(requestTimeout, "requestTimeout", Duration.ofMinutes(2));
        if (maxResponseBytes < 1024 || maxResponseBytes > 16L * 1024 * 1024) {
            throw new IllegalArgumentException(
                "maxResponseBytes must be between 1 KiB and 16 MiB");
        }
        this.maxResponseBytes = maxResponseBytes;
    }

    /**
     * 校验一组 MCP 绑定并为每个绑定签发不可变连接许可。
     *
     * @param bindings Snapshot 中固定的 MCP 绑定
     * @return 与输入顺序一致的连接许可
     */
    public List<ConnectionPermit> authorize(List<McpBinding> bindings) {
        Objects.requireNonNull(bindings, "bindings must not be null");
        return bindings.stream().map(this::authorize).toList();
    }

    /**
     * 校验一个 MCP 绑定。
     *
     * @param binding MCP 绑定
     * @return 只包含允许地址或命令的连接许可
     */
    public ConnectionPermit authorize(McpBinding binding) {
        Objects.requireNonNull(binding, "binding must not be null");
        URI endpoint = binding.endpoint();
        if ("stdio".equals(binding.transport())) {
            String command = endpoint.getHost() == null
                ? endpoint.getRawAuthority() : endpoint.getHost();
            if (!allowedStdioCommands.contains(command.toLowerCase(Locale.ROOT))) {
                throw rejected("MCP stdio command is not allowlisted");
            }
            return new ConnectionPermit(binding.versionId(), endpoint, Set.of(),
                command, connectTimeout, requestTimeout, maxResponseBytes, Instant.now(clock));
        }
        String host = endpoint.getHost();
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getPort() != -1
            && endpoint.getPort() != 443 || host == null || !allowedHost(host)
            || METADATA_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw rejected("MCP remote endpoint is not an allowlisted HTTPS endpoint");
        }
        Set<String> addresses = resolvePublic(host);
        return new ConnectionPermit(binding.versionId(), endpoint, addresses, null,
            connectTimeout, requestTimeout, maxResponseBytes, Instant.now(clock));
    }

    /**
     * 在远程连接重建前重新解析，并拒绝 DNS 地址集合发生变化。
     *
     * @param permit 先前签发的连接许可
     */
    public void revalidate(ConnectionPermit permit) {
        Objects.requireNonNull(permit, "permit must not be null");
        if (permit.stdioCommand() != null) {
            if (!allowedStdioCommands.contains(permit.stdioCommand().toLowerCase(Locale.ROOT))) {
                throw rejected("MCP stdio command authorization has been revoked");
            }
            return;
        }
        Set<String> current = resolvePublic(permit.endpoint().getHost());
        if (!current.equals(permit.addresses())) {
            throw rejected("MCP DNS resolution changed before connection");
        }
    }

    /**
     * @param host 待检查主机名
     * @return 命中精确或子域白名单时为 true
     */
    private boolean allowedHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return allowedRemoteHosts.stream().anyMatch(rule -> rule.startsWith("*.")
            ? normalized.endsWith(rule.substring(1))
            && normalized.length() > rule.substring(1).length()
            : normalized.equals(rule));
    }

    /**
     * 解析主机并拒绝任何非公网、云元数据或混合公网/私网结果。
     *
     * @param host 已命中白名单的主机
     * @return 文本化、去重且稳定顺序的公网地址集合
     */
    private Set<String> resolvePublic(String host) {
        try {
            InetAddress[] resolved = dnsResolver.resolve(host);
            if (resolved == null || resolved.length == 0) {
                throw rejected("MCP host has no DNS address");
            }
            Set<String> addresses = new LinkedHashSet<>();
            for (InetAddress address : resolved) {
                if (address == null || isBlocked(address)) {
                    throw rejected("MCP host resolves to a forbidden network");
                }
                addresses.add(address.getHostAddress());
            }
            return Set.copyOf(addresses);
        } catch (UnknownHostException exception) {
            throw new AgentScopeProviderException(
                ProviderErrorCode.MCP_CONFIGURATION_INVALID,
                "MCP host cannot be resolved", exception);
        }
    }

    /**
     * @param address 已解析地址
     * @return 本地、私网、链路本地、组播、CGNAT、基准测试网或云元数据地址时为 true
     */
    private boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first == 0 || first == 100 && second >= 64 && second <= 127
                || first == 169 && second == 254 || first == 198 && (second == 18 || second == 19)
                || first == 100 && second == 100;
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        return (first & 0xfe) == 0xfc;
    }

    /**
     * 规范化白名单并拒绝空规则和 URL 片段。
     *
     * @param values 原始规则
     * @param kind 规则种类
     * @return 小写不可变规则集合
     */
    private static Set<String> normalize(Set<String> values, String kind) {
        Objects.requireNonNull(values, kind + " allowlist must not be null");
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || value.contains(":") || value.contains("/")
                || value.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException(kind + " allowlist contains an invalid value");
            }
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(result);
    }

    /**
     * @param value 候选超时
     * @param name 属性名
     * @param maximum 最大允许时长
     * @return 正数有界超时
     */
    private static Duration timeout(Duration value, String name, Duration maximum) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(name + " must be positive and bounded");
        }
        return value;
    }

    /**
     * @param message 不含主机、地址或凭据的稳定拒绝原因
     * @return MCP 配置异常
     */
    private AgentScopeProviderException rejected(String message) {
        return new AgentScopeProviderException(
            ProviderErrorCode.MCP_CONFIGURATION_INVALID, message);
    }

    /**
     * 允许测试替换 DNS 响应，生产使用 JVM DNS 解析器。
     *
     * @author refinex
     */
    @FunctionalInterface
    public interface DnsResolver {

        /**
         * @param host 不含凭据的主机名
         * @return 当前解析到的全部地址
         * @throws UnknownHostException 无法解析时抛出
         */
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    /**
     * 表示 MCP 组件工厂唯一可使用的已校验连接目标。
     *
     * @param versionId MCP Server 版本标识
     * @param endpoint 原始 HTTPS 或 STDIO URI
     * @param addresses 远程连接允许使用的固定 IP 集合
     * @param stdioCommand 本地连接允许执行的固定命令；远程连接为空
     * @param connectTimeout TCP/TLS 连接超时
     * @param requestTimeout 单次请求总超时
     * @param maxResponseBytes 单次响应最大字节数
     * @param authorizedAt 签发时刻
     * @author refinex
     */
    public record ConnectionPermit(
        String versionId,
        URI endpoint,
        Set<String> addresses,
        String stdioCommand,
        Duration connectTimeout,
        Duration requestTimeout,
        long maxResponseBytes,
        Instant authorizedAt) {

        /**
         * 固化连接许可，避免组件工厂改变地址集合。
         */
        public ConnectionPermit {
            if (versionId == null || versionId.isBlank()) {
                throw new IllegalArgumentException("versionId must not be blank");
            }
            Objects.requireNonNull(endpoint, "endpoint must not be null");
            addresses = Set.copyOf(Objects.requireNonNull(
                addresses, "addresses must not be null"));
            Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
            Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
            if (maxResponseBytes < 1) {
                throw new IllegalArgumentException("maxResponseBytes must be positive");
            }
            Objects.requireNonNull(authorizedAt, "authorizedAt must not be null");
        }
    }
}
