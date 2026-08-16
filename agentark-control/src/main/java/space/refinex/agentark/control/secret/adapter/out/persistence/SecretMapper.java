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

package space.refinex.agentark.control.secret.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import space.refinex.agentark.control.secret.adapter.out.persistence.SecretPersistenceRows.BindingRow;
import space.refinex.agentark.control.secret.adapter.out.persistence.SecretPersistenceRows.MetadataRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 执行 Secret Metadata 与 Binding 的显式 Project Scope SQL。
 *
 * @author refinex
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface SecretMapper {

    /**
     * @param row   Secret Metadata 行
     * @param actor 创建主体
     */
    @Insert("""
        INSERT INTO secret_metadata
            (id, organization_id, project_id, secret_key, name, provider, external_path,
             external_version, secret_scope, status, version, created_at, created_by,
             updated_at, updated_by)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.secretKey}, #{row.name}, #{row.provider},
             #{row.externalPath}, NULLIF(#{row.externalVersion}, ''), #{row.secretScope},
             #{row.status}, #{row.version}, #{row.createdAt}, #{actor}, #{row.updatedAt}, #{actor})
        """)
    void insertMetadata(@Param("row") MetadataRow row, @Param("actor") String actor);

    /**
     * @param projectId 项目 UUIDv7
     * @param id        元数据 UUIDv7
     * @return 同项目元数据
     */
    @Select("""
        SELECT id, organization_id, project_id, secret_key, name, provider, external_path,
               COALESCE(external_version, '') AS external_version, secret_scope, status, version,
               created_at, updated_at
        FROM secret_metadata
        WHERE project_id = #{projectId,jdbcType=BINARY} AND id = #{id,jdbcType=BINARY}
        """)
    Optional<MetadataRow> findMetadata(
        @Param("projectId") UUID projectId, @Param("id") UUID id);

    /**
     * @param projectId 项目 UUIDv7
     * @param key       稳定 Key
     * @return 启用项目 Scope 元数据
     */
    @Select("""
        SELECT id, organization_id, project_id, secret_key, name, provider, external_path,
               COALESCE(external_version, '') AS external_version, secret_scope, status, version,
               created_at, updated_at
        FROM secret_metadata
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND secret_key = #{key} AND secret_scope = 'PROJECT' AND status = 'ENABLED'
        """)
    Optional<MetadataRow> findEnabledProjectMetadata(
        @Param("projectId") UUID projectId, @Param("key") String key);

    /**
     * @param projectId 项目 UUIDv7
     * @param afterKey  游标 Key
     * @param limit     读取上限
     * @return 元数据行
     */
    @Select("""
        SELECT id, organization_id, project_id, secret_key, name, provider, external_path,
               COALESCE(external_version, '') AS external_version, secret_scope, status, version,
               created_at, updated_at
        FROM secret_metadata
        WHERE project_id = #{projectId,jdbcType=BINARY} AND secret_key > #{afterKey}
        ORDER BY secret_key, id
        LIMIT #{limit}
        """)
    List<MetadataRow> listMetadata(
        @Param("projectId") UUID projectId,
        @Param("afterKey") String afterKey,
        @Param("limit") int limit);

    /**
     * @param row   Secret Binding 行
     * @param actor 创建主体
     */
    @Insert("""
        INSERT INTO secret_binding
            (id, organization_id, project_id, environment_id, secret_metadata_id, binding_key,
             status, version, created_at, created_by, updated_at, updated_by)
        VALUES
            (#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY},
             #{row.projectId,jdbcType=BINARY}, #{row.environmentId,jdbcType=BINARY},
             #{row.secretMetadataId,jdbcType=BINARY}, #{row.bindingKey}, #{row.status},
             #{row.version}, #{row.createdAt}, #{actor}, #{row.updatedAt}, #{actor})
        """)
    void insertBinding(@Param("row") BindingRow row, @Param("actor") String actor);

    /**
     * @param projectId     项目 UUIDv7
     * @param environmentId 环境 UUIDv7
     * @param bindingKey    绑定 Key
     * @return 活动绑定
     */
    @Select("""
        SELECT id, organization_id, project_id, environment_id, secret_metadata_id, binding_key,
               status, version, created_at, updated_at
        FROM secret_binding
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND environment_id = #{environmentId,jdbcType=BINARY}
          AND binding_key = #{bindingKey} AND status = 'ACTIVE'
        """)
    Optional<BindingRow> findActiveBinding(
        @Param("projectId") UUID projectId,
        @Param("environmentId") UUID environmentId,
        @Param("bindingKey") String bindingKey);

    /**
     * @param projectId     项目 UUIDv7
     * @param environmentId 环境 UUIDv7
     * @param afterKey      游标 Key
     * @param limit         读取上限
     * @return 绑定行
     */
    @Select("""
        SELECT id, organization_id, project_id, environment_id, secret_metadata_id, binding_key,
               status, version, created_at, updated_at
        FROM secret_binding
        WHERE project_id = #{projectId,jdbcType=BINARY}
          AND environment_id = #{environmentId,jdbcType=BINARY}
          AND binding_key > #{afterKey}
        ORDER BY binding_key, id
        LIMIT #{limit}
        """)
    List<BindingRow> listBindings(
        @Param("projectId") UUID projectId,
        @Param("environmentId") UUID environmentId,
        @Param("afterKey") String afterKey,
        @Param("limit") int limit);
}

