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

import space.refinex.agentark.control.iam.domain.Environment;
import space.refinex.agentark.control.iam.domain.Organization;
import space.refinex.agentark.control.iam.domain.PrincipalKind;
import space.refinex.agentark.control.iam.domain.Project;
import space.refinex.agentark.kernel.id.EnvironmentId;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 定义 Organization、Project 与 Environment 聚合的租户显式持久化端口。
 *
 * @author refinex
 */
public interface TenantCatalogRepository {

    /**
     * 插入新组织。
     *
     * @param organization 待持久化组织
     */
    void insertOrganization(Organization organization);

    /**
     * 按标识读取组织。
     *
     * @param organizationId 组织标识
     * @return 存在时返回组织
     */
    Optional<Organization> findOrganization(OrganizationId organizationId);

    /**
     * 按全局 Slug 读取组织，供受控 Bootstrap 幂等判断使用。
     *
     * @param slug 组织 Slug
     * @return 存在时返回组织
     */
    Optional<Organization> findOrganizationBySlug(String slug);

    /**
     * 仅列出指定主体通过角色绑定可见的组织。
     *
     * @param principalKind 主体类别
     * @param principalId   主体 UUIDv7
     * @param limit         正数结果上限
     * @return 按 Slug 排序的可见组织
     */
    List<Organization> listOrganizationsForPrincipal(
        PrincipalKind principalKind, UUID principalId, int limit);

    /**
     * 插入新项目。
     *
     * @param project 待持久化项目
     */
    void insertProject(Project project);

    /**
     * 按标识读取项目。
     *
     * @param projectId 项目标识
     * @return 存在时返回项目
     */
    Optional<Project> findProject(ProjectId projectId);

    /**
     * 按组织和 Slug 读取项目。
     *
     * @param organizationId 组织标识
     * @param slug           项目 Slug
     * @return 存在时返回项目
     */
    Optional<Project> findProjectBySlug(OrganizationId organizationId, String slug);

    /**
     * 列出组织下由应用授权后可返回的项目。
     *
     * @param organizationId 组织标识
     * @param limit          正数结果上限
     * @return 按 Slug 排序的项目
     */
    List<Project> listProjects(OrganizationId organizationId, int limit);

    /**
     * 插入新环境。
     *
     * @param environment 待持久化环境
     */
    void insertEnvironment(Environment environment);

    /**
     * 按标识读取环境。
     *
     * @param environmentId 环境标识
     * @return 存在时返回环境
     */
    Optional<Environment> findEnvironment(EnvironmentId environmentId);

    /**
     * 按项目和 Key 读取环境。
     *
     * @param projectId 项目标识
     * @param key       环境 Key
     * @return 存在时返回环境
     */
    Optional<Environment> findEnvironmentByKey(ProjectId projectId, String key);

    /**
     * 列出项目下的环境。
     *
     * @param organizationId 组织标识
     * @param projectId      项目标识
     * @param limit          正数结果上限
     * @return 按 Key 排序的环境
     */
    List<Environment> listEnvironments(
        OrganizationId organizationId, ProjectId projectId, int limit);
}
