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

package space.refinex.agentark.control.iam.adapter.out.persistence;

import space.refinex.agentark.control.iam.application.port.ApiKeyRepository;
import space.refinex.agentark.control.iam.domain.ApiKey;
import space.refinex.agentark.kernel.id.ApiKeyId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.ServiceAccountId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 使用 MyBatis 实现 API Key 摘要端口，数据库行与单次明文交付对象完全分离。
 *
 * @author refinex
 */
public final class MybatisApiKeyRepository implements ApiKeyRepository {

    /**
     * API Key 数据映射器。
     */
    private final ApiKeyMapper mapper;

    /**
     * 创建 API Key 持久化适配器。
     *
     * @param mapper API Key Mapper
     */
    public MybatisApiKeyRepository(ApiKeyMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    /**
     * 插入摘要元数据及规范化 Scope。
     *
     * @param apiKey 只含摘要的元数据
     */
    @Override
    public void insert(ApiKey apiKey) {
        mapper.insert(new IamPersistenceRows.ApiKeyRow(
            apiKey.id().value(), apiKey.organizationId().value(), apiKey.projectId().value(),
            apiKey.serviceAccountId().value(), apiKey.name(), apiKey.prefix(), apiKey.digest(),
            apiKey.expiresAt().orElse(null), apiKey.revokedAt().orElse(null), apiKey.version(),
            apiKey.createdAt(), apiKey.updatedAt()));

        apiKey.scopes().stream()
            .sorted()
            .forEach(scope -> mapper.insertScope(apiKey.id().value(), scope, apiKey.createdAt()));
    }

    /**
     * 按公开前缀读取认证候选。
     *
     * @param prefix 公开前缀
     * @return API Key 元数据或空
     */
    @Override
    public Optional<ApiKey> findByPrefix(String prefix) {
        return mapper.findByPrefix(prefix).map(this::apiKey);
    }

    /**
     * 按完整租户 Scope 列出 API Key。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param limit          结果上限
     * @return 非秘密元数据列表
     */
    @Override
    public List<ApiKey> list(OrganizationId organizationId, ProjectId projectId, int limit) {
        return mapper.list(organizationId.value(), projectId.value(), limit).stream()
            .map(this::apiKey)
            .toList();
    }

    /**
     * 使用乐观锁吊销 API Key。
     *
     * @param organizationId  组织标识
     * @param projectId       项目标识
     * @param apiKeyId        API Key 标识
     * @param revokedAt       吊销时刻
     * @param expectedVersion 期望版本
     * @return 更新成功时为 {@code true}
     */
    @Override
    public boolean revoke(OrganizationId organizationId, ProjectId projectId, ApiKeyId apiKeyId, Instant revokedAt, long expectedVersion) {
        return mapper.revoke(organizationId.value(), projectId.value(), apiKeyId.value(), revokedAt, expectedVersion) == 1;
    }

    /**
     * 转换 API Key 数据库行并读取规范化 Scope。
     *
     * @param row 数据库行
     * @return 只含摘要的领域对象
     */
    private ApiKey apiKey(IamPersistenceRows.ApiKeyRow row) {
        return new ApiKey(new ApiKeyId(row.id()), new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), new ServiceAccountId(row.serviceAccountId()),
            row.name(), row.prefix(), row.digest(), Set.copyOf(mapper.listScopes(row.id())),
            Optional.ofNullable(row.expiresAt()), Optional.ofNullable(row.revokedAt()),
            row.version(), row.createdAt(), row.updatedAt());
    }
}
