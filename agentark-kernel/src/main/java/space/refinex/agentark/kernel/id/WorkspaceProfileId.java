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
 * 表示 Workspace Profile 的强类型 UUIDv7 标识，防止不同领域标识在编译期被误用。
 *
 * @param value RFC 9562 UUIDv7 原始值
 * @author refinex
 */
public record WorkspaceProfileId(UUID value) implements StrongId {

    /**
     * 校验并创建 Workspace Profile 标识。
     *
     * @param value RFC 9562 UUIDv7 原始值
     * @throws NullPointerException     当原始值为 {@code null} 时抛出
     * @throws IllegalArgumentException 当原始值不是 UUIDv7 或 Variant 不合法时抛出
     */
    public WorkspaceProfileId {
        value = UuidV7.require(value, "WorkspaceProfileId");
    }

    /**
     * 使用当前 UTC 毫秒时间和安全随机数生成 Workspace Profile 标识。
     *
     * @return 新生成的 Workspace Profile 标识
     */
    public static WorkspaceProfileId generate() {
        return new WorkspaceProfileId(UuidV7.generate());
    }

    /**
     * 解析小写规范形式的 UUIDv7 字符串。
     *
     * @param value 待解析的 UUIDv7 字符串
     * @return 解析后的 Workspace Profile 标识
     * @throws IllegalArgumentException 当字符串为空、不是规范小写形式或不是 UUIDv7 时抛出
     */
    public static WorkspaceProfileId parse(String value) {
        return new WorkspaceProfileId(UuidV7.parse(value, "WorkspaceProfileId"));
    }
}

