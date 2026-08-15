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

package space.refinex.agentark.foundation.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import space.refinex.agentark.kernel.id.OrganizationId;
import tools.jackson.databind.json.JsonMapper;

/**
 * 验证 Persistence Starter 的 DataSource/开关条件、拦截器顺序和 UUIDv7 二进制映射。
 *
 * @author refinex
 */
class AgentArkPersistenceAutoConfigurationTest {

  /** 持久化自动配置测试运行器。 */
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(DataSource.class, () -> mock(DataSource.class))
          .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
          .withConfiguration(AutoConfigurations.of(AgentArkPersistenceAutoConfiguration.class));

  /** 验证存在 DataSource 时配置 MySQL 分页、乐观锁和 TypeHandler 定制器。 */
  @Test
  void configuresPersistenceFoundationWhenDataSourceExists() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
          assertThat(context).hasSingleBean(ConfigurationCustomizer.class);
          var interceptors = context.getBean(MybatisPlusInterceptor.class).getInterceptors();
          assertThat(interceptors)
              .hasSize(2)
              .element(0)
              .isInstanceOf(PaginationInnerInterceptor.class);
          assertThat(interceptors.get(1)).isInstanceOf(OptimisticLockerInnerInterceptor.class);
        });
  }

  /** 验证显式禁用时不创建持久化增强 Bean。 */
  @Test
  void backsOffWhenDisabled() {
    contextRunner
        .withPropertyValues("agentark.foundation.persistence.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(MybatisPlusInterceptor.class));
  }

  /** 验证没有 DataSource 时自动配置保持静默，不虚构可运行持久化环境。 */
  @Test
  void backsOffWithoutDataSource() {
    new ApplicationContextRunner()
        .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
        .withConfiguration(AutoConfigurations.of(AgentArkPersistenceAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(MybatisPlusInterceptor.class));
  }

  /** 验证 UUIDv7 以固定网络字节序写入 BINARY(16)。 */
  @Test
  void writesUuidV7AsSixteenBytes() throws Exception {
    UUID uuid = OrganizationId.generate().value();
    PreparedStatement statement = mock(PreparedStatement.class);

    new UuidV7BinaryTypeHandler()
        .setNonNullParameter(statement, 1, uuid, org.apache.ibatis.type.JdbcType.BINARY);

    var expected =
        ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits())
            .array();
    org.mockito.Mockito.verify(statement).setBytes(1, expected);
  }
}
