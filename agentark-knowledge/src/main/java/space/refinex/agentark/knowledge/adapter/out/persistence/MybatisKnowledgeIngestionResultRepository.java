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

package space.refinex.agentark.knowledge.adapter.out.persistence;

import org.springframework.dao.DuplicateKeyException;
import space.refinex.agentark.kernel.id.IngestionRequestId;
import space.refinex.agentark.kernel.id.JobId;
import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.kernel.ref.ObjectRef;
import space.refinex.agentark.knowledge.adapter.out.persistence.KnowledgePersistenceRows.IngestionResultRow;
import space.refinex.agentark.knowledge.application.IngestionModels.ArtifactKind;
import space.refinex.agentark.knowledge.application.IngestionModels.ArtifactReference;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionResult;
import space.refinex.agentark.knowledge.application.IngestionModels.ResultStatus;
import space.refinex.agentark.knowledge.application.port.KnowledgeIngestionResultRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 使用 MyBatis 持久化摄取结果、制品引用和同事务 Control Outbox。
 *
 * @author refinex
 */
public final class MybatisKnowledgeIngestionResultRepository
    implements KnowledgeIngestionResultRepository {

    /**
     * 摄取结果 Mapper。
     */
    private final KnowledgeIngestionResultMapper mapper;

    /**
     * 适配器 JSON 编解码器。
     */
    private final JsonMapper jsonMapper;

    /**
     * 创建 MyBatis 摄取结果仓储。
     *
     * @param mapper     摄取结果 Mapper
     * @param jsonMapper JSON Mapper
     */
    public MybatisKnowledgeIngestionResultRepository(
        KnowledgeIngestionResultMapper mapper, JsonMapper jsonMapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * 插入结果，唯一键竞争时返回已提交的项目内幂等结果。
     */
    @Override
    public InsertOutcome insertOrFind(IngestionResult result, String actor) {
        try {
            mapper.insertResult(row(result, actor));
            return new InsertOutcome(result, true);
        } catch (DuplicateKeyException exception) {
            IngestionResult existing = findByIdempotencyKey(
                result.projectId(), result.idempotencyKey()).orElseThrow(() -> exception);
            return new InsertOutcome(existing, false);
        }
    }

    /**
     * 按项目和幂等键查询结果。
     */
    @Override
    public Optional<IngestionResult> findByIdempotencyKey(
        ProjectId projectId, String idempotencyKey) {
        return mapper.findByIdempotencyKey(projectId.value(), idempotencyKey)
            .map(this::result);
    }

    /**
     * 按 Revision 和 Attempt 查询结果。
     */
    @Override
    public Optional<IngestionResult> findByAttempt(
        KnowledgeRevisionId revisionId, UUID attemptId) {
        return mapper.findByAttempt(revisionId.value(), attemptId).map(this::result);
    }

    /**
     * 写入 Knowledge Revision Outbox。
     */
    @Override
    public void insertOutbox(
        UUID eventId,
        KnowledgeRevisionId revisionId,
        String eventType,
        String payloadJson,
        Instant createdAt) {
        mapper.insertOutbox(eventId, revisionId.asString(), eventType, payloadJson, createdAt);
    }

    /**
     * 把领域结果转换为数据库行。
     *
     * @param result 领域结果
     * @param actor  内部服务主体
     * @return 数据库行
     */
    private IngestionResultRow row(IngestionResult result, String actor) {
        List<ArtifactJson> artifacts = result.artifacts().stream()
            .map(value -> new ArtifactJson(
                value.kind().name(), value.ref().uri().toASCIIString(),
                value.ref().checksum().value(), value.ref().size(), value.ref().mediaType()))
            .toList();
        return new IngestionResultRow(
            result.resultId(), result.requestId().value(), result.organizationId().value(),
            result.projectId().value(), result.revisionId().value(),
            result.schedulerJobId().value(), result.attemptId(), result.idempotencyKey(),
            result.documentCount(), result.chunkCount(),
            HexFormat.of().parseHex(result.checksum().hex()),
            jsonMapper.writeValueAsString(artifacts), result.status().name(),
            result.failureCode().isBlank() ? null : result.failureCode(), result.completedAt(),
            Instant.now(), actor);
    }

    /**
     * 把数据库行转换为领域结果。
     *
     * @param row 数据库行
     * @return 领域结果
     */
    private IngestionResult result(IngestionResultRow row) {
        ArtifactJson[] artifactRows = jsonMapper.readValue(
            row.artifactRefsJson(), ArtifactJson[].class);
        List<ArtifactReference> artifacts = Arrays.stream(artifactRows)
            .map(value -> new ArtifactReference(
                ArtifactKind.valueOf(value.kind()), ObjectRef.of(
                    value.uri(), new Checksum(value.checksum()), value.size(), value.mediaType())))
            .toList();
        return new IngestionResult(
            row.id(), new IngestionRequestId(row.requestId()),
            new OrganizationId(row.organizationId()), new ProjectId(row.projectId()),
            new KnowledgeRevisionId(row.knowledgeRevisionId()),
            new JobId(row.schedulerJobId()), row.attemptId(), row.idempotencyKey(),
            row.documentCount(), row.chunkCount(), new Checksum(
                "sha256:" + HexFormat.of().formatHex(row.checksum())),
            artifacts, ResultStatus.valueOf(row.status()),
            row.failureCode() == null ? "" : row.failureCode(), row.completedAt());
    }

    /**
     * @param kind      制品类别
     * @param uri       不含授权参数的对象 URI
     * @param checksum  规范 SHA-256
     * @param size      对象字节数
     * @param mediaType 媒体类型
     * @author refinex
     */
    private record ArtifactJson(
        String kind, String uri, String checksum, long size, String mediaType) {
    }
}
