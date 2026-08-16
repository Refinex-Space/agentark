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

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import space.refinex.agentark.control.iam.adapter.out.persistence.IamPersistenceRows.EnvironmentRow;
import space.refinex.agentark.control.iam.adapter.out.persistence.IamPersistenceRows.OrganizationRow;
import space.refinex.agentark.control.iam.adapter.out.persistence.IamPersistenceRows.ProjectRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 执行租户目录的显式 Scope SQL；所有查询自身携带授权边界，故不依赖隐式 Tenant 插件。
 *
 * @author refinex
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface TenantCatalogMapper {

    /**
     * 插入组织行。
     *
     * @param row 组织数据库行
     */
    @Insert("""
        INSERT INTO organization
            (id, slug, name, status, version, created_at, created_by, updated_at, updated_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{slug}, #{name}, #{status}, #{version},
             #{createdAt}, 'agentark-control', #{updatedAt}, 'agentark-control')
        """)
    void insertOrganization(OrganizationRow row);

    /**
     * 按主键读取组织。
     *
     * @param id 组织 UUIDv7
     * @return 组织行或空
     */
    @Select("""
        SELECT id, slug, name, status, version, created_at, updated_at
        FROM organization
        WHERE id = #{id,jdbcType=BINARY}
        """)
    Optional<OrganizationRow> findOrganization(UUID id);

    /**
     * 按全局 Slug 读取组织。
     *
     * @param slug 组织 Slug
     * @return 组织行或空
     */
    @Select("""
        SELECT id, slug, name, status, version, created_at, updated_at
        FROM organization
        WHERE slug = #{slug}
        """)
    Optional<OrganizationRow> findOrganizationBySlug(String slug);

    /**
     * 只列出主体通过活动角色绑定可见的组织。
     *
     * @param principalType 主体类型代码
     * @param principalId   主体 UUIDv7
     * @param limit         结果上限
     * @return 可见组织行
     */
    @Select("""
        SELECT DISTINCT o.id, o.slug, o.name, o.status, o.version, o.created_at, o.updated_at
        FROM organization o
        JOIN role_binding rb ON rb.organization_id = o.id
        JOIN role r ON r.id = rb.role_id AND r.status = 'ACTIVE'
        WHERE rb.principal_type = #{principalType}
          AND rb.principal_id = #{principalId,jdbcType=BINARY}
          AND o.status = 'ACTIVE'
        ORDER BY o.slug, o.id
        LIMIT #{limit}
        """)
    List<OrganizationRow> listOrganizationsForPrincipal(
        @Param("principalType") String principalType,
        @Param("principalId") UUID principalId,
        @Param("limit") int limit);

    /**
     * 插入项目行。
     *
     * @param row 项目数据库行
     */
    @Insert("""
        INSERT INTO project
            (id, organization_id, slug, name, status, version,
             created_at, created_by, updated_at, updated_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY}, #{slug}, #{name},
             #{status}, #{version}, #{createdAt}, 'agentark-control',
             #{updatedAt}, 'agentark-control')
        """)
    void insertProject(ProjectRow row);

    /**
     * 按主键读取项目。
     *
     * @param id 项目 UUIDv7
     * @return 项目行或空
     */
    @Select("""
        SELECT id, organization_id, slug, name, status, version, created_at, updated_at
        FROM project
        WHERE id = #{id,jdbcType=BINARY}
        """)
    Optional<ProjectRow> findProject(UUID id);

    /**
     * 按组织与 Slug 读取项目。
     *
     * @param organizationId 组织 UUIDv7
     * @param slug           项目 Slug
     * @return 项目行或空
     */
    @Select("""
        SELECT id, organization_id, slug, name, status, version, created_at, updated_at
        FROM project
        WHERE organization_id = #{organizationId,jdbcType=BINARY} AND slug = #{slug}
        """)
    Optional<ProjectRow> findProjectBySlug(
        @Param("organizationId") UUID organizationId, @Param("slug") String slug);

    /**
     * 列出组织下项目。
     *
     * @param organizationId 组织 UUIDv7
     * @param limit          结果上限
     * @return 项目行列表
     */
    @Select("""
        SELECT id, organization_id, slug, name, status, version, created_at, updated_at
        FROM project
        WHERE organization_id = #{organizationId,jdbcType=BINARY} AND status = 'ACTIVE'
        ORDER BY slug, id
        LIMIT #{limit}
        """)
    List<ProjectRow> listProjects(
        @Param("organizationId") UUID organizationId, @Param("limit") int limit);

    /**
     * 插入环境行。
     *
     * @param row 环境数据库行
     */
    @Insert("""
        INSERT INTO environment
            (id, organization_id, project_id, environment_key, name, status, version,
             created_at, created_by, updated_at, updated_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{key}, #{name}, #{status}, #{version},
             #{createdAt}, 'agentark-control', #{updatedAt}, 'agentark-control')
        """)
    void insertEnvironment(EnvironmentRow row);

    /**
     * 按主键读取环境。
     *
     * @param id 环境 UUIDv7
     * @return 环境行或空
     */
    @Select("""
        SELECT id, organization_id, project_id, environment_key AS `key`, name, status,
               version, created_at, updated_at
        FROM environment
        WHERE id = #{id,jdbcType=BINARY}
        """)
    Optional<EnvironmentRow> findEnvironment(UUID id);

    /**
     * 按项目与 Key 读取环境。
     *
     * @param projectId 项目 UUIDv7
     * @param key       环境 Key
     * @return 环境行或空
     */
    @Select("""
        SELECT id, organization_id, project_id, environment_key AS `key`, name, status,
               version, created_at, updated_at
        FROM environment
        WHERE project_id = #{projectId,jdbcType=BINARY} AND environment_key = #{key}
        """)
    Optional<EnvironmentRow> findEnvironmentByKey(
        @Param("projectId") UUID projectId, @Param("key") String key);

    /**
     * 按完整组织和项目 Scope 列出环境。
     *
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param limit          结果上限
     * @return 环境行列表
     */
    @Select("""
        SELECT id, organization_id, project_id, environment_key AS `key`, name, status,
               version, created_at, updated_at
        FROM environment
        WHERE organization_id = #{organizationId,jdbcType=BINARY}
          AND project_id = #{projectId,jdbcType=BINARY}
          AND status = 'ACTIVE'
        ORDER BY environment_key, id
        LIMIT #{limit}
        """)
    List<EnvironmentRow> listEnvironments(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("limit") int limit);
}
