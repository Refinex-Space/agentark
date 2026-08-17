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

package space.refinex.agentark.control.secret.adapter.out.persistence;

import space.refinex.agentark.control.secret.application.port.SecretRepository;
import space.refinex.agentark.control.secret.domain.*;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.SecretRef;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

/**
 * 使用 MyBatis 显式 Scope Mapper 实现 Secret Repository，并解析受控 SecretRef。
 *
 * @author refinex
 */
public final class MybatisSecretRepository implements SecretRepository {

    /**
     * Secret 元数据 Mapper。
     */
    private final SecretMapper mapper;

    /**
     * @param mapper Secret 元数据 Mapper
     */
    public MybatisSecretRepository(SecretMapper mapper) {
        this.mapper = java.util.Objects.requireNonNull(mapper, "mapper must not be null");
    }

    /**
     * @param metadata 待插入元数据
     * @param actor    创建主体
     */
    @Override
    public void insertMetadata(SecretMetadata metadata, String actor) {
        mapper.insertMetadata(new SecretPersistenceRows.MetadataRow(
            metadata.id().value(), metadata.organizationId().value(), metadata.projectId().value(),
            metadata.key(), metadata.name(), metadata.provider().name(), metadata.externalPath(),
            metadata.externalVersion(), metadata.scope().name(), metadata.status().name(),
            metadata.version(), metadata.createdAt(), metadata.updatedAt()), actor);
    }

    /**
     * @param projectId 项目标识
     * @param id        元数据标识
     * @return 同项目元数据
     */
    @Override
    public Optional<SecretMetadata> findMetadata(ProjectId projectId, SecretMetadataId id) {
        return mapper.findMetadata(projectId.value(), id.value()).map(this::metadata);
    }

    /**
     * 使用状态和乐观锁更新 Secret 元数据。
     *
     * @param projectId 项目标识
     * @param id 元数据标识
     * @param currentStatus 预期当前状态
     * @param targetStatus 目标状态
     * @param externalVersion 外部版本
     * @param expectedVersion 预期版本
     * @param actor 操作主体
     * @param updatedAt 更新时间
     * @return 更新行数
     */
    @Override
    public int updateMetadata(
        ProjectId projectId,
        SecretMetadataId id,
        String currentStatus,
        String targetStatus,
        String externalVersion,
        long expectedVersion,
        String actor,
        Instant updatedAt) {
        return mapper.updateMetadata(
            projectId.value(), id.value(), currentStatus, targetStatus, externalVersion,
            expectedVersion, actor, updatedAt);
    }

    /**
     * @param projectId 项目标识
     * @param key       稳定 Key
     * @return 启用项目 Scope 元数据
     */
    @Override
    public Optional<SecretMetadata> findEnabledProjectMetadata(ProjectId projectId, String key) {
        return mapper.findEnabledProjectMetadata(projectId.value(), key).map(this::metadata);
    }

    /**
     * @param projectId 项目标识
     * @param afterKey  游标 Key
     * @param limit     读取上限
     * @return 元数据列表
     */
    @Override
    public List<SecretMetadata> listMetadata(
        ProjectId projectId, String afterKey, int limit) {
        return mapper.listMetadata(projectId.value(), afterKey, limit).stream()
            .map(this::metadata).toList();
    }

    /**
     * @param binding 待插入绑定
     * @param actor   创建主体
     */
    @Override
    public void insertBinding(SecretBinding binding, String actor) {
        mapper.insertBinding(new SecretPersistenceRows.BindingRow(
            binding.id().value(), binding.organizationId().value(), binding.projectId().value(),
            binding.environmentId().value(), binding.secretMetadataId().value(),
            binding.bindingKey(), binding.active() ? "ACTIVE" : "DISABLED", binding.version(),
            binding.createdAt(), binding.updatedAt()), actor);
    }

    /**
     * @param projectId     项目标识
     * @param environmentId 环境标识
     * @param bindingKey    绑定 Key
     * @return 活动绑定
     */
    @Override
    public Optional<SecretBinding> findActiveBinding(
        ProjectId projectId, EnvironmentId environmentId, String bindingKey) {
        return mapper.findActiveBinding(
            projectId.value(), environmentId.value(), bindingKey).map(this::binding);
    }

    /**
     * @param projectId     项目标识
     * @param environmentId 环境标识
     * @param afterKey      游标 Key
     * @param limit         读取上限
     * @return 绑定列表
     */
    @Override
    public List<SecretBinding> listBindings(
        ProjectId projectId, EnvironmentId environmentId, String afterKey, int limit) {
        return mapper.listBindings(
                projectId.value(), environmentId.value(), afterKey, limit)
            .stream().map(this::binding).toList();
    }

    /**
     * @param projectId 项目标识
     * @param ref       SecretRef
     * @return 引用是否属于同项目且启用
     */
    @Override
    public boolean existsReference(ProjectId projectId, SecretRef ref) {
        String[] segments = ref.value().getPath().substring(1).split("/");
        if ("project".equals(ref.value().getAuthority()) && segments.length == 2) {
            try {
                if (!ProjectId.parse(segments[0]).equals(projectId)) {
                    return false;
                }
                return findEnabledProjectMetadata(projectId, segments[1]).isPresent();
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        if ("environment".equals(ref.value().getAuthority()) && segments.length == 2) {
            try {
                return findActiveBinding(
                    projectId, EnvironmentId.parse(segments[0]), segments[1]).isPresent();
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return false;
    }

    /**
     * @param row 元数据行
     * @return 领域元数据
     */
    private SecretMetadata metadata(SecretPersistenceRows.MetadataRow row) {
        return new SecretMetadata(
            new SecretMetadataId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), row.secretKey(), row.name(),
            SecretProviderType.valueOf(row.provider()), row.externalPath(), row.externalVersion(),
            SecretScope.valueOf(row.secretScope()), SecretMetadataStatus.valueOf(row.status()),
            row.version(), row.createdAt(), row.updatedAt());
    }

    /**
     * @param row 绑定行
     * @return 领域绑定
     */
    private SecretBinding binding(SecretPersistenceRows.BindingRow row) {
        return new SecretBinding(
            new SecretBindingId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new EnvironmentId(row.environmentId()),
            new SecretMetadataId(row.secretMetadataId()), row.bindingKey(),
            "ACTIVE".equals(row.status()), row.version(), row.createdAt(), row.updatedAt());
    }
}
