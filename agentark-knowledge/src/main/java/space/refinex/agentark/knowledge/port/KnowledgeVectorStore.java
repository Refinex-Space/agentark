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

import space.refinex.agentark.knowledge.port.VectorStoreModels.*;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 定义版本隔离、租户过滤、校验和删除完整的向量后端 Port。
 *
 * @author refinex
 */
public interface KnowledgeVectorStore {

    /**
     * 幂等写入一个固定 Revision 的向量 Point。
     *
     * @param request 带可信租户和完整清单摘要的写入请求
     * @return 异步完成信号
     */
    CompletionStage<Void> upsert(VectorWriteRequest request);

    /**
     * 校验固定 Revision 下匹配摘要的 Point 数量。
     *
     * @param request 验证请求
     * @return 数量和摘要均匹配时为 {@code true}
     */
    CompletionStage<Boolean> verify(VectorVerificationRequest request);

    /**
     * 使用 Adapter 强制构造的租户、Revision 与 ACL Filter 执行向量检索。
     *
     * @param request 检索请求
     * @return 按相似度排序的命中
     */
    CompletionStage<List<VectorSearchHit>> search(VectorSearchRequest request);

    /**
     * 删除固定租户和 Revision 的全部派生向量。
     *
     * @param scope 可信租户范围
     * @return 异步完成信号
     */
    CompletionStage<Void> delete(VectorScope scope);
}
