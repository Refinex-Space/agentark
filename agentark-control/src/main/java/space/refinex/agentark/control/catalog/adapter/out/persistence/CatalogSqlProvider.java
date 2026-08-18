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

import space.refinex.agentark.control.catalog.domain.CatalogAssetKind;

/**
 * 只根据受控枚举生成 Catalog SQL，任何用户输入都必须继续使用 MyBatis 参数绑定。
 *
 * @author refinex
 */
public final class CatalogSqlProvider {

    /**
     * 创建无状态 SQL Provider。
     */
    public CatalogSqlProvider() {
    }

    /**
     * @param parameters MyBatis 参数
     * @return 稳定身份插入 SQL
     */
    public String insertAsset(java.util.Map<String, Object> parameters) {
        CatalogAssetKind kind = kind(parameters);
        String commonValues = "#{row.id}, #{row.organizationId}, #{row.projectId}, #{row.assetKey}, "
            + "#{row.name}, #{row.description}, 'ACTIVE', 0, #{row.createdAt}, #{row.createdBy}, "
            + "#{row.updatedAt}, #{row.updatedBy}";

        if (kind == CatalogAssetKind.MODEL_PROVIDER) {
            return "INSERT INTO model_provider (id, organization_id, project_id, asset_key, name, "
                + "description, provider_type, descriptor_json, status, version, created_at, "
                + "created_by, updated_at, updated_by) VALUES (#{row.id}, #{row.organizationId}, "
                + "#{row.projectId}, #{row.assetKey}, #{row.name}, #{row.description}, "
                + "JSON_UNQUOTE(JSON_EXTRACT(#{row.metadataJson}, '$.providerType')), "
                + "JSON_EXTRACT(#{row.metadataJson}, '$.descriptor'), 'ACTIVE', 0, "
                + "#{row.createdAt}, #{row.createdBy}, #{row.updatedAt}, #{row.updatedBy})";
        }

        return "INSERT INTO " + kind.tableName()
            + " (id, organization_id, project_id, asset_key, name, description, status, version, "
            + "created_at, created_by, updated_at, updated_by) VALUES (" + commonValues + ")";
    }

    /**
     * @param parameters MyBatis 参数
     * @return 单个稳定身份查询 SQL
     */
    public String findAsset(java.util.Map<String, Object> parameters) {
        CatalogAssetKind kind = kind(parameters);
        return assetSelect(kind) + " WHERE id = #{id} AND project_id = #{projectId}";
    }

    /**
     * @param parameters MyBatis 参数
     * @return 稳定身份游标查询 SQL
     */
    public String listAssets(java.util.Map<String, Object> parameters) {
        CatalogAssetKind kind = kind(parameters);
        return assetSelect(kind) + " WHERE project_id = #{projectId} AND asset_key > #{afterKey} "
            + "ORDER BY asset_key, id LIMIT #{limit}";
    }

    /**
     * @param parameters MyBatis 参数
     * @return 稳定身份归档 SQL
     */
    public String archiveAsset(java.util.Map<String, Object> parameters) {
        return "UPDATE " + kind(parameters).tableName()
            + " SET status = 'ARCHIVED', version = version + 1, updated_at = #{now}, "
            + "updated_by = #{actor} WHERE id = #{id} AND project_id = #{projectId} "
            + "AND version = #{expectedVersion} AND status = 'ACTIVE'";
    }

    /**
     * @param parameters MyBatis 参数
     * @return Owner 行锁 SQL
     */
    public String lockAsset(java.util.Map<String, Object> parameters) {
        return "SELECT id FROM " + kind(parameters).tableName()
            + " WHERE id = #{ownerId} AND project_id = #{projectId} AND status = 'ACTIVE' FOR UPDATE";
    }

    /**
     * @param parameters MyBatis 参数
     * @return 下一版本号查询 SQL
     */
    public String nextVersionNumber(java.util.Map<String, Object> parameters) {
        return "SELECT COALESCE(MAX(version_number), 0) + 1 FROM "
            + kind(parameters).versionTableName()
            + " WHERE owner_id = #{ownerId} AND project_id = #{projectId}";
    }

