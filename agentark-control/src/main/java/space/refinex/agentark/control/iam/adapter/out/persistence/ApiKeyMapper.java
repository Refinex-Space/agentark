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
import org.apache.ibatis.annotations.*;
import space.refinex.agentark.control.iam.adapter.out.persistence.IamPersistenceRows.ApiKeyRow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 执行 API Key 摘要和规范化 Scope SQL；接口从不接收或返回明文 Key。
 *
 * @author refinex
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface ApiKeyMapper {

    /**
     * 插入只含摘要的 API Key 行。
     *
     * @param row API Key 数据库行
     */
    @Insert("""
        INSERT INTO api_key
            (id, organization_id, project_id, service_account_id, name, prefix, digest,
             expires_at, revoked_at, version, created_at, created_by, updated_at, updated_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{serviceAccountId,jdbcType=BINARY},
             #{name}, #{prefix}, #{digest,jdbcType=BINARY}, #{expiresAt}, #{revokedAt},
             #{version}, #{createdAt}, 'agentark-control', #{updatedAt}, 'agentark-control')
        """)
    void insert(ApiKeyRow row);

    /**
     * 将已注册权限关联到 API Key。
     *
     * @param apiKeyId      API Key UUIDv7
     * @param permissionKey 权限键
     * @param createdAt     创建时刻
     */
    @Insert("""
        INSERT INTO api_key_scope (api_key_id, permission_id, created_at)
        SELECT #{apiKeyId,jdbcType=BINARY}, id, #{createdAt}
        FROM permission
        WHERE permission_key = #{permissionKey}
        """)
    void insertScope(
        @Param("apiKeyId") UUID apiKeyId,
        @Param("permissionKey") String permissionKey,
        @Param("createdAt") Instant createdAt);

    /**
     * 按公开前缀读取认证候选。
     *
     * @param prefix 公开前缀
     * @return 唯一候选或空
     */
    @Select("""
        SELECT id, organization_id, project_id, service_account_id, name, prefix, digest,
               expires_at, revoked_at, version, created_at, updated_at
        FROM api_key
        WHERE prefix = #{prefix}
        """)
    Optional<ApiKeyRow> findByPrefix(String prefix);

    /**
     * 按项目完整 Scope 列出 API Key 元数据。
     *
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param limit          结果上限
     * @return API Key 行
     */
    @Select("""
        SELECT id, organization_id, project_id, service_account_id, name, prefix, digest,
               expires_at, revoked_at, version, created_at, updated_at
        FROM api_key
        WHERE organization_id = #{organizationId,jdbcType=BINARY}
          AND project_id = #{projectId,jdbcType=BINARY}
        ORDER BY created_at DESC, id DESC
        LIMIT #{limit}
        """)
    List<ApiKeyRow> list(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("limit") int limit);

    /**
     * 读取 API Key 的规范化权限 Scope。
     *
     * @param apiKeyId API Key UUIDv7
     * @return 权限键列表
     */
    @Select("""
        SELECT p.permission_key
        FROM api_key_scope aks
        JOIN permission p ON p.id = aks.permission_id
        WHERE aks.api_key_id = #{apiKeyId,jdbcType=BINARY}
        ORDER BY p.permission_key
        """)
    List<String> listScopes(UUID apiKeyId);

    /**
     * 使用完整租户 Scope 和乐观锁版本吊销 Key。
     *
     * @param organizationId  组织 UUIDv7
     * @param projectId       项目 UUIDv7
     * @param apiKeyId        API Key UUIDv7
     * @param revokedAt       吊销时刻
     * @param expectedVersion 期望版本
     * @return 更新行数
     */
    @Update("""
        UPDATE api_key
        SET revoked_at = #{revokedAt}, updated_at = #{revokedAt},
            updated_by = 'agentark-control', version = version + 1
        WHERE id = #{apiKeyId,jdbcType=BINARY}
          AND organization_id = #{organizationId,jdbcType=BINARY}
          AND project_id = #{projectId,jdbcType=BINARY}
          AND revoked_at IS NULL
          AND version = #{expectedVersion}
        """)
    int revoke(
        @Param("organizationId") UUID organizationId,
        @Param("projectId") UUID projectId,
        @Param("apiKeyId") UUID apiKeyId,
        @Param("revokedAt") Instant revokedAt,
        @Param("expectedVersion") long expectedVersion);
}
