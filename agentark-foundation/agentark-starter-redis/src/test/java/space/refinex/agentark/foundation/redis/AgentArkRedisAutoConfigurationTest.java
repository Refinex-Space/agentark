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

package space.refinex.agentark.foundation.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 验证 Redis Starter 默认关闭、显式启用和 Key Namespace/TTL 安全约束。
 *
 * @author refinex
 */
class AgentArkRedisAutoConfigurationTest {

  /** Redis 自动配置测试运行器。 */
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
          .withConfiguration(AutoConfigurations.of(AgentArkRedisAutoConfiguration.class));

  /** 验证未显式启用时不创建任何 Redis 语义组件。 */
  @Test
  void remainsDisabledByDefault() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(TypedCacheFactory.class));
  }

  /** 验证显式应用命名空间配置后创建缓存与四类协调契约。 */
  @Test
  void configuresSemanticRedisComponentsWhenEnabled() {
    contextRunner
        .withPropertyValues(
            "agentark.foundation.redis.enabled=true",
            "agentark.foundation.redis.application-name=runtime")
        .run(
            context -> {
              assertThat(context).hasSingleBean(TypedCacheFactory.class);
              assertThat(context).hasSingleBean(DistributedLeaseManager.class);
              assertThat(context).hasSingleBean(FencingTokenSource.class);
              assertThat(context).hasSingleBean(IdempotencyStore.class);
              assertThat(context).hasSingleBean(RateLimiter.class);
            });
  }

  /** 验证启用后缺少应用名称会失败，避免不同服务静默共用 Key。 */
  @Test
  void rejectsMissingApplicationNamespace() {
    contextRunner
        .withPropertyValues("agentark.foundation.redis.enabled=true")
        .run(context -> assertThat(context).hasFailed());
  }

  /** 验证业务键被编码且 TTL 不能超过配置上限。 */
  @Test
  void encodesBusinessKeysAndEnforcesTtl() {
    AgentArkRedisProperties properties = new AgentArkRedisProperties();
    properties.setApplicationName("runtime");
    properties.setMaxTtl(Duration.ofMinutes(5));
    RedisKeyNamespace namespace = new RedisKeyNamespace(properties);

    assertThat(namespace.key("lease", "tenant:../../escape"))
        .startsWith("agentark:runtime:lease:")
        .doesNotContain("../");
    assertThatThrownBy(() -> namespace.requireTtl(Duration.ofMinutes(6)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
