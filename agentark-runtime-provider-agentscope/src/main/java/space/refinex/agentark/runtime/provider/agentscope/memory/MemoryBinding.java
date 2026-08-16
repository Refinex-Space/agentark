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

package space.refinex.agentark.runtime.provider.agentscope.memory;

import java.util.Map;
import java.util.Objects;

/**
 * 表示已在 Snapshot 中冻结的 Memory Profile。
 *
 * @param versionId     Memory Profile 版本标识
 * @param configuration 不含 Secret 的完整运行配置
 * @author refinex
 */
public record MemoryBinding(String versionId, Map<String, Object> configuration) {

    /**
     * 校验 Memory Profile 版本和配置完整。
     */
    public MemoryBinding {
        if (versionId == null || versionId.isBlank()) {
            throw new IllegalArgumentException("memory versionId must not be blank");
        }
        configuration = Map.copyOf(Objects.requireNonNull(
            configuration, "configuration must not be null"));
    }
}
