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

package space.refinex.agentark.scheduling.adapter.out.control;

import org.springframework.web.client.RestClient;
import space.refinex.agentark.kernel.id.IngestionRequestId;
import space.refinex.agentark.knowledge.adapter.out.control.ControlKnowledgeIngestionClient;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionPlan;
import space.refinex.agentark.knowledge.application.IngestionModels.IngestionResult;
import space.refinex.agentark.scheduling.port.ControlInternalClient;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 复用 Phase 14 语言中立 Wire DTO 调用 Control v1 Internal API，禁止使用 Control Mapper。
 *
 * @author refinex
 */
public final class HttpControlInternalClient implements ControlInternalClient {

    /**
     * Phase 14 Control HTTP Client 委托。
     */
    private final ControlKnowledgeIngestionClient delegate;

    /**
     * 创建版本化 Control Internal Client。
     *
     * @param restClient   已固定 Control Base URL 的客户端
     * @param serviceToken 短期服务身份 Token 提供器
     */
    public HttpControlInternalClient(RestClient restClient, Supplier<String> serviceToken) {
        this.delegate = new ControlKnowledgeIngestionClient(
            Objects.requireNonNull(restClient, "restClient must not be null"),
            Objects.requireNonNull(serviceToken, "serviceToken must not be null"));
    }

    /**
     * 加载固定摄取计划。
     *
     * @param requestId Control 摄取请求标识
     * @return 摄取计划
     */
    @Override
    public CompletionStage<IngestionPlan> load(IngestionRequestId requestId) {
        return delegate.load(requestId);
    }

    /**
     * 幂等提交摄取结果。
     *
     * @param result 摄取结果
     * @return Control 接受结果
     */
    @Override
    public CompletionStage<IngestionResult> submit(IngestionResult result) {
        return delegate.submit(result);
    }
}
