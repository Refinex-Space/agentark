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

import java.util.Objects;

import space.refinex.agentark.kernel.id.SkillVersionId;
import space.refinex.agentark.kernel.ref.ObjectRef;

/**
 * 表示不可变 Skill 版本及其带完整性保护的制品引用。
 *
 * @param skillVersionId Skill 版本标识
 * @param artifact       包含 URI、校验和、大小和媒体类型的制品引用
 * @author refinex
 */
public record SkillSpec(SkillVersionId skillVersionId, ObjectRef artifact) {

    /**
     * 校验并创建 Skill 绑定。
     *
     * @param skillVersionId Skill 版本标识
     * @param artifact       完整性保护的制品引用
     * @throws NullPointerException 当任一字段为 {@code null} 时抛出
     */
    public SkillSpec {
        Objects.requireNonNull(skillVersionId, "SkillSpec skillVersionId must not be null");
        Objects.requireNonNull(artifact, "SkillSpec artifact must not be null");
    }
}
