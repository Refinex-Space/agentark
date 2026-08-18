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

package space.refinex.agentark.control.catalog.adapter.in.web;

import jakarta.validation.constraints.*;
import space.refinex.agentark.control.catalog.domain.CatalogAsset;
import space.refinex.agentark.control.catalog.domain.CatalogVersion;
import space.refinex.agentark.kernel.ref.ObjectRef;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

/**
 * 集中定义 Catalog Public API 请求契约，响应直接使用不含厂商类型的领域只读模型。
 *
 * @author refinex
 */
public final class CatalogApiModels {

    /**
     * 禁止实例化 API 模型容器。
     */
    private CatalogApiModels() {
    }

    /**
     * @param key         项目内稳定 Key
     * @param name        显示名称
     * @param description 可选用途说明
     * @param metadata    分类专属非敏感元数据
     * @author refinex
     */
    public record CreateAssetRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{1,62}") String key,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @NotNull Map<String, Object> metadata) {

        /**
         * 防御性复制顶层元数据。
         */
        public CreateAssetRequest {
            metadata = metadata == null ? null : Map.copyOf(metadata);
        }
    }

    /**
     * @param payload 分类专属不可变载荷
     * @param status  版本状态：DRAFT、PUBLISHED 或 ARCHIVED
     * @author refinex
     */
    public record CreateVersionRequest(
        @NotNull Map<String, Object> payload,
        @NotBlank @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED") String status) {

        /**
         * 防御性复制顶层载荷。
         */
        public CreateVersionRequest {
            payload = payload == null ? null : Map.copyOf(payload);
        }
    }

    /**
     * @param expectedVersion 稳定身份乐观锁版本
     * @author refinex
     */
    public record ArchiveAssetRequest(@PositiveOrZero long expectedVersion) {
    }

    /**
     * @param id             稳定身份 UUIDv7
     * @param kind           资产分类
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param key            稳定 Key
     * @param name           显示名称
     * @param description    用途说明
     * @param metadata       分类专属 JSON 对象
     * @param status         生命周期状态
     * @param version        乐观锁版本
     * @param createdAt      创建时刻
     * @param updatedAt      更新时间
     * @author refinex
     */
    public record CatalogAssetView(
        String id,
        String kind,
        String organizationId,
        String projectId,
        String key,
        String name,
        String description,
        Map<String, Object> metadata,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

        /**
         * 防御性复制顶层元数据。
         */
        public CatalogAssetView {
            metadata = metadata == null ? null : Map.copyOf(metadata);
        }

        /**
         * @param asset  领域资产
         * @param mapper JSON 映射器
         * @return 不把 JSON 双重编码为字符串的 Public API 视图
         */
        @SuppressWarnings("unchecked")
        public static CatalogAssetView from(CatalogAsset asset, JsonMapper mapper) {
            try {
                return new CatalogAssetView(
                    asset.id().asString(),
                    asset.kind().apiValue(),
                    asset.organizationId().asString(),
                    asset.projectId().asString(),
                    asset.key(),
                    asset.name(),
                    asset.description(),
                    mapper.readValue(asset.metadataJson(), Map.class),
                    asset.status().name(),
                    asset.version(),
                    asset.createdAt(),
                    asset.updatedAt());
            } catch (tools.jackson.core.JacksonException exception) {
                throw new IllegalStateException("stored catalog metadata is invalid", exception);
            }
        }
    }

    /**
     * @param id             版本 UUIDv7
     * @param kind           资产分类
     * @param organizationId 组织 UUIDv7
     * @param projectId      项目 UUIDv7
     * @param ownerId        稳定身份 UUIDv7
     * @param versionNumber  正数版本号
     * @param payload        分类专属 JSON 对象
     * @param contentHash    规范 SHA-256
     * @param status         版本状态
     * @param createdAt      创建时刻
     * @author refinex
     */
    public record CatalogVersionView(
        String id,
        String kind,
        String organizationId,
        String projectId,
        String ownerId,
        long versionNumber,
        Map<String, Object> payload,
        String contentHash,
        String status,
        Instant createdAt) {

        /**
         * 防御性复制顶层载荷。
         */
        public CatalogVersionView {
            payload = payload == null ? null : Map.copyOf(payload);
        }

        /**
         * @param version 领域版本
         * @param mapper  JSON 映射器
         * @return 语言中立 JSON 载荷视图
         */
        @SuppressWarnings("unchecked")
        public static CatalogVersionView from(CatalogVersion version, JsonMapper mapper) {
            try {
                return new CatalogVersionView(
                    version.id().asString(),
                    version.kind().apiValue(),
                    version.organizationId().asString(),
                    version.projectId().asString(),
                    version.ownerId().asString(),
                    version.versionNumber(),
                    mapper.readValue(version.payloadJson(), Map.class),
                    version.contentHash().toString(),
                    version.status().name(),
                    version.createdAt());
            } catch (tools.jackson.core.JacksonException exception) {
                throw new IllegalStateException("stored catalog payload is invalid", exception);
            }
        }
    }

    /**
     * 将 Kernel ObjectRef 映射为字符串化的语言中立 Public API 契约。
     *
     * @param uri       不携带授权参数的对象 URI
     * @param checksum  SHA-256 完整性校验和
     * @param size      对象字节数
     * @param mediaType 媒体类型
     * @author refinex
     */
    public record ObjectRefView(String uri, String checksum, long size, String mediaType) {

        /**
         * @param ref Kernel 对象引用
         * @return 字符串化的 Public API 视图
         */
        public static ObjectRefView from(ObjectRef ref) {
            return new ObjectRefView(ref.uri().toString(), ref.checksum().toString(), ref.size(), ref.mediaType());
        }
    }
}
