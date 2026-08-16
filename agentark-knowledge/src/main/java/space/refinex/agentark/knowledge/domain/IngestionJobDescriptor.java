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

package space.refinex.agentark.knowledge.domain;

import space.refinex.agentark.kernel.id.IngestionRequestId;
import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.time.Instant;
import java.util.Objects;

/**
 * 描述一次异步摄取意图；Phase 09 只持久化描述，不执行解析、Embedding 或向量写入。
 *
 * @param id                  摄取请求标识
 * @param organizationId      组织标识
 * @param projectId           项目标识
 * @param knowledgeRevisionId Knowledge Revision 标识
 * @param idempotencyKey      项目内幂等键
 * @param status              描述状态
 * @param requestedAt         请求时间
 * @author refinex
 */
public record IngestionJobDescriptor(
    IngestionRequestId id,
    OrganizationId organizationId,
    ProjectId projectId,
    KnowledgeRevisionId knowledgeRevisionId,
    String idempotencyKey,
    IngestionRequestStatus status,
    Instant requestedAt) {

    /**
     * 校验租户、幂等键与请求时间。
     *
     * @param id                  摄取请求标识
     * @param organizationId      组织标识
     * @param projectId           项目标识
     * @param knowledgeRevisionId Knowledge Revision 标识
     * @param idempotencyKey      幂等键
     * @param status              请求状态
     * @param requestedAt         请求时间
     */
    public IngestionJobDescriptor {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(knowledgeRevisionId, "knowledgeRevisionId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        if (idempotencyKey == null
            || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{7,127}")) {
            throw new IllegalArgumentException("idempotencyKey must contain 8 to 128 safe characters");
        }
    }
}
