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

package space.refinex.agentark.runtime.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.JdbcType;
import space.refinex.agentark.foundation.persistence.testing.AbstractMySqlMigrationIT;
import space.refinex.agentark.foundation.persistence.UtcInstantTypeHandler;
import space.refinex.agentark.foundation.persistence.UuidV7BinaryTypeHandler;
import space.refinex.agentark.kernel.id.*;
import space.refinex.agentark.kernel.ref.Checksum;
import space.refinex.agentark.runtime.adapter.out.fake.FakeAgentExecutionEngine;
import space.refinex.agentark.runtime.application.RuntimeApplicationService;
import space.refinex.agentark.runtime.application.RuntimeCommands.AcceptTurnCommand;
import space.refinex.agentark.runtime.application.RuntimeCommands.CreateSessionCommand;
import space.refinex.agentark.runtime.application.RuntimeCommands.ExecuteNextCommand;
import space.refinex.agentark.runtime.domain.RuntimeModels.*;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.json.JsonMapper;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Runtime 只迁移 agentark_runtime，并拒绝读取 Control Schema。
 *
 * @author refinex
 */
class RuntimeMySqlMigrationIT extends AbstractMySqlMigrationIT {

    /** 测试 Session UUIDv7 字符串。 */
    private static final String SESSION_ID = "019d0000-0000-7000-8000-000000000101";

    /** 测试 Turn UUIDv7 字符串。 */
    private static final String TURN_ID = "019d0000-0000-7000-8000-000000000106";

    /** 测试 Run UUIDv7 字符串。 */
    private static final String RUN_ID = "019d0000-0000-7000-8000-000000000107";

    /** 创建 Runtime MySQL 迁移测试实例。 */
    RuntimeMySqlMigrationIT() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /**
     * 返回 Runtime 独占 Schema 名称。
     *
     * @return Runtime Schema 名称
     */
    @Override
    protected String schemaName() {
        return "agentark_runtime";
    }

    /**
     * 返回用于证明越权失败的 Control Schema 名称。
     *
     * @return Control Schema 名称
     */
    @Override
    protected String forbiddenSchemaName() {
        return "agentark_control";
    }

