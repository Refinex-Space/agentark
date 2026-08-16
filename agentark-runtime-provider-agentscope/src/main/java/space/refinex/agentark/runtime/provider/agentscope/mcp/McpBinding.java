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

import space.refinex.agentark.kernel.ref.SecretRef;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 表示经过 SSRF 信息模型和 Tool 唯一性校验的 MCP Server 绑定。
 *
 * @param versionId    MCP Server 版本标识
 * @param transport    streamable-http 或 stdio
 * @param endpoint     不包含授权材料的 Endpoint
 * @param credential   可选认证引用及解析策略
 * @param allowedTools 显式 Tool 白名单
 * @author refinex
 */
public record McpBinding(
    String versionId,
    String transport,
    URI endpoint,
    Optional<Credential> credential,
    List<String> allowedTools) {

    /**
     * MCP Tool 名称稳定格式。
     */
    private static final Pattern TOOL = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}");

    /**
     * 校验 MCP 绑定不包含空值或重复 Tool。
     */
    public McpBinding {
        if (versionId == null || versionId.isBlank() || transport == null || transport.isBlank()) {
            throw new IllegalArgumentException("MCP binding text is invalid");
        }
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        credential = Objects.requireNonNull(credential, "credential must not be null");
        allowedTools = List.copyOf(Objects.requireNonNull(
            allowedTools, "allowedTools must not be null"));
        Set<String> allowedSchemes = "streamable-http".equals(transport)
            ? Set.of("http", "https") : Set.of("stdio");
        if (!allowedSchemes.contains(endpoint.getScheme())
            || endpoint.getRawAuthority() == null || endpoint.getRawAuthority().isBlank()
            || endpoint.getRawUserInfo() != null || endpoint.getRawQuery() != null
            || endpoint.getRawFragment() != null) {
            throw new IllegalArgumentException("MCP endpoint is incompatible with its transport");
        }
        if (allowedTools.isEmpty()
            || allowedTools.stream().anyMatch(tool -> !TOOL.matcher(tool).matches())
            || allowedTools.stream().distinct().count() != allowedTools.size()) {
            throw new IllegalArgumentException("MCP allowed tools are invalid");
        }
    }

    /**
     * 表示 MCP 认证只保留 SecretRef 与解析策略，不携带凭据值。
     *
     * @param secretRef        MCP 认证 Secret 引用
     * @param resolutionPolicy Secret 解析策略
     * @author refinex
     */
    public record Credential(SecretRef secretRef, String resolutionPolicy) {

        /**
         * 校验 MCP 凭据引用及解析策略完整。
         */
        public Credential {
            Objects.requireNonNull(secretRef, "secretRef must not be null");
            if (resolutionPolicy == null || resolutionPolicy.isBlank()) {
                throw new IllegalArgumentException("resolutionPolicy must not be blank");
            }
        }
    }
}
