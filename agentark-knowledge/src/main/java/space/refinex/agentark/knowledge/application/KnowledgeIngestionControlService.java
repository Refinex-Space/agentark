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

import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;
import space.refinex.agentark.kernel.id.DocumentRevisionId;
import space.refinex.agentark.kernel.id.IngestionRequestId;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionPlan;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionResult;
import space.refinex.agentark.knowledge.application.IngestionModels.ResultStatus;
import space.refinex.agentark.knowledge.application.port.KnowledgeIngestionResultRepository;
import space.refinex.agentark.knowledge.application.port.KnowledgeRepository;
import space.refinex.agentark.knowledge.domain.DocumentRevision;
import space.refinex.agentark.knowledge.domain.IngestionJobDescriptor;
import space.refinex.agentark.knowledge.domain.IngestionRequestStatus;
import space.refinex.agentark.knowledge.domain.KnowledgeRevision;
import space.refinex.agentark.knowledge.domain.KnowledgeRevisionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 为 Scheduler Worker 提供受服务身份保护的摄取计划和幂等结果提交事务。
 *
 * <p>成功结果在同一 Control 事务内写入结果、INGESTING→VERIFYING→READY 状态和 Outbox；失败结果
 * 写入 FAILED 与 Outbox。Worker 因而无需且不得持有 Control Schema 凭据。
 *
 * @author refinex
 */
public class KnowledgeIngestionControlService {

    /**
     * 控制面内部 API 受众。
     */
    public static final String CONTROL_AUDIENCE = "agentark-control";

    /**
     * Knowledge 元数据仓储。
     */
    private final KnowledgeRepository repository;

    /**
     * 摄取结果与 Outbox 仓储。
     */
    private final KnowledgeIngestionResultRepository resultRepository;

