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
import space.refinex.agentark.control.iam.adapter.out.persistence.IamPersistenceRows.PermissionRow;
import space.refinex.agentark.control.iam.adapter.out.persistence.IamPersistenceRows.RoleBindingRow;
import space.refinex.agentark.control.iam.adapter.out.persistence.IamPersistenceRows.RoleRow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 执行角色、权限和绑定的显式 Scope SQL；授权查询始终同时限定组织与主体。
 *
 * @author refinex
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AuthorizationMapper {

    /**
     * 列出 Flyway 注册权限。
     *
     * @return 按权限键排序的数据库行
     */
    @Select("""
        SELECT id, permission_key AS `key`, description, risk_level, created_at
        FROM permission
        ORDER BY permission_key
        """)
    List<PermissionRow> listPermissions();

    /**
     * 插入角色行。
     *
     * @param row 角色数据库行
     */
    @Insert("""
        INSERT INTO role
            (id, organization_id, project_id, role_key, name, built_in, status, version,
             created_at, created_by, updated_at, updated_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{key}, #{name}, #{builtIn}, #{status}, #{version},
             #{createdAt}, 'agentark-control', #{updatedAt}, 'agentark-control')
        """)
    void insertRole(RoleRow row);

    /**
     * 将已注册权限关联到角色。
     *
     * @param roleId        角色 UUIDv7
     * @param permissionKey 已注册权限键
     * @param createdAt     创建时刻
     */
    @Insert("""
        INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
        SELECT #{roleId,jdbcType=BINARY}, id, #{createdAt}, 'agentark-control'
        FROM permission
        WHERE permission_key = #{permissionKey}
        """)
    void insertRolePermission(
        @Param("roleId") UUID roleId,
        @Param("permissionKey") String permissionKey,
        @Param("createdAt") Instant createdAt);

    /**
     * 按主键读取角色。
     *
     * @param id 角色 UUIDv7
     * @return 角色行或空
     */
    @Select("""
        SELECT id, organization_id, project_id, role_key AS `key`, name, built_in,
               status, version, created_at, updated_at
        FROM role
        WHERE id = #{id,jdbcType=BINARY}
        """)
    Optional<RoleRow> findRole(UUID id);

    /**
     * 按组织、项目 Scope 和角色键读取角色。
     *
     * @param organizationId 组织 UUIDv7
     * @param projectId      可空项目 UUIDv7
     * @param key            角色键
     * @return 角色行或空
     */
    @Select("""
        <script>
        SELECT id, organization_id, project_id, role_key AS `key`, name, built_in,
               status, version, created_at, updated_at
        FROM role
        WHERE organization_id = #{organizationId,jdbcType=BINARY}
          AND role_key = #{key}
          <choose>
            <when test="projectId != null">AND project_id = #{projectId,jdbcType=BINARY}</when>
            <otherwise>AND project_id IS NULL</otherwise>
          </choose>
        </script>
        """)
    Optional<RoleRow> findRoleByKey(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("key") String key);

    /**
     * 列出组织级和目标项目级活动角色。
     *
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param limit          结果上限
     * @return 角色行
     */
    @Select("""
        SELECT id, organization_id, project_id, role_key AS `key`, name, built_in,
               status, version, created_at, updated_at
        FROM role
        WHERE organization_id = #{organizationId,jdbcType=BINARY}
          AND (project_id IS NULL OR project_id = #{projectId,jdbcType=BINARY})
          AND status = 'ACTIVE'
        ORDER BY project_id, role_key, id
        LIMIT #{limit}
        """)
    List<RoleRow> listRoles(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("limit") int limit);

    /**
     * 读取角色关联的权限键。
     *
     * @param roleId 角色 UUIDv7
     * @return 权限键列表
     */
    @Select("""
        SELECT p.permission_key
        FROM role_permission rp
        JOIN permission p ON p.id = rp.permission_id
        WHERE rp.role_id = #{roleId,jdbcType=BINARY}
        ORDER BY p.permission_key
        """)
    List<String> listRolePermissions(UUID roleId);

    /**
     * 插入角色绑定行。
     *
     * @param row 角色绑定数据库行
     */
    @Insert("""
        INSERT INTO role_binding
            (id, organization_id, project_id, role_id, principal_type, principal_id,
             scope_type, scope_id, version, created_at, created_by, updated_at, updated_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{roleId,jdbcType=BINARY}, #{principalType},
             #{principalId,jdbcType=BINARY}, #{scopeType}, #{scopeId,jdbcType=BINARY},
             #{version}, #{createdAt}, 'agentark-control', #{updatedAt}, 'agentark-control')
        """)
    void insertRoleBinding(RoleBindingRow row);

    /**
     * 列出组织级与目标项目级绑定。
     *
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param limit          结果上限
     * @return 绑定行
     */
    @Select("""
        SELECT id, organization_id, project_id, role_id, principal_type, principal_id,
               scope_type, scope_id, version, created_at, updated_at
        FROM role_binding
        WHERE organization_id = #{organizationId,jdbcType=BINARY}
          AND (project_id IS NULL OR project_id = #{projectId,jdbcType=BINARY})
        ORDER BY created_at, id
        LIMIT #{limit}
        """)
    List<RoleBindingRow> listRoleBindings(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("limit") int limit);

    /**
     * 查询主体在组织、项目和可选环境继承链上的有效权限。
     *
     * @param organizationId 组织 UUIDv7
     * @param projectId      可空项目 UUIDv7
     * @param environmentId  可空环境 UUIDv7
     * @param principalType  主体类型代码
     * @param principalId    主体 UUIDv7
     * @return 去重权限键
     */
    @Select("""
        <script>
        SELECT DISTINCT p.permission_key
        FROM role_binding rb
        JOIN role r ON r.id = rb.role_id AND r.status = 'ACTIVE'
        JOIN role_permission rp ON rp.role_id = r.id
        JOIN permission p ON p.id = rp.permission_id
        WHERE rb.organization_id = #{organizationId,jdbcType=BINARY}
          AND rb.principal_type = #{principalType}
          AND rb.principal_id = #{principalId,jdbcType=BINARY}
          AND (
            (rb.scope_type = 'ORGANIZATION' AND rb.scope_id = #{organizationId,jdbcType=BINARY})
            <if test="projectId != null">
              OR (rb.scope_type = 'PROJECT' AND rb.scope_id = #{projectId,jdbcType=BINARY})
            </if>
            <if test="environmentId != null">
              OR (rb.scope_type = 'ENVIRONMENT' AND rb.scope_id = #{environmentId,jdbcType=BINARY})
            </if>
          )
        ORDER BY p.permission_key
        </script>
        """)
    List<String> findEffectivePermissions(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("environmentId") UUID environmentId,
        @Param("principalType") String principalType,
        @Param("principalId") UUID principalId);
}
