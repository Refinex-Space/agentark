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

package space.refinex.agentark.control.secret.application.port;

import space.refinex.agentark.control.secret.domain.SecretBinding;
import space.refinex.agentark.control.secret.domain.SecretMetadata;
import space.refinex.agentark.kernel.id.EnvironmentId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.SecretMetadataId;
import space.refinex.agentark.kernel.ref.SecretRef;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

/**
 * 定义 Secret 非敏感元数据与 Environment Binding 的显式 Project Scope 持久化端口。
 *
 * @author refinex
 */
public interface SecretRepository {

    /**
     * @param metadata 待插入元数据
     * @param actor    创建主体
     */
    void insertMetadata(SecretMetadata metadata, String actor);

    /**
     * @param projectId 项目标识
     * @param id        元数据标识
     * @return 同项目元数据
     */
    Optional<SecretMetadata> findMetadata(ProjectId projectId, SecretMetadataId id);

    /**
     * 使用项目范围、当前状态和乐观锁更新外部版本或生命周期状态。
     *
     * @param projectId 项目标识
     * @param id 元数据标识
     * @param currentStatus 预期当前状态
     * @param targetStatus 目标状态
     * @param externalVersion 目标外部版本；保持不变时传当前值
     * @param expectedVersion 预期乐观锁版本
     * @param actor 操作主体
     * @param updatedAt 更新时间
     * @return 更新行数，前置条件不满足时为零
     */
    int updateMetadata(
        ProjectId projectId,
        SecretMetadataId id,
        String currentStatus,
        String targetStatus,
        String externalVersion,
        long expectedVersion,
        String actor,
        Instant updatedAt);

    /**
     * @param projectId 项目标识
     * @param key       稳定 Key
     * @return 启用的项目 Scope 元数据
     */
    Optional<SecretMetadata> findEnabledProjectMetadata(ProjectId projectId, String key);

    /**
     * @param projectId 项目标识
     * @param afterKey  游标 Key
     * @param limit     读取上限
     * @return 按 Key 排序元数据
     */
    List<SecretMetadata> listMetadata(ProjectId projectId, String afterKey, int limit);

    /**
     * @param binding 待插入绑定
     * @param actor   创建主体
     */
    void insertBinding(SecretBinding binding, String actor);

    /**
     * @param projectId     项目标识
     * @param environmentId 环境标识
     * @param bindingKey    绑定 Key
     * @return 活动绑定
     */
    Optional<SecretBinding> findActiveBinding(
        ProjectId projectId, EnvironmentId environmentId, String bindingKey);

    /**
     * @param projectId     项目标识
     * @param environmentId 环境标识
     * @param afterKey      游标 Key
     * @param limit         读取上限
     * @return 按绑定 Key 排序的绑定
     */
    List<SecretBinding> listBindings(
        ProjectId projectId, EnvironmentId environmentId, String afterKey, int limit);

    /**
     * @param projectId 项目标识
     * @param ref       SecretRef
     * @return 引用是否解析到同项目启用元数据或活动绑定
     */
    boolean existsReference(ProjectId projectId, SecretRef ref);
}
