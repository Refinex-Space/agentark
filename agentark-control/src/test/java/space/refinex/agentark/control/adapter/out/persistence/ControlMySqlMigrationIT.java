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
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
     * 声明 Control 当前最新迁移为 Phase 19 的 V7；V4/V6 由 Knowledge 制品提供。
     *
     * @return Flyway 版本 7
     */
    @Override
    protected String expectedVersion() {
        return "7";
    }

    /**
     * 声明 Phase 10 的 V5 是 Control 独立制品升级测试起点。
     *
     * @return Flyway 版本 5
     */
    @Override
    protected String previousVersion() {
        return "5";
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
            "control_outbox",
            "audit_event",
            "price_table",
            "price_table_version",
            "usage_ledger",
            "usage_aggregate",
            "quota_policy",
            "quota_reservation",
            "evaluation_dataset",
            "evaluation_dataset_version",
            "evaluation_test_case",
            "evaluator",
            "evaluator_version",
            "evaluation_run",
            "evaluation_score",
            "release_gate");
    }

    /**
     * 证明 Audit Event 和版本化 Governance 事实在数据库层拒绝 UPDATE/DELETE。
     *
     * @throws SQLException 初始化租户或执行非法写入失败时抛出
     */
    @Test
    void rejectsAuditAndGovernanceVersionMutation() throws SQLException {
        migrateCurrentSchema();
        seedGovernanceProject();
        try (var connection = ownerConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                INSERT INTO audit_event
                    (id, source_event_id, source_plane, organization_id, project_id,
                     principal_type, principal_ref, scope_type, scope_ref, action, result,
                     resource_type, resource_ref, diff_summary_json, occurred_at, ingested_at)
                VALUES
                    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000203','-','')),
                     'control:test-audit', 'CONTROL',
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000201','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000202','-','')),
                     'USER', 'issuer:subject', 'PROJECT',
                     '019d0000-0000-7000-8000-000000000202', 'quota_policy.create',
                     'SUCCEEDED', 'quota_policy',
                     '019d0000-0000-7000-8000-000000000204', JSON_OBJECT('fields', JSON_ARRAY('metric')),
                     UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
            assertThatThrownBy(() -> statement.execute(
                "UPDATE audit_event SET result = 'FAILED'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("audit event cannot be updated");
            assertThatThrownBy(() -> statement.execute("DELETE FROM audit_event"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("audit event cannot be deleted");
        }
    }

    /**
     * 证明两个并发事务通过 Policy 行锁最多创建一个额度为 1 的硬配额预留。
     *
     * @throws Exception 并发事务或断言失败时抛出
     */
    @Test
    void preventsConcurrentHardQuotaOversell() throws Exception {
        migrateCurrentSchema();
        seedGovernanceProject();
        try (var connection = ownerConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                INSERT INTO quota_policy
                    (id, organization_id, project_id, scope_type, scope_ref, metric,
                     enforcement, limit_value, window_seconds, budget_action,
                     effective_from, effective_until, status, version,
                     created_at, created_by, updated_at, updated_by)
                VALUES
                    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000204','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000201','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000202','-','')),
                     'PROJECT', '019d0000-0000-7000-8000-000000000202',
                     'CONCURRENT_RUN', 'HARD', 1, NULL, 'STOP',
                     UTC_TIMESTAMP(6), NULL, 'ACTIVE', 0,
                     UTC_TIMESTAMP(6), 'test', UTC_TIMESTAMP(6), 'test')
                """);
        }

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> reserve = this::reserveSingleConcurrentRun;
            var results = executor.invokeAll(java.util.List.of(reserve, reserve));
            executor.shutdown();
            org.assertj.core.api.Assertions.assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                .isTrue();
            org.assertj.core.api.Assertions.assertThat(results)
                .extracting(result -> result.get())
                .containsExactlyInAnyOrder(true, false);
        }
        try (var connection = ownerConnection(); var statement = connection.createStatement();
            var result = statement.executeQuery(
                "SELECT COUNT(*) FROM quota_reservation WHERE status = 'HELD'")) {
            org.assertj.core.api.Assertions.assertThat(result.next()).isTrue();
            org.assertj.core.api.Assertions.assertThat(result.getInt(1)).isEqualTo(1);
        }
    }

    /**
     * 在独立事务锁定 Policy、检查当前预留并在额度允许时插入一条 HELD Reservation。
     *
     * @return 是否成功创建预留
     * @throws SQLException 事务失败时抛出
     */
    private boolean reserveSingleConcurrentRun() throws SQLException {
        try (var connection = ownerConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeQuery("""
                    SELECT limit_value FROM quota_policy
                    WHERE id = UNHEX(REPLACE('019d0000-0000-7000-8000-000000000204','-',''))
                    FOR UPDATE
                    """).close();
                int held;
                try (var result = statement.executeQuery("""
                    SELECT COUNT(*) FROM quota_reservation
                    WHERE policy_id = UNHEX(REPLACE('019d0000-0000-7000-8000-000000000204','-',''))
                      AND status = 'HELD' AND expires_at > UTC_TIMESTAMP(6)
                    """)) {
                    result.next();
                    held = result.getInt(1);
                }
                if (held >= 1) {
                    connection.commit();
                    return false;
                }
                String suffix = Thread.currentThread().getName().endsWith("1") ? "205" : "206";
                statement.execute("""
                    INSERT INTO quota_reservation
                        (id, organization_id, project_id, policy_id, idempotency_key,
                         subject_ref, amount, status, expires_at, version, created_at, updated_at)
                    VALUES
                        (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000%s','-','')),
                         UNHEX(REPLACE('019d0000-0000-7000-8000-000000000201','-','')),
                         UNHEX(REPLACE('019d0000-0000-7000-8000-000000000202','-','')),
                         UNHEX(REPLACE('019d0000-0000-7000-8000-000000000204','-','')),
                         'quota-%s', 'run-%s', 1, 'HELD',
                         UTC_TIMESTAMP(6) + INTERVAL 5 MINUTE, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                    """.formatted(suffix, suffix, suffix));
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    /**
     * 初始化 Governance 测试使用的 Organization 与 Project。
     *
     * @throws SQLException 插入失败时抛出
     */
    private void seedGovernanceProject() throws SQLException {
        try (var connection = ownerConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                INSERT INTO organization
                    (id, slug, name, status, version, created_at, created_by, updated_at, updated_by)
                VALUES
                    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000201','-','')),
                     'governance-org', '治理组织', 'ACTIVE', 0,
                     UTC_TIMESTAMP(6), 'test', UTC_TIMESTAMP(6), 'test')
                """);
            statement.execute("""
                INSERT INTO project
                    (id, organization_id, slug, name, status, version,
                     created_at, created_by, updated_at, updated_by)
                VALUES
                    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000202','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000201','-','')),
                     'governance-project', '治理项目', 'ACTIVE', 0,
                     UTC_TIMESTAMP(6), 'test', UTC_TIMESTAMP(6), 'test')
                """);
        }
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
