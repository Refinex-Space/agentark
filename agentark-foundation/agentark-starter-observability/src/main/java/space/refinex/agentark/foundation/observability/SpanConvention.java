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

package space.refinex.agentark.foundation.observability;

/**
 * 定义 Agent、Model、Tool、RAG 与 Sandbox 操作的稳定 OpenTelemetry Span 名称前缀。
 *
 * @author refinex
 */
public enum SpanConvention {

    /**
     * Agent 调度或执行操作，Span 前缀为 {@code agentark.agent}。
     */
    AGENT("agentark.agent"),

    /**
     * 模型推理操作，Span 前缀为 {@code agentark.model}。
     */
    MODEL("agentark.model"),

    /**
     * Tool 或 MCP 调用操作，Span 前缀为 {@code agentark.tool}。
     */
    TOOL("agentark.tool"),

    /**
     * RAG 检索操作，Span 前缀为 {@code agentark.rag}。
     */
    RAG("agentark.rag"),

    /**
     * Sandbox 受限执行操作，Span 前缀为 {@code agentark.sandbox}。
     */
    SANDBOX("agentark.sandbox");

    /**
     * 稳定 Span 名称前缀。
     */
    private final String prefix;

    /**
     * 创建 Span 约定枚举值。
     *
     * @param prefix 稳定名称前缀
     */
    SpanConvention(String prefix) {
        this.prefix = prefix;
    }

    /**
     * 使用受限操作名构造完整 Span 名称。
     *
     * @param operation 小写点分操作名
     * @return 稳定 Span 名称
     * @throws IllegalArgumentException 当操作名格式不合法时抛出
     */
    public String spanName(String operation) {
        if (operation == null || !operation.matches("[a-z][a-z0-9.]{0,62}")) {
            throw new IllegalArgumentException("operation must be a stable lowercase name");
        }
        return prefix + "." + operation;
    }
}
