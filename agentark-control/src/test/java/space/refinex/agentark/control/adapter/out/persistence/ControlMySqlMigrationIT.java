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

package space.refinex.agentark.control.adapter.out.persistence;

import java.sql.SQLException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import space.refinex.agentark.foundation.persistence.testing.AbstractMySqlMigrationIT;

/**
 * 验证 Control 只迁移 agentark_control，并拒绝读取 Runtime Schema。
 *
 * @author refinex
 */
class ControlMySqlMigrationIT extends AbstractMySqlMigrationIT {

    /** 创建 Control MySQL 迁移测试实例。 */
    ControlMySqlMigrationIT() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /**
     * 返回 Control 独占 Schema 名称。
     *
     * @return Control Schema 名称
     */
    @Override
    protected String schemaName() {
        return "agentark_control";
    }

    /**
     * 返回用于证明越权失败的 Runtime Schema 名称。
     *
     * @return Runtime Schema 名称
     */
    @Override
    protected String forbiddenSchemaName() {
        return "agentark_runtime";
    }

    /**
     * 返回 Control 独占 Flyway Location。
     *
     * @return Control Migration classpath Location
     */
    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/control";
    }

    /**
     * 声明 Control 当前最新迁移为 Phase 10 的 V5；V4 由 Knowledge 制品提供。
     *
     * @return Flyway 版本 5
     */
    @Override
    protected String expectedVersion() {
        return "5";
    }

    /**
     * 声明 Phase 08 的 V3 是 Control 独立制品升级测试起点。
     *
     * @return Flyway 版本 3
     */
    @Override
    protected String previousVersion() {
        return "3";
    }

    /**
     * 声明 Phase 07 IAM、Phase 08 Catalog 与 Phase 10 Release 允许创建的业务表。
     *
     * @return IAM、资产、Secret 与 Release 表集合；Knowledge V4 由组合根测试覆盖
     */
    @Override
    protected Set<String> expectedBusinessTables() {
        return Set.of(
            "organization",
            "project",
            "environment",
            "user_identity",
            "service_account",
            "membership",
            "permission",
            "role",
            "role_permission",
            "role_binding",
            "api_key",
            "api_key_scope",
            "agent",
            "prompt",
            "prompt_version",
            "model_provider",
            "model_profile",
            "mcp_server",
            "mcp_server_version",
            "mcp_tool_descriptor",
            "skill",
            "skill_version",
            "memory_profile",
            "memory_profile_version",
            "workspace_profile",
            "workspace_profile_version",
            "sandbox_profile",
            "sandbox_profile_version",
            "permission_policy",
            "permission_policy_version",
            "secret_metadata",
            "secret_binding",
            "agent_draft",
            "agent_draft_component",
            "validation_report",
            "agent_revision",
            "agent_revision_snapshot",
            "publish_operation",
            "deployment",
            "deployment_revision",
            "control_outbox");
    }

    /**
     * 证明数据库 Trigger 拒绝对 Published Revision 和 Snapshot 执行 UPDATE/DELETE。
     *
     * @throws SQLException 构造测试租户或执行 SQL 失败时抛出
     */
    @Test
    void rejectsMutationOfPublishedRevisionAndSnapshot() throws SQLException {
        migrateCurrentSchema();
        try (var connection = ownerConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                INSERT INTO organization
                    (id, slug, name, status, version, created_at, created_by, updated_at, updated_by)
                VALUES (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000001','-','')),
                    'immutable-org', '不可变组织', 'ACTIVE', 0, UTC_TIMESTAMP(6), 'test', UTC_TIMESTAMP(6), 'test')
                """);
            statement.execute("""
                INSERT INTO project
                    (id, organization_id, slug, name, status, version,
                     created_at, created_by, updated_at, updated_by)
                VALUES (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000002','-','')),
                    UNHEX(REPLACE('019d0000-0000-7000-8000-000000000001','-','')),
                    'immutable-project', '不可变项目', 'ACTIVE', 0,
                    UTC_TIMESTAMP(6), 'test', UTC_TIMESTAMP(6), 'test')
                """);
            statement.execute("""
                INSERT INTO agent
                    (id, organization_id, project_id, asset_key, name, description, status,
                     version, created_at, created_by, updated_at, updated_by)
                VALUES (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000003','-','')),
                    UNHEX(REPLACE('019d0000-0000-7000-8000-000000000001','-','')),
                    UNHEX(REPLACE('019d0000-0000-7000-8000-000000000002','-','')),
                    'immutable-agent', '不可变 Agent', NULL, 'ACTIVE', 0,
                    UTC_TIMESTAMP(6), 'test', UTC_TIMESTAMP(6), 'test')
                """);
            statement.execute("""
                INSERT INTO agent_revision
                    (id, organization_id, project_id, agent_id, snapshot_id, revision_number,
                     schema_version, runtime_provider, content_hash, required_capabilities_json,
                     status, created_at, created_by)
                VALUES (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000004','-','')),
                    UNHEX(REPLACE('019d0000-0000-7000-8000-000000000001','-','')),
                    UNHEX(REPLACE('019d0000-0000-7000-8000-000000000002','-','')),
                    UNHEX(REPLACE('019d0000-0000-7000-8000-000000000003','-','')),
                    UNHEX(REPLACE('019d0000-0000-7000-8000-000000000005','-','')),
                    1, 1, 'agentscope-java-2', UNHEX(REPEAT('ab', 32)), JSON_ARRAY(),
                    'PUBLISHED', UTC_TIMESTAMP(6), 'test')
                """);
            statement.execute("""
                INSERT INTO agent_revision_snapshot
                    (id, organization_id, project_id, revision_id, schema_version,
                     runtime_provider, content_hash, snapshot_json, created_at, created_by)
                VALUES (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000005','-','')),
                    UNHEX(REPLACE('019d0000-0000-7000-8000-000000000001','-','')),
                    UNHEX(REPLACE('019d0000-0000-7000-8000-000000000002','-','')),
                    UNHEX(REPLACE('019d0000-0000-7000-8000-000000000004','-','')),
                    1, 'agentscope-java-2', UNHEX(REPEAT('ab', 32)), JSON_OBJECT('schemaVersion', 1),
                    UTC_TIMESTAMP(6), 'test')
                """);

            assertThatThrownBy(() -> statement.execute(
                "UPDATE agent_revision SET runtime_provider = 'other' WHERE revision_number = 1"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("published agent revision is immutable");
            assertThatThrownBy(() -> statement.execute(
                "DELETE FROM agent_revision_snapshot WHERE schema_version = 1"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("agent revision snapshot cannot be deleted");
        }
    }
}
