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

package space.refinex.agentark.runtime.provider.agentscope.skill;

import space.refinex.agentark.kernel.ref.ObjectRef;

import java.util.Objects;

/**
 * 表示需在运行前按 URI、Hash、Size 和媒体类型校验的 Skill 制品。
 *
 * @param versionId Skill 版本标识
 * @param artifact  带完整性信息的制品引用
 * @author refinex
 */
public record SkillBinding(String versionId, ObjectRef artifact) {

    /**
     * 校验 Skill 版本和制品引用完整。
     */
    public SkillBinding {
        if (versionId == null || versionId.isBlank()) {
            throw new IllegalArgumentException("skill versionId must not be blank");
        }
        Objects.requireNonNull(artifact, "artifact must not be null");
    }
}
