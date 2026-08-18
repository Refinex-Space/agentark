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

package space.refinex.agentark.control.catalog.application;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 编解码不透明游标，只携带已授权查询中的稳定排序值。
 *
 * @author refinex
 */
final class CatalogCursorCodec {

    /**
     * 禁止实例化游标工具。
     */
    private CatalogCursorCodec() {
    }

    /**
     * @param value 稳定排序值
     * @return URL 安全无填充游标
     */
    static String encode(String value) {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param cursor       可选游标
     * @param defaultValue 未提供时默认值
     * @return 解码值
     */
    static String decode(String cursor, String defaultValue) {
        if (cursor == null || cursor.isBlank()) {
            return defaultValue;
        }

        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (value.isBlank() || value.length() > 128) {
                throw new IllegalArgumentException("cursor value is invalid");
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("cursor is invalid", exception);
        }
    }
}

