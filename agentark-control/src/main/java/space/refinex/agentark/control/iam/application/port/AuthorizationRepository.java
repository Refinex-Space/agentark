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

import space.refinex.agentark.control.iam.domain.*;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.RoleId;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 定义权限注册、角色、绑定和有效权限查询的持久化端口。
 *
 * @author refinex
 */
public interface AuthorizationRepository {

    /**
     * 读取数据库中的权限注册项。
     *
     * @return 按权限键排序的注册项
     */
    List<Permission> listPermissions();

    /**
     * 原子插入角色及其权限关联。
     *
     * @param role 待持久化角色
     */
    void insertRole(Role role);

    /**
     * 按标识读取角色及其权限。
     *
     * @param roleId 角色标识
     * @return 存在时返回角色
     */
    Optional<Role> findRole(RoleId roleId);

    /**
     * 按组织、可选项目和角色键读取角色。
     *
     * @param organizationId 组织标识
     * @param projectId      可选项目
     * @param key            角色键
     * @return 存在时返回角色
     */
    Optional<Role> findRoleByKey(
        OrganizationId organizationId, Optional<ProjectId> projectId, String key);

    /**
     * 列出项目可用的组织级与项目级角色。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param limit          正数结果上限
     * @return 按 Scope 和 Key 排序的角色
     */
    List<Role> listRoles(OrganizationId organizationId, ProjectId projectId, int limit);

    /**
     * 插入 Scope-aware 角色绑定。
     *
     * @param binding 待持久化绑定
     */
    void insertRoleBinding(RoleBinding binding);

    /**
     * 列出项目及组织继承范围内的角色绑定。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param limit          正数结果上限
     * @return 角色绑定列表
     */
    List<RoleBinding> listRoleBindings(
        OrganizationId organizationId, ProjectId projectId, int limit);

    /**
     * 查询主体在目标资源 Scope 上由活动角色产生的有效权限。
     *
     * @param organizationId 组织标识
     * @param projectId      可选项目
     * @param environmentId  可选环境 UUIDv7
     * @param principalKind  主体类别
     * @param principalId    主体 UUIDv7
     * @return 去重后的有效权限键
     */
    Set<String> findEffectivePermissions(
        OrganizationId organizationId,
        Optional<ProjectId> projectId,
        Optional<UUID> environmentId,
        PrincipalKind principalKind,
        UUID principalId);

    /**
     * 校验角色 Scope 能否绑定到目标资源 Scope。
     *
     * @param role           待绑定角色
     * @param scopeType      目标 Scope 类别
     * @param scopeId        目标 Scope 标识
     * @param organizationId 目标组织
     * @param projectId      目标可选项目
     * @return Scope 层级和归属一致时为 {@code true}
     */
    boolean supportsBinding(
        Role role,
        IamScopeType scopeType,
        UUID scopeId,
        OrganizationId organizationId,
        Optional<ProjectId> projectId);
}
