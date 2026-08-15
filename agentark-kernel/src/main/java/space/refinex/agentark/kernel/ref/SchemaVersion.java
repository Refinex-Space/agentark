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

/**
 * 表示语言中立契约 Schema 的正整数版本。
 *
 * @param value 从 1 开始递增的 Schema 版本
 * @author refinex
 */
public record SchemaVersion(int value) {

    /**
     * 校验并创建 Schema 版本。
     *
     * @param value Schema 版本
     * @throws IllegalArgumentException 当版本小于 1 时抛出
     */
    public SchemaVersion {
        if (value < 1) {
            throw new IllegalArgumentException("SchemaVersion must be positive");
        }
    }

    /**
     * 创建首个 Schema 版本。
     *
     * @return 值为 1 的 Schema 版本
     */
    public static SchemaVersion initial() {
        return new SchemaVersion(1);
    }
}
