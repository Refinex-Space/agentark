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

package space.refinex.agentark.foundation.persistence.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 使用固定 MySQL 8.4 镜像验证所属平面的空库迁移、N-1 升级框架和 Schema 权限边界。
 *
 * @author refinex
 */
@Testcontainers
public abstract class AbstractMySqlMigrationIT {

    /**
     * 创建所属平面 MySQL 迁移测试基类实例。
     */
    protected AbstractMySqlMigrationIT() {
        // JUnit Jupiter 通过具体 Owner 子类创建测试实例。
    }

    /**
     * 容器启动时随机生成的临时引导口令，不写入源码、Fixture 或日志。
     */
    private static final String BOOTSTRAP_PASSWORD = randomPassword();

    /**
     * 三个测试 Owner 共用的运行期随机口令，仅存在于当前测试进程和临时容器。
     */
    private static final String OWNER_PASSWORD = randomPassword();

    /**
     * 所有平面迁移测试固定使用与本地 Core 相同的 MySQL 8.4 补丁镜像。
     */
    @Container
    protected static final MySQLContainer MYSQL =
        new MySQLContainer(DockerImageName.parse("mysql:8.4.11"))
            .withDatabaseName("agentark_bootstrap")
            .withUsername("agentark_bootstrap")
            .withPassword(BOOTSTRAP_PASSWORD)
            .withCommand("--log-bin-trust-function-creators=ON");

    /**
     * 以容器内临时 root 身份创建三套 Schema、同名最小权限账号和越权哨兵表。
     *
     * @throws SQLException 初始化数据库所有权失败时抛出
     */
    @BeforeAll
    static void initializeSchemaOwners() throws SQLException {
        try (Connection connection =
                java.sql.DriverManager.getConnection(
                    jdbcUrl("mysql"), "root", BOOTSTRAP_PASSWORD);
            Statement statement = connection.createStatement()) {
            for (String schema :
                Set.of("agentark_control", "agentark_runtime", "agentark_scheduler")) {
                requireIdentifier(schema);
                statement.execute(
                    "CREATE DATABASE "
                        + schema
                        + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                statement.execute(
                    "CREATE USER '"
                        + schema
                        + "'@'%' IDENTIFIED BY '"
                        + OWNER_PASSWORD
                        + "'");
                statement.execute(
                    "GRANT ALL PRIVILEGES ON " + schema + ".* TO '" + schema + "'@'%'");
                statement.execute(
                    "CREATE TABLE " + schema + ".ownership_sentinel (id INT PRIMARY KEY)");
            }
        }
    }

    /**
     * 每个测试前只用所属账号清理所属 Schema，禁止借助 root 或跨 Schema 权限。
     */
    @BeforeEach
    void resetOwnerSchema() {
        requireIdentifier(schemaName());
        requireIdentifier(forbiddenSchemaName());
        testFlyway().clean();
    }

    /**
     * 证明当前迁移可从空库执行，且只创建所属阶段声明的业务表。
     *
     * @throws SQLException 查询迁移结果失败时抛出
     */
    @Test
    void migratesAnEmptySchemaToExpectedVersion() throws SQLException {
        var result = currentFlyway().migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).hasToString(expectedVersion());
        try (Connection connection = ownerConnection();
            var statement = connection.prepareStatement(
                "SELECT table_name FROM information_schema.tables "
                    + "WHERE table_schema = ? AND table_name <> 'flyway_schema_history'")) {
            statement.setString(1, schemaName());
            try (ResultSet tables = statement.executeQuery()) {
                Set<String> actual = new java.util.HashSet<>();
                while (tables.next()) {
                    actual.add(tables.getString(1));
                }
                assertThat(actual).containsExactlyInAnyOrderElementsOf(expectedBusinessTables());
            }
        }
    }

