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

import space.refinex.agentark.control.iam.application.port.TenantCatalogRepository;
import space.refinex.agentark.control.iam.domain.*;
import space.refinex.agentark.kernel.id.EnvironmentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 使用显式 Scope Mapper 实现租户目录 Repository，并隔离数据库行与领域对象。
 *
 * @author refinex
 */
public final class MybatisTenantCatalogRepository implements TenantCatalogRepository {

    /**
     * 租户目录 Mapper。
     */
    private final TenantCatalogMapper mapper;

    /**
     * 创建租户目录适配器。
     *
     * @param mapper 显式 Scope Mapper
     */
    public MybatisTenantCatalogRepository(TenantCatalogMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    /**
     * 插入组织领域对象。
     *
     * @param organization 待持久化组织
     */
    @Override
    public void insertOrganization(Organization organization) {
        mapper.insertOrganization(new IamPersistenceRows.OrganizationRow(
            organization.id().value(), organization.slug(), organization.name(),
            organization.status().name(), organization.version(), organization.createdAt(),
            organization.updatedAt()));
    }

    /**
     * 按标识读取并转换组织。
     *
     * @param organizationId 组织标识
     * @return 组织或空
     */
    @Override
    public Optional<Organization> findOrganization(OrganizationId organizationId) {
        return mapper.findOrganization(organizationId.value()).map(this::organization);
    }

    /**
     * 按 Slug 读取并转换组织。
     *
     * @param slug 组织 Slug
     * @return 组织或空
     */
    @Override
    public Optional<Organization> findOrganizationBySlug(String slug) {
        return mapper.findOrganizationBySlug(slug).map(this::organization);
    }

    /**
     * 仅返回主体绑定可见组织。
     *
     * @param principalKind 主体类别
     * @param principalId   主体 UUIDv7
     * @param limit         结果上限
     * @return 可见组织
     */
    @Override
    public List<Organization> listOrganizationsForPrincipal(PrincipalKind principalKind, UUID principalId, int limit) {
        return mapper.listOrganizationsForPrincipal(principalKind.name(), principalId, limit).stream()
            .map(this::organization)
            .toList();
    }

    /**
     * 插入项目领域对象。
     *
     * @param project 待持久化项目
     */
    @Override
    public void insertProject(Project project) {
        mapper.insertProject(new IamPersistenceRows.ProjectRow(
            project.id().value(), project.organizationId().value(), project.slug(), project.name(),
            project.status().name(), project.version(), project.createdAt(), project.updatedAt()));
    }

    /**
     * 按标识读取项目。
     *
     * @param projectId 项目标识
     * @return 项目或空
     */
    @Override
    public Optional<Project> findProject(ProjectId projectId) {
        return mapper.findProject(projectId.value()).map(this::project);
    }

    /**
     * 按组织与 Slug 读取项目。
     *
     * @param organizationId 组织标识
     * @param slug           项目 Slug
     * @return 项目或空
     */
    @Override
    public Optional<Project> findProjectBySlug(OrganizationId organizationId, String slug) {
        return mapper.findProjectBySlug(organizationId.value(), slug).map(this::project);
    }

    /**
     * 列出组织下项目。
     *
     * @param organizationId 组织标识
     * @param limit          结果上限
     * @return 项目列表
     */
    @Override
    public List<Project> listProjects(OrganizationId organizationId, int limit) {
        return mapper.listProjects(organizationId.value(), limit).stream()
            .map(this::project).toList();
    }

    /**
     * 插入环境领域对象。
     *
     * @param environment 待持久化环境
     */
    @Override
    public void insertEnvironment(Environment environment) {
        mapper.insertEnvironment(new IamPersistenceRows.EnvironmentRow(
            environment.id().value(), environment.organizationId().value(),
            environment.projectId().value(), environment.key(), environment.name(),
            environment.status().name(), environment.version(), environment.createdAt(),
            environment.updatedAt()));
    }

    /**
     * 按标识读取环境。
     *
     * @param environmentId 环境标识
     * @return 环境或空
     */
    @Override
    public Optional<Environment> findEnvironment(EnvironmentId environmentId) {
        return mapper.findEnvironment(environmentId.value()).map(this::environment);
    }

    /**
     * 按项目与 Key 读取环境。
     *
     * @param projectId 项目标识
     * @param key       环境 Key
     * @return 环境或空
     */
    @Override
    public Optional<Environment> findEnvironmentByKey(ProjectId projectId, String key) {
        return mapper.findEnvironmentByKey(projectId.value(), key).map(this::environment);
    }

    /**
     * 按完整租户 Scope 列出环境。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param limit          结果上限
     * @return 环境列表
     */
    @Override
    public List<Environment> listEnvironments(OrganizationId organizationId, ProjectId projectId, int limit) {
        return mapper.listEnvironments(organizationId.value(), projectId.value(), limit).stream()
            .map(this::environment)
            .toList();
    }

    /**
     * 将组织数据库行转换为领域对象。
     *
     * @param row 数据库行
     * @return 组织领域对象
     */
    private Organization organization(IamPersistenceRows.OrganizationRow row) {
        return new Organization(new OrganizationId(row.id()), row.slug(), row.name(),
            IamStatus.valueOf(row.status()), row.version(), row.createdAt(), row.updatedAt());
    }

    /**
     * 将项目数据库行转换为领域对象。
     *
     * @param row 数据库行
     * @return 项目领域对象
     */
    private Project project(IamPersistenceRows.ProjectRow row) {
        return new Project(new ProjectId(row.id()), new OrganizationId(row.organizationId()),
            row.slug(), row.name(), IamStatus.valueOf(row.status()), row.version(),
            row.createdAt(), row.updatedAt());
    }

    /**
     * 将环境数据库行转换为领域对象。
     *
     * @param row 数据库行
     * @return 环境领域对象
     */
    private Environment environment(IamPersistenceRows.EnvironmentRow row) {
        return new Environment(new EnvironmentId(row.id()),
            new OrganizationId(row.organizationId()), new ProjectId(row.projectId()), row.key(),
            row.name(), IamStatus.valueOf(row.status()), row.version(), row.createdAt(),
            row.updatedAt());
    }
}
