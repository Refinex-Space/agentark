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

package space.refinex.agentark.control.catalog.application.port;

import space.refinex.agentark.control.catalog.domain.CatalogAsset;
import space.refinex.agentark.control.catalog.domain.CatalogAssetKind;
import space.refinex.agentark.control.catalog.domain.CatalogVersion;
import space.refinex.agentark.control.catalog.domain.McpToolDescriptorSnapshot;
import space.refinex.agentark.kernel.id.ProjectId;
import space.refinex.agentark.kernel.id.StrongId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 定义 AI 资产稳定身份、不可变版本和 MCP Tool Descriptor 的持久化端口。
 *
 * @author refinex
 */
public interface CatalogRepository {

    /**
     * @param asset 待插入稳定身份
     * @param actor 创建主体稳定引用
     */
    void insertAsset(CatalogAsset asset, String actor);

    /**
     * @param kind 资产分类
     * @param projectId 项目标识
     * @param id 稳定身份
     * @return 同项目可见资产
     */
    Optional<CatalogAsset> findAsset(CatalogAssetKind kind, ProjectId projectId, StrongId id);

    /**
     * @param kind 资产分类
     * @param projectId 项目标识
     * @param afterKey 不含当前值的游标 Key
     * @param limit 读取上限
     * @return 按 Key 升序资产
     */
    List<CatalogAsset> listAssets(
        CatalogAssetKind kind, ProjectId projectId, String afterKey, int limit);

    /**
     * @param kind 资产分类
     * @param projectId 项目标识
     * @param id 稳定身份
     * @param expectedVersion 调用方读取的乐观锁版本
     * @param actor 操作主体
     * @param now 操作时刻
     * @return 实际更新行数
     */
    int archiveAsset(
        CatalogAssetKind kind,
        ProjectId projectId,
        StrongId id,
        long expectedVersion,
        String actor,
        Instant now);

    /**
     * 锁定稳定身份并计算下一版本号，调用方必须在同一事务内立即插入版本。
     *
     * @param kind 资产分类
     * @param projectId 项目标识
     * @param ownerId 稳定身份
     * @return 下一正数版本号；Owner 不存在时为空
     */
    Optional<Long> nextVersionNumber(
        CatalogAssetKind kind, ProjectId projectId, StrongId ownerId);

    /**
     * @param version 待追加不可变版本
     * @param tools MCP Tool Descriptor 快照；非 MCP 版本必须为空
     * @param actor 创建主体稳定引用
     */
    void insertVersion(
        CatalogVersion version, List<McpToolDescriptorSnapshot> tools, String actor);

    /**
     * @param kind 资产分类
     * @param projectId 项目标识
     * @param ownerId 稳定身份
     * @param versionId 版本标识
     * @return 同项目且同 Owner 的版本
     */
    Optional<CatalogVersion> findVersion(
        CatalogAssetKind kind, ProjectId projectId, StrongId ownerId, StrongId versionId);

    /**
     * @param kind 资产分类
     * @param projectId 项目标识
     * @param ownerId 稳定身份
     * @param afterVersionNumber 不含当前值的游标版本号
     * @param limit 读取上限
     * @return 按版本号升序的不可变版本
     */
    List<CatalogVersion> listVersions(
        CatalogAssetKind kind,
        ProjectId projectId,
        StrongId ownerId,
        long afterVersionNumber,
        int limit);
}