    /**
     * 事务发件箱 JSON 编解码器。
     */
    private final JsonMapper jsonMapper;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * 创建 Knowledge 摄取 Control 服务。
     *
     * @param repository       Knowledge 元数据仓储
     * @param resultRepository 摄取结果仓储
     * @param jsonMapper       JSON Mapper
     * @param clock            UTC 时钟
     */
    public KnowledgeIngestionControlService(
        KnowledgeRepository repository,
        KnowledgeIngestionResultRepository resultRepository,
        JsonMapper jsonMapper,
        Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.resultRepository = Objects.requireNonNull(
            resultRepository, "resultRepository must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 为已认证 Scheduler 服务加载固定 Revision、Document 与 Profile 计划。
     *
     * @param principal 已认证内部服务主体
     * @param requestId Control 摄取请求标识
     * @return 不含凭据值的固定摄取计划
     */
    @Transactional(readOnly = true)
    public IngestionPlan loadPlan(
        AgentArkPrincipal principal, IngestionRequestId requestId) {
        requireService(principal);
        IngestionJobDescriptor request = repository.findIngestionRequestInternal(requestId)
            .filter(value -> value.status() == IngestionRequestStatus.DESCRIBED)
            .orElseThrow(() -> new KnowledgeNotFoundException(
                "ingestion request is not available"));
        KnowledgeRevision revision = repository.findKnowledgeRevision(
                request.projectId(), request.knowledgeRevisionId())
            .orElseThrow(() -> new KnowledgeNotFoundException(
                "knowledge revision is not available"));
        List<DocumentRevision> documents = revision.documentRevisionIds().stream()
            .map(id -> document(request, id))
            .toList();
        return new IngestionPlan(
            request.id(), request.organizationId(), request.projectId(), revision, documents,
            repository.findParserProfile(request.projectId(), revision.parserProfileId())
                .orElseThrow(() -> new KnowledgeConflictException("parser profile is missing")),
            repository.findChunkProfile(request.projectId(), revision.chunkProfileId())
                .orElseThrow(() -> new KnowledgeConflictException("chunk profile is missing")),
            repository.findEmbeddingProfile(request.projectId(), revision.embeddingProfileId())
                .orElseThrow(() -> new KnowledgeConflictException("embedding profile is missing")),
            repository.findRetrievalProfile(request.projectId(), revision.retrievalProfileId())
                .orElseThrow(() -> new KnowledgeConflictException("retrieval profile is missing")));
    }

    /**
     * 幂等接收 Worker 结果，并在本地事务内完成状态转换与 Outbox 写入。
     *
     * @param principal 已认证内部服务主体
     * @param result    当前 Attempt 结果
     * @return 持久结果
     */
    @Transactional
    public IngestionResult acceptResult(
        AgentArkPrincipal principal, IngestionResult result) {
        requireService(principal);
        Objects.requireNonNull(result, "result must not be null");
        resultRepository.findByIdempotencyKey(result.projectId(), result.idempotencyKey())
            .ifPresent(existing -> requireSameResult(existing, result));
        IngestionResult existing = resultRepository.findByIdempotencyKey(
            result.projectId(), result.idempotencyKey()).orElse(null);
        if (existing != null) {
            return existing;
        }

        IngestionJobDescriptor request = repository.findIngestionRequest(
                result.projectId(), result.requestId())
            .filter(value -> value.status() == IngestionRequestStatus.DESCRIBED)
            .orElseThrow(() -> new KnowledgeNotFoundException(
                "ingestion request is not available"));
        requireMatchingResult(request, result);
        KnowledgeRevision revision = repository.findKnowledgeRevision(
                result.projectId(), result.revisionId())
            .orElseThrow(() -> new KnowledgeNotFoundException(
                "knowledge revision is not available"));
        validateResult(revision, result);

        String actor = principal.issuer() + ':' + principal.subject();
        KnowledgeIngestionResultRepository.InsertOutcome outcome =
            resultRepository.insertOrFind(result, actor);
        IngestionResult stored = outcome.result();
        requireSameResult(stored, result);
        if (!outcome.inserted()) {
            return stored;
        }

        Instant now = Instant.now(clock);
        KnowledgeRevision terminal = result.status() == ResultStatus.SUCCEEDED
            ? markReady(revision, now, actor)
            : markFailed(revision, result.failureCode(), now, actor);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resultId", result.resultId().toString());
        payload.put("requestId", result.requestId().asString());
        payload.put("knowledgeRevisionId", result.revisionId().asString());
        payload.put("attemptId", result.attemptId().toString());
        payload.put("status", result.status().name());
        payload.put("documentCount", result.documentCount());
        payload.put("chunkCount", result.chunkCount());
        payload.put("checksum", result.checksum().value());
        payload.put("revisionStatus", terminal.status().name());
        resultRepository.insertOutbox(
            JobId.generate().value(), revision.id(),
            result.status() == ResultStatus.SUCCEEDED
                ? "knowledge.revision.ready" : "knowledge.revision.failed",
            jsonMapper.writeValueAsString(payload), now);
        return stored;
    }

    /**
     * 加载并验证单个固定文档修订。
     *
     * @param request 摄取请求
     * @param id      文档修订标识
     * @return 文档修订
     */
    private DocumentRevision document(
        IngestionJobDescriptor request, DocumentRevisionId id) {
        return repository.findDocumentRevision(request.projectId(), id)
            .filter(value -> value.organizationId().equals(request.organizationId()))
            .orElseThrow(() -> new KnowledgeConflictException(
                "document revision is missing or outside the ingestion scope"));
    }

    /**
     * 校验结果的租户、请求和 Revision 绑定。
     *
     * @param request 摄取请求
     * @param result  摄取结果
     */
    private void requireMatchingResult(
        IngestionJobDescriptor request, IngestionResult result) {
        if (!request.id().equals(result.requestId())
            || !request.organizationId().equals(result.organizationId())
            || !request.projectId().equals(result.projectId())
            || !request.knowledgeRevisionId().equals(result.revisionId())) {
            throw new KnowledgeConflictException("ingestion result scope does not match request");
        }
    }

    /**
     * 校验结果计数、状态和完成时间满足 Revision 当前状态。
     *
     * @param revision Knowledge Revision
     * @param result   摄取结果
     */
    private void validateResult(KnowledgeRevision revision, IngestionResult result) {
        Instant now = Instant.now(clock);
        if (!revision.organizationId().equals(result.organizationId())
            || revision.status() != KnowledgeRevisionStatus.INGESTING
            || result.completedAt().isAfter(now.plus(Duration.ofMinutes(5)))
            || (result.status() == ResultStatus.SUCCEEDED
                && (result.documentCount() != revision.documentRevisionIds().size()
                    || result.artifacts().isEmpty()))) {
            throw new KnowledgeConflictException(
                "ingestion result does not satisfy revision verification rules");
        }
    }

    /**
     * 执行 INGESTING→VERIFYING→READY 乐观锁转换。
     *
     * @param revision 当前 Revision
     * @param now      转换时间
     * @param actor    内部服务主体
     * @return READY Revision
     */
    private KnowledgeRevision markReady(
        KnowledgeRevision revision, Instant now, String actor) {
        KnowledgeRevision verifying = revision.transitionTo(
            KnowledgeRevisionStatus.VERIFYING, "", now);
        requireUpdated(repository.updateKnowledgeRevisionState(
            verifying, revision.version(), actor));
        KnowledgeRevision ready = verifying.transitionTo(KnowledgeRevisionStatus.READY, "", now);
        requireUpdated(repository.updateKnowledgeRevisionState(
            ready, verifying.version(), actor));
        return ready;
    }

    /**
     * 执行 INGESTING→FAILED 乐观锁转换。
     *
     * @param revision    当前 Revision
     * @param failureCode 稳定失败代码
     * @param now         转换时间
     * @param actor       内部服务主体
     * @return FAILED Revision
     */
    private KnowledgeRevision markFailed(
        KnowledgeRevision revision, String failureCode, Instant now, String actor) {
        KnowledgeRevision failed = revision.transitionTo(
            KnowledgeRevisionStatus.FAILED, failureCode, now);
        requireUpdated(repository.updateKnowledgeRevisionState(
            failed, revision.version(), actor));
        return failed;
    }

    /**
     * 校验结果幂等重放的全部不可变内容相同。
     *
     * @param existing 已存在结果
     * @param requested 当前结果
     */
    private void requireSameResult(
        IngestionResult existing, IngestionResult requested) {
        if (!existing.equals(requested)) {
            throw new KnowledgeConflictException(
                "idempotency key is already bound to another ingestion result");
        }
    }

    /**
     * 校验乐观锁更新确实修改一行。
     *
     * @param rows 更新行数
     */
    private void requireUpdated(int rows) {
        if (rows != 1) {
            throw new KnowledgeConflictException(
                "knowledge revision was changed by another transaction");
        }
    }

    /**
     * 校验内部服务身份和 Control Audience。
     *
     * @param principal 候选主体
     */
    private void requireService(AgentArkPrincipal principal) {
        if (principal == null
            || principal.type() != PrincipalType.SERVICE
            || principal.serviceIdentity().isEmpty()
            || !principal.serviceIdentity().orElseThrow().audiences().contains(CONTROL_AUDIENCE)) {
            throw new AccessDeniedException("internal service audience is required");
        }
    }
}
