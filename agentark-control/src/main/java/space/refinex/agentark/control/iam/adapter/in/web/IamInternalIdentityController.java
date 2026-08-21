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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.refinex.agentark.control.iam.application.IamAccessDeniedException;
import space.refinex.agentark.control.iam.application.IamIdentityMappingService;
import space.refinex.agentark.control.iam.domain.IamStatus;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.security.PrincipalType;

import java.util.Objects;
import java.util.Optional;

/**
 * 接收 Gateway 签名服务身份投递的非敏感用户投影，不允许浏览器或 API Key 调用。
 *
 * @author refinex
 */
@RestController
@RequestMapping("/internal/v1/identity")
public final class IamInternalIdentityController {

    /** 身份映射事务服务。 */
    private final IamIdentityMappingService mappingService;

    /** 创建内部身份投影 Controller。 */
    public IamInternalIdentityController(IamIdentityMappingService mappingService) {
        this.mappingService = Objects.requireNonNull(mappingService, "mappingService must not be null");
    }

    /**
     * 幂等写入内置身份投影；Gateway 服务 JWT 必须面向 Control Audience。
     */
    @PostMapping("/accounts:project")
    public ProjectionResponse project(
        Authentication authentication, @Valid @RequestBody ProjectionRequest request) {
        requireGateway(authentication);
        var identity = mappingService.provision(
            request.issuer(),
            request.subject(),
            optional(request.displayName()),
            optional(request.email()),
            IamStatus.valueOf(request.status()));
        return new ProjectionResponse(identity.id().asString(), identity.status().name());
    }

    /** 只接受 Gateway 服务身份。 */
    private static void requireGateway(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof AgentArkPrincipal principal)
            || principal.type() != PrincipalType.SERVICE
            || principal.serviceIdentity().isEmpty()
            || !"agentark-gateway".equals(principal.serviceIdentity().orElseThrow().serviceId())) {
            throw new IamAccessDeniedException("Gateway service identity is required");
        }
    }

    /** 规范化可空文本。 */
    private static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /**
     * Gateway Identity 非敏感投影请求。
     *
     * @param issuer      内置 Issuer
     * @param subject     UUIDv7 账号主体标识
     * @param username    用户名，仅用于审计兼容且不作为 Control 授权键
     * @param displayName 展示名称
     * @param email       可空邮箱
     * @param status      ACTIVE、SUSPENDED 或 DISABLED
     * @author refinex
     */
    public record ProjectionRequest(
        @NotBlank String issuer,
        @NotBlank String subject,
        @NotBlank String username,
        @NotBlank String displayName,
        String email,
        @NotBlank String status) {
    }

    /**
     * Control 用户投影响应。
     *
     * @param userIdentityId Control 用户身份 UUIDv7
     * @param status         投影状态
     * @author refinex
     */
    public record ProjectionResponse(String userIdentityId, String status) {
    }
}
