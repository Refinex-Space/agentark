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

package space.refinex.agentark.kernel.ref;

import java.net.URI;
import java.util.Objects;

/**
 * 表示由外部 Secret Manager 管理的非敏感定位引用，Snapshot 和持久事件只能保存该引用。
 *
 * @param value 形如 {@code secret://<scope>/<name>} 的 URI
 * @author refinex
 */
public record SecretRef(URI value) {

    /**
     * 校验并创建 Secret 引用，拒绝 User Info、Query 和 Fragment。
     *
     * @param value Secret URI
     * @throws NullPointerException     当 URI 为 {@code null} 时抛出
     * @throws IllegalArgumentException 当 URI 不符合 Secret 引用约束时抛出
     */
    public SecretRef {
        Objects.requireNonNull(value, "SecretRef must not be null");
        if (!"secret".equals(value.getScheme())
            || value.getRawAuthority() == null
            || value.getRawAuthority().isBlank()
            || value.getRawPath() == null
            || value.getRawPath().length() < 2
            || value.getRawUserInfo() != null
            || value.getRawQuery() != null
            || value.getRawFragment() != null) {
            throw new IllegalArgumentException(
                "SecretRef must be secret://<scope>/<name> without credentials, query, or fragment");
        }
    }

    /**
     * 从字符串解析 Secret 引用。
     *
     * @param value Secret URI 字符串
     * @return 通过校验的 Secret 引用
     * @throws IllegalArgumentException 当字符串为空、无法解析或不满足引用约束时抛出
     */
    public static SecretRef parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SecretRef must not be blank");
        }
        try {
            return new SecretRef(URI.create(value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("SecretRef is not a valid secret URI", exception);
        }
    }

    /**
     * 返回 ASCII 规范形式的 Secret URI。
     *
     * @return 可进入语言中立契约的引用字符串
     */
    public String asString() {
        return value.toASCIIString();
    }

    /**
     * 返回 ASCII 规范形式的 Secret URI，不解析或暴露 Secret 值。
     *
     * @return Secret 引用字符串
     */
    @Override
    public String toString() {
        return asString();
    }
}
