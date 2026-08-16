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

package space.refinex.agentark.knowledge.adapter.out.vector.agentscope;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Mono;
import space.refinex.agentark.knowledge.application.KnowledgeRetrievalService;
import space.refinex.agentark.knowledge.application.RetrievalModels.RetrievalRequest;
import space.refinex.agentark.knowledge.application.RetrievalModels.RetrievalResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

/**
 * 把 AgentArk 固定 READY Knowledge Revision 检索映射为 AgentScope Harness 只读 Tool。
 *
 * <p>该防腐层不使用已废弃的 AgentScope Knowledge/Vector 实现，也不允许模型更换租户或 Revision。
 *
 * @author refinex
 */
public final class AgentScopeKnowledgeAdapter {

    /**
     * Provider 中立检索应用服务。
     */
    private final KnowledgeRetrievalService retrievalService;

    /**
     * Snapshot 编译阶段固定的检索模板。
     */
    private final RetrievalRequest fixedRequest;

    /**
     * Tool 结果 JSON 编码器。
     */
    private final JsonMapper jsonMapper;

    /**
     * 创建 AgentScope Knowledge 防腐层。
     *
     * @param retrievalService Provider 中立检索服务
     * @param fixedRequest     固定租户、Revision、Profile、ACL 与预算的请求模板
     * @param jsonMapper       JSON Mapper
     */
    public AgentScopeKnowledgeAdapter(
        KnowledgeRetrievalService retrievalService,
        RetrievalRequest fixedRequest,
        JsonMapper jsonMapper) {
        this.retrievalService = Objects.requireNonNull(
            retrievalService, "retrievalService must not be null");
        this.fixedRequest = Objects.requireNonNull(fixedRequest, "fixedRequest must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
    }

    /**
     * 把当前适配器注册到 AgentScope Toolkit。
     *
     * @param toolkit 当前 Session 独享 Toolkit
     */
    public void register(Toolkit toolkit) {
        Objects.requireNonNull(toolkit, "toolkit must not be null").registerTool(this);
    }

    /**
     * 在 Snapshot 固定的 Knowledge Revision 和文档 ACL 内检索并返回 Citation 与 Trace。
     *
     * @param query 用户查询，不作为租户或 Revision 选择依据
     * @return 异步 JSON 结果
     */
    @Tool(
        name = "knowledge_retrieve",
        description = "Search the fixed published knowledge revision and return cited context.",
        readOnly = true,
        concurrencySafe = true)
    public Mono<String> retrieve(
        @ToolParam(name = "query", description = "The knowledge question to search.")
        String query) {
        RetrievalRequest request = new RetrievalRequest(
            fixedRequest.organizationId(), fixedRequest.projectId(), fixedRequest.revision(),
            fixedRequest.embeddingProfile(), fixedRequest.retrievalProfile(),
            fixedRequest.allowedDocumentIds(), fixedRequest.documentTitles(), query,
            fixedRequest.candidateLimit(), fixedRequest.resultLimit(),
            fixedRequest.contextBudgetChars(), fixedRequest.scoreThreshold(),
            fixedRequest.hybridEnabled());
        return Mono.fromCompletionStage(retrievalService.retrieve(request))
            .map(this::serialize);
    }

    /**
     * 序列化语言中立检索结果，不暴露 AgentScope Event 或内部 Provider 类型。
     *
     * @param result 检索结果
     * @return JSON 字符串
     */
    private String serialize(RetrievalResult result) {
        try {
            return jsonMapper.writeValueAsString(result);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("knowledge tool result serialization failed", exception);
        }
    }
}
