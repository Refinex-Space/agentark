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

package space.refinex.agentark.control.iam.application;

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.util.Objects;
import java.util.Optional;

/**
 * 表示成员关系、角色、绑定或 API Key 变化后的缓存失效事件。
 *
 * @param organizationId 必需组织标识
 * @param projectId      项目级变化的可选项目
 * @author refinex
 */
public record IamAuthorizationChanged(OrganizationId organizationId, Optional<ProjectId> projectId) {

    /**
     * 校验事件 Scope。
     *
     * @param organizationId 组织标识
     * @param projectId      可选项目
     */
    public IamAuthorizationChanged {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        projectId = Objects.requireNonNull(projectId, "projectId must not be null");
    }
}
