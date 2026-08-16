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

import space.refinex.agentark.control.catalog.application.port.CatalogRepository;
import space.refinex.agentark.control.catalog.domain.*;
import space.refinex.agentark.kernel.id.OrganizationId;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.StrongId;
import space.refinex.agentark.kernel.ref.Checksum;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 使用 MyBatis 显式 Scope Mapper 实现 Catalog Repository，并隔离数据库行对象。
 *
 * @author refinex
 */
public final class MybatisCatalogRepository implements CatalogRepository {

    /** 资产目录 Mapper。 */
    private final CatalogMapper mapper;

    /**
     * @param mapper 显式 Scope Mapper
     */
    public MybatisCatalogRepository(CatalogMapper mapper) {
        this.mapper = java.util.Objects.requireNonNull(mapper, "mapper must not be null");
    }

    /**
     * @param asset 待插入稳定身份
     * @param actor 创建主体稳定引用
     */
    @Override
    public void insertAsset(CatalogAsset asset, String actor) {
        mapper.insertAsset(asset.kind(), new CatalogPersistenceRows.AssetRow(
            asset.id().value(), asset.organizationId().value(), asset.projectId().value(),
            asset.key(), asset.name(), asset.description(), asset.metadataJson(),
            asset.status().name(), asset.version(), asset.createdAt(), actor, asset.updatedAt(),
            actor));
    }

    /**
     * @param kind 资产分类
     * @param projectId 项目标识
     * @param id 稳定身份
     * @return 同项目资产
     */
    @Override
    public Optional<CatalogAsset> findAsset(
        CatalogAssetKind kind, ProjectId projectId, StrongId id) {
        return mapper.findAsset(kind, projectId.value(), id.value())
            .map(row -> asset(kind, row));
    }

    /**
     * @param kind 资产分类
     * @param projectId 项目标识
     * @param afterKey 游标 Key
     * @param limit 读取上限
     * @return 按 Key 排序的资产
     */
    @Override
    public List<CatalogAsset> listAssets(
        CatalogAssetKind kind, ProjectId projectId, String afterKey, int limit) {
        return mapper.listAssets(kind, projectId.value(), afterKey, limit).stream()
            .map(row -> asset(kind, row)).toList();
    }

    /**
     * @param kind 资产分类
     * @param projectId 项目标识
     * @param id 稳定身份
     * @param expectedVersion 乐观锁版本
     * @param actor 操作主体
     * @param now 操作时刻
     * @return 更新行数
     */
    @Override
    public int archiveAsset(
        CatalogAssetKind kind,
        ProjectId projectId,
        StrongId id,
        long expectedVersion,
        String actor,
        Instant now) {
        return mapper.archiveAsset(
            kind, projectId.value(), id.value(), expectedVersion, actor, now);
    }

    /**
     * @param kind 资产分类
     * @param projectId 项目标识
     * @param ownerId 稳定身份
     * @return Owner 存在时下一版本号
     */
    @Override
    public Optional<Long> nextVersionNumber(
        CatalogAssetKind kind, ProjectId projectId, StrongId ownerId) {
        return mapper.lockAsset(kind, projectId.value(), ownerId.value())
            .map(ignored -> mapper.nextVersionNumber(kind, projectId.value(), ownerId.value()));
    }

    /**
     * @param version 待追加不可变版本
     * @param tools MCP Tool Descriptor 快照
     * @param actor 创建主体稳定引用
     */
    @Override
    public void insertVersion(
        CatalogVersion version, List<McpToolDescriptorSnapshot> tools, String actor) {
        byte[] hash = HexFormat.of().parseHex(version.contentHash().hex());
        mapper.insertVersion(version.kind(), new CatalogPersistenceRows.VersionRow(
            version.id().value(), version.organizationId().value(), version.projectId().value(),
            version.ownerId().value(), version.versionNumber(), version.payloadJson(), hash,
            version.status().name(), version.createdAt(), actor));
        for (McpToolDescriptorSnapshot tool : List.copyOf(tools)) {
            mapper.insertTool(new CatalogPersistenceRows.ToolRow(
                tool.id().value(), tool.organizationId().value(), tool.projectId().value(),
                tool.serverVersionId().value(), tool.toolName(), tool.description(),
                tool.argumentSchemaJson(), tool.accessMode(), tool.riskLevel(), tool.idempotency(),
                tool.permissionMetadataJson(), HexFormat.of().parseHex(tool.contentHash().hex()),
                tool.createdAt(), actor));
        }
    }

    /**
     * @param kind 资产分类
     * @param projectId 项目标识
     * @param ownerId 稳定身份
     * @param versionId 版本标识
     * @return 同项目版本
     */
    @Override
    public Optional<CatalogVersion> findVersion(
        CatalogAssetKind kind,
        ProjectId projectId,
        StrongId ownerId,
        StrongId versionId) {
        return mapper.findVersion(
                kind, projectId.value(), ownerId.value(), versionId.value())
            .map(row -> version(kind, row));
    }

    /**
     * @param kind 资产分类
     * @param projectId 项目标识
     * @param ownerId 稳定身份
     * @param afterVersionNumber 游标版本号
     * @param limit 读取上限
     * @return 版本列表
     */
    @Override
    public List<CatalogVersion> listVersions(
        CatalogAssetKind kind,
        ProjectId projectId,
        StrongId ownerId,
        long afterVersionNumber,
        int limit) {
        return mapper.listVersions(
                kind, projectId.value(), ownerId.value(), afterVersionNumber, limit)
            .stream().map(row -> version(kind, row)).toList();
    }

    /**
     * @param kind 资产分类
     * @param row 稳定身份行
     * @return 领域资产
     */
    private CatalogAsset asset(
        CatalogAssetKind kind, CatalogPersistenceRows.AssetRow row) {
        return new CatalogAsset(
            kind.parseId(row.id().toString()), kind, new OrganizationId(row.organizationId()),
            new ProjectId(row.projectId()), row.assetKey(), row.name(), row.description(),
            row.metadataJson(), CatalogAssetStatus.valueOf(row.status()), row.version(),
            row.createdAt(), row.updatedAt());
    }

    /**
     * @param kind 资产分类
     * @param row 版本行
     * @return 领域版本
     */
    private CatalogVersion version(
        CatalogAssetKind kind, CatalogPersistenceRows.VersionRow row) {
        return new CatalogVersion(
            kind.parseVersionId(row.id().toString()), kind,
            new OrganizationId(row.organizationId()), new ProjectId(row.projectId()),
            kind.parseId(row.ownerId().toString()), row.versionNumber(), row.payloadJson(),
            new Checksum("sha256:" + HexFormat.of().formatHex(row.contentHash())),
            CatalogVersionStatus.valueOf(row.status()), row.createdAt());
    }
}
