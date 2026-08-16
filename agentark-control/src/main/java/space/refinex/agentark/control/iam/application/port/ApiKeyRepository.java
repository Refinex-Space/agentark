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

package space.refinex.agentark.control.iam.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import space.refinex.agentark.control.iam.domain.ApiKey;
import space.refinex.agentark.kernel.id.ApiKeyId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

/**
 * 定义 API Key 摘要、Scope、到期和吊销元数据的持久化端口。
 *
 * @author refinex
 */
public interface ApiKeyRepository {

    /**
     * 原子插入 API Key 摘要和 Scope；禁止接收明文 Key。
     *
     * @param apiKey 只包含摘要的元数据
     */
    void insert(ApiKey apiKey);

    /**
     * 按公开前缀查找认证候选。
     *
     * @param prefix 12 字符公开前缀
     * @return 唯一候选或空
     */
    Optional<ApiKey> findByPrefix(String prefix);

    /**
     * 列出项目 API Key 的非秘密元数据。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param limit          正数结果上限
     * @return 不包含明文的 Key 元数据
     */
    List<ApiKey> list(OrganizationId organizationId, ProjectId projectId, int limit);

    /**
     * 使用乐观锁吊销 API Key。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param apiKeyId       API Key 标识
     * @param revokedAt      吊销时刻
     * @param expectedVersion 期望版本
     * @return 成功更新时为 {@code true}
     */
    boolean revoke(
        OrganizationId organizationId,
        ProjectId projectId,
        ApiKeyId apiKeyId,
        Instant revokedAt,
        long expectedVersion);
}
