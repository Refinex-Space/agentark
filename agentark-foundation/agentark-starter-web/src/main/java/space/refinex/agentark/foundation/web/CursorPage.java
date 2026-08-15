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

package space.refinex.agentark.foundation.web;

import java.util.List;
import java.util.Optional;

/**
 * 表示包含不可变条目和可选下一页游标的公共分页结果，不提供统一响应套壳。
 *
 * @param items      当前页不可变条目
 * @param nextCursor 有下一页时返回的不透明游标
 * @param hasMore    是否仍存在下一页
 * @param <T>        条目类型
 * @author refinex
 */
public record CursorPage<T>(List<T> items, Optional<String> nextCursor, boolean hasMore) {

    /**
     * 防御性复制条目并校验游标与 hasMore 的一致性。
     *
     * @param items      当前页条目
     * @param nextCursor 可选下一页游标
     * @param hasMore    是否存在下一页
     * @throws IllegalArgumentException 当 hasMore 与游标存在性不一致时抛出
     * @throws NullPointerException     当列表或游标容器为 {@code null} 时抛出
     */
    public CursorPage {
        items = List.copyOf(java.util.Objects.requireNonNull(items, "items must not be null"));
        nextCursor = java.util.Objects.requireNonNull(nextCursor, "nextCursor must not be null");
        if (hasMore != nextCursor.isPresent()) {
            throw new IllegalArgumentException("hasMore must match nextCursor presence");
        }
    }
}
