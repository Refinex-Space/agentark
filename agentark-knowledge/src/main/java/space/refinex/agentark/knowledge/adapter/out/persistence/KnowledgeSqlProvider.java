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

package space.refinex.agentark.knowledge.adapter.out.persistence;

import java.util.Map;

/**
 * 仅为四张同构 Profile 表生成受控 SQL，表名只能来自 {@link ProfileTable}。
 *
 * @author refinex
 */
public final class KnowledgeSqlProvider {

    /**
     * 创建无状态 SQL Provider。
     */
    public KnowledgeSqlProvider() {
        // MyBatis 通过公开无参构造器实例化 Provider。
    }

    /**
     * 生成 Profile 插入 SQL。
     *
     * @param parameters MyBatis 参数映射
     * @return 受控插入 SQL
     */
    public String insertProfile(Map<String, Object> parameters) {
        ProfileTable table = table(parameters);
        String credentialColumn = table.credentialColumn() ? ", credential_secret_ref" : "";
        String credentialValue = table.credentialColumn() ? ", #{row.credentialSecretRef}" : "";
        return "INSERT INTO " + table.tableName()
            + " (id, organization_id, project_id, profile_key, version_number, config_json"
            + credentialColumn
            + ", content_hash, status, created_at, created_by) VALUES "
            + "(#{row.id,jdbcType=BINARY}, #{row.organizationId,jdbcType=BINARY}, "
            + "#{row.projectId,jdbcType=BINARY}, #{row.profileKey}, #{row.versionNumber}, "
            + "#{row.configJson}" + credentialValue
            + ", #{row.contentHash,jdbcType=BINARY}, #{row.status}, #{row.createdAt}, #{row.createdBy})";
    }

    /**
     * 生成 Profile 同项目按标识读取 SQL。
     *
     * @param parameters MyBatis 参数映射
     * @return 受控查询 SQL
     */
    public String findProfile(Map<String, Object> parameters) {
        ProfileTable table = table(parameters);
        String credential = table.credentialColumn()
            ? "credential_secret_ref"
            : "NULL AS credential_secret_ref";
        return "SELECT id, organization_id, project_id, profile_key, version_number, config_json, "
            + credential
            + ", content_hash, status, created_at, created_by FROM "
            + table.tableName()
            + " WHERE project_id = #{projectId,jdbcType=BINARY} AND id = #{id,jdbcType=BINARY}";
    }

    /**
     * 生成同项目同 Key 的下一版本号查询 SQL。
     *
     * @param parameters MyBatis 参数映射
     * @return 受控聚合 SQL
     */
    public String nextProfileVersion(Map<String, Object> parameters) {
        ProfileTable table = table(parameters);
        return "SELECT COALESCE(MAX(version_number), 0) + 1 FROM " + table.tableName()
            + " WHERE project_id = #{projectId,jdbcType=BINARY} AND profile_key = #{profileKey}";
    }

    /**
     * 从 MyBatis 参数中读取受控枚举。
     *
     * @param parameters 参数映射
     * @return 受控 Profile 表
     */
    private static ProfileTable table(Map<String, Object> parameters) {
        Object value = parameters.get("table");
        if (!(value instanceof ProfileTable table)) {
            throw new IllegalArgumentException("profile table must be controlled enum");
        }
        return table;
    }
}
