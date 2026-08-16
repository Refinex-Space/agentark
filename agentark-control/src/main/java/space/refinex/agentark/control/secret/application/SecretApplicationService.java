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

package space.refinex.agentark.control.secret.application;

import org.springframework.transaction.annotation.Transactional;
import space.refinex.agentark.control.iam.application.*;
import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.iam.domain.Environment;
import space.refinex.agentark.control.iam.domain.Project;
import space.refinex.agentark.control.secret.application.port.SecretRepository;
import space.refinex.agentark.control.secret.domain.*;
import space.refinex.agentark.foundation.security.AgentArkPrincipal;
import space.refinex.agentark.foundation.web.CursorPage;
import space.refinex.agentark.kernel.id.EnvironmentId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.SecretBindingId;
import space.refinex.agentark.kernel.id.SecretMetadataId;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 编排 Secret Metadata、Environment Binding、项目授权、游标分页和安全审计。
 *
 * @author refinex
 */
public class SecretApplicationService {

    /**
     * Secret 持久化端口。
     */
    private final SecretRepository repository;

    /**
     * IAM 租户目录端口。
     */
    private final TenantCatalogRepository tenantRepository;

    /**
     * IAM 授权服务。
     */
    private final IamAuthorizationService authorizationService;

    /**
     * 事务感知审计发布器。
     */
    private final IamAuditPublisher auditPublisher;

    /**
     * UTC 时钟。
     */
    private final Clock clock;