    /**
     * 返回 Runtime 独占 Flyway Location。
     *
     * @return Runtime Migration classpath Location
     */
    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/runtime";
    }

    /**
     * 声明 Runtime 当前最新迁移为 Phase 19 的 V3。
     *
     * @return Flyway 版本 3
     */
    @Override
    protected String expectedVersion() {
        return "3";
    }

    /**
     * 声明 Phase 11 V2 是升级测试起点。
     *
     * @return Flyway 版本 2
     */
    @Override
    protected String previousVersion() {
        return "2";
    }

    /**
     * 声明 Phase 11 Runtime 权威业务表集合。
     *
     * @return 十三张 Runtime 业务表
     */
    @Override
    protected Set<String> expectedBusinessTables() {
        return Set.of(
            "session",
            "turn",
            "run",
            "runtime_work_item",
            "runtime_instance",
            "runtime_event",
            "runtime_event_payload_ref",
            "approval",
            "runtime_agent_state",
            "runtime_checkpoint",
            "usage_record",
            "runtime_idempotency_record",
            "runtime_outbox");
    }

    /**
     * 证明 Session 固定 Snapshot、Run Fencing Token 与追加 Event 均由 MySQL 拒绝非法修改。
     *
     * @throws SQLException 初始化权威聚合或执行非法写入失败时抛出
     */
    @Test
    void rejectsSnapshotMutationStaleFencingAndEventMutation() throws SQLException {
        migrateCurrentSchema();
        seedRunningAggregate();
        try (var connection = ownerConnection(); var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute(
                "UPDATE session SET revision_id = UNHEX(REPEAT('aa', 16)) "
                    + "WHERE id = UNHEX(REPLACE('" + SESSION_ID + "','-',''))"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("revision and snapshot are immutable");
            assertThatThrownBy(() -> statement.execute(
                "UPDATE run SET fencing_token = 0 "
                    + "WHERE id = UNHEX(REPLACE('" + RUN_ID + "','-',''))"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("stale run fencing token");

            allocateAndInsertEvent(statement);
            assertThatThrownBy(() -> statement.execute(
                "UPDATE runtime_event SET type = 'run.changed' WHERE run_sequence = 1"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("append-only");
            assertThatThrownBy(() -> statement.execute(
                "DELETE FROM runtime_event WHERE run_sequence = 1"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("cannot be deleted");
        }
    }

    /**
     * 证明多个数据库连接竞争追加 Event 时，Session 与 Run Sequence 均单调且唯一。
     *
     * @throws Exception 并发事务、SQL 或线程等待失败时抛出
     */
    @Test
    void allocatesUniqueMonotonicSequencesAcrossConnections() throws Exception {
        migrateCurrentSchema();
        seedRunningAggregate();
        int additions = 20;
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int index = 0; index < additions; index++) {
                futures.add(executor.submit(() -> {
                    appendEventInTransaction();
                    return null;
                }));
            }
            for (var future : futures) {
                future.get();
            }
        }

        try (var connection = ownerConnection(); var statement = connection.createStatement();
            var result = statement.executeQuery(
                "SELECT COUNT(*), COUNT(DISTINCT session_sequence), "
                    + "COUNT(DISTINCT run_sequence), MIN(session_sequence), "
                    + "MAX(session_sequence), MIN(run_sequence), MAX(run_sequence) "
                    + "FROM runtime_event")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(additions);
            assertThat(result.getInt(2)).isEqualTo(additions);
            assertThat(result.getInt(3)).isEqualTo(additions);
            assertThat(result.getLong(4)).isEqualTo(1);
            assertThat(result.getLong(5)).isEqualTo(additions);
            assertThat(result.getLong(6)).isEqualTo(1);
            assertThat(result.getLong(7)).isEqualTo(additions);
        }
    }

    /**
     * 证明 Redis 不参与权威恢复：已提交大 State 的 ObjectRef 和 Checkpoint 可由 MySQL 重连恢复。
     *
     * @throws SQLException 插入 State、Checkpoint 或重连查询失败时抛出
     */
    @Test
    void recoversCommittedObjectStateAndCheckpointFromMySql() throws SQLException {
        migrateCurrentSchema();
        seedRunningAggregate();
        try (var connection = ownerConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                INSERT INTO runtime_agent_state
                    (id, organization_id, project_id, session_id, run_id, agent_key, state_key,
                     item_index, state_version, state_storage, state_json, object_uri,
                     object_size, media_type, content_hash, committed, fencing_token, created_at)
                VALUES
                    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000121','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000001','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000002','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000101','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000107','-','')),
                     'agent-main', 'conversation', 0, 1, 'OBJECT', NULL,
                     'agentark-object://runtime-state/state-1', 4096, 'application/json',
                     UNHEX(REPEAT('ab', 32)), FALSE, 1, UTC_TIMESTAMP(6))
                """);
            statement.execute("""
                UPDATE runtime_agent_state SET committed = TRUE
                WHERE id = UNHEX(REPLACE('019d0000-0000-7000-8000-000000000121','-',''))
                """);
            statement.execute("""
                INSERT INTO runtime_checkpoint
                    (id, run_id, sequence, agent_state_id, agent_state_version,
                     event_sequence, content_hash, recoverable, fencing_token, created_at)
                VALUES
                    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000122','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000107','-','')),
                     1, UNHEX(REPLACE('019d0000-0000-7000-8000-000000000121','-','')),
                     1, 1, UNHEX(REPEAT('cd', 32)), TRUE, 1, UTC_TIMESTAMP(6))
                """);
            assertThatThrownBy(() -> statement.execute("""
                INSERT INTO runtime_checkpoint
                    (id, run_id, sequence, agent_state_id, agent_state_version,
                     event_sequence, content_hash, recoverable, fencing_token, created_at)
                VALUES
                    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000123','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000107','-','')),
                     2, UNHEX(REPLACE('019d0000-0000-7000-8000-000000000121','-','')),
                     1, 1, UNHEX(REPEAT('ef', 32)), TRUE, 0, UTC_TIMESTAMP(6))
                """))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("stale checkpoint fencing token");
        }

        try (var connection = ownerConnection(); var statement = connection.createStatement();
            var result = statement.executeQuery("""
                SELECT s.object_uri, s.object_size, s.committed, c.recoverable
                FROM runtime_checkpoint c
                JOIN runtime_agent_state s ON s.id = c.agent_state_id
                WHERE c.run_id = UNHEX(REPLACE('019d0000-0000-7000-8000-000000000107','-',''))
                """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("agentark-object://runtime-state/state-1");
            assertThat(result.getLong(2)).isEqualTo(4096);
            assertThat(result.getBoolean(3)).isTrue();
            assertThat(result.getBoolean(4)).isTrue();
        }
    }

    /**
     * 证明真实 MyBatis Adapter 可贯穿固定 Snapshot、Turn 接收、Claim、Event 与成功终态事务。
     *
     * @throws SQLException 创建单连接测试 DataSource 或验证落库结果失败时抛出
     */
    @Test
    void persistsSuccessfulApplicationFlowThroughMybatisAdapter() throws SQLException {
        migrateCurrentSchema();
        OrganizationId organizationId = OrganizationId.generate();
        ProjectId projectId = ProjectId.generate();
        DeploymentId deploymentId = DeploymentId.generate();
        RevisionId revisionId = RevisionId.generate();
        SnapshotId snapshotId = SnapshotId.generate();
        Checksum snapshotHash = Checksum.sha256("mysql-snapshot");
        Instant now = Instant.parse("2026-08-16T06:00:00Z");

        try (var connection = ownerConnection()) {
            var dataSource = new SingleConnectionDataSource(connection, true);
            Configuration configuration = new Configuration(new Environment(
                "runtime-it", new JdbcTransactionFactory(), dataSource));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.getTypeHandlerRegistry().register(
                java.util.UUID.class, JdbcType.BINARY, new UuidV7BinaryTypeHandler());
            configuration.getTypeHandlerRegistry().register(
                Instant.class, JdbcType.TIMESTAMP, new UtcInstantTypeHandler());
            configuration.addMapper(RuntimeMapper.class);

            try (var sqlSession = new SqlSessionFactoryBuilder()
                .build(configuration).openSession(false)) {
                MybatisRuntimeStore store = new MybatisRuntimeStore(
                    sqlSession.getMapper(RuntimeMapper.class), JsonMapper.builder().build());
                RuntimeApplicationService service = new RuntimeApplicationService(
                    store, store, store, store,
                    ignored -> new SnapshotDescriptor(
                        revisionId, snapshotId, snapshotHash, 1,
                        "agentscope-java-2", "{\"schemaVersion\":1}"),
                    new FakeAgentExecutionEngine(), Clock.fixed(now, ZoneOffset.UTC));
                Session session = service.createSession(new CreateSessionCommand(
                    organizationId, projectId, deploymentId, revisionId, snapshotId,
                    snapshotHash, Map.of("actor", "integration"), Map.of("channel", "api"),
                    "mysql-session", Checksum.sha256("mysql-session-request")));
                Turn turn = service.acceptTurn(new AcceptTurnCommand(
                    organizationId, projectId, session.id(),
                    RuntimePayload.inline("{\"text\":\"hello\"}"), Checksum.sha256("hello"),
                    "agentscope-java-2", "compiler-v1", 10,
                    "mysql-turn", Checksum.sha256("mysql-turn-request")));

                ExecutionResult result = service.executeNext(
                    new ExecuteNextCommand("runtime-it", Duration.ofSeconds(30))).orElseThrow();

                assertThat(result.outcome()).isEqualTo(ExecutionOutcome.SUCCEEDED);
                assertThat(store.findTurn(turn.id()).orElseThrow().status())
                    .isEqualTo(TurnStatus.COMPLETED);
                assertThat(store.listAfter(session.id(), 0, 100))
                    .extracting(RuntimeEvent::sessionSequence)
                    .containsExactly(1L, 2L, 3L, 4L);
                assertThat(store.findIdempotency(
                    "TURN_ACCEPT", session.id().asString(), "mysql-turn")).isPresent();
                sqlSession.commit();
            }
        }

        try (var connection = ownerConnection(); var statement = connection.createStatement();
            var result = statement.executeQuery("""
                SELECT (SELECT COUNT(*) FROM session),
                       (SELECT COUNT(*) FROM turn),
                       (SELECT COUNT(*) FROM run),
                       (SELECT COUNT(*) FROM runtime_event),
                       (SELECT COUNT(*) FROM runtime_outbox)
                """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
            assertThat(result.getInt(2)).isEqualTo(1);
            assertThat(result.getInt(3)).isEqualTo(1);
            assertThat(result.getInt(4)).isEqualTo(4);
            assertThat(result.getInt(5)).isEqualTo(2);
        }
    }

    /**
     * 使用一个独立数据库事务锁定 Session 与 Run 计数器并追加 Event。
     *
     * @throws SQLException 事务或 Event 插入失败时抛出
     */
    private void appendEventInTransaction() throws SQLException {
        try (var connection = ownerConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeQuery(
                    "SELECT event_sequence FROM session WHERE id = UNHEX(REPLACE('"
                        + SESSION_ID + "','-','')) FOR UPDATE").close();
                statement.executeQuery(
                    "SELECT event_sequence FROM run WHERE id = UNHEX(REPLACE('"
                        + RUN_ID + "','-','')) FOR UPDATE").close();
                statement.execute(
                    "UPDATE session SET event_sequence = event_sequence + 1 WHERE id = "
                        + "UNHEX(REPLACE('" + SESSION_ID + "','-',''))");
                statement.execute(
                    "UPDATE run SET event_sequence = event_sequence + 1 WHERE id = "
                        + "UNHEX(REPLACE('" + RUN_ID + "','-',''))");
                statement.execute("""
                    INSERT INTO runtime_event
                        (id, organization_id, project_id, session_id, turn_id, run_id,
                         session_sequence, run_sequence, type, schema_version,
                         trace_id, payload_storage, payload_json, occurred_at, fencing_token)
                    SELECT UUID_TO_BIN(UUID()), organization_id, project_id,
                           UNHEX(REPLACE('019d0000-0000-7000-8000-000000000101','-','')),
                           UNHEX(REPLACE('019d0000-0000-7000-8000-000000000106','-','')),
                           id, (SELECT event_sequence FROM session WHERE id =
                               UNHEX(REPLACE('019d0000-0000-7000-8000-000000000101','-',''))),
                           event_sequence, 'run.observed', 1,
                           '019d0000000070008000000000000107', 'INLINE', JSON_OBJECT(),
                           UTC_TIMESTAMP(6), fencing_token
                    FROM run
                    WHERE id = UNHEX(REPLACE('019d0000-0000-7000-8000-000000000107','-',''))
                    """);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    /**
     * 初始化持有 Fencing Token 1 的 Session、Turn、Run 与 Work Item。
     *
     * @throws SQLException 初始化失败时抛出
     */
    private void seedRunningAggregate() throws SQLException {
        try (var connection = ownerConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                INSERT INTO session
                    (id, organization_id, project_id, deployment_id, revision_id, snapshot_id,
                     snapshot_hash, participant_metadata, channel_metadata, status,
                     event_sequence, version, created_at, updated_at)
                VALUES
                    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000101','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000001','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000002','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000103','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000104','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000105','-','')),
                     UNHEX(REPEAT('11', 32)), JSON_OBJECT(), JSON_OBJECT(),
                     'ACTIVE', 0, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
            statement.execute("""
                INSERT INTO turn
                    (id, organization_id, project_id, session_id, sequence, input_storage,
                     input_json, input_object_uri, input_object_size, input_media_type,
                     input_hash, status, current_run_id, fencing_token, version,
                     created_at, updated_at)
                VALUES
                    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000106','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000001','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000002','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000101','-','')),
                     1, 'INLINE', JSON_OBJECT('text', 'hello'), NULL, NULL, NULL,
                     UNHEX(REPEAT('22', 32)), 'RUNNING',
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000107','-','')),
                     1, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
            statement.execute("""
                INSERT INTO run
                    (id, organization_id, project_id, session_id, turn_id, attempt_number,
                     runtime_provider, compiler_version, status, event_sequence, fencing_token,
                     started_at, ended_at, error_code, created_at)
                VALUES
                    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000107','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000001','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000002','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000101','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000106','-','')),
                     1, 'fake', 'fake-v1', 'RUNNING', 0, 1,
                     UTC_TIMESTAMP(6), NULL, NULL, UTC_TIMESTAMP(6))
                """);
            statement.execute("""
                INSERT INTO runtime_work_item
                    (id, run_id, status, priority, available_at, claimed_by, claim_until,
                     fencing_token, attempt_count, created_at, updated_at)
                VALUES
                    (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000108','-','')),
                     UNHEX(REPLACE('019d0000-0000-7000-8000-000000000107','-','')),
                     'CLAIMED', 10, UTC_TIMESTAMP(6), 'runtime-test',
                     DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR), 1, 1,
                     UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
        }
    }

    /**
     * 在当前连接分配首个 Event 序号并追加接受 Event。
     *
     * @param statement SQL Statement
     * @throws SQLException 序号更新或 Event 插入失败时抛出
     */
    private void allocateAndInsertEvent(java.sql.Statement statement) throws SQLException {
        statement.execute(
            "UPDATE session SET event_sequence = 1 WHERE id = UNHEX(REPLACE('"
                + SESSION_ID + "','-',''))");
        statement.execute(
            "UPDATE run SET event_sequence = 1 WHERE id = UNHEX(REPLACE('"
                + RUN_ID + "','-',''))");
        statement.execute("""
            INSERT INTO runtime_event
                (id, organization_id, project_id, session_id, turn_id, run_id,
                 session_sequence, run_sequence, type, schema_version,
                 trace_id, payload_storage, payload_json, occurred_at, fencing_token)
            VALUES
                (UNHEX(REPLACE('019d0000-0000-7000-8000-000000000109','-','')),
                 UNHEX(REPLACE('019d0000-0000-7000-8000-000000000001','-','')),
                 UNHEX(REPLACE('019d0000-0000-7000-8000-000000000002','-','')),
                 UNHEX(REPLACE('019d0000-0000-7000-8000-000000000101','-','')),
                 UNHEX(REPLACE('019d0000-0000-7000-8000-000000000106','-','')),
                 UNHEX(REPLACE('019d0000-0000-7000-8000-000000000107','-','')),
                 1, 1, 'run.observed', 1, '019d0000000070008000000000000107',
                 'INLINE', JSON_OBJECT(), UTC_TIMESTAMP(6), 1)
            """);
    }
}
