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

import space.refinex.agentark.kernel.id.WorkspaceProfileVersionId;

/**
 * 表示 Snapshot 固定的不可变工作区配置版本。
 *
 * @param profileVersionId 工作区配置版本标识
 * @author refinex
 */
public record WorkspaceSpec(WorkspaceProfileVersionId profileVersionId) {

    /**
     * 校验并创建工作区配置绑定。
     *
     * @param profileVersionId 工作区配置版本标识
     * @throws NullPointerException 当版本标识为 {@code null} 时抛出
     */
    public WorkspaceSpec {
        Objects.requireNonNull(profileVersionId, "WorkspaceSpec profileVersionId must not be null");
    }
}
