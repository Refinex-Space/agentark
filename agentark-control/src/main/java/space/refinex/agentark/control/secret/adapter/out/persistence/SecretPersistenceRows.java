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

import java.time.Instant;
import java.util.UUID;

/**
 * 集中定义 Secret MyBatis 行对象，不向 Public API 暴露。
 *
 * @author refinex
 */
final class SecretPersistenceRows {

    /**
     * 禁止实例化行对象容器。
     */
    private SecretPersistenceRows() {
    }

    /**
     * @param id              主键
     * @param organizationId  组织
     * @param projectId       项目
     * @param secretKey       稳定 Key
     * @param name            名称
     * @param provider        外部 Provider 类型
     * @param externalPath    外部路径
     * @param externalVersion 外部版本
     * @param secretScope     Secret 作用域
     * @param status          状态
     * @param version         乐观锁
     * @param createdAt       创建时刻
     * @param updatedAt       更新时间
     * @author refinex
     */
    record MetadataRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String secretKey,
        String name,
        String provider,
        String externalPath,
        String externalVersion,
        String secretScope,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {
    }

    /**
     * @param id               主键
     * @param organizationId   组织
     * @param projectId        项目
     * @param environmentId    环境
     * @param secretMetadataId 元数据
     * @param bindingKey       绑定 Key
     * @param status           状态
     * @param version          乐观锁
     * @param createdAt        创建时刻
     * @param updatedAt        更新时间
     * @author refinex
     */
    record BindingRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        UUID environmentId,
        UUID secretMetadataId,
        String bindingKey,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {
    }
}
