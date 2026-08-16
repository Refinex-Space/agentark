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

package space.refinex.agentark.control.catalog.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import space.refinex.agentark.control.catalog.adapter.out.persistence.CatalogPersistenceRows.AssetRow;
import space.refinex.agentark.control.catalog.adapter.out.persistence.CatalogPersistenceRows.ToolRow;
import space.refinex.agentark.control.catalog.adapter.out.persistence.CatalogPersistenceRows.VersionRow;
import space.refinex.agentark.control.catalog.domain.CatalogAssetKind;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 执行显式 Project Scope 的 Catalog SQL，并通过受控枚举阻止动态表名注入。
 *
 * @author refinex
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface CatalogMapper {

    /**
     * @param kind 受控资产分类
     * @param row 稳定身份数据库行
     */
    @InsertProvider(type = CatalogSqlProvider.class, method = "insertAsset")
    void insertAsset(@Param("kind") CatalogAssetKind kind, @Param("row") AssetRow row);

    /**
     * @param kind 资产分类
     * @param projectId 项目 UUIDv7
     * @param id 资产 UUIDv7
     * @return 同项目资产行
     */
    @SelectProvider(type = CatalogSqlProvider.class, method = "findAsset")
    Optional<AssetRow> findAsset(
        @Param("kind") CatalogAssetKind kind,
        @Param("projectId") UUID projectId,
        @Param("id") UUID id);

    /**
     * @param kind 资产分类
     * @param projectId 项目 UUIDv7
     * @param afterKey 游标 Key
     * @param limit 读取上限
     * @return 资产行
     */
    @SelectProvider(type = CatalogSqlProvider.class, method = "listAssets")
    List<AssetRow> listAssets(
        @Param("kind") CatalogAssetKind kind,
        @Param("projectId") UUID projectId,
        @Param("afterKey") String afterKey,
        @Param("limit") int limit);

    /**
     * @param kind 资产分类
     * @param projectId 项目 UUIDv7
     * @param id 资产 UUIDv7
     * @param expectedVersion 乐观锁版本
     * @param actor 更新主体
     * @param now 更新时间
     * @return 更新行数
     */
    @UpdateProvider(type = CatalogSqlProvider.class, method = "archiveAsset")
    int archiveAsset(
        @Param("kind") CatalogAssetKind kind,
        @Param("projectId") UUID projectId,
        @Param("id") UUID id,
        @Param("expectedVersion") long expectedVersion,
        @Param("actor") String actor,
        @Param("now") Instant now);

    /**
     * @param kind 资产分类
     * @param projectId 项目 UUIDv7
     * @param ownerId Owner UUIDv7
     * @return 存在时返回被锁定 Owner
     */
    @SelectProvider(type = CatalogSqlProvider.class, method = "lockAsset")
    Optional<UUID> lockAsset(
        @Param("kind") CatalogAssetKind kind,
        @Param("projectId") UUID projectId,
        @Param("ownerId") UUID ownerId);

    /**
     * @param kind 资产分类
     * @param projectId 项目 UUIDv7
     * @param ownerId Owner UUIDv7
     * @return 下一版本号
     */
    @SelectProvider(type = CatalogSqlProvider.class, method = "nextVersionNumber")
    long nextVersionNumber(
        @Param("kind") CatalogAssetKind kind,
        @Param("projectId") UUID projectId,
        @Param("ownerId") UUID ownerId);

    /**
     * @param kind 资产分类
     * @param row 版本数据库行
     */
    @InsertProvider(type = CatalogSqlProvider.class, method = "insertVersion")
    void insertVersion(@Param("kind") CatalogAssetKind kind, @Param("row") VersionRow row);

    /**
     * @param kind 资产分类
     * @param projectId 项目 UUIDv7
     * @param ownerId Owner UUIDv7
     * @param versionId 版本 UUIDv7
     * @return 同项目版本行
     */
    @SelectProvider(type = CatalogSqlProvider.class, method = "findVersion")
    Optional<VersionRow> findVersion(
        @Param("kind") CatalogAssetKind kind,
        @Param("projectId") UUID projectId,
        @Param("ownerId") UUID ownerId,
        @Param("versionId") UUID versionId);

    /**
     * @param kind 资产分类
     * @param projectId 项目 UUIDv7
     * @param ownerId Owner UUIDv7
     * @param afterVersionNumber 游标版本号
     * @param limit 读取上限
     * @return 不可变版本行
     */
    @SelectProvider(type = CatalogSqlProvider.class, method = "listVersions")
    List<VersionRow> listVersions(
        @Param("kind") CatalogAssetKind kind,
        @Param("projectId") UUID projectId,
        @Param("ownerId") UUID ownerId,
        @Param("afterVersionNumber") long afterVersionNumber,
        @Param("limit") int limit);

    /**
     * @param row MCP Tool Descriptor 行
     */
    @Insert("""
        INSERT INTO mcp_tool_descriptor
            (id, organization_id, project_id, mcp_server_version_id, tool_name, description,
             argument_schema, access_mode, risk_level, idempotency, permission_metadata,
             content_hash, created_at, created_by)
        VALUES
            (#{id,jdbcType=BINARY}, #{organizationId,jdbcType=BINARY},
             #{projectId,jdbcType=BINARY}, #{serverVersionId,jdbcType=BINARY}, #{toolName},
             #{description}, #{argumentSchemaJson}, #{accessMode}, #{riskLevel}, #{idempotency},
             #{permissionMetadataJson}, #{contentHash,jdbcType=BINARY}, #{createdAt}, #{createdBy})
        """)
    void insertTool(ToolRow row);
}

