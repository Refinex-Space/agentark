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

package space.refinex.agentark.scheduling.adapter.out.runtime;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import space.refinex.agentark.scheduling.port.InternalClientException;
import space.refinex.agentark.scheduling.port.RuntimeInternalClient;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 通过 Runtime v1 Internal API 幂等创建 Turn，不依赖 Runtime 实现模块。
 *
 * @author refinex
 */
public final class HttpRuntimeInternalClient implements RuntimeInternalClient {

    /**
     * Runtime REST 客户端。
     */
    private final RestClient restClient;

    /**
     * 短期服务身份 Token 提供器。
     */
    private final Supplier<String> serviceToken;

    /**
     * 创建 Runtime Internal Client。
     *
     * @param restClient   已固定 Runtime Base URL 的客户端
     * @param serviceToken 短期服务身份 Token 提供器
     */
    public HttpRuntimeInternalClient(RestClient restClient, Supplier<String> serviceToken) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.serviceToken = Objects.requireNonNull(serviceToken, "serviceToken must not be null");
    }

    /**
     * 调用 `/internal/v1/runtime/turns` 并返回稳定 Run 标识。
     *
     * @param command Runtime Turn 命令
     * @return Run 标识
     */
    @Override
    public CompletionStage<String> createTurn(RuntimeTurnCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                .uri("/internal/v1/runtime/turns")
                .header("Idempotency-Key", command.idempotencyKey())
                .headers(this::authorize)
                .body(Map.of(
                    "organizationId", command.organizationId().asString(),
                    "projectId", command.projectId().asString(),
                    "sessionId", command.sessionId().asString(),
                    "input", command.inputJson(),
                    "inputHash", command.inputHash().value(),
                    "priority", command.priority()))
                .retrieve()
                .body(Map.class);
            Object runId = response == null ? null : response.get("runId");
            if (!(runId instanceof String value) || value.isBlank()) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                    "runtime internal response is missing runId"));
            }
            return CompletableFuture.completedFuture(value);
        } catch (RestClientResponseException exception) {
            boolean retryable = exception.getStatusCode().value() == 429
                || exception.getStatusCode().is5xxServerError();
            return CompletableFuture.failedFuture(new InternalClientException(
                "RUNTIME_INTERNAL_REJECTED", retryable));
        } catch (RestClientException exception) {
            return CompletableFuture.failedFuture(new InternalClientException(
                "RUNTIME_INTERNAL_UNAVAILABLE", true));
        }
    }

    /**
     * 写入短期 Bearer Token，禁止记录凭据。
     *
     * @param headers HTTP 请求头
     */
    private void authorize(HttpHeaders headers) {
        String token = serviceToken.get();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("scheduler service identity token is not configured");
        }
        headers.setBearerAuth(token);
    }

}
