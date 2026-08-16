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

package space.refinex.agentark.kernel.snapshot;

import space.refinex.agentark.kernel.id.MemoryProfileVersionId;

import java.util.Map;
import java.util.Objects;

/**
 * 表示 Snapshot 固定的不可变记忆配置版本。
 *
 * @param profileVersionId 记忆配置版本标识
 * @param configuration    发布时冻结的供应商中立配置
 * @author refinex
 */
public record MemorySpec(
    MemoryProfileVersionId profileVersionId, Map<String, Object> configuration) {

    /**
     * 校验并创建记忆配置绑定。
     *
     * @param profileVersionId 记忆配置版本标识
     * @param configuration    不含 Secret 明文的配置对象
     * @throws NullPointerException 当版本标识或配置为 {@code null} 时抛出
     */
    public MemorySpec {
        Objects.requireNonNull(profileVersionId, "MemorySpec profileVersionId must not be null");
        configuration = SnapshotRequirements.immutableJsonObject(
            configuration, "MemorySpec configuration");
    }
}