    /**
     * @param repository           Secret 持久化端口
     * @param tenantRepository     IAM 租户端口
     * @param authorizationService IAM 授权服务
     * @param auditPublisher       审计发布器
     * @param clock                UTC 时钟
     */
    public SecretApplicationService(
        SecretRepository repository,
        TenantCatalogRepository tenantRepository,
        IamAuthorizationService authorizationService,
        IamAuditPublisher auditPublisher,
        Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.tenantRepository = Objects.requireNonNull(
            tenantRepository, "tenantRepository must not be null");
        this.authorizationService = Objects.requireNonNull(
            authorizationService, "authorizationService must not be null");
        this.auditPublisher = Objects.requireNonNull(
            auditPublisher, "auditPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * @param principal       已认证主体
     * @param projectId       项目标识
     * @param key             稳定 Key
     * @param name            显示名称
     * @param provider        Provider 类型
     * @param externalPath    外部定位
     * @param externalVersion 可选外部版本
     * @param scope           Scope
     * @return 新 Secret Metadata
     */
    @Transactional
    public SecretMetadata createMetadata(
        AgentArkPrincipal principal,
        ProjectId projectId,
        String key,
        String name,
        SecretProviderType provider,
        String externalPath,
        String externalVersion,
        SecretScope scope) {
        Project project = authorize(principal, projectId, PermissionRegistry.SECRET_MANAGE);
        Instant now = Instant.now(clock);
        SecretMetadata metadata = new SecretMetadata(
            SecretMetadataId.generate(), project.organizationId(), project.id(), key, name,
            provider, externalPath, externalVersion, scope, SecretMetadataStatus.ENABLED, 0,
            now, now);
        repository.insertMetadata(metadata, principal.subject());
        auditPublisher.afterCommit(new IamAuditRecord(
            "secret.metadata.create", principal.subject(), "secret-metadata",
            metadata.id().asString(), Optional.of(project.organizationId()),
            Optional.of(project.id()), "SUCCEEDED", now));
        return metadata;
    }

    /**
     * @param principal 已认证主体
     * @param projectId 项目标识
     * @param cursor    可选不透明游标
     * @param limit     页大小
     * @return Secret Metadata 游标页
     */
    @Transactional(readOnly = true)
    public CursorPage<SecretMetadata> listMetadata(
        AgentArkPrincipal principal, ProjectId projectId, String cursor, int limit) {
        authorize(principal, projectId, PermissionRegistry.SECRET_READ);
        int pageSize = requireLimit(limit);
        List<SecretMetadata> loaded = repository.listMetadata(
            projectId, decode(cursor), pageSize + 1);
        boolean hasMore = loaded.size() > pageSize;
        List<SecretMetadata> items = loaded.stream().limit(pageSize).toList();
        Optional<String> next = hasMore
            ? Optional.of(encode(items.get(items.size() - 1).key()))
            : Optional.empty();
        return new CursorPage<>(items, next, hasMore);
    }

    /**
     * @param principal     已认证主体
     * @param projectId     项目标识
     * @param environmentId 环境标识
     * @param metadataId    Secret Metadata 标识
     * @param bindingKey    环境内别名
     * @return 新 Environment Binding
     */
    @Transactional
    public SecretBinding createBinding(
        AgentArkPrincipal principal,
        ProjectId projectId,
        EnvironmentId environmentId,
        SecretMetadataId metadataId,
        String bindingKey) {
        Project project = authorize(principal, projectId, PermissionRegistry.SECRET_MANAGE);
        Environment environment = tenantRepository.findEnvironment(environmentId)
            .filter(value -> value.projectId().equals(projectId))
            .orElseThrow(() -> new IamNotFoundException("environment is not visible"));
        SecretMetadata metadata = repository.findMetadata(projectId, metadataId)
            .filter(value -> value.scope() == SecretScope.ENVIRONMENT
                && value.status() == SecretMetadataStatus.ENABLED)
            .orElseThrow(() -> new IamNotFoundException("secret metadata is not bindable"));
        Instant now = Instant.now(clock);
        SecretBinding binding = new SecretBinding(
            SecretBindingId.generate(), project.organizationId(), project.id(), environment.id(),
            metadata.id(), bindingKey, true, 0, now, now);
        repository.insertBinding(binding, principal.subject());
        auditPublisher.afterCommit(new IamAuditRecord(
            "secret.binding.create", principal.subject(), "secret-binding",
            binding.id().asString(), Optional.of(project.organizationId()),
            Optional.of(project.id()), "SUCCEEDED", now));
        return binding;
    }

    /**
     * @param principal     已认证主体
     * @param projectId     项目标识
     * @param environmentId 环境标识
     * @param cursor        可选游标
     * @param limit         页大小
     * @return Environment Binding 游标页
     */
    @Transactional(readOnly = true)
    public CursorPage<SecretBinding> listBindings(
        AgentArkPrincipal principal,
        ProjectId projectId,
        EnvironmentId environmentId,
        String cursor,
        int limit) {
        authorize(principal, projectId, PermissionRegistry.SECRET_READ);
        tenantRepository.findEnvironment(environmentId)
            .filter(value -> value.projectId().equals(projectId))
            .orElseThrow(() -> new IamNotFoundException("environment is not visible"));
        int pageSize = requireLimit(limit);
        List<SecretBinding> loaded = repository.listBindings(
            projectId, environmentId, decode(cursor), pageSize + 1);
        boolean hasMore = loaded.size() > pageSize;
        List<SecretBinding> items = loaded.stream().limit(pageSize).toList();
        Optional<String> next = hasMore
            ? Optional.of(encode(items.get(items.size() - 1).bindingKey()))
            : Optional.empty();
        return new CursorPage<>(items, next, hasMore);
    }

    /**
     * @param principal  已认证主体
     * @param projectId  项目标识
     * @param permission 必需权限
     * @return 已授权项目
     */
    private Project authorize(
        AgentArkPrincipal principal, ProjectId projectId, String permission) {
        Project project = tenantRepository.findProject(projectId)
            .orElseThrow(() -> new IamNotFoundException("project is not visible"));
        authorizationService.requirePermission(
            principal, project.organizationId(), Optional.of(projectId), Optional.empty(),
            permission);
        return project;
    }

    /**
     * @param limit 请求页大小
     * @return 1 到 100 页大小
     */
    private int requireLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return limit;
    }

    /**
     * @param value 游标排序值
     * @return URL 安全游标
     */
    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param cursor 可选游标
     * @return 解码排序值
     */
    private String decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return "";
        }
        try {
            String value = new String(
                Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!value.matches("[a-z][a-z0-9-]{1,62}")) {
                throw new IllegalArgumentException("cursor value is invalid");
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("cursor is invalid", exception);
        }
    }
}
