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

import space.refinex.agentark.control.iam.application.port.AuthorizationRepository;
import space.refinex.agentark.control.iam.domain.*;
import space.refinex.agentark.kernel.id.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 使用 MyBatis 显式 Scope SQL 实现权限注册、角色、绑定和有效权限端口。
 *
 * @author refinex
 */
public final class MybatisAuthorizationRepository implements AuthorizationRepository {

    /**
     * 授权 Mapper。
     */
    private final AuthorizationMapper mapper;

    /**
     * 创建授权持久化适配器。
     *
     * @param mapper 授权 Mapper
     */
    public MybatisAuthorizationRepository(AuthorizationMapper mapper) {
        this.mapper = java.util.Objects.requireNonNull(mapper, "mapper must not be null");
    }

    /**
     * 读取权限注册项。
     *
     * @return 权限列表
     */
    @Override
    public List<Permission> listPermissions() {
        return mapper.listPermissions().stream()
            .map(row -> new Permission(new PermissionId(row.id()), row.key(), row.description(),
                PermissionRiskLevel.valueOf(row.riskLevel()), row.createdAt()))
            .toList();
    }

    /**
     * 插入角色及全部权限关联；外层应用事务保证原子性。
     *
     * @param role 待持久化角色
     */
    @Override
    public void insertRole(Role role) {
        mapper.insertRole(new IamPersistenceRows.RoleRow(
            role.id().value(), role.organizationId().value(),
            role.projectId().map(ProjectId::value).orElse(null), role.key(), role.name(),
            role.builtIn(), role.status().name(), role.version(), role.createdAt(), role.updatedAt()));
        role.permissionKeys().stream().sorted()
            .forEach(key -> mapper.insertRolePermission(role.id().value(), key, role.createdAt()));
    }

    /**
     * 按标识读取角色及权限。
     *
     * @param roleId 角色标识
     * @return 角色或空
     */
    @Override
    public Optional<Role> findRole(RoleId roleId) {
        return mapper.findRole(roleId.value()).map(this::role);
    }

    /**
     * 按 Scope 和 Key 读取角色。
     *
     * @param organizationId 组织标识
     * @param projectId      可选项目
     * @param key            角色键
     * @return 角色或空
     */
    @Override
    public Optional<Role> findRoleByKey(
        OrganizationId organizationId, Optional<ProjectId> projectId, String key) {
        return mapper.findRoleByKey(organizationId.value(),
                projectId.map(ProjectId::value).orElse(null), key)
            .map(this::role);
    }

    /**
     * 列出组织与项目可用角色。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param limit          结果上限
     * @return 角色列表
     */
    @Override
    public List<Role> listRoles(
        OrganizationId organizationId, ProjectId projectId, int limit) {
        return mapper.listRoles(organizationId.value(), projectId.value(), limit).stream()
            .map(this::role).toList();
    }

    /**
     * 插入角色绑定。
     *
     * @param binding 待持久化绑定
     */
    @Override
    public void insertRoleBinding(RoleBinding binding) {
        mapper.insertRoleBinding(new IamPersistenceRows.RoleBindingRow(
            binding.id().value(), binding.organizationId().value(),
            binding.projectId().map(ProjectId::value).orElse(null), binding.roleId().value(),
            binding.principalKind().name(), binding.principalId(), binding.scopeType().name(),
            binding.scopeId(), binding.version(), binding.createdAt(), binding.updatedAt()));
    }

    /**
     * 列出组织与项目范围绑定。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param limit          结果上限
     * @return 绑定列表
     */
    @Override
    public List<RoleBinding> listRoleBindings(
        OrganizationId organizationId, ProjectId projectId, int limit) {
        return mapper.listRoleBindings(organizationId.value(), projectId.value(), limit).stream()
            .map(this::binding).toList();
    }

    /**
     * 查询主体有效权限。
     *
     * @param organizationId 组织标识
     * @param projectId      可选项目
     * @param environmentId  可选环境 UUIDv7
     * @param principalKind  主体类别
     * @param principalId    主体 UUIDv7
     * @return 去重权限集合
     */
    @Override
    public Set<String> findEffectivePermissions(
        OrganizationId organizationId,
        Optional<ProjectId> projectId,
        Optional<UUID> environmentId,
        PrincipalKind principalKind,
        UUID principalId) {
        return Set.copyOf(mapper.findEffectivePermissions(
            organizationId.value(), projectId.map(ProjectId::value).orElse(null),
            environmentId.orElse(null), principalKind.name(), principalId));
    }

    /**
     * 校验角色与绑定 Scope 的组织和项目归属。
     *
     * @param role           角色
     * @param scopeType      目标 Scope 类型
     * @param scopeId        目标 Scope UUIDv7
     * @param organizationId 组织标识
     * @param projectId      可选项目
     * @return 层级一致时为 {@code true}
     */
    @Override
    public boolean supportsBinding(
        Role role,
        IamScopeType scopeType,
        UUID scopeId,
        OrganizationId organizationId,
        Optional<ProjectId> projectId) {
        if (!role.organizationId().equals(organizationId)) {
            return false;
        }
        if (scopeType == IamScopeType.ORGANIZATION) {
            return projectId.isEmpty()
                && role.projectId().isEmpty()
                && scopeId.equals(organizationId.value());
        }
        return projectId.isPresent()
            && role.projectId().map(projectId.orElseThrow()::equals).orElse(true);
    }

    /**
     * 转换角色行并读取规范化权限键。
     *
     * @param row 角色行
     * @return 角色领域对象
     */
    private Role role(IamPersistenceRows.RoleRow row) {
        return new Role(new RoleId(row.id()), new OrganizationId(row.organizationId()),
            Optional.ofNullable(row.projectId()).map(ProjectId::new), row.key(), row.name(),
            row.builtIn(), IamStatus.valueOf(row.status()),
            Set.copyOf(mapper.listRolePermissions(row.id())), row.version(), row.createdAt(),
            row.updatedAt());
    }

    /**
     * 转换角色绑定行。
     *
     * @param row 绑定行
     * @return 角色绑定领域对象
     */
    private RoleBinding binding(IamPersistenceRows.RoleBindingRow row) {
        return new RoleBinding(new RoleBindingId(row.id()),
            new OrganizationId(row.organizationId()),
            Optional.ofNullable(row.projectId()).map(ProjectId::new), new RoleId(row.roleId()),
            PrincipalKind.valueOf(row.principalType()), row.principalId(),
            IamScopeType.valueOf(row.scopeType()), row.scopeId(), row.version(), row.createdAt(),
            row.updatedAt());
    }
}
