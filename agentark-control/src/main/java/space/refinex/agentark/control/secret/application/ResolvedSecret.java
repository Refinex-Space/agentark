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

package space.refinex.agentark.control.secret.application;

import java.util.Arrays;

/**
 * 持有可清零 Secret 字符数组；禁止字符串化并要求使用后关闭。
 *
 * @author refinex
 */
public final class ResolvedSecret implements AutoCloseable {

    /**
     * 可在关闭时覆盖的 Secret 字符数组。
     */
    private final char[] value;

    /**
     * 是否已关闭。
     */
    private boolean closed;

    /**
     * @param value Provider 读取的 Secret 字符数组
     */
    public ResolvedSecret(char[] value) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("resolved secret must not be empty");
        }
        this.value = value.clone();
    }

    /**
     * @return 使用方持有的防御性副本
     * @throws IllegalStateException 已关闭时抛出
     */
    public synchronized char[] copy() {
        if (closed) {
            throw new IllegalStateException("resolved secret is already closed");
        }
        return value.clone();
    }

    /**
     * 清零内部字符数组并标记关闭。
     */
    @Override
    public synchronized void close() {
        Arrays.fill(value, '\0');
        closed = true;
    }

    /**
     * @return 固定脱敏文本，绝不返回 Secret 值
     */
    @Override
    public String toString() {
        return "[REDACTED]";
    }
}

