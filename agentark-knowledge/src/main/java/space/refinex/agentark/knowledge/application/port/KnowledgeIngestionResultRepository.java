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

package space.refinex.agentark.knowledge.application.port;

import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionResult;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 定义 Control Schema 内摄取结果和同事务 Outbox 的持久化边界。
 *
 * @author refinex
 */
public interface KnowledgeIngestionResultRepository {

    /**
     * 表示幂等插入结果以及本次调用是否真正创建了新记录。
     *
     * @param result   已持久化或已存在的摄取结果
     * @param inserted 本次调用是否插入了新记录
     * @author refinex
     */
    record InsertOutcome(IngestionResult result, boolean inserted) {
    }

    /**
     * 幂等插入摄取结果；唯一键冲突时返回已存在结果。
     *
     * @param result 摄取结果
     * @param actor  内部服务主体
     * @return 携带插入判定的新结果或已存在结果
     */
    InsertOutcome insertOrFind(IngestionResult result, String actor);

    /**
     * 按项目和 Internal Command 幂等键查询结果。
     *
     * @param projectId      项目标识
     * @param idempotencyKey 幂等键
     * @return 已存在结果
     */
    Optional<IngestionResult> findByIdempotencyKey(
        ProjectId projectId, String idempotencyKey);

    /**
     * 按固定 Revision 与 Attempt 查询结果。
     *
     * @param revisionId Knowledge Revision 标识
     * @param attemptId  Attempt UUIDv7
     * @return 已存在结果
     */
    Optional<IngestionResult> findByAttempt(
        KnowledgeRevisionId revisionId, UUID attemptId);

    /**
     * 在同一 Control 本地事务写入 Knowledge Revision Outbox。
     *
     * @param eventId    Outbox Event UUIDv7
     * @param revisionId Knowledge Revision 标识
     * @param eventType  稳定事件类型
     * @param payloadJson 不含原文与 Secret 的 JSON
     * @param createdAt  创建时间
     */
    void insertOutbox(
        UUID eventId,
        KnowledgeRevisionId revisionId,
        String eventType,
        String payloadJson,
        Instant createdAt);
}
