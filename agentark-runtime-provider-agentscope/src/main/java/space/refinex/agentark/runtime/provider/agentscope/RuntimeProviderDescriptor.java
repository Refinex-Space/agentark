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

package space.refinex.agentark.runtime.provider.agentscope;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 声明 AgentScope Runtime Provider 版本、Snapshot Schema 与可对 Control 暴露的能力。
 *
 * @param providerId       Runtime Provider 稳定标识
 * @param providerVersion  AgentScope Java 依赖版本
 * @param compilerVersion  Snapshot Compiler 语义版本
 * @param supportedSchemas 受支持的 Snapshot Schema 版本
 * @param capabilities     供发布校验使用的稳定能力集合
 * @author refinex
 */
public record RuntimeProviderDescriptor(
    String providerId,
    String providerVersion,
    String compilerVersion,
    Set<Integer> supportedSchemas,
    Set<String> capabilities) {

    /**
     * AgentScope Java 2 Provider 的稳定标识。
     */
    public static final String PROVIDER_ID = "agentscope-java-2";

    /**
     * 与固定上游 Commit 对应的 AgentScope Java 版本。
     */
    public static final String PROVIDER_VERSION = "2.0.2";

    /**
     * 当前 Snapshot Compiler 语义版本。
     */
    public static final String COMPILER_VERSION = "1.0.0";

    /**
     * 校验 Descriptor 字段并创建不可变集合。
     */
    public RuntimeProviderDescriptor {
        requireText(providerId, "providerId");
        requireText(providerVersion, "providerVersion");
        requireText(compilerVersion, "compilerVersion");
        supportedSchemas = Set.copyOf(Objects.requireNonNull(
            supportedSchemas, "supportedSchemas must not be null"));
        capabilities = Set.copyOf(Objects.requireNonNull(
            capabilities, "capabilities must not be null"));
        if (supportedSchemas.isEmpty() || capabilities.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("provider descriptor collections are invalid");
        }
    }

    /**
     * 创建与当前已锁定 AgentScope 版本对应的 Descriptor。
     *
     * @return 默认 Provider Descriptor
     */
    public static RuntimeProviderDescriptor current() {
        return new RuntimeProviderDescriptor(
            PROVIDER_ID,
            PROVIDER_VERSION,
            COMPILER_VERSION,
            Set.of(1),
            Set.of(
                "streaming", "tool-calling", "structured-output", "mcp", "skill",
                "knowledge", "memory", "workspace", "sandbox", "state", "permission",
                "sub-agent"));
    }

    /**
     * 转换为 Internal Contract 可直接发布的低基数能力字段。
     *
     * @return 不含敏感信息的 Descriptor 字段
     */
    public Map<String, String> asContractFields() {
        return Map.of(
            "runtimeProvider", providerId,
            "providerVersion", providerVersion,
            "compilerVersion", compilerVersion,
            "schemaVersions", supportedSchemas.stream().sorted()
                .map(String::valueOf).reduce((left, right) -> left + "," + right).orElse(""),
            "capabilities", capabilities.stream().sorted()
                .reduce((left, right) -> left + "," + right).orElse(""));
    }

    /**
     * 校验 Descriptor 文本字段。
     *
     * @param value 待校验值
     * @param name  字段名
     */
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
