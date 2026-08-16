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

package space.refinex.agentark.runtime.provider.agentscope.secret;

import java.util.Arrays;
import java.util.Objects;

/**
 * 以可主动清零的字符数组承载短生命周期 Secret，禁止通过日志输出内容。
 *
 * @author refinex
 */
public final class ResolvedSecret implements AutoCloseable {

    /**
     * 只在当前 RuntimeHandle 生命周期内存在的 Secret 字符。
     */
    private final char[] value;

    /**
     * @param value Secret 字符，将被立即防御性复制
     */
    public ResolvedSecret(char[] value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.length == 0) {
            throw new IllegalArgumentException("resolved secret must not be empty");
        }
        this.value = value.clone();
    }

    /**
     * 为创建外部 SDK 对象返回可由调用方清零的副本。
     *
     * @return Secret 字符副本
     */
    public char[] copyValue() {
        return value.clone();
    }

    /**
     * 主动覆盖当前内存中的 Secret 字符。
     */
    @Override
    public void close() {
        Arrays.fill(value, '\0');
    }

    /**
     * 始终返回脱敏描述，避免意外日志泄漏。
     *
     * @return 固定脱敏文本
     */
    @Override
    public String toString() {
        return "ResolvedSecret[redacted]";
    }
}
