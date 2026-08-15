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

package space.refinex.agentark.kernel.snapshot;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import space.refinex.agentark.kernel.id.McpServerVersionId;

/**
 * 表示版本化 MCP Endpoint、可选引用式凭据以及显式 Tool 白名单。
 *
 * @param mcpServerVersionId MCP 服务版本标识
 * @param transport          MCP 传输方式
 * @param endpoint           不携带授权材料的 Endpoint URI
 * @param credential         可选凭据绑定，空值必须使用 {@link Optional#empty()}
 * @param allowedTools       允许 Agent 调用的显式 Tool 名称列表
 * @author refinex
 */
public record McpSpec(
    McpServerVersionId mcpServerVersionId,
    McpTransport transport,
    URI endpoint,
    Optional<CredentialSpec> credential,
    List<String> allowedTools) {

    /**
     * MCP Tool 名称的稳定格式。
     */
    private static final Pattern TOOL = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}");

    /**
     * 校验并创建 MCP 规范，同时防御性复制 Tool 白名单。
     *
     * @param mcpServerVersionId MCP 服务版本标识
     * @param transport          MCP 传输方式
     * @param endpoint           Endpoint URI；HTTP 传输只允许 http/https，STDIO 只允许 stdio
     * @param credential         可选引用式凭据
     * @param allowedTools       非空且不重复的 Tool 白名单
     * @throws NullPointerException     当任一容器或必填字段为 {@code null} 时抛出
     * @throws IllegalArgumentException 当 Endpoint 含敏感 URI 组成、传输不匹配或 Tool 名称不合法时抛出
     */
    public McpSpec {
        Objects.requireNonNull(mcpServerVersionId, "McpSpec mcpServerVersionId must not be null");
        Objects.requireNonNull(transport, "McpSpec transport must not be null");
        Objects.requireNonNull(endpoint, "McpSpec endpoint must not be null");
        credential = Objects.requireNonNull(credential, "McpSpec credential must not be null");
        allowedTools = SnapshotRequirements.immutableList(allowedTools, "McpSpec allowedTools");

        Set<String> allowedSchemes =
            transport == McpTransport.STREAMABLE_HTTP ? Set.of("https", "http") : Set.of("stdio");
        if (!allowedSchemes.contains(endpoint.getScheme())
            || endpoint.getRawAuthority() == null
            || endpoint.getRawAuthority().isBlank()
            || endpoint.getRawUserInfo() != null
            || endpoint.getRawQuery() != null
            || endpoint.getRawFragment() != null) {
            throw new IllegalArgumentException(
                "McpSpec endpoint is incompatible with its transport or contains sensitive URI components");
        }
        if (allowedTools.isEmpty()
            || allowedTools.stream().anyMatch(tool -> !TOOL.matcher(tool).matches())) {
            throw new IllegalArgumentException(
                "McpSpec allowedTools must contain valid explicit tool names");
        }
        if (allowedTools.stream().distinct().count() != allowedTools.size()) {
            throw new IllegalArgumentException("McpSpec allowedTools must not contain duplicates");
        }
    }
}
