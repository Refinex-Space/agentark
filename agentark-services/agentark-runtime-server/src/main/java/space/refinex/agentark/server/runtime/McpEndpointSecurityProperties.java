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

package space.refinex.agentark.server.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;
import java.time.Duration;

/**
 * 定义 Runtime MCP 远程主机和 STDIO 命令的部署级白名单。
 *
 * @author refinex
 */
@ConfigurationProperties("agentark.runtime.security.mcp")
public class McpEndpointSecurityProperties {

    /**
     * 允许访问的远程 HTTPS 主机；支持精确主机和 `*.example.com` 子域规则。
     */
    private Set<String> allowedRemoteHosts = new LinkedHashSet<>();

    /**
     * 允许执行的本地 STDIO MCP 命令；生产默认空集合。
     */
    private Set<String> allowedStdioCommands = new LinkedHashSet<>();

    /**
     * MCP TCP/TLS 连接超时。
     */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /**
     * MCP 单次请求总超时。
     */
    private Duration requestTimeout = Duration.ofSeconds(30);

    /**
     * MCP 单次响应最大字节数。
     */
    private long maxResponseBytes = 1024 * 1024;

    /**
     * @return 远程主机白名单副本
     */
    public Set<String> getAllowedRemoteHosts() {
        return Set.copyOf(allowedRemoteHosts);
    }

    /**
     * @param allowedRemoteHosts 远程主机白名单
     */
    public void setAllowedRemoteHosts(Set<String> allowedRemoteHosts) {
        this.allowedRemoteHosts = new LinkedHashSet<>(allowedRemoteHosts == null
            ? Set.of() : allowedRemoteHosts);
    }

    /**
     * @return STDIO 命令白名单副本
     */
    public Set<String> getAllowedStdioCommands() {
        return Set.copyOf(allowedStdioCommands);
    }

    /**
     * @param allowedStdioCommands STDIO 命令白名单
     */
    public void setAllowedStdioCommands(Set<String> allowedStdioCommands) {
        this.allowedStdioCommands = new LinkedHashSet<>(allowedStdioCommands == null
            ? Set.of() : allowedStdioCommands);
    }

    /**
     * @return MCP 连接超时
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * @param connectTimeout 正数且不超过十秒
     */
    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = timeout(connectTimeout, Duration.ofSeconds(10), "connectTimeout");
    }

    /**
     * @return MCP 请求总超时
     */
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * @param requestTimeout 正数且不超过两分钟
     */
    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = timeout(requestTimeout, Duration.ofMinutes(2), "requestTimeout");
    }

    /**
     * @return MCP 单次响应最大字节数
     */
    public long getMaxResponseBytes() {
        return maxResponseBytes;
    }

    /**
     * @param maxResponseBytes 1 KiB 到 16 MiB 的字节数
     */
    public void setMaxResponseBytes(long maxResponseBytes) {
        if (maxResponseBytes < 1024 || maxResponseBytes > 16L * 1024 * 1024) {
            throw new IllegalArgumentException(
                "maxResponseBytes must be between 1 KiB and 16 MiB");
        }
        this.maxResponseBytes = maxResponseBytes;
    }

    /**
     * @param value 候选超时
     * @param maximum 最大允许时长
     * @param name 属性名
     * @return 已校验时长
     */
    private Duration timeout(Duration value, Duration maximum, String name) {
        if (value == null || value.isZero() || value.isNegative()
            || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(name + " must be positive and bounded");
        }
        return value;
    }
}
