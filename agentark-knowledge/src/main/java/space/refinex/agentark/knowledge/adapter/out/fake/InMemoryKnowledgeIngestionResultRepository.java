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

package space.refinex.agentark.knowledge.adapter.out.fake;

import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionResult;
import space.refinex.agentark.knowledge.application.port.KnowledgeIngestionResultRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提供摄取结果幂等与 Outbox 计数的内存实现，供事务应用服务测试。
 *
 * @author refinex
 */
public final class InMemoryKnowledgeIngestionResultRepository
    implements KnowledgeIngestionResultRepository {

    /**
     * 结果内存表。
     */
    private final Map<UUID, IngestionResult> results = new ConcurrentHashMap<>();

    /**
     * Outbox 内存表。
     */
    private final Map<UUID, String> outbox = new ConcurrentHashMap<>();

    /**
     * 幂等插入或返回项目内相同 Key 的结果。
     */
    @Override
    public synchronized InsertOutcome insertOrFind(IngestionResult result, String actor) {
        Optional<IngestionResult> existing = findByIdempotencyKey(
            result.projectId(), result.idempotencyKey());
        if (existing.isPresent()) {
            return new InsertOutcome(existing.orElseThrow(), false);
        }
        results.put(result.resultId(), result);
        return new InsertOutcome(result, true);
    }

    /**
     * 按项目和幂等键查询。
     */
    @Override
    public Optional<IngestionResult> findByIdempotencyKey(
        ProjectId projectId, String idempotencyKey) {
        return results.values().stream()
            .filter(value -> value.projectId().equals(projectId))
            .filter(value -> value.idempotencyKey().equals(idempotencyKey))
            .findFirst();
    }

    /**
     * 按 Revision 和 Attempt 查询。
     */
    @Override
    public Optional<IngestionResult> findByAttempt(
        KnowledgeRevisionId revisionId, UUID attemptId) {
        return results.values().stream()
            .filter(value -> value.revisionId().equals(revisionId))
            .filter(value -> value.attemptId().equals(attemptId))
            .findFirst();
    }

    /**
     * 记录不含敏感内容的 Outbox JSON。
     */
    @Override
    public void insertOutbox(
        UUID eventId,
        KnowledgeRevisionId revisionId,
        String eventType,
        String payloadJson,
        Instant createdAt) {
        if (outbox.putIfAbsent(eventId, payloadJson) != null) {
            throw new IllegalStateException("knowledge outbox event already exists");
        }
    }

    /**
     * 返回已记录 Outbox 数量。
     *
     * @return Outbox 数量
     */
    public int outboxCount() {
        return outbox.size();
    }
}
