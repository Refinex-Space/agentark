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

import java.util.Optional;

/**
 * 表示不暴露数据库偏移量的 Cursor 分页请求。
 *
 * @param cursor 可选的不透明游标
 * @param limit  正整数单页上限
 * @author refinex
 */
public record CursorPageRequest(Optional<String> cursor, int limit) {

    /**
     * 校验并创建 Cursor 分页请求。
     *
     * @param cursor 可选游标，最大 2048 字符
     * @param limit  由调用方结合配置上限进一步约束的正整数
     * @throws IllegalArgumentException 当游标过长或 limit 非正数时抛出
     * @throws NullPointerException     当游标容器为 {@code null} 时抛出
     */
    public CursorPageRequest {
        cursor = java.util.Objects.requireNonNull(cursor, "cursor must not be null");
        if (cursor.filter(value -> value.length() > 2048).isPresent()) {
            throw new IllegalArgumentException("cursor must not exceed 2048 characters");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    /**
     * 按 Starter 配置校验单页上限。
     *
     * @param maxPageSize 配置的最大单页条目数
     * @return 当前请求，便于在入口链式使用
     * @throws IllegalArgumentException 当当前 limit 超过配置上限时抛出
     */
    public CursorPageRequest requireWithin(int maxPageSize) {
        if (limit > maxPageSize) {
            throw new IllegalArgumentException("limit exceeds configured maximum");
        }
        return this;
    }
}