    /**
     * 证明所有业务表和字段的中文注释已实际写入 MySQL 元数据，而不是只存在于 SQL 行注释中。
     *
     * @throws SQLException 查询 MySQL 元数据失败时抛出
     */
    @Test
    void persistsChineseCommentsForEveryBusinessTableAndColumn() throws SQLException {
        var result = currentFlyway().migrate();
        assertThat(result.success).isTrue();

        Set<String> actualTables = new java.util.HashSet<>();
        try (Connection connection = ownerConnection();
            var statement = connection.prepareStatement(
                "SELECT table_name, table_comment FROM information_schema.tables "
                    + "WHERE table_schema = ? AND table_name <> 'flyway_schema_history'")) {
            statement.setString(1, schemaName());
            try (ResultSet tables = statement.executeQuery()) {
                while (tables.next()) {
                    actualTables.add(tables.getString(1));
                    assertThat(tables.getString(2)).containsPattern("[\\p{IsHan}]");
                }
            }
        }
        assertThat(actualTables).containsExactlyInAnyOrderElementsOf(expectedBusinessTables());

        try (Connection connection = ownerConnection();
            var statement = connection.prepareStatement(
                "SELECT table_name, column_name, column_comment FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name <> 'flyway_schema_history'")) {
            statement.setString(1, schemaName());
            try (ResultSet columns = statement.executeQuery()) {
                while (columns.next()) {
                    assertThat(columns.getString(3))
                        .as("字段注释 %s.%s", columns.getString(1), columns.getString(2))
                        .containsPattern("[\\p{IsHan}]");
                }
            }
        }
    }

