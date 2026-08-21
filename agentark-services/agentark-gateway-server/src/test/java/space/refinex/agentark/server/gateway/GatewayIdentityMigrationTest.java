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

package space.refinex.agentark.server.gateway;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import space.refinex.agentark.foundation.redis.RateLimitDecision;
import space.refinex.agentark.foundation.redis.RateLimiter;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 使用真实 MySQL 8.4 验证 Identity V1 表、数据库注释、状态约束和种子权限。
 *
 * @author refinex
 */
@Testcontainers(disabledWithoutDocker = true)
class GatewayIdentityMigrationTest {

    /** 固定与本地 Compose 一致的 MySQL 8.4.11。 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
        .withDatabaseName("agentark_identity")
        .withUsername("agentark_identity")
        .withPassword("identity-test-password");

    /**
     * 证明 V1 可在真实 MySQL 迁移，十三张业务表及全部表/字段中文注释实际写入。
     *
     * @throws Exception 当数据库验证失败时向 JUnit 报告原始上下文
     */
    @Test
    void migratesIdentitySchemaWithCommentsAndSecuritySeed() throws Exception {
        migrate();

        try (Connection connection = DriverManager.getConnection(
            MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            assertThat(singleLong(statement, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'agentark_identity'
                  AND table_name <> 'flyway_schema_history'
                """)).isEqualTo(13);
            assertThat(singleLong(statement, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'agentark_identity'
                  AND table_name <> 'flyway_schema_history'
                  AND table_comment <> ''
                """)).isEqualTo(13);
            long columns = singleLong(statement, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'agentark_identity'
                  AND table_name <> 'flyway_schema_history'
                """);
            assertThat(singleLong(statement, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'agentark_identity'
                  AND table_name <> 'flyway_schema_history'
                  AND column_comment <> ''
                """)).isEqualTo(columns);
            assertThat(singleLong(statement, "SELECT COUNT(*) FROM identity_permission"))
                .isEqualTo(6);
            assertThat(singleLong(statement, "SELECT COUNT(*) FROM identity_role"))
                .isEqualTo(3);
            assertThat(singleString(statement, """
                SELECT state FROM identity_bootstrap_state
                WHERE singleton_key = 'built-in-identity'
                """)).isEqualTo("UNINITIALIZED");
        }
    }

    /**
     * 证明本人改密在真实 MySQL 中校验当前摘要、追加历史、递增认证版本并记录安全事件。
     *
     * @throws Exception 当数据库装配或查询失败时向 JUnit 报告原始上下文
     */
    @Test
    void changesOwnPasswordAgainstRealMySql() throws Exception {
        migrate();
        GatewayIdentityProperties properties = new GatewayIdentityProperties();
        properties.setPasswordPepper("identity-integration-pepper");
        GatewayIdentityPasswordService passwords = new GatewayIdentityPasswordService(properties);

        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(MYSQL.getJdbcUrl());
        dataSource.setUser(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        GatewayIdentityRepository repository = new GatewayIdentityRepository(
            new JdbcTemplate(dataSource),
            new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
            new ObjectMapper(),
            properties);
        Instant now = Instant.parse("2026-08-22T01:00:00Z");
        repository.bootstrap(
            "agentark-admin",
            "agentark-admin@example.test",
            "AgentArk Administrator",
            passwords.encode("temporary password phrase"),
            now);
        repository.changePassword(
            GatewayIdentityRepository.BOOTSTRAP_ADMIN_ID,
            passwords.encode("current password phrase"),
            GatewayIdentityRepository.BOOTSTRAP_ADMIN_ID.toString(),
            now.plusSeconds(1));

        ReactiveRedisIndexedSessionRepository sessions =
            mock(ReactiveRedisIndexedSessionRepository.class);
        when(sessions.findByPrincipalName("agentark-admin")).thenReturn(Mono.just(Map.of()));
        RateLimiter rateLimiter = mock(RateLimiter.class);
        when(rateLimiter.acquire(eq("identity-password-change"), anyString(), eq(5L),
            eq(Duration.ofMinutes(1))))
            .thenReturn(new RateLimitDecision(true, 4, Duration.ZERO));
        GatewayIdentityService service = new GatewayIdentityService(
            properties,
            passwords,
            repository,
            sessions,
            rateLimiter,
            Clock.fixed(now.plusSeconds(2), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.changeOwnPassword(
            GatewayIdentityRepository.BOOTSTRAP_ADMIN_ID,
            "wrong current password",
            "replacement password phrase").block())
            .isInstanceOf(BadCredentialsException.class);

        service.changeOwnPassword(
            GatewayIdentityRepository.BOOTSTRAP_ADMIN_ID,
            "current password phrase",
            "replacement password phrase").block();

        GatewayIdentityModels.Account changed = repository.findById(
            GatewayIdentityRepository.BOOTSTRAP_ADMIN_ID).orElseThrow();
        assertThat(passwords.matches("replacement password phrase", changed.passwordHash())).isTrue();
        assertThat(passwords.matches("current password phrase", changed.passwordHash())).isFalse();
        assertThat(changed.authVersion()).isEqualTo(2L);
        assertThat(repository.recentPasswordHashes(changed.id())).hasSize(2);
        assertThat(repository.listSecurityEvents())
            .anySatisfy(event -> {
                assertThat(event.eventType()).isEqualTo("PASSWORD_CHANGED");
                assertThat(event.result()).isEqualTo("DENIED");
                assertThat(event.detailCode()).isEqualTo("CURRENT_PASSWORD_MISMATCH");
            })
            .anySatisfy(event -> {
                assertThat(event.eventType()).isEqualTo("PASSWORD_CHANGED");
                assertThat(event.result()).isEqualTo("SUCCESS");
            });
    }

    /** 在隔离测试容器中清理并重新执行 Identity V1，避免测试方法共享账号状态。 */
    private static void migrate() {
        Flyway flyway = Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration/identity")
            .defaultSchema("agentark_identity")
            .schemas("agentark_identity")
            .cleanDisabled(false)
            .validateMigrationNaming(true)
            .load();
        flyway.clean();
        flyway.migrate();
    }

    /** 执行只返回一个整数的验证查询。 */
    private static long singleLong(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    /** 执行只返回一个字符串的验证查询。 */
    private static String singleString(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
