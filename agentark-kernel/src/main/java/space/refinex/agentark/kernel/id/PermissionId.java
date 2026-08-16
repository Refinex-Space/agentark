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
 * 表示权限注册项的强类型 UUIDv7 标识。
 *
 * @param value RFC 9562 UUIDv7 原始值
 * @author refinex
 */
public record PermissionId(UUID value) implements StrongId {

    /**
     * 校验并创建权限标识。
     *
     * @param value RFC 9562 UUIDv7 原始值
     */
    public PermissionId {
        value = UuidV7.require(value, "PermissionId");
    }

    /**
     * 生成新的权限标识。
     *
     * @return 新生成的标识
     */
    public static PermissionId generate() {
        return new PermissionId(UuidV7.generate());
    }

    /**
     * 解析规范 UUIDv7 字符串。
     *
     * @param value 待解析字符串
     * @return 解析后的标识
     */
    public static PermissionId parse(String value) {
        return new PermissionId(UuidV7.parse(value, "PermissionId"));
    }
}
