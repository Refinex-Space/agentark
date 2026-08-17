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

package space.refinex.agentark.foundation.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

/**
 * 验证 Observability Starter 的条件化装配、敏感字段清理和 W3C Trace 解析。
 *
 * @author refinex
 */
class AgentArkObservabilityAutoConfigurationTest {

  /** 可观测性自动配置测试运行器。 */
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
          .withConfiguration(AutoConfigurations.of(AgentArkObservabilityAutoConfiguration.class));

  /** 验证默认启用时装配 OTel/Micrometer 记录器、策略和 JSON 日志写入器。 */
  @Test
  void configuresObservabilityFoundationByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(AgentArkTelemetry.class);
          assertThat(context).hasSingleBean(MetricTagPolicy.class);
          assertThat(context).hasSingleBean(SensitiveDataSanitizer.class);
          assertThat(context).hasSingleBean(StructuredLogWriter.class);
        });
  }

  /** 验证显式禁用时不创建任何 AgentArk 可观测 Bean。 */
  @Test
  void backsOffWhenDisabled() {
    contextRunner
        .withPropertyValues("agentark.foundation.observability.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(AgentArkTelemetry.class));
  }

  /** 验证 Secret、Prompt、Tool 参数和文档正文默认均不会进入结构化日志。 */
  @Test
  void redactsSensitiveAndContentFieldsByDefault() throws Exception {
    SensitiveDataSanitizer sanitizer =
        new SensitiveDataSanitizer(ObservabilityDataPolicy.secureDefaults());
    Map<String, String> sanitized =
        sanitizer.sanitize(
            Map.of(
                "authorization", "Bearer secret-value",
                "prompt.text", "private prompt",
                "tool_argument", "dangerous argument",
                "document_text", "private document",
                "outcome", "success"));
    StructuredLogWriter writer = new StructuredLogWriter(JsonMapper.builder().build(), sanitizer);
    String json =
        writer.write(
            "INFO",
            "operation completed",
            Optional.of("1234567890abcdef1234567890abcdef"),
            sanitized);

    assertThat(sanitized)
        .containsEntry("authorization", "[REDACTED]")
        .containsEntry("prompt.text", "[REDACTED]")
        .containsEntry("tool_argument", "[REDACTED]")
        .containsEntry("document_text", "[REDACTED]")
        .containsEntry("outcome", "success");
    assertThat(json)
        .doesNotContain("secret-value", "private prompt", "dangerous argument", "private document")
        .doesNotContain("\n", "\r");
  }

  /** 验证 W3C traceparent 可规范往返且拒绝全零 Trace ID。 */
  @Test
  void parsesStrictW3cTraceParent() {
    String traceParent = "00-1234567890abcdef1234567890abcdef-1234567890abcdef-01";

    assertThat(W3cTraceContext.parse(traceParent).toString()).isEqualTo(traceParent);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> W3cTraceContext.parse("00-00000000000000000000000000000000-1234567890abcdef-01"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** 验证业务 Span 生成有效 Trace ID 和 Timer，且高基数租户标签不会进入指标。 */
  @Test
  void recordsTraceAndBoundedDurationMetric() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
    try {
      OpenTelemetrySdk openTelemetry =
          OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
      AgentArkTelemetry telemetry =
          new AgentArkTelemetry(
              Optional.of(registry),
              Optional.of(openTelemetry),
              new MetricTagPolicy(Set.of("operation", "outcome")));
      AtomicReference<String> traceId = new AtomicReference<>();

      telemetry.inSpan(
          SpanConvention.RUNTIME,
          "turn.execute",
          Map.of(
              "operation", "turn.execute",
              "projectId", "019d0000-0000-7000-8000-000000000001"),
          () -> {
            traceId.set(telemetry.currentTraceId().orElseThrow());
            return null;
          });

      assertThat(traceId.get()).matches("[0-9a-f]{32}");
      var timer = registry.get("agentark.runtime.turn.execute.duration").timer();
      assertThat(timer.count()).isEqualTo(1L);
      assertThat(timer.getId().getTags())
          .extracting(io.micrometer.core.instrument.Tag::getKey)
          .containsExactlyInAnyOrder("operation", "outcome")
          .doesNotContain("projectId");
    } finally {
      tracerProvider.close();
      registry.close();
    }
  }
}
