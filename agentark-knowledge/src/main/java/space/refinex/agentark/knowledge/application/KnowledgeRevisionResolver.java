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

import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.knowledge.application.port.KnowledgeRepository;
import space.refinex.agentark.knowledge.domain.KnowledgeRevision;

import java.util.Objects;

/**
 * 为后续 Agent Revision Resolver 提供唯一的 READY Knowledge Revision 解析入口。
 *
 * @author refinex
 */
public final class KnowledgeRevisionResolver {

    /**
     * Knowledge 元数据仓储。
     */
    private final KnowledgeRepository repository;

    /**
     * 创建 READY Revision Resolver。
     *
     * @param repository Knowledge 元数据仓储
     */
    public KnowledgeRevisionResolver(KnowledgeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 解析同项目且 READY 的 Knowledge Revision。
     *
     * @param projectId  项目标识
     * @param revisionId Knowledge Revision 标识
     * @return 允许 Snapshot 引用的不可变 Revision
     * @throws KnowledgeNotFoundException 当资源不存在或跨项目不可见时抛出
     * @throws KnowledgeConflictException 当 Revision 尚未 READY 时抛出
     */
    public KnowledgeRevision resolveReady(ProjectId projectId, KnowledgeRevisionId revisionId) {
        KnowledgeRevision revision = repository.findKnowledgeRevision(projectId, revisionId)
            .orElseThrow(() -> new KnowledgeNotFoundException("knowledge revision is not visible"));
        if (!revision.isReferenceable()) {
            throw new KnowledgeConflictException("knowledge revision is not READY");
        }
        return revision;
    }
}
