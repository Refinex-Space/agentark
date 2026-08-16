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

package space.refinex.agentark.knowledge.port;

import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 定义向量索引写入和派生数据删除 Port；Collection 名不进入领域授权模型。
 *
 * @author refinex
 */
public interface VectorIndex {

    /**
     * 在已经完成项目授权后写入指定 Knowledge Revision 的向量。
     *
     * @param projectId  显式项目边界
     * @param revisionId Knowledge Revision 标识
     * @param chunks     带向量的 Chunk
     * @return 异步完成信号
     */
    CompletionStage<Void> upsert(
        ProjectId projectId, KnowledgeRevisionId revisionId, List<EmbeddedChunk> chunks);

    /**
     * 清理指定 Knowledge Revision 的全部派生向量数据。
     *
     * @param projectId  显式项目边界
     * @param revisionId Knowledge Revision 标识
     * @return 异步完成信号
     */
    CompletionStage<Void> delete(ProjectId projectId, KnowledgeRevisionId revisionId);
}
