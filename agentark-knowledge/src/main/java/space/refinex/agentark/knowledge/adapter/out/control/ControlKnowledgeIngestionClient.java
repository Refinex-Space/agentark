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

package space.refinex.agentark.knowledge.adapter.out.control;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import space.refinex.agentark.kernel.id.IngestionRequestId;
import space.refinex.agentark.knowledge.adapter.contract.KnowledgeIngestionWireModels.IngestionPlanView;
import space.refinex.agentark.knowledge.adapter.contract.KnowledgeIngestionWireModels.IngestionResultView;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionPlan;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionResult;
import space.refinex.agentark.knowledge.port.IngestionPlanSource;
import space.refinex.agentark.knowledge.port.IngestionResultSink;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 通过受保护的 Control Internal API 加载计划和提交结果，禁止 Worker 使用 Control DataSource。
 *
 * @author refinex
 */
public final class ControlKnowledgeIngestionClient
    implements IngestionPlanSource, IngestionResultSink {

    /**
     * 已固定 Control Base URL 和请求超时的 REST 客户端。
     */
    private final RestClient restClient;

    /**
     * 短期服务身份 Token 提供器。
     */
    private final Supplier<String> serviceToken;

    /**
     * 创建 Control Internal API 客户端。
     *
     * @param restClient   已固定 Control Base URL 的客户端
     * @param serviceToken 短期服务身份 Token 提供器
     */
    public ControlKnowledgeIngestionClient(
        RestClient restClient, Supplier<String> serviceToken) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.serviceToken = Objects.requireNonNull(
            serviceToken, "serviceToken must not be null");
    }

    /**
     * 从 Control 加载固定摄取计划。
     *
     * @param requestId Control 摄取请求标识
     * @return 已完成或失败的异步计划
     */
    @Override
    public CompletionStage<IngestionPlan> load(IngestionRequestId requestId) {
        Objects.requireNonNull(requestId, "requestId must not be null");
        try {
            IngestionPlanView response = restClient.get()
                .uri("/internal/v1/knowledge/ingestions/{requestId}/plan", requestId.asString())
                .headers(this::authorize)
                .retrieve()
                .body(IngestionPlanView.class);
            IngestionPlan plan = response == null ? null : response.toDomain();
            if (plan == null || !plan.requestId().equals(requestId)) {
                return CompletableFuture.failedFuture(
                    new IllegalStateException("control returned an invalid ingestion plan"));
            }
            return CompletableFuture.completedFuture(plan);
        } catch (RestClientException exception) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                "control ingestion plan request failed", exception));
        }
    }

    /**
     * 向 Control 幂等提交当前 Attempt 结果。
     *
     * @param result 待提交结果
     * @return Control 已接受结果
     */
    @Override
    public CompletionStage<IngestionResult> submit(IngestionResult result) {
        Objects.requireNonNull(result, "result must not be null");
        try {
            IngestionResultView response = restClient.post()
                .uri("/internal/v1/knowledge/ingestions/{requestId}:complete",
                    result.requestId().asString())
                .headers(this::authorize)
                .body(IngestionResultView.from(result))
                .retrieve()
                .body(IngestionResultView.class);
            IngestionResult accepted = response == null ? null : response.toDomain();
            if (!result.equals(accepted)) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                    "control returned a different ingestion result"));
            }
            return CompletableFuture.completedFuture(accepted);
        } catch (RestClientException exception) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                "control ingestion result command failed", exception));
        }
    }

    /**
     * 把短期服务凭据仅写入 Authorization Header，不记录或持久化。
     *
     * @param headers HTTP 请求头
     */
    private void authorize(HttpHeaders headers) {
        String token = serviceToken.get();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                "scheduler service identity token is not configured");
        }
        headers.setBearerAuth(token);
    }
}