    /**
     * 证明先迁移到 Owner 声明的上一版本再升级到当前版本可重复执行。
     */
    @Test
    void upgradesFromPreviousVersionToCurrentVersion() {
        Flyway previous = "0".equals(previousVersion())
            ? previousBaselineFlyway()
            : flyway(MigrationVersion.fromVersion(previousVersion()));
        if ("0".equals(previousVersion())) {
            previous.baseline();
            assertThat(previous.info().current().getVersion()).hasToString("0");
        } else {
            var previousResult = previous.migrate();
            assertThat(previousResult.success).isTrue();
            assertThat(previous.info().current().getVersion()).hasToString(previousVersion());
        }

        var result = currentFlyway().migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).hasToString(expectedVersion());
    }

    /**
     * 证明连接会话使用 UTC、严格模式、utf8mb4 和固定排序规则。
     *
     * @throws SQLException 查询会话与 Schema 参数失败时抛出
     */
    @Test
    void usesUtcStrictModeAndFixedCharacterRules() throws SQLException {
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet settings =
                statement.executeQuery(
                    "SELECT @@session.time_zone, @@session.sql_mode, "
                        + "@@character_set_database, @@collation_database")) {
                assertThat(settings.next()).isTrue();
                assertThat(settings.getString(1)).isEqualTo("+00:00");
                assertThat(Set.of(settings.getString(2).split(",")))
                    .contains("STRICT_TRANS_TABLES", "ONLY_FULL_GROUP_BY");
                assertThat(settings.getString(3)).isEqualTo("utf8mb4");
                assertThat(settings.getString(4)).isEqualTo("utf8mb4_0900_ai_ci");
            }
        }
    }

    /**
     * 证明所属平面迁移账号无法读取另一个 Schema 的哨兵表。
     */
    @Test
    void rejectsCrossSchemaAccess() {
        assertThatThrownBy(
                () -> {
                    try (Connection connection = ownerConnection();
                        Statement statement = connection.createStatement()) {
                        statement.executeQuery(
                            "SELECT id FROM " + forbiddenSchemaName() + ".ownership_sentinel");
                    }
                })
            .isInstanceOf(SQLException.class);
    }

    /**
     * 返回当前平面拥有的 MySQL Schema 名称。
     *
     * @return 符合小写下划线规范的 Schema 名称
     */
    protected abstract String schemaName();

    /**
     * 返回当前平面禁止访问的哨兵 Schema 名称。
     *
     * @return 与所属 Schema 不同的合法名称
     */
    protected abstract String forbiddenSchemaName();

    /**
     * 返回当前平面的 Flyway classpath Location。
     *
     * @return 以 classpath:db/migration 开头的 Location
     */
    protected abstract String migrationLocation();

    /**
     * 返回当前 Owner 应迁移到的最新版本。
     *
     * @return 正整数 Flyway 版本
     */
    protected String expectedVersion() {
        return "1";
    }

    /**
     * 返回当前 Owner 上一已发布版本。
     *
     * @return 小于最新版本的非负 Flyway 版本
     */
    protected String previousVersion() {
        return "0";
    }

    /**
     * 返回当前阶段应存在的业务表集合，不包含 Flyway 历史表。
     *
     * @return 不可变表名集合
     */
    protected Set<String> expectedBusinessTables() {
        return Set.of();
    }

    /**
     * 创建迁移到最新版本的 Flyway 实例。
     *
     * @return 只作用于所属 Schema 的 Flyway
     */
    private Flyway currentFlyway() {
        return flyway(MigrationVersion.LATEST);
    }

    /**
     * 创建目标版本受控且禁止自动建 Schema 的 Flyway 实例。
     *
     * @param target 目标迁移版本
     * @return Flyway 实例
     */
    private Flyway flyway(MigrationVersion target) {
        return Flyway.configure()
            .dataSource(ownerJdbcUrl(), schemaName(), OWNER_PASSWORD)
            .locations(migrationLocation())
            .schemas(schemaName())
            .defaultSchema(schemaName())
            .table("flyway_schema_history")
            .createSchemas(false)
            .cleanDisabled(true)
            .validateMigrationNaming(true)
            .target(target)
            .load();
    }

    /**
     * 创建仅用于模拟尚无版本化业务表的零版本基线实例。
     *
     * @return 以版本零写入历史表且不创建业务表的 Flyway
     */
    private Flyway previousBaselineFlyway() {
        return Flyway.configure()
            .dataSource(ownerJdbcUrl(), schemaName(), OWNER_PASSWORD)
            .locations(migrationLocation())
            .schemas(schemaName())
            .defaultSchema(schemaName())
            .table("flyway_schema_history")
            .createSchemas(false)
            .cleanDisabled(true)
            .baselineVersion(MigrationVersion.fromVersion("0"))
            .baselineDescription("测试零版本基线")
            .load();
    }

    /**
     * 创建允许测试专用 clean 的 Flyway 实例，只能清理所属 Schema。
     *
     * @return 测试清理实例
     */
    private Flyway testFlyway() {
        return Flyway.configure()
            .dataSource(ownerJdbcUrl(), schemaName(), OWNER_PASSWORD)
            .locations(migrationLocation())
            .schemas(schemaName())
            .defaultSchema(schemaName())
            .table("flyway_schema_history")
            .createSchemas(false)
            .cleanDisabled(false)
            .load();
    }

    /**
     * 创建只拥有当前 Schema 权限的测试连接。
     *
     * @return 最小权限测试连接
     * @throws SQLException 连接失败时抛出
     */
    protected final Connection ownerConnection() throws SQLException {
        return java.sql.DriverManager.getConnection(
            ownerJdbcUrl(), schemaName(), OWNER_PASSWORD);
    }

    /**
     * 供具体 Owner 的附加数据库行为测试迁移到当前最新版本。
     */
    protected final void migrateCurrentSchema() {
        currentFlyway().migrate();
    }

    /**
     * 返回当前 Schema 的 JDBC URL，并强制驱动把会话时区固定为 UTC。
     *
     * @return MySQL JDBC URL
     */
    private String ownerJdbcUrl() {
        return jdbcUrl(schemaName());
    }

    /**
     * 生成不含凭据的测试 JDBC URL。
     *
     * @param database 数据库名称
     * @return 使用 UTC 和 Unicode 的 JDBC URL
     */
    private static String jdbcUrl(String database) {
        requireIdentifier(database);
        return "jdbc:mysql://"
            + MYSQL.getHost()
            + ":"
            + MYSQL.getMappedPort(3306)
            + "/"
            + database
            + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
            + "&characterEncoding=UTF-8&useUnicode=true";
    }

    /**
     * 限制测试 Schema 标识符为代码内固定的小写下划线形式，防止拼接动态 SQL。
     *
     * @param identifier 待校验标识符
     */
    private static void requireIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("schema identifier is invalid");
        }
    }

    /**
     * 生成不含引号或控制字符的运行期随机口令，避免测试凭据进入版本库。
     *
     * @return 由 UUID 随机位组成的十六进制口令
     */
    private static String randomPassword() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
