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

import java.time.Instant;
import java.util.UUID;

/**
 * 集中定义 Catalog MyBatis 边界行对象，不向 Domain 或 Public API 暴露。
 *
 * @author refinex
 */
final class CatalogPersistenceRows {

    /** 禁止实例化行对象容器。 */
    private CatalogPersistenceRows() {
    }

    /**
     * @param id 主键 UUID
     * @param organizationId 组织 UUID
     * @param projectId 项目 UUID
     * @param assetKey 稳定 Key
     * @param name 显示名称
     * @param description 用途说明
     * @param metadataJson 分类元数据 JSON
     * @param status 生命周期状态
     * @param version 乐观锁版本
     * @param createdAt 创建时刻
     * @param createdBy 创建主体
     * @param updatedAt 更新时间
     * @param updatedBy 更新主体
     * @author refinex
     */
    record AssetRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String assetKey,
        String name,
        String description,
        String metadataJson,
        String status,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {
    }

    /**
     * @param id 版本主键 UUID
     * @param organizationId 组织 UUID
     * @param projectId 项目 UUID
     * @param ownerId 稳定身份 UUID
     * @param versionNumber 正数版本号
     * @param payloadJson 规范 JSON
     * @param contentHash SHA-256 原始 32 字节
     * @param status 版本状态
     * @param createdAt 创建时刻
     * @param createdBy 创建主体
     * @author refinex
     */
    record VersionRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        UUID ownerId,
        long versionNumber,
        String payloadJson,
        byte[] contentHash,
        String status,
        Instant createdAt,
        String createdBy) {

        /** 防御性复制 Hash，避免持久化调用期间被修改。 */
        VersionRow {
            contentHash = contentHash.clone();
        }

        /** @return SHA-256 原始 32 字节防御性副本 */
        @Override
        public byte[] contentHash() {
            return contentHash.clone();
        }
    }

    /**
     * @param id 工具描述符 UUID
     * @param organizationId 组织 UUID
     * @param projectId 项目 UUID
     * @param serverVersionId MCP Server 版本 UUID
     * @param toolName 工具名
     * @param description 描述
     * @param argumentSchemaJson 参数 Schema
     * @param accessMode 读写模式
     * @param riskLevel 风险等级
     * @param idempotency 幂等语义
     * @param permissionMetadataJson 权限元数据
     * @param contentHash 内容 Hash
     * @param createdAt 创建时刻
     * @param createdBy 创建主体
     * @author refinex
     */
    record ToolRow(
        UUID id,
        UUID organizationId,
        UUID projectId,
        UUID serverVersionId,
        String toolName,
        String description,
        String argumentSchemaJson,
        String accessMode,
        String riskLevel,
        String idempotency,
        String permissionMetadataJson,
        byte[] contentHash,
        Instant createdAt,
        String createdBy) {

        /** 防御性复制 Hash。 */
        ToolRow {
            contentHash = contentHash.clone();
        }

        /** @return SHA-256 原始 32 字节防御性副本 */
        @Override
        public byte[] contentHash() {
            return contentHash.clone();
        }
    }
}
