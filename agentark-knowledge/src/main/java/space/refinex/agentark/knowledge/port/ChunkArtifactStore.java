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
import space.refinex.agentark.kernel.ref.ObjectRef;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 定义规范化 Chunk 制品的不可变 Object Store 写入和删除 Port。
 *
 * @author refinex
 */
public interface ChunkArtifactStore {

    /**
     * 写入确定性 Chunk 制品。
     *
     * @param revisionId 固定 Knowledge Revision 标识
     * @param chunks     有序 Chunk 列表
     * @return 不含授权参数的对象引用
     */
    CompletionStage<ObjectRef> put(
        KnowledgeRevisionId revisionId, List<KnowledgeChunk> chunks);

    /**
     * 删除当前 Store 拥有的派生制品。
     *
     * @param ref 制品引用
     * @return 异步完成信号
     */
    CompletionStage<Void> delete(ObjectRef ref);
}
