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

package space.refinex.agentark.kernel.id;

import java.util.UUID;

/**
 * 表示 Agent 资源的强类型 UUIDv7 标识，防止不同领域标识在编译期被误用。
 *
 * @param value RFC 9562 UUIDv7 原始值
 * @author refinex
 */
public record AgentId(UUID value) implements StrongId {

    /**
     * 校验并创建 Agent 资源标识。
     *
     * @param value RFC 9562 UUIDv7 原始值
     * @throws NullPointerException     当原始值为 {@code null} 时抛出
     * @throws IllegalArgumentException 当原始值不是 UUIDv7 或 Variant 不合法时抛出
     */
    public AgentId {
        value = UuidV7.require(value, "AgentId");
    }

    /**
     * 使用当前 UTC 毫秒时间和安全随机数生成 Agent 资源标识。
     *
     * @return 新生成的 Agent 资源标识
     */
    public static AgentId generate() {
        return new AgentId(UuidV7.generate());
    }

    /**
     * 解析小写规范形式的 UUIDv7 字符串。
     *
     * @param value 待解析的 UUIDv7 字符串
     * @return 解析后的 Agent 资源标识
     * @throws IllegalArgumentException 当字符串为空、不是规范小写形式或不是 UUIDv7 时抛出
     */
    public static AgentId parse(String value) {
        return new AgentId(UuidV7.parse(value, "AgentId"));
    }
}
