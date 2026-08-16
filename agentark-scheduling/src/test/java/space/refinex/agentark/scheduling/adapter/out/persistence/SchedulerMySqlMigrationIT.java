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

package space.refinex.agentark.scheduling.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import space.refinex.agentark.foundation.persistence.testing.AbstractMySqlMigrationIT;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Scheduler 只迁移 agentark_scheduler，并拒绝读取 Runtime Schema。
 *
 * @author refinex
 */
class SchedulerMySqlMigrationIT extends AbstractMySqlMigrationIT {

    /** 创建 Scheduler MySQL 迁移测试实例。 */
    SchedulerMySqlMigrationIT() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /**
     * 返回 Scheduler 独占 Schema 名称。
     *
     * @return Scheduler Schema 名称
     */
    @Override
    protected String schemaName() {
        return "agentark_scheduler";
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
     * 证明使用 Scheduler Schema 账号运行的摄取 Worker 无法读取 Control Schema。
     */
    @Test
    void rejectsControlSchemaAccessForIngestionWorker() {
        assertThatThrownBy(
                () -> {
                    try (Connection connection = ownerConnection();
                        Statement statement = connection.createStatement()) {
                        statement.executeQuery(
                            "SELECT id FROM agentark_control.ownership_sentinel");
                    }
                })
            .isInstanceOf(SQLException.class);
    }

    /**
     * 返回 Scheduler 独占 Flyway Location。
     *
     * @return Scheduler Migration classpath Location
     */
    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/scheduler";
    }

    /**
     * 返回 Phase 15 Scheduler 当前 Flyway 版本。
     *
     * @return V2
     */
    @Override
    protected String expectedVersion() {
        return "2";
    }

    /**
     * 返回 Phase 06 的空 Scheduler 基线版本。
     *
     * @return V1
     */
    @Override
    protected String previousVersion() {
        return "1";
    }

    /**
     * 返回 Scheduler 独占的九张 Phase 15 业务表。
     *
     * @return 业务表集合
     */
    @Override
    protected Set<String> expectedBusinessTables() {
        return Set.of(
            "trigger_definition", "trigger_cursor", "job", "job_attempt", "job_lease",
            "delivery", "dead_letter", "scheduler_idempotency_record", "scheduler_outbox");
    }

    /**
     * 证明两个 Scheduler 实例竞争同一到期 Job 时，SKIP LOCKED 只暴露给一个 Owner。
     *
     * @throws Exception 数据库或并发验证失败时抛出
     */
    @Test
    void allowsOnlyOneConcurrentClaimOwner() throws Exception {
        migrateCurrentSchema();
        UUID jobId = insertReadyJob("claim-race");
        try (Connection ownerOne = ownerConnection(); Connection ownerTwo = ownerConnection()) {
            ownerOne.setAutoCommit(false);
            ownerTwo.setAutoCommit(false);
            try (PreparedStatement first = claimCandidate(ownerOne)) {
                try (ResultSet result = first.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo(jobId.toString());
                }
            }
            try (var executor = Executors.newSingleThreadExecutor()) {
                var secondClaim = executor.submit(() -> {
                    try (PreparedStatement second = claimCandidate(ownerTwo);
                        ResultSet result = second.executeQuery()) {
                        return result.next();
                    }
                });
                assertThat(secondClaim.get(5, TimeUnit.SECONDS)).isFalse();
            } finally {
                ownerOne.rollback();
                ownerTwo.rollback();
            }
        }
    }

    /**
     * 证明数据库更新条件会拒绝陈旧 Fencing Token 提交 Job 终态。
     *
     * @throws SQLException 数据库验证失败时抛出
     */
    @Test
    void rejectsStaleFencingTokenCompletion() throws SQLException {
        migrateCurrentSchema();
        UUID jobId = insertReadyJob("stale-token");
        try (Connection connection = ownerConnection();
            PreparedStatement claim = connection.prepareStatement(
                "UPDATE job SET status='CLAIMED', claimed_by='owner-new', claim_until=?, "
                    + "current_attempt=1, current_fencing_token=2 WHERE id=UUID_TO_BIN(?)")) {
            claim.setObject(1, Instant.now().plusSeconds(30));
            claim.setString(2, jobId.toString());
            assertThat(claim.executeUpdate()).isEqualTo(1);
        }
        try (Connection connection = ownerConnection();
            PreparedStatement stale = connection.prepareStatement(
                "UPDATE job SET status='SUCCEEDED', claimed_by=NULL, claim_until=NULL "
                    + "WHERE id=UUID_TO_BIN(?) AND status='CLAIMED' "
                    + "AND claimed_by='owner-old' AND current_fencing_token=1")) {
            stale.setString(1, jobId.toString());
            assertThat(stale.executeUpdate()).isZero();
        }
    }

    /**
     * 插入满足全部约束的 READY Job Fixture。
     *
     * @param businessKey 唯一业务键
     * @return Job UUID
     * @throws SQLException 插入失败时抛出
     */
    private UUID insertReadyJob(String businessKey) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = ownerConnection();
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO job
                    (id, organization_id, project_id, type, business_key, payload_json,
                     payload_hash, status, priority, available_at, retry_policy_json,
                     idempotency_capability, created_at, updated_at)
                VALUES
                    (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), 'RUNTIME_TURN', ?,
                     JSON_OBJECT('sessionId', 'fixture'), UNHEX(SHA2('{}', 256)), 'READY', 0,
                     UTC_TIMESTAMP(6), JSON_OBJECT('maxAttempts', 1), 'PROVIDER_KEY',
                     UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """)) {
            statement.setString(1, id.toString());
            statement.setString(2, UUID.randomUUID().toString());
            statement.setString(3, UUID.randomUUID().toString());
            statement.setString(4, businessKey);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        return id;
    }

    /**
     * 创建与生产 Mapper 一致的到期 Job 锁定语句。
     *
     * @param connection 当前 Scheduler Owner 连接
     * @return Claim 查询
     * @throws SQLException 语句创建失败时抛出
     */
    private PreparedStatement claimCandidate(Connection connection) throws SQLException {
        return connection.prepareStatement("""
            SELECT BIN_TO_UUID(id) FROM job
            WHERE type='RUNTIME_TURN' AND status='READY' AND available_at <= UTC_TIMESTAMP(6)
            ORDER BY priority DESC, available_at, id
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """);
    }
}
