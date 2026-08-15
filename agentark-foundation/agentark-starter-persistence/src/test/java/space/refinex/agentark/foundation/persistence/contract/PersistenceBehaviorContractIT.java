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

package space.refinex.agentark.foundation.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import space.refinex.agentark.kernel.id.OrganizationId;
import tools.jackson.databind.json.JsonMapper;

/**
 * 在真实 MySQL 8.4 上覆盖机械 JPA 基线迁往 MyBatis-Plus 后必须保持的仓储语义。
 *
 * @author refinex
 */
@Testcontainers
@SpringBootTest(
    classes = PersistenceContractApplication.class,
    properties = {
        "spring.flyway.locations=classpath:db/contract",
        "spring.flyway.schemas=agentark_contract",
        "spring.flyway.default-schema=agentark_contract",
        "spring.flyway.create-schemas=false",
        "spring.flyway.clean-disabled=true",
        "spring.flyway.validate-migration-naming=true",
        "spring.datasource.hikari.connection-init-sql=SET SESSION time_zone = '+00:00'",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true",
        "agentark.foundation.persistence.slow-query-threshold=10s"
    })
class PersistenceBehaviorContractIT {

    /**
     * 创建真实 MySQL 持久化行为契约测试实例。
     */
    PersistenceBehaviorContractIT() {
        // JUnit Jupiter 为每个测试生命周期创建实例。
    }

    /**
     * 容器启动时随机生成的临时口令，不写入 Fixture 或日志。
     */
    private static final String CONTAINER_PASSWORD =
        UUID.randomUUID().toString().replace("-", "");

    /**
     * 契约测试使用的固定 MySQL 8.4 容器。
     */
    @Container
    private static final MySQLContainer MYSQL =
        new MySQLContainer(DockerImageName.parse("mysql:8.4.11"))
            .withDatabaseName("agentark_contract")
            .withUsername("agentark_contract")
            .withPassword(CONTAINER_PASSWORD);

    /**
     * 契约测试 Mapper。
     */
    @Autowired
    private PersistenceContractMapper mapper;

    /**
     * 事务回滚测试夹具。
     */
    @Autowired
    private PersistenceContractTransactionFixture transactionFixture;

    /**
     * Jackson 3 JSON 映射器。
     */
    @Autowired
    private JsonMapper jsonMapper;

    /**
     * 将 Testcontainers 的临时 JDBC 地址和凭据注入 Spring DataSource。
     *
     * @param registry 动态配置注册表
     */
    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    /**
     * 每个测试前清空临时表，保持断言相互独立。
     */
    @BeforeEach
    void clearFixtureTable() {
        mapper.delete(new LambdaQueryWrapper<>());
    }

    /**
     * 验证 UUIDv7、Instant、JSON 往返，以及 Scope 内排序和分页保持稳定。
     *
     * @throws Exception 构造 JSON 失败时抛出
     */
    @Test
    void roundTripsTypesAndProvidesStableSortingAndPaging() throws Exception {
        UUID organizationId = OrganizationId.generate().value();
        Instant observedAt = Instant.parse("2026-08-15T12:34:56.123456Z");
        PersistenceContractRecordDO beta = record(organizationId, "beta", observedAt);
        mapper.insert(beta);
        mapper.insert(record(organizationId, "alpha", observedAt.plusSeconds(1)));
        mapper.insert(record(organizationId, "gamma", observedAt.plusSeconds(2)));

        Page<PersistenceContractRecordDO> page = new Page<>(1, 2);
        mapper.selectPage(
            page,
            new LambdaQueryWrapper<PersistenceContractRecordDO>()
                .eq(PersistenceContractRecordDO::getOrganizationId, organizationId)
                .orderByAsc(PersistenceContractRecordDO::getRecordKey)
                .orderByAsc(PersistenceContractRecordDO::getId));

        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getRecords())
            .extracting(PersistenceContractRecordDO::getRecordKey)
            .containsExactly("alpha", "beta");
        PersistenceContractRecordDO restored = mapper.selectById(beta.getId());
        assertThat(restored.getId()).isEqualTo(beta.getId());
        assertThat(restored.getOrganizationId()).isEqualTo(organizationId);
        assertThat(restored.getObservedAt()).isEqualTo(observedAt);
        assertThat(restored.getPayload()).isEqualTo(jsonMapper.readTree("{\"kind\":\"contract\"}"));
    }

    /**
     * 验证两个读取副本并发更新时只有携带当前 version 的写入成功。
     */
    @Test
    void rejectsStaleOptimisticLockUpdates() {
        PersistenceContractRecordDO inserted =
            record(OrganizationId.generate().value(), "optimistic", Instant.now());
        mapper.insert(inserted);
        PersistenceContractRecordDO first = mapper.selectById(inserted.getId());
        PersistenceContractRecordDO stale = mapper.selectById(inserted.getId());

        first.setRecordKey("first-wins");
        stale.setRecordKey("stale-loses");

        assertThat(mapper.updateById(first)).isEqualTo(1);
        assertThat(mapper.updateById(stale)).isZero();
        assertThat(mapper.selectById(inserted.getId()).getRecordKey()).isEqualTo("first-wins");
    }

    /**
     * 验证唯一约束不会被 Mapper 静默吞掉，并且异常会回滚整个 Spring 事务。
     */
    @Test
    void preservesUniqueConstraintsAndTransactionRollback() {
        UUID organizationId = OrganizationId.generate().value();
        mapper.insert(record(organizationId, "unique", Instant.now()));

        assertThatThrownBy(() -> mapper.insert(record(organizationId, "unique", Instant.now())))
            .isInstanceOf(DuplicateKeyException.class);

        PersistenceContractRecordDO rollback =
            record(organizationId, "rollback", Instant.now());
        assertThatThrownBy(() -> transactionFixture.insertThenFail(rollback))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("intentional contract rollback");
        assertThat(mapper.selectById(rollback.getId())).isNull();
    }

    /**
     * 创建具备完整受控字段的契约测试记录。
     *
     * @param organizationId Organization UUIDv7
     * @param recordKey      Scope 内记录键
     * @param observedAt     微秒精度 UTC 时刻
     * @return 可直接交给 Mapper 的数据对象
     */
    private PersistenceContractRecordDO record(
        UUID organizationId, String recordKey, Instant observedAt) {
        PersistenceContractRecordDO record = new PersistenceContractRecordDO();
        record.setId(OrganizationId.generate().value());
        record.setOrganizationId(organizationId);
        record.setRecordKey(recordKey);
        record.setPayload(jsonMapper.createObjectNode().put("kind", "contract"));
        record.setObservedAt(observedAt);
        record.setVersion(0L);
        return record;
    }
}