    /**
     * @param parameters MyBatis 参数
     * @return 分类专属版本插入 SQL
     */
    public String insertVersion(java.util.Map<String, Object> parameters) {
        CatalogAssetKind kind = kind(parameters);
        String prefix = "INSERT INTO " + kind.versionTableName()
            + " (id, organization_id, project_id, owner_id, version_number, ";
        String suffix = ", content_hash, status, created_at, created_by) VALUES (#{row.id}, "
            + "#{row.organizationId}, #{row.projectId}, #{row.ownerId}, #{row.versionNumber}, ";
        String tail = ", #{row.contentHash}, #{row.status}, #{row.createdAt}, #{row.createdBy})";

        return switch (kind) {
            case PROMPT -> prefix
                + "template_text, variable_schema, purpose, payload_json" + suffix
                + "JSON_UNQUOTE(JSON_EXTRACT(#{row.payloadJson}, '$.template')), "
                + "JSON_EXTRACT(#{row.payloadJson}, '$.variableSchema'), "
                + "JSON_UNQUOTE(JSON_EXTRACT(#{row.payloadJson}, '$.purpose')), "
                + "#{row.payloadJson}" + tail;
            case MODEL_PROVIDER -> prefix
                + "model_name, capabilities_json, parameters_json, credential_secret_ref, payload_json"
                + suffix
                + "JSON_UNQUOTE(JSON_EXTRACT(#{row.payloadJson}, '$.modelName')), "
                + "JSON_EXTRACT(#{row.payloadJson}, '$.capabilities'), "
                + "JSON_EXTRACT(#{row.payloadJson}, '$.parameters'), "
                + "JSON_UNQUOTE(JSON_EXTRACT(#{row.payloadJson}, '$.credentialSecretRef')), "
                + "#{row.payloadJson}" + tail;
            case MCP_SERVER -> prefix
                + "transport, endpoint_uri, command_name, transport_config_json, tls_secret_ref, "
                + "auth_secret_ref, ssrf_policy_json, payload_json" + suffix
                + "JSON_UNQUOTE(JSON_EXTRACT(#{row.payloadJson}, '$.transport')), "
                + "JSON_UNQUOTE(JSON_EXTRACT(#{row.payloadJson}, '$.endpointUri')), "
                + "JSON_UNQUOTE(JSON_EXTRACT(#{row.payloadJson}, '$.commandName')), "
                + "JSON_EXTRACT(#{row.payloadJson}, '$.transportConfig'), "
                + "JSON_UNQUOTE(JSON_EXTRACT(#{row.payloadJson}, '$.tlsSecretRef')), "
                + "JSON_UNQUOTE(JSON_EXTRACT(#{row.payloadJson}, '$.authSecretRef')), "
                + "JSON_EXTRACT(#{row.payloadJson}, '$.ssrfPolicy'), #{row.payloadJson}" + tail;
            case SKILL -> prefix
                + "artifact_uri, artifact_hash, artifact_size, media_type, source_uri, "
                + "license_expression, signature_json, compatibility_json, payload_json" + suffix
                + "JSON_UNQUOTE(JSON_EXTRACT(#{row.payloadJson}, '$.artifact.uri')), "
                + "UNHEX(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(#{row.payloadJson}, "
                + "'$.artifact.checksum')), 8)), "
                + "JSON_EXTRACT(#{row.payloadJson}, '$.artifact.size'), "
                + "JSON_UNQUOTE(JSON_EXTRACT(#{row.payloadJson}, '$.artifact.mediaType')), "
                + "JSON_UNQUOTE(JSON_EXTRACT(#{row.payloadJson}, '$.sourceUri')), "
                + "JSON_UNQUOTE(JSON_EXTRACT(#{row.payloadJson}, '$.license')), "
                + "JSON_EXTRACT(#{row.payloadJson}, '$.signature'), "
                + "JSON_EXTRACT(#{row.payloadJson}, '$.compatibility'), #{row.payloadJson}" + tail;
            case MEMORY_PROFILE, WORKSPACE_PROFILE, SANDBOX_PROFILE, PERMISSION_POLICY ->
                prefix + "payload_json" + suffix + "#{row.payloadJson}" + tail;
            case AGENT -> throw new IllegalArgumentException("agent does not support versions");
        };
    }

    /**
     * @param parameters MyBatis 参数
     * @return 单版本查询 SQL
     */
    public String findVersion(java.util.Map<String, Object> parameters) {
        CatalogAssetKind kind = kind(parameters);
        return versionSelect(kind) + " WHERE id = #{versionId} AND owner_id = #{ownerId} AND project_id = #{projectId}";
    }

    /**
     * @param parameters MyBatis 参数
     * @return 版本游标查询 SQL
     */
    public String listVersions(java.util.Map<String, Object> parameters) {
        CatalogAssetKind kind = kind(parameters);
        return versionSelect(kind) + " WHERE owner_id = #{ownerId} AND project_id = #{projectId} "
            + "AND version_number > #{afterVersionNumber} ORDER BY version_number, id LIMIT #{limit}";
    }

    /**
     * @param kind 资产分类
     * @return 稳定身份公共投影
     */
    private String assetSelect(CatalogAssetKind kind) {
        String metadata = kind == CatalogAssetKind.MODEL_PROVIDER
            ? "JSON_OBJECT('providerType', provider_type, 'descriptor', descriptor_json)"
            : "JSON_OBJECT()";

        return "SELECT id, organization_id, project_id, asset_key, name, "
            + "COALESCE(description, '') AS description, " + metadata
            + " AS metadata_json, status, version, created_at, created_by, updated_at, updated_by "
            + "FROM " + kind.tableName();
    }

    /**
     * @param kind 资产分类
     * @return 不可变版本公共投影
     */
    private String versionSelect(CatalogAssetKind kind) {
        return "SELECT id, organization_id, project_id, owner_id, version_number, payload_json, "
            + "content_hash, status, created_at, created_by FROM " + kind.versionTableName();
    }

    /**
     * @param parameters MyBatis 参数
     * @return 受控资产分类
     */
    private CatalogAssetKind kind(java.util.Map<String, Object> parameters) {
        Object value = parameters.get("kind");
        if (!(value instanceof CatalogAssetKind kind)) {
            throw new IllegalArgumentException("kind must be a CatalogAssetKind");
        }
        return kind;
    }
}

