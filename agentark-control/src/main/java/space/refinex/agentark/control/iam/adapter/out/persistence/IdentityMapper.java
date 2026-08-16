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
import space.refinex.agentark.control.iam.adapter.out.persistence.IamPersistenceRows.MembershipRow;
import space.refinex.agentark.control.iam.adapter.out.persistence.IamPersistenceRows.ServiceAccountRow;
import space.refinex.agentark.control.iam.adapter.out.persistence.IamPersistenceRows.UserIdentityRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 执行身份、服务账号和成员关系的显式 Scope SQL，所有租户查询都携带组织与项目。
 *
 * @author refinex
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface IdentityMapper {

    /**
     * 幂等插入外部身份映射；唯一键竞争只刷新最近认证时刻。
     *
     * @param row 身份数据库行
     */
    @Insert("""
        INSERT INTO user_identity
            (id, issuer, subject, display_name, email, status, last_seen_at,
             version, created_at, updated_at)
        VALUES
            (#{id,jdbcType=BINARY}, #{issuer}, #{subject}, #{displayName}, #{email}, #{status},
             #{lastSeenAt}, #{version}, #{createdAt}, #{updatedAt})
        ON DUPLICATE KEY UPDATE
            last_seen_at = VALUES(last_seen_at), updated_at = VALUES(updated_at)
        """)
    void upsertUserIdentity(UserIdentityRow row);

    /**
     * 按 Issuer 与 Subject 读取身份映射。
     *
     * @param issuer  Issuer
     * @param subject Subject
     * @return 身份行或空
     */
    @Select("""
        SELECT id, issuer, subject, display_name, email, status, last_seen_at,
               version, created_at, updated_at
        FROM user_identity
        WHERE issuer = #{issuer} AND subject = #{subject}
        """)
    Optional<UserIdentityRow> findUserIdentity(
        @Param("issuer") String issuer, @Param("subject") String subject);

    /**
     * 插入服务账号行。
     *
     * @param row 服务账号数据库行
     */
    @Insert("""
        INSERT INTO service_account
            (id, organization_id, project_id, name, status, version,
             created_at, created_by, updated_at, updated_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{name}, #{status}, #{version},
             #{createdAt}, 'agentark-control', #{updatedAt}, 'agentark-control')
        """)
    void insertServiceAccount(ServiceAccountRow row);

    /**
     * 按主键读取活动服务账号。
     *
     * @param id 服务账号 UUIDv7
     * @return 服务账号行或空
     */
    @Select("""
        SELECT id, organization_id, project_id, name, status, version, created_at, updated_at
        FROM service_account
        WHERE id = #{id,jdbcType=BINARY} AND status = 'ACTIVE'
        """)
    Optional<ServiceAccountRow> findServiceAccount(UUID id);

    /**
     * 按完整租户 Scope 列出服务账号。
     *
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param limit          结果上限
     * @return 服务账号行
     */
    @Select("""
        SELECT id, organization_id, project_id, name, status, version, created_at, updated_at
        FROM service_account
        WHERE organization_id = #{organizationId,jdbcType=BINARY}
          AND project_id = #{projectId,jdbcType=BINARY}
          AND status = 'ACTIVE'
        ORDER BY name, id
        LIMIT #{limit}
        """)
    List<ServiceAccountRow> listServiceAccounts(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("limit") int limit);

    /**
     * 统计活动用户身份。
     *
     * @param id 用户身份 UUIDv7
     * @return 存在时为 1
     */
    @Select("SELECT COUNT(*) FROM user_identity WHERE id = #{id,jdbcType=BINARY} AND status = 'ACTIVE'")
    int countActiveUserIdentity(UUID id);

    /**
     * 统计活动服务账号。
     *
     * @param id 服务账号 UUIDv7
     * @return 存在时为 1
     */
    @Select("SELECT COUNT(*) FROM service_account WHERE id = #{id,jdbcType=BINARY} AND status = 'ACTIVE'")
    int countActiveServiceAccount(UUID id);

    /**
     * 插入项目成员关系。
     *
     * @param row 成员关系数据库行
     */
    @Insert("""
        INSERT INTO membership
            (id, organization_id, project_id, principal_type, principal_id, status, version,
             created_at, created_by, updated_at, updated_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{principalType}, #{principalId,jdbcType=BINARY},
             #{status}, #{version}, #{createdAt}, 'agentark-control',
             #{updatedAt}, 'agentark-control')
        """)
    void insertMembership(MembershipRow row);

    /**
     * 判断主体是否为目标项目活动成员。
     *
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param principalType  主体类型代码
     * @param principalId    主体 UUIDv7
     * @return 活动成员数量
     */
    @Select("""
        SELECT COUNT(*)
        FROM membership
        WHERE organization_id = #{organizationId,jdbcType=BINARY}
          AND project_id = #{projectId,jdbcType=BINARY}
          AND principal_type = #{principalType}
          AND principal_id = #{principalId,jdbcType=BINARY}
          AND status = 'ACTIVE'
        """)
    int countActiveMembership(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("principalType") String principalType,
        @Param("principalId") UUID principalId);

    /**
     * 按完整租户 Scope 列出成员关系。
     *
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param limit          结果上限
     * @return 成员关系行
     */
    @Select("""
        SELECT id, organization_id, project_id, principal_type, principal_id, status,
               version, created_at, updated_at
        FROM membership
        WHERE organization_id = #{organizationId,jdbcType=BINARY}
          AND project_id = #{projectId,jdbcType=BINARY}
        ORDER BY created_at, id
        LIMIT #{limit}
        """)
    List<MembershipRow> listMemberships(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("limit") int limit);
}
