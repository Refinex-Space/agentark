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

package space.refinex.agentark.knowledge.adapter.in.web;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.kernel.id.IngestionRequestId;
import space.refinex.agentark.knowledge.adapter.contract.KnowledgeIngestionWireModels.IngestionPlanView;
import space.refinex.agentark.knowledge.adapter.contract.KnowledgeIngestionWireModels.IngestionResultView;
import space.refinex.agentark.knowledge.application.KnowledgeConflictException;
import space.refinex.agentark.knowledge.application.KnowledgeIngestionControlService;

import java.util.Objects;

/**
 * 提供 Scheduler Worker 加载固定计划和幂等提交结果的受保护 Internal API。
 *
 * @author refinex
 */
@RestController
public final class KnowledgeIngestionInternalController {

    /**
     * Knowledge 摄取 Control 应用服务。
     */
    private final KnowledgeIngestionControlService service;

    /**
     * 创建 Internal API Controller。
     *
     * @param service 摄取 Control 应用服务
     */
    public KnowledgeIngestionInternalController(
        KnowledgeIngestionControlService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    /**
     * 加载当前请求绑定的不可变 Revision、Document 与 Profile 计划。
     *
     * @param authentication Spring Security 已认证上下文
     * @param requestId      摄取请求 UUIDv7
     * @return 固定摄取计划
     */
    @GetMapping("/internal/v1/knowledge/ingestions/{requestId}/plan")
    public IngestionPlanView plan(
        Authentication authentication, @PathVariable String requestId) {
        return IngestionPlanView.from(service.loadPlan(
            principal(authentication), IngestionRequestId.parse(requestId)));
    }

    /**
     * 幂等提交当前 Attempt 的成功或失败结果。
     *
     * @param authentication Spring Security 已认证上下文
     * @param requestId      URL 摄取请求 UUIDv7
     * @param result         Worker 结果
     * @return Control 持久结果
     */
    @PostMapping("/internal/v1/knowledge/ingestions/{requestId}:complete")
    public IngestionResultView complete(
        Authentication authentication,
        @PathVariable String requestId,
        @RequestBody IngestionResultView result) {
        IngestionRequestId parsed = IngestionRequestId.parse(requestId);
        if (!parsed.asString().equals(result.requestId())) {
            throw new KnowledgeConflictException(
                "request path does not match ingestion result");
        }
        return IngestionResultView.from(service.acceptResult(
            principal(authentication), result.toDomain()));
    }

    /**
     * 从 Spring Security 上下文提取 AgentArk 主体。
     *
     * @param authentication 安全上下文
     * @return AgentArk 主体
     */
    private AgentArkPrincipal principal(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof AgentArkPrincipal principal)) {
            throw new AccessDeniedException("authenticated AgentArk principal is required");
        }
        return principal;
    }
}
