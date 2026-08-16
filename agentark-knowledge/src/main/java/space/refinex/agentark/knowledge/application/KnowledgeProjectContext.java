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

package space.refinex.agentark.knowledge.application;

import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.util.Objects;
import java.util.UUID;

/**
 * 表示 Control 组合根完成 IAM 授权后交给 Knowledge 的可信项目上下文。
 *
 * @param organizationId 组织标识
 * @param projectId      项目标识
 * @param actorKind      持久主体类型代码
 * @param actorId        持久主体 UUIDv7
 * @author refinex
 */
public record KnowledgeProjectContext(
    OrganizationId organizationId, ProjectId projectId, String actorKind, UUID actorId) {

    /**
     * 校验租户和已解析主体引用。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param actorKind      主体类型
     * @param actorId        主体标识
     */
    public KnowledgeProjectContext {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        if (actorKind == null || !actorKind.matches("[A-Z][A-Z_]{1,31}")) {
            throw new IllegalArgumentException("actorKind must be a stable uppercase code");
        }
        if (actorId == null || actorId.version() != 7 || actorId.variant() != 2) {
            throw new IllegalArgumentException("actorId must be an RFC 9562 UUIDv7");
        }
    }

    /**
     * 返回适合审计字段且不包含凭据的主体引用。
     *
     * @return 主体类型与 UUIDv7 组合
     */
    public String actorReference() {
        return actorKind + ":" + actorId;
    }
}
