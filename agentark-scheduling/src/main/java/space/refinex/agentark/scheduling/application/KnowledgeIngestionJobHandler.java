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

package space.refinex.agentark.scheduling.application;

import space.refinex.agentark.kernel.id.IngestionRequestId;
import space.refinex.agentark.kernel.id.KnowledgeRevisionId;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionCommand;
import space.refinex.agentark.knowledge.application.IngestionModels.ResultStatus;
import space.refinex.agentark.knowledge.application.KnowledgeIngestionWorker;
import space.refinex.agentark.scheduling.domain.SchedulerModels.ClaimedJob;
import space.refinex.agentark.scheduling.domain.SchedulerModels.IdempotencyCapability;
import space.refinex.agentark.scheduling.domain.SchedulerModels.JobType;
import space.refinex.agentark.scheduling.port.JobHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 将 KNOWLEDGE_INGESTION Job 映射到 Phase 14 Worker，并由 Worker 经 Control Client 提交结果。
 *
 * @author refinex
 */
public final class KnowledgeIngestionJobHandler implements JobHandler {

    /**
     * 安全摄取 Worker。
     */
    private final KnowledgeIngestionWorker worker;

    /**
     * Job Payload JSON 解析器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建 Knowledge 摄取 Handler。
     *
     * @param worker       Phase 14 Worker
     * @param objectMapper JSON 解析器
     */
    public KnowledgeIngestionJobHandler(
        KnowledgeIngestionWorker worker, ObjectMapper objectMapper) {
        this.worker = Objects.requireNonNull(worker, "worker must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 返回 Knowledge 摄取类型。
     *
     * @return KNOWLEDGE_INGESTION
     */
    @Override
    public JobType type() {
        return JobType.KNOWLEDGE_INGESTION;
    }

    /**
     * Control 结果命令使用 Job/Attempt 派生稳定幂等键。
     *
     * @return PROVIDER_KEY
     */
    @Override
    public IdempotencyCapability idempotencyCapability() {
        return IdempotencyCapability.PROVIDER_KEY;
    }

    /**
     * 解析固定摄取命令并异步执行；业务失败已提交 Control，因此不重复写 Control Schema。
     *
     * @param claim 当前 Claim
     * @return Handler 结果
     */
    @Override
    public CompletionStage<HandlerResult> handle(ClaimedJob claim) {
        JsonNode root = read(claim.job().payload());
        IngestionCommand command = new IngestionCommand(
            IngestionRequestId.parse(text(root, "requestId")),
            claim.job().organizationId(), claim.job().projectId(),
            KnowledgeRevisionId.parse(text(root, "knowledgeRevisionId")),
            claim.job().id(), claim.attemptId(),
            "ingestion:" + claim.job().id().asString() + ":" + claim.attemptNumber());
        return worker.execute(command).thenApply(result ->
            result.status() == ResultStatus.SUCCEEDED
                ? HandlerResult.success(Optional.of(
                "knowledge-result:" + result.resultId()))
                : HandlerResult.failure(result.failureCode(), false));
    }

    /**
     * 解析 Job Payload JSON。
     *
     * @param json JSON 文本
     * @return JSON 根节点
     */
    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("knowledge ingestion payload is invalid", exception);
        }
    }

    /**
     * 读取必需非空字符串字段。
     *
     * @param root  JSON 根节点
     * @param field 字段名
     * @return 文本值
     */
    private String text(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("knowledge ingestion payload field is missing");
        }
        return value.asText();
    }
}
