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

package space.refinex.agentark.control.secret.adapter.in.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import space.refinex.agentark.control.iam.application.IamAccessDeniedException;
import space.refinex.agentark.control.secret.adapter.in.web.SecretApiModels.CreateSecretBindingRequest;
import space.refinex.agentark.control.secret.adapter.in.web.SecretApiModels.CreateSecretMetadataRequest;
import space.refinex.agentark.control.secret.adapter.in.web.SecretApiModels.RotateSecretMetadataRequest;
import space.refinex.agentark.control.secret.adapter.in.web.SecretApiModels.ChangeSecretStatusRequest;
import space.refinex.agentark.control.secret.application.SecretApplicationService;
import space.refinex.agentark.control.secret.domain.SecretBinding;
import space.refinex.agentark.control.secret.domain.SecretMetadata;
import space.refinex.agentark.control.secret.domain.SecretProviderType;
import space.refinex.agentark.control.secret.domain.SecretScope;
import space.refinex.agentark.control.secret.domain.SecretMetadataStatus;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.web.CursorPage;
import space.refinex.agentark.kernel.id.EnvironmentId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.SecretMetadataId;

import java.net.URI;
import java.util.Locale;

/**
 * 暴露不读取 Secret 值的 Metadata 与 Environment Binding Public API。
 *
 * @author refinex
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@PreAuthorize("isAuthenticated()")
public class SecretController {

    /**
     * Secret 应用服务。
     */
    private final SecretApplicationService service;

    /**
     * @param service Secret 应用服务
     */
    public SecretController(SecretApplicationService service) {
        this.service = java.util.Objects.requireNonNull(service, "service must not be null");
    }

    /**
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param request        创建元数据请求
     * @return 带地址的新 Secret Metadata
     */
    @PostMapping("/secrets")
    public ResponseEntity<SecretMetadata> createMetadata(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody CreateSecretMetadataRequest request) {
        SecretMetadata created = service.createMetadata(
            principal(authentication), ProjectId.parse(projectId), request.key(), request.name(),
            enumValue(SecretProviderType.class, request.provider()), request.externalPath(),
            request.externalVersion(), enumValue(SecretScope.class, request.scope()));
        return ResponseEntity.created(URI.create(
                "/api/v1/projects/" + projectId + "/secrets/" + created.id().asString()))
            .body(created);
    }

    /**
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param cursor         可选游标
     * @param limit          页大小
     * @return Secret Metadata 游标页
     */
    @GetMapping("/secrets")
    public CursorPage<SecretMetadata> listMetadata(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "50") int limit) {
        return service.listMetadata(
            principal(authentication), ProjectId.parse(projectId), cursor, limit);
    }

    /**
     * 轮换 Secret 外部版本指针，不接收或回显 Secret 值。
     *
     * @param authentication 已认证安全上下文
     * @param projectId 项目 UUIDv7
     * @param secretMetadataId Secret 元数据 UUIDv7
     * @param request 轮换请求
     * @return 轮换后的元数据
     */
    @PostMapping("/secrets/{secretMetadataId}:rotate")
    public SecretMetadata rotateMetadata(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String secretMetadataId,
        @Valid @RequestBody RotateSecretMetadataRequest request) {
        return service.rotateMetadata(
            principal(authentication), ProjectId.parse(projectId),
            SecretMetadataId.parse(secretMetadataId), request.externalVersion(),
            request.expectedVersion());
    }

    /**
     * 紧急禁用 Secret 元数据，使后续解析失败关闭。
     *
     * @param authentication 已认证安全上下文
     * @param projectId 项目 UUIDv7
     * @param secretMetadataId Secret 元数据 UUIDv7
     * @param request 状态前置条件
     * @return 禁用后的元数据
     */
    @PostMapping("/secrets/{secretMetadataId}:disable")
    public SecretMetadata disableMetadata(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String secretMetadataId,
        @Valid @RequestBody ChangeSecretStatusRequest request) {
        return service.changeMetadataStatus(
            principal(authentication), ProjectId.parse(projectId),
            SecretMetadataId.parse(secretMetadataId), SecretMetadataStatus.DISABLED,
            request.expectedVersion());
    }

    /**
     * 重新启用已完成外部修复的 Secret 元数据。
     *
     * @param authentication 已认证安全上下文
     * @param projectId 项目 UUIDv7
     * @param secretMetadataId Secret 元数据 UUIDv7
     * @param request 状态前置条件
     * @return 启用后的元数据
     */
    @PostMapping("/secrets/{secretMetadataId}:enable")
    public SecretMetadata enableMetadata(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String secretMetadataId,
        @Valid @RequestBody ChangeSecretStatusRequest request) {
        return service.changeMetadataStatus(
            principal(authentication), ProjectId.parse(projectId),
            SecretMetadataId.parse(secretMetadataId), SecretMetadataStatus.ENABLED,
            request.expectedVersion());
    }

    /**
     * 永久吊销 Secret 元数据；吊销后不能重新启用。
     *
     * @param authentication 已认证安全上下文
     * @param projectId 项目 UUIDv7
     * @param secretMetadataId Secret 元数据 UUIDv7
     * @param request 状态前置条件
     * @return 吊销后的元数据
     */
    @PostMapping("/secrets/{secretMetadataId}:revoke")
    public SecretMetadata revokeMetadata(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String secretMetadataId,
        @Valid @RequestBody ChangeSecretStatusRequest request) {
        return service.changeMetadataStatus(
            principal(authentication), ProjectId.parse(projectId),
            SecretMetadataId.parse(secretMetadataId), SecretMetadataStatus.REVOKED,
            request.expectedVersion());
    }

    /**
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param environmentId  环境 UUIDv7
     * @param request        创建绑定请求
     * @return 带地址的新 Secret Binding
     */
    @PostMapping("/environments/{environmentId}/secret-bindings")
    public ResponseEntity<SecretBinding> createBinding(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String environmentId,
        @Valid @RequestBody CreateSecretBindingRequest request) {
        SecretBinding created = service.createBinding(
            principal(authentication), ProjectId.parse(projectId),
            EnvironmentId.parse(environmentId), SecretMetadataId.parse(request.secretMetadataId()),
            request.bindingKey());
        return ResponseEntity.created(URI.create(
            "/api/v1/projects/" + projectId + "/environments/" + environmentId
                + "/secret-bindings/" + created.id().asString())).body(created);
    }

    /**
     * @param authentication 已认证安全上下文
     * @param projectId      项目 UUIDv7
     * @param environmentId  环境 UUIDv7
     * @param cursor         可选游标
     * @param limit          页大小
     * @return Secret Binding 游标页
     */
    @GetMapping("/environments/{environmentId}/secret-bindings")
    public CursorPage<SecretBinding> listBindings(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String environmentId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "50") int limit) {
        return service.listBindings(
            principal(authentication), ProjectId.parse(projectId),
            EnvironmentId.parse(environmentId), cursor, limit);
    }

    /**
     * @param authentication 已认证安全上下文
     * @return AgentArk 协议主体
     */
    private AgentArkPrincipal principal(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof AgentArkPrincipal principal)) {
            throw new IamAccessDeniedException("authenticated AgentArk principal is required");
        }
        return principal;
    }

    /**
     * @param type  枚举类型
     * @param value 请求值
     * @param <E>   枚举类型参数
     * @return 不区分大小写的受控枚举值
     */
    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("enum request value is invalid", exception);
        }
    }
}
