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

import space.refinex.agentark.control.iam.application.AuthorizationCacheKey;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.util.Optional;
import java.util.Set;

/**
 * 定义短 TTL 有效权限缓存；MySQL 始终是授权事实源。
 *
 * @author refinex
 */
public interface AuthorizationCache {

    /**
     * 读取尚未过期的权限集合。
     *
     * @param key 复合授权键
     * @return 缓存未命中或过期时为空
     */
    Optional<Set<String>> get(AuthorizationCacheKey key);

    /**
     * 使用实现固定的短 TTL 写入权限集合。
     *
     * @param key         复合授权键
     * @param permissions 不可包含 Secret 的权限键集合
     */
    void put(AuthorizationCacheKey key, Set<String> permissions);

    /**
     * 失效组织内全部缓存，供组织级角色或绑定变化使用。
     *
     * @param organizationId 组织标识
     */
    void evictOrganization(OrganizationId organizationId);

    /**
     * 失效项目及其环境缓存，供成员关系、项目角色或 API Key 变化使用。
     *
     * @param projectId 项目标识
     */
    void evictProject(ProjectId projectId);
}
