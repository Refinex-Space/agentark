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

package space.refinex.agentark.foundation.redis;

import java.time.Instant;

/**
 * 表示带单调栅栏令牌的短期分布式租约，业务写入必须同时校验 fencingToken。
 *
 * @param namespace    租约业务命名空间
 * @param resourceKey  业务资源键
 * @param ownerId      当前持有者稳定实例标识
 * @param fencingToken 单调递增栅栏令牌
 * @param expiresAt    租约预计失效的 UTC 时刻
 * @author refinex
 */
public record Lease(
    String namespace, String resourceKey, String ownerId, long fencingToken, Instant expiresAt) {

    /**
     * 校验并创建租约值对象。
     *
     * @param namespace    业务命名空间
     * @param resourceKey  业务资源键
     * @param ownerId      持有者标识
     * @param fencingToken 正数栅栏令牌
     * @param expiresAt    失效时刻
     * @throws IllegalArgumentException 当文本为空或栅栏令牌非正数时抛出
     * @throws NullPointerException     当失效时刻为 {@code null} 时抛出
     */
    public Lease {
        requireText(namespace, "namespace");
        requireText(resourceKey, "resourceKey");
        requireText(ownerId, "ownerId");
        if (fencingToken < 1) {
            throw new IllegalArgumentException("fencingToken must be positive");
        }
        java.util.Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    /**
     * 校验租约文本字段。
     *
     * @param value 待校验值
     * @param name  字段名称
     * @throws IllegalArgumentException 当值为空或过长时抛出
     */
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 512) {
            throw new IllegalArgumentException(name + " must contain 1 to 512 characters");
        }
    }
}
