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

package space.refinex.agentark.knowledge.application;

import space.refinex.agentark.kernel.id.StrongId;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Function;

/**
 * 编解码只携带已授权查询末标识的 Knowledge 不透明游标。
 *
 * @author refinex
 */
final class KnowledgeCursorCodec {

    /**
     * 禁止实例化游标编解码器。
     */
    private KnowledgeCursorCodec() {
    }

    /**
     * 将上一页末 UUIDv7 编码为 URL 安全游标。
     *
     * @param id 上一页末标识
     * @return URL 安全且无填充的游标
     */
    static String encode(StrongId id) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(id.asString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码并通过具体强类型标识解析器验证游标。
     *
     * @param cursor 可选不透明游标
     * @param parser 强类型标识解析器
     * @param <I>    强类型标识
     * @return 未提供游标时为空，否则为通过 UUIDv7 校验的标识
     */
    static <I extends StrongId> Optional<I> decode(String cursor, Function<String, I> parser) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        try {
            String value = new String(
                Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Optional.of(parser.apply(value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("knowledge cursor is invalid", exception);
        }
    }
}
