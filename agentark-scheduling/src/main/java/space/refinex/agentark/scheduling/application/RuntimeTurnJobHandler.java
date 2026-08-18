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

import space.refinex.agentark.kernel.id.SessionId;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.scheduling.domain.SchedulerModels.ClaimedJob;
import space.refinex.agentark.scheduling.domain.SchedulerModels.IdempotencyCapability;
import space.refinex.agentark.scheduling.domain.SchedulerModels.JobType;
import space.refinex.agentark.scheduling.port.InternalClientException;
import space.refinex.agentark.scheduling.port.JobHandler;
import space.refinex.agentark.scheduling.port.RuntimeInternalClient;
import space.refinex.agentark.scheduling.port.RuntimeInternalClient.RuntimeTurnCommand;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 仅通过版本化 Runtime Internal Client 创建 Agent Turn，不链接 Runtime 实现模块。
 *
 * @author refinex
 */
public final class RuntimeTurnJobHandler implements JobHandler {

    /**
     * Runtime 内部接口客户端。
     */
    private final RuntimeInternalClient client;

    /**
     * Payload JSON 解析器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建 Runtime Turn Handler。
     *
     * @param client       Runtime Internal Client
     * @param objectMapper JSON 解析器
     */
    public RuntimeTurnJobHandler(RuntimeInternalClient client, ObjectMapper objectMapper) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 返回 Runtime Turn Job 类型。
     *
     * @return RUNTIME_TURN
     */
    @Override
    public JobType type() {
        return JobType.RUNTIME_TURN;
    }

    /**
     * Runtime 接单事务使用 Scheduler 派生幂等键。
     *
     * @return PROVIDER_KEY
     */
    @Override
    public IdempotencyCapability idempotencyCapability() {
        return IdempotencyCapability.PROVIDER_KEY;
    }

    /**
     * 映射 Runtime Internal Command 并保留 429、5xx 与网络错误重试分类。
     *
     * @param claim 当前 Claim
     * @return Handler 结果
     */
    @Override
    public CompletionStage<HandlerResult> handle(ClaimedJob claim) {
        JsonNode root = read(claim.job().payload());
        String input = text(root, "input");
        RuntimeTurnCommand command = new RuntimeTurnCommand(
            claim.job().organizationId(), claim.job().projectId(),
            SessionId.parse(text(root, "sessionId")), input, Checksum.sha256(input),
            integer(root, "priority"), "scheduler-turn:" + claim.job().id().asString());
        return client.createTurn(command)
            .thenApply(runId -> HandlerResult.success(Optional.of("run:" + runId)))
            .exceptionally(exception -> {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                if (cause instanceof InternalClientException internal) {
                    return HandlerResult.failure(internal.getMessage(), internal.retryable());
                }
                return HandlerResult.failure("RUNTIME_INTERNAL_FAILED", true);
            });
    }

    /**
     * 解析 Payload JSON。
     *
     * @param json JSON 文本
     * @return 根节点
     */
    private JsonNode read(String json) {
        return objectMapper.readTree(json);
    }

    /**
     * 读取必需文本字段。
     *
     * @param root  根节点
     * @param field 字段名
     * @return 文本值
     */
    private String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("runtime turn payload field is missing");
        }
        return value.asText();
    }

    /**
     * 读取整数优先级字段。
     *
     * @param root  根节点
     * @param field 字段名
     * @return 整数值
     */
    private int integer(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null) {
            throw new IllegalArgumentException("runtime turn priority is missing");
        }
        int parsed;
        if (value.isIntegralNumber()) {
            parsed = value.asInt();
        } else if (value.isTextual() && value.asText().matches("-?[0-9]{1,3}")) {
            parsed = Integer.parseInt(value.asText());
        } else {
            throw new IllegalArgumentException("runtime turn priority is invalid");
        }
        if (parsed < -100 || parsed > 100) {
            throw new IllegalArgumentException("runtime turn priority is outside allowed range");
        }
        return parsed;
    }
}
