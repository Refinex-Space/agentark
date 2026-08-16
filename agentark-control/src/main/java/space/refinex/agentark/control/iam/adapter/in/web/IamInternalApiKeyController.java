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

package space.refinex.agentark.control.iam.adapter.in.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.refinex.agentark.control.iam.application.IamAccessDeniedException;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;

/**
 * 暴露 API Key 自省端点；凭据仍由 Control 本地摘要认证过滤器独立验证。
 *
 * @author refinex
 */
@RestController
@RequestMapping("/internal/v1/auth")
public final class IamInternalApiKeyController {

    /** 创建无状态 API Key 自省 Controller。 */
    public IamInternalApiKeyController() {
    }

    /**
     * 返回当前已认证 API Key 的非秘密主体视图。
     *
     * @param authentication Control 已建立的认证上下文
     * @return 不包含凭据、摘要、名称或到期信息的主体视图
     */
    @PostMapping("/api-keys:verify")
    public IamInternalApiModels.ApiKeyVerificationResponse verify(
        Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof AgentArkPrincipal principal)
            || principal.type() != PrincipalType.API_KEY) {
            throw new IamAccessDeniedException("API key authentication is required");
        }
        var tenant = principal.tenantSelection()
            .orElseThrow(() -> new IamAccessDeniedException("API key tenant is required"));
        var projectId = tenant.projectId()
            .orElseThrow(() -> new IamAccessDeniedException("API key project is required"));
        return new IamInternalApiModels.ApiKeyVerificationResponse(
            principal.issuer(),
            principal.subject(),
            principal.type().name(),
            principal.authorities(),
            tenant.organizationId().asString(),
            projectId.asString());
    }
}
