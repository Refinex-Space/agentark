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

package space.refinex.agentark.foundation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import space.refinex.agentark.kernel.error.DomainErrorCode;
import space.refinex.agentark.kernel.error.DomainException;
import space.refinex.agentark.kernel.id.OrganizationId;
import tools.jackson.databind.json.JsonMapper;

/**
 * 验证 Web Starter 公共、Servlet、Reactive 条件和安全错误/ID 序列化契约。
 *
 * @author refinex
 */
class AgentArkWebAutoConfigurationTest {

  /** 公共自动配置测试运行器。 */
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AgentArkWebAutoConfiguration.class));

  /** 验证缺省启用时装配请求上下文、错误和 Jackson 定制能力。 */
  @Test
  void configuresCommonWebFoundationByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(RequestContextAccessor.class);
          assertThat(context).hasSingleBean(RequestContextFactory.class);
          assertThat(context).hasSingleBean(ProblemDetailFactory.class);
          assertThat(context).hasBean("agentArkJsonMapperBuilderCustomizer");
        });
  }

  /** 验证显式禁用时不装配任何公共 Web Bean。 */
  @Test
  void backsOffWhenDisabled() {
    contextRunner
        .withPropertyValues("agentark.foundation.web.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(RequestContextFactory.class));
  }

  /** 验证 Servlet 应用只装配 Servlet 过滤器和异常解析器。 */
  @Test
  void configuresServletStackConditionally() {
    new WebApplicationContextRunner()
        .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
        .withConfiguration(
            AutoConfigurations.of(
                AgentArkWebAutoConfiguration.class,
                AgentArkServletWebAutoConfiguration.class,
                AgentArkReactiveWebAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(AgentArkServletRequestContextFilter.class);
              assertThat(context).hasBean("agentArkProblemDetailExceptionResolver");
              assertThat(context).doesNotHaveBean("agentArkReactiveRequestContextFilter");
            });
  }

  /** 验证 Reactive 应用只装配 Reactor Context 过滤器和异常处理器。 */
  @Test
  void configuresReactiveStackConditionally() {
    new ReactiveWebApplicationContextRunner()
        .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
        .withConfiguration(
            AutoConfigurations.of(
                AgentArkWebAutoConfiguration.class,
                AgentArkServletWebAutoConfiguration.class,
                AgentArkReactiveWebAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasBean("agentArkReactiveRequestContextFilter");
              assertThat(context).hasBean("agentArkReactiveProblemDetailExceptionHandler");
              assertThat(context).doesNotHaveBean(AgentArkServletRequestContextFilter.class);
            });
  }

  /** 验证强类型 ID 使用规范字符串往返且未知枚举值不会静默变为 null。 */
  @Test
  void serializesStrongIdsAsCanonicalStrings() throws Exception {
    JsonMapper mapper = JsonMapper.builder().addModule(new AgentArkJacksonModule()).build();
    OrganizationId id = OrganizationId.generate();

    String json = mapper.writeValueAsString(id);

    assertThat(json).isEqualTo("\"" + id.asString() + "\"");
    assertThat(mapper.readValue(json, OrganizationId.class)).isEqualTo(id);
  }

  /** 验证领域错误映射保留稳定码和关联 ID，未知错误不泄露原始异常消息。 */
  @Test
  void mapsProblemDetailsWithoutLeakingUnknownError() {
    RequestContext context =
        new RequestContext("request-1", "1234567890abcdef1234567890abcdef", Optional.empty());
    ProblemDetailFactory factory = new ProblemDetailFactory();
    var domain =
        factory.create(
            new DomainException(new DomainErrorCode("ARK-TEST-INVALID-00001"), "字段不合法", List.of()),
            context);
    var unknown = factory.create(new IllegalStateException("secret-token-value"), context);

    assertThat(domain.getProperties()).containsEntry("code", "ARK-TEST-INVALID-00001");
    assertThat(domain.getProperties()).containsEntry("requestId", "request-1");
    assertThat(unknown.getDetail()).doesNotContain("secret-token-value");
  }

  /** 验证 Bean Validation 错误映射字段路径但不回显被拒绝的非法值。 */
  @Test
  void mapsBeanValidationWithoutRejectedValue() {
    @SuppressWarnings("unchecked")
    ConstraintViolation<Object> constraint = mock(ConstraintViolation.class);
    Path path = mock(Path.class);
    when(path.toString()).thenReturn("request.name");
    when(constraint.getPropertyPath()).thenReturn(path);
    when(constraint.getMessage()).thenReturn("不能为空");
    when(constraint.getInvalidValue()).thenReturn("secret-invalid-value");
    RequestContext context =
        new RequestContext("request-1", "1234567890abcdef1234567890abcdef", Optional.empty());

    var problem =
        new ProblemDetailFactory()
            .create(new ConstraintViolationException(Set.of(constraint)), context);

    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getProperties()).containsKey("violations");
    assertThat(problem.toString()).doesNotContain("secret-invalid-value");
  }
}
